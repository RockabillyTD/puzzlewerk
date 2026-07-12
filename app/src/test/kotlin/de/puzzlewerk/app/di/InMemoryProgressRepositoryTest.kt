package de.puzzlewerk.app.di

import app.cash.turbine.test
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.data.progress.CampaignProgress
import de.puzzlewerk.game.score.Score
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Vertragstests der Übergangs-Implementierung (Bestwert-Semantik §7.2). */
class InMemoryProgressRepositoryTest {
    private val repository = InMemoryProgressRepository()

    private suspend fun currentProgress(): CampaignProgress {
        var value: CampaignProgress? = null
        repository.progress.test {
            value = (awaitItem() as DataResult.Success).value
            cancelAndIgnoreRemainingEvents()
        }
        return checkNotNull(value)
    }

    @Test
    fun `startet mit leerem Fortschritt`() =
        runTest {
            assertEquals(CampaignProgress.EMPTY, currentProgress())
        }

    @Test
    fun `recordSolved speichert ein neues Ergebnis`() =
        runTest {
            val result = repository.recordSolved(1, Score(points = 1200, stars = 2))

            assertEquals(WriteResult.Success, result)
            assertEquals(Score(points = 1200, stars = 2), currentProgress().bestByLevel[1])
        }

    @Test
    fun `nur ein besseres Ergebnis ueberschreibt den Bestand`() =
        runTest {
            repository.recordSolved(1, Score(points = 1200, stars = 2))

            repository.recordSolved(1, Score(points = 1000, stars = 1))
            assertEquals(Score(points = 1200, stars = 2), currentProgress().bestByLevel[1])

            // Gleiches Ergebnis: No-op mit Success (Interface-Vertrag).
            assertEquals(WriteResult.Success, repository.recordSolved(1, Score(points = 1200, stars = 2)))
            assertEquals(Score(points = 1200, stars = 2), currentProgress().bestByLevel[1])

            repository.recordSolved(1, Score(points = 1500, stars = 3))
            assertEquals(Score(points = 1500, stars = 3), currentProgress().bestByLevel[1])
        }

    @Test
    fun `reset leert den gesamten Fortschritt`() =
        runTest {
            repository.recordSolved(7, Score(points = 1500, stars = 3))

            assertEquals(WriteResult.Success, repository.reset())

            assertEquals(CampaignProgress.EMPTY, currentProgress())
        }

    @Test
    fun `ungueltige Levelnummer ist ein Programmierfehler`() =
        runTest {
            for (levelNumber in listOf(0, 51, -1)) {
                try {
                    repository.recordSolved(levelNumber, Score(points = 1000, stars = 1))
                    error("Levelnummer $levelNumber hätte abgelehnt werden müssen")
                } catch (expected: IllegalArgumentException) {
                    // erwartet (Regel C3: Vorbedingung)
                }
            }
        }
}
