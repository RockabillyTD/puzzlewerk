package de.puzzlewerk.game.engine

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.trace.DefaultTracer
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Property-Tests fuer die Invarianten I6, I7, I9 und I10 (Design Paragraf 14)
 * mit festen Seeds. I5 wird erschoepfend in DefaultScoreCalculatorTest geprueft.
 *
 * Fixture: ein UNLOESBARES Level (nur rotes Licht, gruener Kristall) — damit
 * sperrt kein zufaelliger Zug die Partie (Paragraf 6.3) und beliebig lange
 * Zugfolgen bleiben anwendbar.
 */
class DefaultGameEnginePropertyTest {
    private val engine: GameEngine = defaultGameEngine(DefaultTracer)

    private val mirrorCell = HexCoord(0, 0)
    private val splitterCell = HexCoord(0, 1)
    private val emptyCell = HexCoord(1, 0)
    private val sourceCell = HexCoord(-2, 0)
    private val rotatableCells = listOf(mirrorCell, splitterCell)

    private val level =
        LevelDefinition(
            board =
                Board(
                    radius = 2,
                    elements =
                        mapOf(
                            sourceCell to Element.Source(LightColor.RED, Direction.EAST),
                            mirrorCell to Element.Mirror(Orientation(3)),
                            splitterCell to Element.Splitter(Orientation(4)),
                            HexCoord(2, -2) to Element.Crystal(LightColor.GREEN),
                        ),
                ),
            par = 2,
            tier = Difficulty.D2,
            seed = 0xBEEFL,
        )

    private fun randomMove(random: Random): Move =
        when (random.nextInt(6)) {
            0 -> Move.Rotate(mirrorCell)
            1 -> Move.Rotate(splitterCell)
            2 -> Move.Rotate(emptyCell)
            3 -> Move.Rotate(sourceCell)
            4 -> Move.Undo
            else -> Move.Reset
        }

    private fun applied(
        state: GameState,
        move: Move,
    ): MoveResult.Applied = engine.applyMove(state, move).shouldBeInstanceOf<MoveResult.Applied>()

    @Test
    fun `I6 - Zugzaehler entspricht nach jeder Zugfolge exakt der Verlaufslaenge`() {
        val random = Random(0x505249534D41L) // "PRISMA"
        var state = engine.newGame(level).state
        var expectedCount = 0

        repeat(600) {
            val move = randomMove(random)
            when (val result = engine.applyMove(state, move)) {
                is MoveResult.Applied -> {
                    expectedCount =
                        when (move) {
                            is Move.Rotate -> expectedCount + 1
                            Move.Undo -> expectedCount - 1
                            Move.Reset -> 0
                        }
                    state = result.state
                }
                is MoveResult.Invalid -> Unit // Zaehler und Verlauf unveraendert
            }

            state.moveCount shouldBe expectedCount
            state.moveCount shouldBe state.history.size
            state.moveCount shouldBeGreaterThanOrEqual 0
        }
    }

    @Test
    fun `I7 - Applied liefert gueltige Zustaende, Invalid laesst den Zustand unveraendert`() {
        val random = Random(20260710L)
        var state = engine.newGame(level).state

        repeat(600) {
            val move = randomMove(random)
            val snapshot = state.copy()

            when (val result = engine.applyMove(state, move)) {
                is MoveResult.Applied -> {
                    // Schema bleibt erfuellt: Levelbrett unveraendert, Orientierungs-
                    // schluessel exakt die drehbaren Zellen, Verlauf nur drehbare Zellen
                    result.state.level shouldBe level
                    result.state.orientations.keys shouldBe rotatableCells.toSet()
                    result.state.history.all { it in rotatableCells } shouldBe true
                    result.state.solved shouldBe result.trace.solved
                    state = result.state
                }
                is MoveResult.Invalid -> state shouldBe snapshot
            }
        }
    }

    @Test
    fun `I9 - gleiche End-Orientierungen ergeben gleichen Zustand und gleichen Trace`() {
        val random = Random(0x4C4556454CL) // "LEVEL"
        repeat(100) {
            val rotations =
                buildList {
                    repeat(random.nextInt(1, 13)) { add(Move.Rotate(rotatableCells.random(random))) }
                }
            val firstOrder = rotations.shuffled(random)
            val secondOrder = rotations.shuffled(random)

            val first = playRotations(firstOrder)
            val second = playRotations(secondOrder)

            first.state.orientations shouldBe second.state.orientations
            first.state.currentBoard() shouldBe second.state.currentBoard()
            first.trace shouldBe second.trace
            first.state.moveCount shouldBe second.state.moveCount
        }
    }

    @Test
    fun `I10 - Undo nach Rotate ist die Identitaet in Zustand, Zaehler und Trace`() {
        val random = Random(0x554E444FL) // "UNDO"
        repeat(200) {
            val prefix = List(random.nextInt(0, 12)) { Move.Rotate(rotatableCells.random(random)) }
            val before = playRotations(prefix)
            before.state.solved.shouldBeFalse()

            val rotated = applied(before.state, Move.Rotate(rotatableCells.random(random)))
            val undone = applied(rotated.state, Move.Undo)

            undone.state shouldBe before.state
            undone.state.moveCount shouldBe before.state.moveCount
            undone.trace shouldBe before.trace
        }
    }

    /** Spielt [rotations] ab dem frischen Start; jede Rotation muss anwendbar sein. */
    private fun playRotations(rotations: List<Move.Rotate>): MoveResult.Applied {
        var current = engine.newGame(level)
        for (move in rotations) {
            current = applied(current.state, move)
        }
        return current
    }
}
