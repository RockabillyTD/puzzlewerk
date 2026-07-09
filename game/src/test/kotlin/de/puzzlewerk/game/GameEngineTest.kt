package de.puzzlewerk.game

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class GameEngineTest {

    @Test
    fun `newGame startet mit Punktestand 0 und Status RUNNING`() {
        val state = GameEngine.newGame(levelId = 1, seed = 42L)

        state.levelId shouldBe 1
        state.seed shouldBe 42L
        state.score shouldBe 0
        state.movesPlayed shouldBe 0
        state.status shouldBe GameStatus.RUNNING
    }

    @Test
    fun `newGame lehnt ungueltige Level-Nummern ab`() {
        shouldThrow<IllegalArgumentException> {
            GameEngine.newGame(levelId = 0, seed = 1L)
        }
    }

    @Test
    fun `applyScore addiert Punkte und zaehlt den Zug`() {
        val start = GameEngine.newGame(levelId = 1, seed = 42L)

        val result = GameEngine.applyScore(start, points = 50)

        val applied = result.shouldBeInstanceOf<MoveResult.Applied>()
        applied.state.score shouldBe 50
        applied.state.movesPlayed shouldBe 1
    }

    @Test
    fun `applyScore lehnt negative Punkte als Rejected ab`() {
        val start = GameEngine.newGame(levelId = 1, seed = 42L)

        val result = GameEngine.applyScore(start, points = -1)

        val rejected = result.shouldBeInstanceOf<MoveResult.Rejected>()
        rejected.reason shouldBe RejectionReason.NEGATIVE_POINTS
    }

    @Test
    fun `applyScore lehnt Zuege auf beendete Partien ab`() {
        val finished = GameEngine.newGame(levelId = 1, seed = 42L).copy(status = GameStatus.WON)

        val result = GameEngine.applyScore(finished, points = 10)

        val rejected = result.shouldBeInstanceOf<MoveResult.Rejected>()
        rejected.reason shouldBe RejectionReason.GAME_ALREADY_FINISHED
    }

    @Test
    fun `applyScore veraendert den Eingabezustand nicht (pure function)`() {
        val start = GameEngine.newGame(levelId = 1, seed = 42L)

        GameEngine.applyScore(start, points = 50)

        start.score shouldBe 0
        start.movesPlayed shouldBe 0
    }
}
