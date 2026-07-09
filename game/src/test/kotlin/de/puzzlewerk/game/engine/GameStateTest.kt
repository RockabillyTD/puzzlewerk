package de.puzzlewerk.game.engine

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class GameStateTest {
    private val mirrorCell = HexCoord(0, 0)
    private val splitterCell = HexCoord(1, 0)
    private val level =
        LevelDefinition(
            board =
                Board(
                    radius = 2,
                    elements =
                        mapOf(
                            HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                            mirrorCell to Element.Mirror(Orientation(5)),
                            splitterCell to Element.Splitter(Orientation(2)),
                            HexCoord(2, 0) to Element.Crystal(LightColor.WHITE),
                        ),
                ),
            par = 2,
            tier = Difficulty.D2,
            seed = 7L,
        )

    private fun freshState(): GameState =
        GameState(
            level = level,
            orientations = mapOf(mirrorCell to Orientation(5), splitterCell to Orientation(2)),
            history = emptyList(),
            solved = false,
        )

    @Test
    fun `moveCount entspricht der Verlaufslaenge (Invariante I6)`() {
        freshState().moveCount shouldBe 0
        freshState().copy(history = listOf(mirrorCell, mirrorCell, splitterCell)).moveCount shouldBe 3
    }

    @Test
    fun `currentBoard uebernimmt im Startzustand das Level-Brett unveraendert`() {
        freshState().currentBoard() shouldBe level.board
    }

    @Test
    fun `currentBoard traegt aktuelle Orientierungen in drehbare Elemente ein`() {
        val state =
            freshState().copy(
                orientations = mapOf(mirrorCell to Orientation(1), splitterCell to Orientation(2)),
                history = listOf(mirrorCell, mirrorCell),
            )

        val board = state.currentBoard()

        board[mirrorCell] shouldBe Element.Mirror(Orientation(1))
        board[splitterCell] shouldBe Element.Splitter(Orientation(2))
        // Nicht drehbare Elemente bleiben identisch
        board[HexCoord(-2, 0)] shouldBe Element.Source(LightColor.WHITE, Direction.EAST)
        board[HexCoord(2, 0)] shouldBe Element.Crystal(LightColor.WHITE)
        board.radius shouldBe level.board.radius
    }

    @Test
    fun `currentBoard mutiert weder Level noch Zustand (Regel C2)`() {
        val state =
            freshState().copy(orientations = mapOf(mirrorCell to Orientation(0), splitterCell to Orientation(0)))

        state.currentBoard()

        level.board.elements[mirrorCell] shouldBe Element.Mirror(Orientation(5))
        state.orientations[mirrorCell] shouldBe Orientation(0)
    }
}
