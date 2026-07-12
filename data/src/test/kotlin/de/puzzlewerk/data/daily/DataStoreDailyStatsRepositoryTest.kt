package de.puzzlewerk.data.daily

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

class DataStoreDailyStatsRepositoryTest {
    @TempDir
    lateinit var tempDir: File

    private fun storeFile(): File = File(tempDir, "daily_stats.json")

    private fun TestScope.newRepository(): DataStoreDailyStatsRepository =
        DataStoreDailyStatsRepository(
            backgroundScope,
            ::storeFile,
        )

    private fun record(
        moves: Int = 3,
        par: Int = 3,
        points: Int = 1500,
        stars: Int = 3,
    ): DailyRecord = DailyRecord(moves = moves, par = par, score = Score(points = points, stars = stars))

    private fun goldenText(): String =
        requireNotNull(
            javaClass.getResource("/golden/daily_stats_v1.json"),
        ).readText().trim()

    @Test
    fun `Erststart ohne Datei liefert Success EMPTY`() =
        runTest {
            newRepository().stats.first() shouldBe DataResult.Success(DailyStats.EMPTY)
        }

    @Test
    fun `Serien-Semantik Paragraf 10_3 wird persistiert und emittiert`() =
        runTest {
            val repository = newRepository()
            repository.stats.test {
                awaitItem() shouldBe DataResult.Success(DailyStats.EMPTY)
                repository.recordSolved(100, record()) shouldBe WriteResult.Success
                (awaitItem() as DataResult.Success).value.currentStreak shouldBe 1
                repository.recordSolved(101, record()) shouldBe WriteResult.Success
                (awaitItem() as DataResult.Success).value.currentStreak shouldBe 2
                // Luecke: Serie neu bei 1, laengsteSerie bleibt 2
                repository.recordSolved(103, record()) shouldBe WriteResult.Success
                val afterGap = (awaitItem() as DataResult.Success).value
                afterGap.currentStreak shouldBe 1
                afterGap.longestStreak shouldBe 2
                // Datum rueckwaerts (R38): Serie unveraendert, Ergebnis zaehlt
                repository.recordSolved(102, record()) shouldBe WriteResult.Success
                val afterBackwards = (awaitItem() as DataResult.Success).value
                afterBackwards.currentStreak shouldBe 1
                afterBackwards.solvedTotal shouldBe 4
            }
        }

    @Test
    fun `recordPlayed und recordSolved sind idempotent pro Datum (R38)`() =
        runTest {
            val repository = newRepository()
            repository.recordPlayed(100) shouldBe WriteResult.Success
            repository.recordPlayed(100) shouldBe WriteResult.Success
            repository.recordSolved(100, record()) shouldBe WriteResult.Success
            repository.recordSolved(100, record(moves = 9, points = 1200, stars = 1)) shouldBe WriteResult.Success
            val stats = (repository.stats.first() as DataResult.Success).value
            stats.playedTotal shouldBe 1
            stats.solvedTotal shouldBe 1
            stats.resultByEpochDay.getValue(100L).moves shouldBe 3
        }

    @Test
    fun `geschriebene Datei entspricht der Golden-Datei v1`() =
        runTest {
            val repository = newRepository()
            repository.recordPlayed(20508)
            repository.recordPlayed(20509)
            repository.recordPlayed(20510)
            repository.recordPlayed(20511)
            repository.recordSolved(20508, record())
            repository.recordSolved(20509, record(moves = 6, points = 1350, stars = 2))
            // Tag 20510 gespielt, aber nicht geloest; 20511 nach Luecke ⇒ Serie 1
            repository.recordSolved(20511, record(moves = 14, par = 4, points = 1000, stars = 1))
            storeFile().readText() shouldBe goldenText()
        }

    @Test
    fun `Golden-Datei v1 wird zur erwarteten Statistik gelesen`() =
        runTest {
            storeFile().writeText(goldenText())
            val stats = (newRepository().stats.first() as DataResult.Success).value
            stats shouldBe
                DailyStats(
                    playedTotal = 4,
                    solvedTotal = 3,
                    currentStreak = 1,
                    longestStreak = 2,
                    resultByEpochDay =
                        mapOf(
                            20508L to record(),
                            20509L to record(moves = 6, points = 1350, stars = 2),
                            20511L to record(moves = 14, par = 4, points = 1000, stars = 1),
                        ),
                )
        }

    @Test
    fun `korrupte Datei ist Failure Corrupted und wird nie stillschweigend ueberschrieben`() =
        runTest {
            storeFile().writeText("{")
            val repository = newRepository()
            repository.stats
                .first()
                .shouldBeInstanceOf<DataResult.Failure>()
                .failure
                .shouldBeInstanceOf<PersistenceFailure.Corrupted>()
            repository
                .recordPlayed(100)
                .shouldBeInstanceOf<WriteResult.Failure>()
                .failure
                .shouldBeInstanceOf<PersistenceFailure.Corrupted>()
            repository
                .recordSolved(100, record())
                .shouldBeInstanceOf<WriteResult.Failure>()
                .failure
                .shouldBeInstanceOf<PersistenceFailure.Corrupted>()
            storeFile().readText() shouldBe "{"
        }

    @Test
    fun `reset ist der definierte Ausweg aus einem korrupten Bestand`() =
        runTest {
            storeFile().writeText("kein json")
            val repository = newRepository()
            repository.reset() shouldBe WriteResult.Success
            repository.stats.first() shouldBe DataResult.Success(DailyStats.EMPTY)
            repository.recordSolved(100, record()) shouldBe WriteResult.Success
        }

    @Test
    fun `neuere Schemaversion ist Failure UnsupportedVersion ohne Ueberschreiben`() =
        runTest {
            val newerVersion = """{"version":2,"payload":{}}"""
            storeFile().writeText(newerVersion)
            val repository = newRepository()
            val expected = PersistenceFailure.UnsupportedVersion(storedVersion = 2, supportedVersion = 1)
            repository.stats.first() shouldBe DataResult.Failure(expected)
            repository.recordSolved(100, record()) shouldBe WriteResult.Failure(expected)
            storeFile().readText() shouldBe newerVersion
        }

    @Test
    fun `ungueltige DailyRecords sind Programmierfehler (Regel C3)`() =
        runTest {
            val repository = newRepository()
            shouldThrow<IllegalArgumentException> { repository.recordSolved(100, record(moves = 0)) }
            shouldThrow<IllegalArgumentException> { repository.recordSolved(100, record(par = 15)) }
        }
}
