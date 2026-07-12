package de.puzzlewerk.data.progress

import app.cash.turbine.test
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.game.score.Score
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreProgressRepositoryTest {
    @TempDir
    lateinit var tempDir: File

    private fun storeFile(): File = File(tempDir, "progress.json")

    private fun TestScope.newRepository(): DataStoreProgressRepository =
        DataStoreProgressRepository(
            backgroundScope,
            ::storeFile,
        )

    @Test
    fun `Erststart ohne Datei liefert Success EMPTY`() =
        runTest {
            newRepository().progress.first() shouldBe DataResult.Success(CampaignProgress.EMPTY)
        }

    @Test
    fun `recordSolved speichert und der Flow emittiert den neuen Bestand`() =
        runTest {
            val repository = newRepository()
            repository.progress.test {
                awaitItem() shouldBe DataResult.Success(CampaignProgress.EMPTY)
                repository.recordSolved(1, Score(points = 1500, stars = 3)) shouldBe WriteResult.Success
                awaitItem() shouldBe
                    DataResult.Success(CampaignProgress(mapOf(1 to Score(points = 1500, stars = 3))))
            }
        }

    @Test
    fun `nur ein besseres Ergebnis ueberschreibt den Bestwert (Paragraf 7_2)`() =
        runTest {
            val repository = newRepository()
            repository.recordSolved(3, Score(points = 1200, stars = 2)) shouldBe WriteResult.Success
            // schlechter und gleich gut: No-op mit Success
            repository.recordSolved(3, Score(points = 1100, stars = 1)) shouldBe WriteResult.Success
            repository.recordSolved(3, Score(points = 1200, stars = 2)) shouldBe WriteResult.Success
            repository.progress.first() shouldBe
                DataResult.Success(CampaignProgress(mapOf(3 to Score(points = 1200, stars = 2))))
            repository.recordSolved(3, Score(points = 1450, stars = 3)) shouldBe WriteResult.Success
            repository.progress.first() shouldBe
                DataResult.Success(CampaignProgress(mapOf(3 to Score(points = 1450, stars = 3))))
        }

    @Test
    fun `geschriebene Datei entspricht der Golden-Datei v1`() =
        runTest {
            val repository = newRepository()
            // absichtlich unsortiert gespeichert — encode sortiert nach Level
            repository.recordSolved(5, Score(points = 1000, stars = 1))
            repository.recordSolved(1, Score(points = 1500, stars = 3))
            repository.recordSolved(2, Score(points = 1200, stars = 2))
            storeFile().readText() shouldBe goldenText()
        }

    @Test
    fun `Golden-Datei v1 wird zum erwarteten Bestand gelesen`() =
        runTest {
            storeFile().writeText(goldenText())
            newRepository().progress.first() shouldBe
                DataResult.Success(
                    CampaignProgress(
                        mapOf(
                            1 to Score(points = 1500, stars = 3),
                            2 to Score(points = 1200, stars = 2),
                            5 to Score(points = 1000, stars = 1),
                        ),
                    ),
                )
        }

    @Test
    fun `korrupte Datei ist Failure Corrupted und wird nie stillschweigend ueberschrieben`() =
        runTest {
            storeFile().writeText("{")
            val repository = newRepository()
            repository.progress
                .first()
                .shouldBeInstanceOf<DataResult.Failure>()
                .failure
                .shouldBeInstanceOf<PersistenceFailure.Corrupted>()
            repository
                .recordSolved(1, Score(points = 1500, stars = 3))
                .shouldBeInstanceOf<WriteResult.Failure>()
                .failure
                .shouldBeInstanceOf<PersistenceFailure.Corrupted>()
            storeFile().readText() shouldBe "{"
        }

    @Test
    fun `reset ist der definierte Ausweg aus einem korrupten Bestand`() =
        runTest {
            storeFile().writeText("nicht json")
            val repository = newRepository()
            repository.reset() shouldBe WriteResult.Success
            repository.progress.first() shouldBe DataResult.Success(CampaignProgress.EMPTY)
            repository.recordSolved(1, Score(points = 1000, stars = 1)) shouldBe WriteResult.Success
        }

    @Test
    fun `neuere Schemaversion ist Failure UnsupportedVersion ohne Ueberschreiben`() =
        runTest {
            val newerVersion = """{"version":2,"payload":{"entries":[]}}"""
            storeFile().writeText(newerVersion)
            val repository = newRepository()
            val expected = PersistenceFailure.UnsupportedVersion(storedVersion = 2, supportedVersion = 1)
            repository.progress.first() shouldBe DataResult.Failure(expected)
            repository.recordSolved(1, Score(points = 1500, stars = 3)) shouldBe WriteResult.Failure(expected)
            storeFile().readText() shouldBe newerVersion
        }

    @Test
    fun `doppelter Level-Eintrag in der Datei ist Corrupted`() =
        runTest {
            val duplicate = """{"level":1,"points":1500,"stars":3},{"level":1,"points":1000,"stars":1}"""
            storeFile().writeText("""{"version":1,"payload":{"entries":[$duplicate]}}""")
            newRepository()
                .progress
                .first()
                .shouldBeInstanceOf<DataResult.Failure>()
                .failure
                .shouldBeInstanceOf<PersistenceFailure.Corrupted>()
        }

    @Test
    fun `ungueltige Parameter sind Programmierfehler (Regel C3)`() =
        runTest {
            val repository = newRepository()
            shouldThrow<IllegalArgumentException> { repository.recordSolved(0, Score(points = 1500, stars = 3)) }
            shouldThrow<IllegalArgumentException> { repository.recordSolved(51, Score(points = 1500, stars = 3)) }
            shouldThrow<IllegalArgumentException> { repository.recordSolved(1, Score(points = 999, stars = 1)) }
            shouldThrow<IllegalArgumentException> { repository.recordSolved(1, Score(points = 1501, stars = 3)) }
        }

    @Test
    fun `reset setzt einen gefuellten Bestand auf EMPTY zurueck (Paragraf 12_5)`() =
        runTest {
            val repository = newRepository()
            repository.recordSolved(4, Score(points = 1300, stars = 2))
            repository.reset() shouldBe WriteResult.Success
            repository.progress.first() shouldBe DataResult.Success(CampaignProgress.EMPTY)
        }

    private fun goldenText(): String =
        requireNotNull(
            javaClass.getResource("/golden/progress_v1.json"),
        ).readText().trim()
}
