package de.puzzlewerk.data.daily

import app.cash.turbine.test
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.game.score.Score
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FakeDailyStatsRepositoryTest {
    // §7.2-konsistente Fixture: Punkte/Sterne folgen aus moves und par.
    private fun record(moves: Int = 3): DailyRecord = consistentDailyRecord(moves = moves, par = 3)

    @Test
    fun `Erststart emittiert Success EMPTY`() =
        runTest {
            FakeDailyStatsRepository().stats.first() shouldBe DataResult.Success(DailyStats.EMPTY)
        }

    @Test
    fun `recordPlayed zaehlt jeden Kalendertag genau einmal (R38)`() =
        runTest {
            val repository = FakeDailyStatsRepository()
            repository.recordPlayed(100) shouldBe WriteResult.Success
            repository.recordPlayed(100) shouldBe WriteResult.Success
            repository.recordPlayed(101) shouldBe WriteResult.Success
            (repository.stats.first() as DataResult.Success).value.playedTotal shouldBe 2
        }

    @Test
    fun `recordSolved folgt der Serien-Semantik aus Paragraf 10_3`() =
        runTest {
            val repository = FakeDailyStatsRepository()
            repository.stats.test {
                awaitItem() shouldBe DataResult.Success(DailyStats.EMPTY)
                repository.recordSolved(100, record()) shouldBe WriteResult.Success
                (awaitItem() as DataResult.Success).value.currentStreak shouldBe 1
                repository.recordSolved(101, record()) shouldBe WriteResult.Success
                (awaitItem() as DataResult.Success).value.currentStreak shouldBe 2
                // verpasster Tag 102: Serie startet neu, laengsteSerie bleibt
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
    fun `Zweitloesung desselben Tages ist ein No-op mit Success (R38)`() =
        runTest {
            val repository = FakeDailyStatsRepository()
            repository.recordSolved(100, record()) shouldBe WriteResult.Success
            repository.recordSolved(100, record(moves = 9)) shouldBe WriteResult.Success
            val stats = (repository.stats.first() as DataResult.Success).value
            stats.solvedTotal shouldBe 1
            stats.resultByEpochDay.getValue(100L).moves shouldBe 3
        }

    @Test
    fun `failWith simuliert einen Fehlerbestand, reset ist der Ausweg`() =
        runTest {
            val repository = FakeDailyStatsRepository()
            val failure = PersistenceFailure.UnsupportedVersion(storedVersion = 2, supportedVersion = 1)
            repository.failWith(failure)
            repository.stats.first() shouldBe DataResult.Failure(failure)
            repository.recordPlayed(100) shouldBe WriteResult.Failure(failure)
            repository.recordSolved(100, record()) shouldBe WriteResult.Failure(failure)
            repository.reset() shouldBe WriteResult.Success
            repository.stats.first() shouldBe DataResult.Success(DailyStats.EMPTY)
        }

    @Test
    fun `ungueltige DailyRecords sind Programmierfehler (Regel C3)`() =
        runTest {
            val repository = FakeDailyStatsRepository()
            shouldThrow<IllegalArgumentException> {
                repository.recordSolved(100, DailyRecord(moves = 0, par = 3, score = Score(points = 1500, stars = 3)))
            }
        }
}
