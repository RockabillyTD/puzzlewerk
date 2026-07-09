package de.puzzlewerk.game.engine

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.trace.TraceResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * API-Vertragstest: prueft die Wert-Semantik der Zug-Typen und dass die
 * Schnittstelle wie vorgesehen implementier- und benutzbar ist (PW-2.4
 * implementiert die echte Engine gegen dieselben Signaturen).
 */
class EngineApiTest {
    private val level =
        LevelDefinition(
            board = Board(radius = 2, elements = mapOf(HexCoord(0, 0) to Element.Mirror(Orientation.ZERO))),
            par = 1,
            tier = Difficulty.D1,
            seed = 1L,
        )

    private val emptyTrace = TraceResult(segments = emptyList(), received = emptyMap(), solved = false)

    // Minimale Fake-Engine, die nur den API-Vertrag der Typen demonstriert.
    private val engine =
        object : GameEngine {
            override fun newGame(level: LevelDefinition): MoveResult.Applied {
                val state =
                    GameState(
                        level = level,
                        orientations = mapOf(HexCoord(0, 0) to Orientation.ZERO),
                        history = emptyList(),
                        solved = false,
                    )
                return MoveResult.Applied(state, emptyTrace)
            }

            override fun applyMove(
                state: GameState,
                move: Move,
            ): MoveResult =
                when (move) {
                    is Move.Rotate -> MoveResult.Invalid(InvalidMoveReason.NOT_ROTATABLE)
                    Move.Undo -> MoveResult.Invalid(InvalidMoveReason.HISTORY_EMPTY)
                    Move.Reset -> MoveResult.Applied(state, emptyTrace)
                }
        }

    @Test
    fun `Move-Typen sind Werte`() {
        Move.Rotate(HexCoord(1, 2)) shouldBe Move.Rotate(HexCoord(1, 2))
        Move.Rotate(HexCoord(1, 2)) shouldNotBe Move.Rotate(HexCoord(2, 1))
        Move.Undo shouldBe Move.Undo
        Move.Reset shouldBe Move.Reset
        Move.Undo.toString() shouldBe "Undo"
        Move.Reset.toString() shouldBe "Reset"
    }

    @Test
    fun `newGame liefert Startzustand mit initialer Auswertung`() {
        val start = engine.newGame(level)

        start.state.level shouldBe level
        start.state.moveCount shouldBe 0
        start.state.solved shouldBe false
        start.trace shouldBe emptyTrace
    }

    @Test
    fun `applyMove liefert sealed Ergebnisse mit benanntem Grund`() {
        val start = engine.newGame(level).state

        val rotate = engine.applyMove(start, Move.Rotate(HexCoord(1, 1)))
        val undo = engine.applyMove(start, Move.Undo)
        val reset = engine.applyMove(start, Move.Reset)

        rotate.shouldBeInstanceOf<MoveResult.Invalid>().reason shouldBe InvalidMoveReason.NOT_ROTATABLE
        undo.shouldBeInstanceOf<MoveResult.Invalid>().reason shouldBe InvalidMoveReason.HISTORY_EMPTY
        reset.shouldBeInstanceOf<MoveResult.Applied>().state shouldBe start
    }

    @Test
    fun `alle Invalid-Gruende aus dem Design sind abgebildet (R27, R28, R32)`() {
        InvalidMoveReason.entries.map { it.name } shouldBe
            listOf("NO_ELEMENT", "NOT_ROTATABLE", "ALREADY_SOLVED", "HISTORY_EMPTY")
    }
}
