package de.puzzlewerk.data.progress

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

class FakeProgressRepositoryTest {
    @Test
    fun `Erststart emittiert Success EMPTY`() =
        runTest {
            FakeProgressRepository().progress.first() shouldBe DataResult.Success(CampaignProgress.EMPTY)
        }

    @Test
    fun `recordSolved verhaelt sich wie die DataStore-Implementierung`() =
        runTest {
            val repository = FakeProgressRepository()
            repository.progress.test {
                awaitItem() shouldBe DataResult.Success(CampaignProgress.EMPTY)
                repository.recordSolved(2, Score(points = 1200, stars = 2)) shouldBe WriteResult.Success
                awaitItem() shouldBe DataResult.Success(CampaignProgress(mapOf(2 to Score(points = 1200, stars = 2))))
                // schlechteres Ergebnis: No-op ohne neue Emission
                repository.recordSolved(2, Score(points = 1000, stars = 1)) shouldBe WriteResult.Success
                repository.recordSolved(2, Score(points = 1500, stars = 3)) shouldBe WriteResult.Success
                awaitItem() shouldBe DataResult.Success(CampaignProgress(mapOf(2 to Score(points = 1500, stars = 3))))
            }
        }

    @Test
    fun `failWith simuliert einen Fehlerbestand, reset ist der Ausweg`() =
        runTest {
            val repository = FakeProgressRepository()
            val failure = PersistenceFailure.Corrupted("progress: Testkorruption")
            repository.failWith(failure)
            repository.progress.first() shouldBe DataResult.Failure(failure)
            repository.recordSolved(1, Score(points = 1500, stars = 3)) shouldBe WriteResult.Failure(failure)
            repository.reset() shouldBe WriteResult.Success
            repository.progress.first() shouldBe DataResult.Success(CampaignProgress.EMPTY)
        }

    @Test
    fun `Initialbestand ist konfigurierbar (Previews)`() =
        runTest {
            val initial = CampaignProgress(mapOf(1 to Score(points = 1500, stars = 3)))
            FakeProgressRepository(initial).progress.first() shouldBe DataResult.Success(initial)
            initial.highestSolvedLevel shouldBe 1
        }

    @Test
    fun `ungueltige Parameter sind Programmierfehler (Regel C3)`() =
        runTest {
            val repository = FakeProgressRepository()
            shouldThrow<IllegalArgumentException> { repository.recordSolved(0, Score(points = 1500, stars = 3)) }
            shouldThrow<IllegalArgumentException> { repository.recordSolved(1, Score(points = 1501, stars = 3)) }
        }
}
