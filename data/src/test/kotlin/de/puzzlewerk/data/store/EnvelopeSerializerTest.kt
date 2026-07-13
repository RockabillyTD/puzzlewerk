package de.puzzlewerk.data.store

import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.progress.CampaignProgress
import de.puzzlewerk.data.progress.ProgressSchemaV1
import de.puzzlewerk.game.score.Score
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Byte-Kappung und defensiver Failed-Zweig (Security-Review PW-3.2). */
class EnvelopeSerializerTest {
    private val serializer = EnvelopeSerializer(ProgressSchemaV1, StoreState.Loaded(CampaignProgress.EMPTY))

    @Test
    fun `readFrom kappt uebergrosse Dateien VOR dem Parsen (S4-Groessenlimit)`() =
        runTest {
            val oversized = ByteArrayInputStream(ByteArray(MAX_STORE_FILE_BYTES + 1) { 'x'.code.toByte() })
            serializer.readFrom(oversized) shouldBe
                StoreState.Failed(PersistenceFailure.Corrupted("progress: Datei überschreitet Maximalgröße"))
        }

    @Test
    fun `genau an der Grenze greift die Kappung nicht, es entscheidet der Parser`() =
        runTest {
            val atLimit = ByteArrayInputStream(ByteArray(MAX_STORE_FILE_BYTES) { 'x'.code.toByte() })
            serializer.readFrom(atLimit) shouldBe
                StoreState.Failed(
                    PersistenceFailure.Corrupted(
                        "progress: Envelope ist kein JSON-Objekt mit genau den Feldern version und payload",
                    ),
                )
        }

    @Test
    fun `readFrom akkumuliert Teil-Reads korrekt und decodiert normal (minSdk-26-sichere Lesart)`() =
        runTest {
            val state = StoreState.Loaded(CampaignProgress(mapOf(2 to Score(points = 1200, stars = 2))))
            val output = ByteArrayOutputStream()
            serializer.writeTo(state, output)
            // Stream, der pro read() nur 1 Byte liefert: beweist, dass die Schleife
            // Teil-Reads akkumuliert (der Kern des API-33-readNBytes-Bugs, PW-3.2).
            val dribbling = SingleByteInputStream(output.toByteArray())
            serializer.readFrom(dribbling) shouldBe state
        }

    @Test
    fun `readFrom kappt Uebergroesse auch bei 1-Byte-Teil-Reads (Grenze plus 1)`() =
        runTest {
            val dribbling = SingleByteInputStream(ByteArray(MAX_STORE_FILE_BYTES + 1) { 'x'.code.toByte() })
            serializer.readFrom(dribbling) shouldBe
                StoreState.Failed(PersistenceFailure.Corrupted("progress: Datei überschreitet Maximalgröße"))
        }

    @Test
    fun `readFrom liest exakt an der Grenze auch bei 1-Byte-Teil-Reads vollstaendig (Kappung greift nicht)`() =
        runTest {
            val dribbling = SingleByteInputStream(ByteArray(MAX_STORE_FILE_BYTES) { 'x'.code.toByte() })
            serializer.readFrom(dribbling) shouldBe
                StoreState.Failed(
                    PersistenceFailure.Corrupted(
                        "progress: Envelope ist kein JSON-Objekt mit genau den Feldern version und payload",
                    ),
                )
        }

    @Test
    fun `writeTo verweigert Fehlerzustaende defensiv als IOException ohne Bytes zu schreiben`() =
        runTest {
            val output = ByteArrayOutputStream()
            val failed = StoreState.Failed(PersistenceFailure.Corrupted("progress: Testkorruption"))
            shouldThrow<IOException> { serializer.writeTo(failed, output) }
            output.size() shouldBe 0
        }

    @Test
    fun `writeTo readFrom Roundtrip ist verlustfrei`() =
        runTest {
            val state = StoreState.Loaded(CampaignProgress(mapOf(1 to Score(points = 1500, stars = 3))))
            val output = ByteArrayOutputStream()
            serializer.writeTo(state, output)
            serializer.readFrom(ByteArrayInputStream(output.toByteArray())) shouldBe state
        }
}

/**
 * [InputStream], der pro `read(buf, off, len)`-Aufruf HÖCHSTENS ein Byte liefert.
 * Simuliert Teil-Reads, wie sie echte Streams jederzeit erlauben — der Fix muss
 * über die Schleife akkumulieren statt sich auf einen einzelnen read zu verlassen.
 */
private class SingleByteInputStream(private val data: ByteArray) : InputStream() {
    private var pos = 0

    override fun read(): Int = if (pos < data.size) data[pos++].toInt() and 0xFF else -1

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        if (len == 0) return 0
        if (pos >= data.size) return -1
        b[off] = data[pos++]
        return 1
    }
}
