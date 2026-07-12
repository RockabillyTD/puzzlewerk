package de.puzzlewerk.data.store

import androidx.datastore.core.Serializer
import de.puzzlewerk.data.PersistenceFailure
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

// S4-Größenlimit (Security-Review PW-3.2): die größte reale Store-Datei (≤ 50 Level bzw.
// ~365 Daily-Einträge/Jahr) bleibt weit unter 100 KB (ADR-007) — 256 KiB kappen
// DoS-artige Riesen-Dateien, BEVOR sie den JSON-Parser erreichen.
internal const val MAX_STORE_FILE_BYTES: Int = 256 * 1024

/**
 * DataStore-[Serializer] für versionierte Envelope-Stores (ADR-007).
 * Lesefehler (Korruption, Versionskonflikt, Übergröße) werden als
 * [StoreState.Failed] WERT zurückgegeben statt als Exception — so erreicht
 * der Fehler die Repository-Flows, ohne dass ein
 * `ReplaceFileCorruptionHandler` Nutzdaten wegwerfen könnte. E/A-Fehler des
 * Streams propagieren als `IOException` (DataStore-Vertrag) und werden im
 * Repository auf `PersistenceFailure.Io` abgebildet.
 */
internal class EnvelopeSerializer<T>(
    private val schema: StoreSchema<T>,
    override val defaultValue: StoreState<T>,
) : Serializer<StoreState<T>> {
    override suspend fun readFrom(input: InputStream): StoreState<T> {
        // Kappung VOR dem Parsen: readNBytes liest höchstens limit + 1 Bytes.
        val bytes = input.readNBytes(MAX_STORE_FILE_BYTES + 1)
        if (bytes.size > MAX_STORE_FILE_BYTES) {
            return StoreState.Failed(
                PersistenceFailure.Corrupted("${schema.storeName}: Datei überschreitet Maximalgröße"),
            )
        }
        return schema.decodeStoreText(bytes.decodeToString())
    }

    override suspend fun writeTo(
        t: StoreState<T>,
        output: OutputStream,
    ) {
        when (t) {
            is StoreState.Failed ->
                // Fehlerzustände werden nie persistiert (ADR-007). Die Update-Pfade reichen
                // Failed unverändert durch (kein Write dank Gleichheits-Skip von DataStore);
                // sollte sich dieses Verhalten je ändern, bricht der Write hier defensiv als
                // IOException ab: DataStore verwirft die Temp-Datei, der Bestand bleibt
                // unangetastet und der Aufrufer erhält WriteResult.Failure(Io) statt eines
                // Crashs (Security-Review PW-3.2).
                throw IOException("${schema.storeName}: Fehlerzustand wird nicht persistiert (ADR-007)")
            is StoreState.Loaded -> output.write(schema.encodeStoreText(t.value).encodeToByteArray())
        }
    }
}
