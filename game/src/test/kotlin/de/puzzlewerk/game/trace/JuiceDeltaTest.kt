package de.puzzlewerk.game.trace

import de.puzzlewerk.game.DreiFarbenLevel
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * juiceDelta (ADR-012; Design Paragraf 13.9-13.11, R46): Vorher/Nachher-
 * Vergleich zweier Traces desselben Levels. Fixture: Beispiel-Level
 * "Drei Farben" (Paragraf 7.3) mit den Traces m=5 (Start, dunkel),
 * m=0 (Zug 1, dunkel) und m=1 (Zug 2, geloest).
 */
class JuiceDeltaTest {
    private val startBoard = DreiFarbenLevel.board(mirrorSteps = 5)
    private val darkBoard = DreiFarbenLevel.board(mirrorSteps = 0)
    private val solvedBoard = DreiFarbenLevel.board(mirrorSteps = 1)
    private val allThree =
        setOf(DreiFarbenLevel.redCrystal, DreiFarbenLevel.greenCrystal, DreiFarbenLevel.blueCrystal)

    @Test
    fun `Partie-Start ohne Vorher-Trace loest keine Bursts aus`() {
        val delta = juiceDelta(before = null, after = trace(startBoard), board = startBoard)

        delta.newlyFulfilled.shouldBeEmpty()
        delta.newlyLit.shouldBeEmpty()
        delta.fulfilled.shouldBeEmpty()
        delta.crystalTotal shouldBe 3
        delta.comboSize shouldBe 0
    }

    @Test
    fun `Partie-Start in bereits geloestem Zustand - keine Bursts, aber L ist voll (R31-Fall)`() {
        val delta = juiceDelta(before = null, after = trace(solvedBoard), board = solvedBoard)

        delta.newlyFulfilled.shouldBeEmpty()
        delta.newlyLit.shouldBeEmpty()
        delta.fulfilled shouldBe allThree
        delta.crystalTotal shouldBe 3
    }

    @Test
    fun `Null neue Kristalle - Zug ohne Lichtaenderung liefert leere Mengen`() {
        val delta = juiceDelta(before = trace(startBoard), after = trace(darkBoard), board = darkBoard)

        delta.newlyFulfilled.shouldBeEmpty()
        delta.newlyLit.shouldBeEmpty()
        delta.fulfilled.shouldBeEmpty()
        delta.comboSize shouldBe 0
    }

    @Test
    fun `Genau ein neuer Kristall - Menge, Combo-Groesse und L stimmen`() {
        val crystal = HexCoord(2, 0)
        val delta =
            juiceDelta(
                before = trace(gateBoard(mirrorSteps = 2, required = LightColor.RED)),
                after = trace(gateBoard(mirrorSteps = 0, required = LightColor.RED)),
                board = gateBoard(mirrorSteps = 0, required = LightColor.RED),
            )

        delta.newlyFulfilled shouldBe listOf(crystal)
        delta.newlyLit shouldBe setOf(crystal)
        delta.fulfilled shouldBe setOf(crystal)
        delta.crystalTotal shouldBe 1
        delta.comboSize shouldBe 1
    }

    @Test
    fun `N=3 neue Kristalle - Kaskadenordnung aufsteigend r dann q (Beispiel 13_9)`() {
        val delta = juiceDelta(before = trace(darkBoard), after = trace(solvedBoard), board = solvedBoard)

        // Normative Reihenfolge aus 13.9: (1,-2) vor (2,-2) vor (2,-1).
        delta.newlyFulfilled shouldBe
            listOf(DreiFarbenLevel.redCrystal, DreiFarbenLevel.greenCrystal, DreiFarbenLevel.blueCrystal)
        delta.newlyLit shouldBe allThree
        delta.fulfilled shouldBe allThree
        delta.comboSize shouldBe 3
    }

    @Test
    fun `Kaskadenordnung ist r-dann-q, nicht q-dann-r (deterministischer Diskriminanztest 13_9)`() {
        // A und B unterscheiden die beiden Sortierschluessel: A.q < B.q, aber A.r > B.r.
        val a = HexCoord(q = 1, r = 1)
        val b = HexCoord(q = 2, r = 0)
        val board =
            Board(
                radius = 2,
                elements =
                    mapOf(
                        a to Element.Crystal(LightColor.RED),
                        b to Element.Crystal(LightColor.GREEN),
                    ),
            )
        val before = TraceResult(segments = emptyList(), received = emptyMap(), solved = false, endpoints = emptyList())
        val after =
            TraceResult(
                segments = emptyList(),
                received = mapOf(a to LightColor.RED, b to LightColor.GREEN),
                solved = true,
                endpoints = emptyList(),
            )

        val delta = juiceDelta(before = before, after = after, board = board)

        // compareBy(r, q): B (r=0) vor A (r=1). compareBy(q, r) wuerde A (q=1) zuerst liefern.
        delta.newlyFulfilled shouldBe listOf(b, a)
    }

    @Test
    fun `Kristall geht durch Drehung AUS - zaehlt nicht als neu, L folgt abwaerts`() {
        val delta = juiceDelta(before = trace(solvedBoard), after = trace(darkBoard), board = darkBoard)

        delta.newlyFulfilled.shouldBeEmpty()
        delta.newlyLit.shouldBeEmpty()
        delta.fulfilled.shouldBeEmpty()
    }

    @Test
    fun `Undo entleuchtet - erneutes Erfuellen gilt wieder als neu (R46)`() {
        val firstSolve = juiceDelta(trace(darkBoard), trace(solvedBoard), solvedBoard)
        val undo = juiceDelta(trace(solvedBoard), trace(darkBoard), darkBoard)
        val resolve = juiceDelta(trace(darkBoard), trace(solvedBoard), solvedBoard)

        undo.newlyFulfilled.shouldBeEmpty()
        resolve.newlyFulfilled shouldBe firstSolve.newlyFulfilled
        resolve.comboSize shouldBe 3
    }

    @Test
    fun `Erstmals Licht ohne Erfuellung - newlyLit gesetzt, newlyFulfilled leer (sfx_beam_connect)`() {
        val crystal = HexCoord(2, 0)
        val delta =
            juiceDelta(
                before = trace(gateBoard(mirrorSteps = 2, required = LightColor.YELLOW)),
                after = trace(gateBoard(mirrorSteps = 0, required = LightColor.YELLOW)),
                board = gateBoard(mirrorSteps = 0, required = LightColor.YELLOW),
            )

        delta.newlyLit shouldBe setOf(crystal)
        delta.newlyFulfilled.shouldBeEmpty()
        delta.fulfilled.shouldBeEmpty()
    }

    @Test
    fun `Uebersaettigung zaehlt nicht als erfuellt (Paragraf 4_7, R23)`() {
        val crystal = HexCoord(2, 0)
        val before = trace(oversaturationBoard(mirrorSteps = 2))
        val afterBoard = oversaturationBoard(mirrorSteps = 0)
        val delta = juiceDelta(before = before, after = trace(afterBoard), board = afterBoard)

        delta.newlyLit shouldBe setOf(crystal)
        delta.newlyFulfilled.shouldBeEmpty()
        delta.fulfilled.shouldBeEmpty()
    }

    @Test
    fun `Bereits erleuchteter Kristall wird beim Erfuellen nicht erneut newlyLit`() {
        val crystal = HexCoord(0, 0)
        val delta =
            juiceDelta(
                before = trace(twoSourceBoard(gateMirrorSteps = 1)),
                after = trace(twoSourceBoard(gateMirrorSteps = 0)),
                board = twoSourceBoard(gateMirrorSteps = 0),
            )

        delta.newlyLit.shouldBeEmpty()
        delta.newlyFulfilled shouldBe listOf(crystal)
        delta.fulfilled shouldBe setOf(crystal)
    }

    @Test
    fun `Property - neu Erleuchtetes und neu Erfuelltes sind Teilmengen des Nachher-Zustands`() {
        val random = Random(20260718)
        repeat(300) {
            val level = randomBoard(random)
            val before = scrambleOrientations(level, random)
            val afterTrace = trace(level)

            val delta = juiceDelta(trace(before), afterTrace, level)

            (delta.newlyLit - afterTrace.received.keys).shouldBeEmpty()
            (delta.newlyFulfilled.toSet() - delta.fulfilled).shouldBeEmpty()
            (delta.fulfilled - crystalCells(level)).shouldBeEmpty()
            delta.newlyFulfilled shouldBe delta.newlyFulfilled.sortedWith(compareBy({ it.r }, { it.q }))
            delta.comboSize shouldBe delta.newlyFulfilled.size
            delta.crystalTotal shouldBe crystalCells(level).size
        }
    }

    /** Quelle Rot -> Spiegel als Tor (m=0: Durchlass, sonst Ablenkung) -> Kristall. */
    private fun gateBoard(
        mirrorSteps: Int,
        required: LightColor,
    ): Board =
        boardOf(
            2,
            HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
            HexCoord(0, 0) to Element.Mirror(Orientation(mirrorSteps)),
            HexCoord(2, 0) to Element.Crystal(required),
        )

    /** Quelle Weiss -> Spiegel-Tor -> Kristall Gelb: Weiss traegt Blau als Fremdkomponente. */
    private fun oversaturationBoard(mirrorSteps: Int): Board =
        boardOf(
            2,
            HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
            HexCoord(0, 0) to Element.Mirror(Orientation(mirrorSteps)),
            HexCoord(2, 0) to Element.Crystal(LightColor.YELLOW),
        )

    /**
     * Gelb-Kristall in der Mitte; Rot trifft immer, Gruen nur wenn das
     * Spiegel-Tor auf Durchlass steht (m=0 ist fuer d_in=3 der Parallelfall).
     */
    private fun twoSourceBoard(gateMirrorSteps: Int): Board =
        boardOf(
            2,
            HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
            HexCoord(2, 0) to Element.Source(LightColor.GREEN, Direction.WEST),
            HexCoord(1, 0) to Element.Mirror(Orientation(gateMirrorSteps)),
            HexCoord(0, 0) to Element.Crystal(LightColor.YELLOW),
        )

    private fun randomBoard(random: Random): Board {
        val radius = random.nextInt(Board.MIN_RADIUS, Board.MAX_RADIUS + 1)
        val cells = allCells(radius).shuffled(random)
        val occupied = random.nextInt(1, cells.size + 1)
        val elements = mutableMapOf<HexCoord, Element>()
        elements[cells[0]] = Element.Source(randomColor(random), Direction.entries.random(random))
        for (cell in cells.subList(1, occupied)) {
            elements[cell] = randomElement(random)
        }
        return Board(radius, elements)
    }

    private fun randomElement(random: Random): Element =
        when (random.nextInt(6)) {
            0 -> Element.Source(randomColor(random), Direction.entries.random(random))
            1 -> Element.Mirror(Orientation(random.nextInt(Orientation.COUNT)))
            2 -> Element.Splitter(Orientation(random.nextInt(Orientation.COUNT)))
            3 -> Element.Prism
            4 -> Element.Crystal(randomColor(random))
            else -> Element.Wall
        }

    private fun randomColor(random: Random): LightColor = LightColor(1 + random.nextInt(7))

    private fun scrambleOrientations(
        board: Board,
        random: Random,
    ): Board =
        board.copy(
            elements =
                board.elements.mapValues { (_, element) ->
                    if (element is Element.Rotatable) {
                        element.withOrientation(Orientation(random.nextInt(Orientation.COUNT)))
                    } else {
                        element
                    }
                },
        )

    private fun crystalCells(board: Board): Set<HexCoord> = board.elements.filterValues { it is Element.Crystal }.keys
}
