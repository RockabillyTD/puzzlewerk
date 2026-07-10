package de.puzzlewerk.game.engine

import de.puzzlewerk.game.DreiFarbenLevel
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.score.DefaultScoreCalculator
import de.puzzlewerk.game.score.Score
import de.puzzlewerk.game.trace.DefaultTracer
import de.puzzlewerk.game.trace.Tracer
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * Verhaltenstests der Engine-Implementierung gegen Design Paragraf 6
 * (Zuege, Undo, Reset, Geloest-Sperre) inklusive Golden-Test Paragraf 7.3
 * und der Randfaelle R27-R32 sowie R42 (Paragraf 15).
 */
class DefaultGameEngineTest {
    private val engine: GameEngine = defaultGameEngine(DefaultTracer)

    private val dreiFarben =
        LevelDefinition(
            board = DreiFarbenLevel.board(),
            par = 2,
            tier = Difficulty.D1,
            seed = 0x7331L,
        )

    /** Unloesbar (nur rotes Licht, gruener Kristall) — sicher fuer lange Zugfolgen. */
    private val unsolvable =
        LevelDefinition(
            board =
                Board(
                    radius = 2,
                    elements =
                        mapOf(
                            HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
                            HexCoord(0, 0) to Element.Mirror(Orientation(3)),
                            HexCoord(0, 1) to Element.Splitter(Orientation(4)),
                            HexCoord(2, -2) to Element.Crystal(LightColor.GREEN),
                        ),
                ),
            par = 1,
            tier = Difficulty.D2,
            seed = 0x99L,
        )

    private fun applied(
        state: GameState,
        move: Move,
    ): MoveResult.Applied = engine.applyMove(state, move).shouldBeInstanceOf<MoveResult.Applied>()

    private fun invalid(
        state: GameState,
        move: Move,
    ): InvalidMoveReason = engine.applyMove(state, move).shouldBeInstanceOf<MoveResult.Invalid>().reason

    // ------------------------------------------------------------------
    // Golden-Test Paragraf 7.3: Drei Farben Ende-zu-Ende ueber die Engine
    // ------------------------------------------------------------------

    @Test
    fun `Golden Paragraf 7_3 - Drei Farben in zwei Zuegen geloest, Score 1500, drei Sterne`() {
        val start = engine.newGame(dreiFarben)
        start.state.moveCount shouldBe 0
        start.state.solved shouldBe false
        start.trace.solved shouldBe false
        start.trace.received.shouldBeEmpty() // alle Kristalle dunkel
        start.state.orientations shouldBe mapOf(DreiFarbenLevel.mirror to Orientation(5))

        val afterFirst = applied(start.state, Move.Rotate(DreiFarbenLevel.mirror))
        afterFirst.state.orientations[DreiFarbenLevel.mirror] shouldBe Orientation(0)
        afterFirst.state.moveCount shouldBe 1
        afterFirst.state.solved shouldBe false // Parallelfall: Strahl laeuft geradeaus
        afterFirst.trace.received.shouldBeEmpty()

        val afterSecond = applied(afterFirst.state, Move.Rotate(DreiFarbenLevel.mirror))
        afterSecond.state.orientations[DreiFarbenLevel.mirror] shouldBe Orientation(1)
        afterSecond.state.moveCount shouldBe 2
        afterSecond.state.solved shouldBe true
        afterSecond.trace.solved shouldBe true
        afterSecond.trace.received shouldBe
            mapOf(
                DreiFarbenLevel.redCrystal to LightColor.RED,
                DreiFarbenLevel.greenCrystal to LightColor.GREEN,
                DreiFarbenLevel.blueCrystal to LightColor.BLUE,
            )

        val score = DefaultScoreCalculator.scoreFor(moves = afterSecond.state.moveCount, par = dreiFarben.par)
        score shouldBe Score(points = 1500, stars = 3)
    }

    @Test
    fun `newGame liefert den initialen Trace des Startbretts`() {
        val start = engine.newGame(dreiFarben)

        start.trace shouldBe DefaultTracer.trace(dreiFarben.board)
        start.state.level shouldBe dreiFarben
        start.state.history shouldBe emptyList()
    }

    // ------------------------------------------------------------------
    // R27: ungueltige Rotationsziele
    // ------------------------------------------------------------------

    @Test
    fun `R27 - Rotate auf leerer Zelle liefert NO_ELEMENT und aendert nichts`() {
        val start = engine.newGame(dreiFarben).state
        val snapshot = start.copy()

        invalid(start, Move.Rotate(HexCoord(1, 1))) shouldBe InvalidMoveReason.NO_ELEMENT

        start shouldBe snapshot
        start.moveCount shouldBe 0
    }

    @Test
    fun `R27 - Rotate ausserhalb des Bretts liefert NO_ELEMENT`() {
        val start = engine.newGame(dreiFarben).state

        invalid(start, Move.Rotate(HexCoord(5, 5))) shouldBe InvalidMoveReason.NO_ELEMENT
    }

    /** Alle sechs nicht drehbaren Elementtypen auf einem Brett (fuer R27). */
    private val allFixedElements: Map<HexCoord, Element> =
        mapOf(
            HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
            HexCoord(0, -1) to Element.Prism,
            HexCoord(1, -1) to Element.Filter(LightColor.RED),
            HexCoord(-1, 0) to Element.Portal(0),
            HexCoord(1, 1) to Element.Portal(0),
            HexCoord(2, 0) to Element.Crystal(LightColor.RED),
            HexCoord(0, 2) to Element.Wall,
        )

    @Test
    fun `R27 - Rotate auf jedem nicht drehbaren Elementtyp liefert NOT_ROTATABLE`() {
        val level =
            LevelDefinition(
                board =
                    Board(
                        radius = 2,
                        elements = allFixedElements + (HexCoord(0, 0) to Element.Mirror(Orientation.ZERO)),
                    ),
                par = 1,
                tier = Difficulty.D5,
                seed = 0x42L,
            )
        val start = engine.newGame(level).state

        for (cell in allFixedElements.keys) {
            invalid(start, Move.Rotate(cell)) shouldBe InvalidMoveReason.NOT_ROTATABLE
        }
        start.moveCount shouldBe 0
    }

    // ------------------------------------------------------------------
    // R28/R29: Undo- und Reset-Randfaelle
    // ------------------------------------------------------------------

    @Test
    fun `R28 - Undo bei leerem Verlauf liefert HISTORY_EMPTY, Zaehler bleibt 0`() {
        val start = engine.newGame(dreiFarben).state

        invalid(start, Move.Undo) shouldBe InvalidMoveReason.HISTORY_EMPTY

        start.moveCount shouldBe 0
    }

    @Test
    fun `R28 - Undo nach vollstaendigem Rueckabbau liefert wieder HISTORY_EMPTY`() {
        val start = engine.newGame(unsolvable).state
        val one = applied(start, Move.Rotate(HexCoord(0, 0))).state
        val undone = applied(one, Move.Undo).state

        undone.moveCount shouldBe 0
        invalid(undone, Move.Undo) shouldBe InvalidMoveReason.HISTORY_EMPTY
    }

    @Test
    fun `R29 - Reset bei 0 Zuegen ist gueltig und ein No-op`() {
        val start = engine.newGame(dreiFarben)

        val reset = applied(start.state, Move.Reset)

        reset.state shouldBe start.state
        reset.trace shouldBe start.trace
    }

    @Test
    fun `R29 - Reset nach Zuegen stellt exakt den Startzustand her`() {
        val start = engine.newGame(unsolvable)
        var state = start.state
        repeat(4) { state = applied(state, Move.Rotate(HexCoord(0, 0))).state }
        state = applied(state, Move.Rotate(HexCoord(0, 1))).state
        state.moveCount shouldBe 5

        val reset = applied(state, Move.Reset)

        reset.state shouldBe start.state
        reset.state.moveCount shouldBe 0
        reset.trace shouldBe start.trace
    }

    // ------------------------------------------------------------------
    // R30: kein automatisches Kuerzen voller Umdrehungen
    // ------------------------------------------------------------------

    @Test
    fun `R30 - 6 Taps auf dasselbe Element ergeben die Ausgangsorientierung bei Zaehler 6`() {
        val mirror = HexCoord(0, 0)
        var state = engine.newGame(unsolvable).state
        repeat(6) { state = applied(state, Move.Rotate(mirror)).state }

        state.orientations[mirror] shouldBe Orientation(3) // wie im Startzustand
        state.moveCount shouldBe 6
        state.history shouldBe List(6) { mirror }
    }

    // ------------------------------------------------------------------
    // R31/R32: Geloest-Sperre
    // ------------------------------------------------------------------

    @Test
    fun `R31 - bereits geloest geladenes Level startet sofort als Geloest mit 0 Zuegen`() {
        val preSolved =
            LevelDefinition(
                board =
                    Board(
                        radius = 2,
                        elements =
                            mapOf(
                                HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
                                HexCoord(2, 0) to Element.Crystal(LightColor.RED),
                                HexCoord(0, 1) to Element.Mirror(Orientation.ZERO),
                            ),
                    ),
                par = 1,
                tier = Difficulty.D1,
                seed = 0x13L,
            )

        val start = engine.newGame(preSolved)

        start.state.solved shouldBe true
        start.state.moveCount shouldBe 0
        start.trace.solved shouldBe true
        DefaultScoreCalculator.scoreFor(moves = 0, par = preSolved.par) shouldBe Score(points = 1500, stars = 3)
    }

    @Test
    fun `R32 - Rotate, Undo und Reset sind im Zustand Geloest gesperrt`() {
        val start = engine.newGame(dreiFarben).state
        val one = applied(start, Move.Rotate(DreiFarbenLevel.mirror)).state
        val solved = applied(one, Move.Rotate(DreiFarbenLevel.mirror)).state
        solved.solved shouldBe true

        invalid(solved, Move.Rotate(DreiFarbenLevel.mirror)) shouldBe InvalidMoveReason.ALREADY_SOLVED
        invalid(solved, Move.Undo) shouldBe InvalidMoveReason.ALREADY_SOLVED
        invalid(solved, Move.Reset) shouldBe InvalidMoveReason.ALREADY_SOLVED
        solved.moveCount shouldBe 2
    }

    // ------------------------------------------------------------------
    // R42: Minimal-Level am unteren Rand
    // ------------------------------------------------------------------

    @Test
    fun `R42 - Minimal-Level mit einem Spiegel und Par 1 - letzter Zug ist der erste`() {
        val minimal =
            LevelDefinition(
                board =
                    Board(
                        radius = 2,
                        elements =
                            mapOf(
                                HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
                                HexCoord(0, 0) to Element.Mirror(Orientation.ZERO),
                                HexCoord(1, -1) to Element.Crystal(LightColor.RED),
                            ),
                    ),
                par = 1,
                tier = Difficulty.D1,
                seed = 0x1L,
            )
        val start = engine.newGame(minimal)
        start.state.solved shouldBe false

        val solved = applied(start.state, Move.Rotate(HexCoord(0, 0)))

        solved.state.solved shouldBe true
        solved.state.moveCount shouldBe 1
        solved.trace.received shouldBe mapOf(HexCoord(1, -1) to LightColor.RED)
        val score = DefaultScoreCalculator.scoreFor(moves = solved.state.moveCount, par = minimal.par)
        score shouldBe Score(points = 1500, stars = 3)
    }

    // ------------------------------------------------------------------
    // Undo-Semantik (Paragraf 6.2) und Trace-Sparsamkeit (NIT-3)
    // ------------------------------------------------------------------

    @Test
    fun `Undo nimmt den letzten Zug des richtigen Elements zurueck`() {
        val mirror = HexCoord(0, 0)
        val splitter = HexCoord(0, 1)
        val start = engine.newGame(unsolvable).state
        val one = applied(start, Move.Rotate(mirror)).state
        val two = applied(one, Move.Rotate(splitter)).state
        two.history shouldBe listOf(mirror, splitter)

        val undone = applied(two, Move.Undo).state

        undone.orientations[splitter] shouldBe Orientation(4) // Startwert wiederhergestellt
        undone.orientations[mirror] shouldBe Orientation(4) // erster Zug bleibt bestehen
        undone.history shouldBe listOf(mirror)
        undone.moveCount shouldBe 1
    }

    @Test
    fun `NIT-3 - genau eine trace-Auswertung je angewandtem Zug, keine bei Invalid`() {
        var traceCalls = 0
        val countingTracer =
            Tracer { board ->
                traceCalls += 1
                DefaultTracer.trace(board)
            }
        val countingEngine = defaultGameEngine(countingTracer)

        val start = countingEngine.newGame(unsolvable)
        traceCalls shouldBe 1

        val one = countingEngine.applyMove(start.state, Move.Rotate(HexCoord(0, 0)))
        one.shouldBeInstanceOf<MoveResult.Applied>()
        traceCalls shouldBe 2

        countingEngine.applyMove(start.state, Move.Rotate(HexCoord(1, 1)))
        countingEngine.applyMove(start.state, Move.Undo)
        traceCalls shouldBe 2
    }
}
