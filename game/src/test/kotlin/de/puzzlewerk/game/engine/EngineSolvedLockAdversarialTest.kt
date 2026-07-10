package de.puzzlewerk.game.engine

import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.trace.DefaultTracer
import de.puzzlewerk.game.trace.TraceResult
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * Unabhaengiger QS-Pass PW-2.3-QS: Angriff auf die Geloest-Sperre (Design
 * Paragraf 6.1/6.2, R32), die Kommutativitaets-Kante von I9 an der Sperre
 * (Paragraf 6.4/14) und Zustands-Aliasing ueber die gesamte Partie-Historie
 * (Paragraf 16.1: GameState ist ein unveraenderlicher Wert).
 */
class EngineSolvedLockAdversarialTest {
    private val engine: GameEngine = defaultGameEngine(DefaultTracer)
    private val level = EngineQsFixtures.koederLevel()
    private val mirror = EngineQsFixtures.mirror
    private val decoy = EngineQsFixtures.decoy

    private fun applied(
        state: GameState,
        move: Move,
    ): MoveResult.Applied = engine.applyMove(state, move).shouldBeInstanceOf<MoveResult.Applied>()

    private fun invalid(
        state: GameState,
        move: Move,
    ): InvalidMoveReason = engine.applyMove(state, move).shouldBeInstanceOf<MoveResult.Invalid>().reason

    /** Loest das Koeder-Level auf dem dokumentierten Weg (Paragraf 7.3): Spiegel m 5 -> 0 -> 1. */
    private fun solvedState(): GameState {
        val start = engine.newGame(level).state
        val one = applied(start, Move.Rotate(mirror)).state
        val solved = applied(one, Move.Rotate(mirror)).state
        solved.solved.shouldBeTrue()
        return solved
    }

    @Test
    fun `R32 adversarial - nach dem Loesen ist Rotate auf JEDER Zelle sowie Undo und Reset gesperrt`() {
        val solved = solvedState()
        val orientationsBefore = HashMap(solved.orientations)
        val historyBefore = ArrayList(solved.history)

        // Alle 19 Brettzellen PLUS Zellen ausserhalb des Radius (Ring > 2)
        val cells = (-2..2).flatMap { q -> (-2..2).map { r -> HexCoord(q, r) } } + HexCoord(7, -7)
        for (cell in cells) {
            invalid(solved, Move.Rotate(cell)) shouldBe InvalidMoveReason.ALREADY_SOLVED
        }
        invalid(solved, Move.Undo) shouldBe InvalidMoveReason.ALREADY_SOLVED
        invalid(solved, Move.Reset) shouldBe InvalidMoveReason.ALREADY_SOLVED

        solved.orientations shouldBe orientationsBefore
        solved.history shouldBe historyBefore
        solved.solved.shouldBeTrue()
    }

    @Test
    fun `I9-Kante - Permutation ueber einen loesenden Zwischenzustand kommutiert NICHT, die Sperre hat Vorrang`() {
        val minimal = EngineQsFixtures.minimalMitKoeder()

        // Reihenfolge 1: loesender Zug zuerst -> Sperre frisst den Rest der Sequenz
        val solvedFirst = applied(engine.newGame(minimal).state, Move.Rotate(mirror))
        solvedFirst.state.solved.shouldBeTrue()
        invalid(solvedFirst.state, Move.Rotate(decoy)) shouldBe InvalidMoveReason.ALREADY_SOLVED

        // Reihenfolge 2: Koeder zuerst -> beide Zuege werden angewandt
        val decoyFirst = applied(engine.newGame(minimal).state, Move.Rotate(decoy))
        decoyFirst.state.solved.shouldBeFalse()
        val solvedSecond = applied(decoyFirst.state, Move.Rotate(mirror))
        solvedSecond.state.solved.shouldBeTrue()

        // Gleiches Zug-Multiset, verschiedene Endzustaende: I9 gilt nur, solange
        // KEIN Zwischenzustand loest (Paragraf 6.4); danach greift Paragraf 6.1.
        solvedFirst.state.moveCount shouldBe 1
        solvedSecond.state.moveCount shouldBe 2
        solvedFirst.state.orientations[decoy] shouldBe Orientation.ZERO
        solvedSecond.state.orientations[decoy] shouldBe Orientation(1)
        solvedFirst.state.orientations shouldNotBe solvedSecond.state.orientations
    }

    @Test
    fun `Loesen durch Undo ist unerreichbar - jeder geloeste Zustand ist terminal (Paragraf 6_1 und 6_2)`() {
        // Ein Zug, der loest, sperrt sofort; Undo kann daher nie einen geloesten
        // Zustand wiederherstellen. Getestet wird die dokumentierte Konsequenz:
        // Undo liefert nie solved = true, und der geloeste Zustand bleibt terminal.
        val start = engine.newGame(level).state
        val one = applied(start, Move.Rotate(mirror)).state // m = 0, ungeloest
        val two = applied(one, Move.Rotate(decoy)).state

        val undoDecoy = applied(two, Move.Undo)
        undoDecoy.state.solved.shouldBeFalse()
        undoDecoy.trace.solved.shouldBeFalse()

        val undoMirror = applied(undoDecoy.state, Move.Undo)
        undoMirror.state.solved.shouldBeFalse()
        undoMirror.state shouldBe start

        // Der einzige Weg in Geloest ist ein Rotate — und danach geht nichts mehr
        invalid(solvedState(), Move.Undo) shouldBe InvalidMoveReason.ALREADY_SOLVED
    }

    @Test
    fun `Verschachtelt - Undo nach Reset ist VerlaufLeer, Reset nach Undo stellt exakt den Start her`() {
        val start = engine.newGame(level).state
        var state = applied(start, Move.Rotate(decoy)).state
        state = applied(state, Move.Rotate(decoy)).state
        state.orientations[decoy] shouldBe Orientation(4)

        // Reset verwirft den Verlauf vollstaendig -> Undo danach ist R28
        val reset = applied(state, Move.Reset).state
        reset shouldBe start
        invalid(reset, Move.Undo) shouldBe InvalidMoveReason.HISTORY_EMPTY

        // Rotate, Undo, dann Reset: gueltiges No-op zurueck auf den Start (R29)
        val rotated = applied(reset, Move.Rotate(mirror)).state
        val undone = applied(rotated, Move.Undo).state
        undone shouldBe start
        applied(undone, Move.Reset).state shouldBe start
        invalid(undone, Move.Undo) shouldBe InvalidMoveReason.HISTORY_EMPTY
    }

    private data class Recorded(
        val state: GameState,
        val orientations: Map<HexCoord, Orientation>,
        val history: List<HexCoord>,
        val solved: Boolean,
        val trace: TraceResult,
        val received: Map<HexCoord, LightColor>,
    )

    private fun record(result: MoveResult.Applied): Recorded =
        Recorded(
            state = result.state,
            orientations = HashMap(result.state.orientations),
            history = ArrayList(result.state.history),
            solved = result.state.solved,
            trace = result.trace,
            received = HashMap(result.trace.received),
        )

    @Test
    fun `Zustands-Aliasing - fruehere Applied-Snapshots bleiben ueber Rotate, Undo und Reset unveraendert`() {
        val snapshots = mutableListOf(record(engine.newGame(level)))
        val script =
            listOf(
                Move.Rotate(decoy),
                Move.Rotate(mirror),
                Move.Rotate(decoy),
                Move.Undo,
                Move.Reset,
                Move.Rotate(mirror),
                // der zweite Spiegel-Zug loest (m = 1)
                Move.Rotate(mirror),
            )
        for (move in script) {
            snapshots += record(applied(snapshots.last().state, move))
        }
        invalid(snapshots.last().state, Move.Rotate(mirror)) shouldBe InvalidMoveReason.ALREADY_SOLVED

        for (snapshot in snapshots) {
            snapshot.state.orientations shouldBe snapshot.orientations
            snapshot.state.history shouldBe snapshot.history
            snapshot.state.solved shouldBe snapshot.solved
            snapshot.trace.received shouldBe snapshot.received
            // Der Snapshot muss weiterhin exakt seinen eigenen Brettzustand tracen
            DefaultTracer.trace(snapshot.state.currentBoard()) shouldBe snapshot.trace
        }
        snapshots.last().solved.shouldBeTrue()
        snapshots[snapshots.size - 2].solved.shouldBeFalse()
    }
}
