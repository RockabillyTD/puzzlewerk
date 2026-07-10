package de.puzzlewerk.game.engine

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.DefaultTracer
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import kotlin.random.Random

private const val WALK_STEPS = 1500
private const val ORIENTATION_COUNT = 6
private const val SOLVING_MIRROR_STEPS = 1

/**
 * Unabhaengiger QS-Pass PW-2.3-QS: lange adversariale Rotate/Undo/Reset-
 * Mischsequenzen gegen ein UNABHAENGIG mitgefuehrtes Orakel (eigene mod-6-
 * Arithmetik, eigener Undo-Stack, eigene Geloest-Regel) statt gegen die
 * Engine-Buchfuehrung (Design Paragraf 6, Invarianten I6/I7/I10).
 *
 * Geloest-Orakel: Das Koeder-Level ist EXAKT dann geloest, wenn der Spiegel
 * Orientierung 1 hat (Paragraf 7.3; der Koeder auf (1,1) wird nie getroffen,
 * siehe [EngineQsFixtures]). Trace-Orakel: jedes Applied-Ergebnis muss
 * byte-identisch dem DefaultTracer-Lauf ueber ein UNABHAENGIG aus Level plus
 * Orakel-Orientierungen rekonstruiertes Brett entsprechen — faengt stale
 * Trace-Caches und currentBoard-Fehler gleichermassen.
 */
class EngineCounterOracleWalkTest {
    private val engine: GameEngine = defaultGameEngine(DefaultTracer)
    private val level = EngineQsFixtures.koederLevel()
    private val mirror = EngineQsFixtures.mirror
    private val decoy = EngineQsFixtures.decoy
    private val emptyCell = HexCoord(-1, 1)
    private val offBoard = HexCoord(3, 0)
    private val fixedCells = listOf(EngineQsFixtures.source, EngineQsFixtures.prism, EngineQsFixtures.redCrystal)
    private val startSteps = mapOf(mirror to 5, decoy to EngineQsFixtures.decoyStart.steps)

    /** Unabhaengiges Zaehler- und Orientierungs-Modell — bewusst OHNE Engine-Typen-Logik. */
    private inner class Oracle {
        val steps: MutableMap<HexCoord, Int> = startSteps.toMutableMap()
        val stack: MutableList<HexCoord> = mutableListOf()

        /** Geloest-Regel des Fixtures, handhergeleitet aus Paragraf 7.3. */
        val locked: Boolean get() = steps.getValue(mirror) == SOLVING_MIRROR_STEPS

        fun apply(move: Move) {
            when (move) {
                is Move.Rotate -> {
                    steps[move.cell] = (steps.getValue(move.cell) + 1) % ORIENTATION_COUNT
                    stack += move.cell
                }
                Move.Undo -> {
                    val cell = stack.removeAt(stack.size - 1)
                    steps[cell] = (steps.getValue(cell) + ORIENTATION_COUNT - 1) % ORIENTATION_COUNT
                }
                Move.Reset -> {
                    steps.clear()
                    steps += startSteps
                    stack.clear()
                }
            }
        }
    }

    @Test
    fun `Orakel-Walk Seed PRISMA - 1500 gemischte Zuege gegen unabhaengige Buchfuehrung`() {
        runWalk(Random(0x505249534D41L))
    }

    @Test
    fun `Orakel-Walk Seed 20260710 - 1500 gemischte Zuege gegen unabhaengige Buchfuehrung`() {
        runWalk(Random(20260710L))
    }

    private fun runWalk(random: Random) {
        var state = engine.newGame(level).state
        val oracle = Oracle()
        repeat(WALK_STEPS) {
            if (oracle.locked) {
                verifyLocked(state, random)
                state = restart()
                oracle.apply(Move.Reset)
            }
            state = step(state, oracle, randomMove(random))
        }
    }

    /** R32: im Zustand Geloest ist jeder Zugtyp gesperrt — mehrfach stichprobenartig. */
    private fun verifyLocked(
        state: GameState,
        random: Random,
    ) {
        repeat(3) {
            val result = engine.applyMove(state, randomMove(random))
            result.shouldBeInstanceOf<MoveResult.Invalid>().reason shouldBe InvalidMoveReason.ALREADY_SOLVED
        }
    }

    private fun restart(): GameState {
        val fresh = engine.newGame(level)
        fresh.state.solved.shouldBeFalse()
        fresh.state.moveCount shouldBe 0
        fresh.trace shouldBe DefaultTracer.trace(level.board)
        return fresh.state
    }

    private fun step(
        state: GameState,
        oracle: Oracle,
        move: Move,
    ): GameState {
        val orientationsBefore = HashMap(state.orientations)
        val historyBefore = ArrayList(state.history)
        return when (val result = engine.applyMove(state, move)) {
            is MoveResult.Applied -> {
                expectedReason(oracle, move) shouldBe null
                oracle.apply(move)
                verifyApplied(result, oracle, move)
                result.state
            }
            is MoveResult.Invalid -> {
                result.reason shouldBe expectedReason(oracle, move)
                // moveCount, Verlauf und Orientierungen strikt unveraendert (Paragraf 6.1, I7)
                state.orientations shouldBe orientationsBefore
                state.history shouldBe historyBefore
                state.moveCount shouldBe oracle.stack.size
                state
            }
        }
    }

    /** Erwartete Ablehnung laut Design Paragraf 6.1/6.2 (R27/R28), unabhaengig von der Engine berechnet. */
    private fun expectedReason(
        oracle: Oracle,
        move: Move,
    ): InvalidMoveReason? =
        when (move) {
            is Move.Rotate -> expectedRotateReason(move.cell)
            Move.Undo -> if (oracle.stack.isEmpty()) InvalidMoveReason.HISTORY_EMPTY else null
            Move.Reset -> null
        }

    private fun expectedRotateReason(cell: HexCoord): InvalidMoveReason? =
        when {
            cell == mirror || cell == decoy -> null
            cell in fixedCells -> InvalidMoveReason.NOT_ROTATABLE
            else -> InvalidMoveReason.NO_ELEMENT
        }

    private fun verifyApplied(
        result: MoveResult.Applied,
        oracle: Oracle,
        move: Move,
    ) {
        result.state.orientations shouldBe oracle.steps.mapValues { (_, steps) -> Orientation(steps) }
        result.state.history shouldBe oracle.stack.toList()
        result.state.moveCount shouldBe oracle.stack.size
        result.state.solved shouldBe oracle.locked
        result.trace.solved shouldBe oracle.locked
        // Loesen ist nur per Rotate erreichbar (Paragraf 6.1/6.2: Undo/Reset
        // koennen keinen geloesten Zustand herstellen, geloest ist terminal)
        if (move !is Move.Rotate) result.state.solved.shouldBeFalse()
        // Trace-Orakel gegen ein unabhaengig rekonstruiertes Brett (stale-Cache-Falle)
        result.trace shouldBe DefaultTracer.trace(oracleBoard(oracle))
    }

    /** Brett aus Level-Definition plus Orakel-Orientierungen — bewusst OHNE GameState.currentBoard(). */
    private fun oracleBoard(oracle: Oracle): Board =
        Board(
            radius = level.board.radius,
            elements =
                level.board.elements +
                    mapOf(
                        mirror to Element.Mirror(Orientation(oracle.steps.getValue(mirror))),
                        decoy to Element.Splitter(Orientation(oracle.steps.getValue(decoy))),
                    ),
        )

    private fun randomMove(random: Random): Move =
        when (random.nextInt(14)) {
            in 0..3 -> Move.Rotate(mirror)
            in 4..7 -> Move.Rotate(decoy)
            8 -> Move.Rotate(EngineQsFixtures.prism)
            9 -> Move.Rotate(EngineQsFixtures.redCrystal)
            10 -> Move.Rotate(emptyCell)
            11 -> Move.Rotate(offBoard)
            12 -> Move.Undo
            else -> Move.Reset
        }
}
