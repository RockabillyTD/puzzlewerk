package de.puzzlewerk.data.store

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore-[Serializer] für versionierte Envelope-Stores (ADR-007).
 * Lesefehler (Korruption, Versionskonflikt) werden als [StoreState.Failed]
 * WERT zurückgegeben statt als Exception — so erreicht der Fehler die
 * Repository-Flows, ohne dass ein `ReplaceFileCorruptionHandler` Nutzdaten
 * wegwerfen könnte. E/A-Fehler des Streams propagieren als `IOException`
 * (DataStore-Vertrag) und werden im Repository auf `PersistenceFailure.Io`
 * abgebildet.
 */
internal class EnvelopeSerializer<T>(
    private val schema: StoreSchema<T>,
    override val defaultValue: StoreState<T>,
) : Serializer<StoreState<T>> {
    override suspend fun readFrom(input: InputStream): StoreState<T> =
        schema.decodeStoreText(input.readBytes().decodeToString())

    override suspend fun writeTo(
        t: StoreState<T>,
        output: OutputStream,
    ) {
        // Fehlerzustände werden nie persistiert (ADR-007); Verstoß = Programmierfehler.
        check(t is StoreState.Loaded) { "${schema.storeName}: Fehlerzustand darf nicht geschrieben werden" }
        output.write(schema.encodeStoreText(t.value).encodeToByteArray())
    }
}
