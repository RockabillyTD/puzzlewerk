package de.puzzlewerk.game

import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

/**
 * Property-Tests für die Invarianten der Spiellogik (Vorbild für Phase 2,
 * siehe Agent-Grundregeln des test-engineers).
 */
class GameEnginePropertyTest {

    @Test
    fun `Invariante - Punktestand ist nie negativ`() {
        runBlocking {
            checkAll(Arb.long(), Arb.list(Arb.int(-100, 100), 0..50)) { seed, pointsList ->
                var state = GameEngine.newGame(levelId = 1, seed = seed)

                for (points in pointsList) {
                    when (val result = GameEngine.applyScore(state, points)) {
                        is MoveResult.Applied -> state = result.state
                        is MoveResult.Rejected -> Unit
                    }
                }

                state.score shouldBeGreaterThanOrEqual 0
            }
        }
    }

    @Test
    fun `Invariante - movesPlayed entspricht der Anzahl angewendeter Zuege`() {
        runBlocking {
            checkAll(Arb.long(), Arb.list(Arb.int(-100, 100), 0..50)) { seed, pointsList ->
                var state = GameEngine.newGame(levelId = 1, seed = seed)
                var appliedCount = 0

                for (points in pointsList) {
                    when (val result = GameEngine.applyScore(state, points)) {
                        is MoveResult.Applied -> {
                            state = result.state
                            appliedCount += 1
                        }

                        is MoveResult.Rejected -> Unit
                    }
                }

                state.movesPlayed shouldBe appliedCount
            }
        }
    }

    @Test
    fun `Invariante - gleicher Seed erzeugt identischen Startzustand`() {
        runBlocking {
            checkAll(Arb.long()) { seed ->
                GameEngine.newGame(levelId = 3, seed = seed) shouldBe
                    GameEngine.newGame(levelId = 3, seed = seed)
            }
        }
    }
}
