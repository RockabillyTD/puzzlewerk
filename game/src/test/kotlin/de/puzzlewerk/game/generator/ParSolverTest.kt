package de.puzzlewerk.game.generator

import de.puzzlewerk.game.DreiFarbenLevel
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.DefaultTracer
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis

/**
 * Par-Solver (Paragraf 9.4): exaktes Minimum per kostengeordneter Aufzaehlung
 * ueber End-Orientierungsvektoren der drehbaren Elemente (Kommutativitaet 6.4,
 * Invariante I9). Referenzwerte aus dem durchgerechneten Beispiel 7.3 und
 * einem unabhaengigen Vollenumerations-Orakel.
 */
class ParSolverTest {
    private val solver = ParSolver(DefaultTracer)

    // --- Normalfaelle -----------------------------------------------------

    @Test
    fun `Drei Farben Startzustand m=5 hat exakt Par 2 (Paragraf 7_3)`() {
        solver.minimalMoves(DreiFarbenLevel.board(mirrorSteps = 5)) shouldBe 2
    }

    @Test
    fun `Drei Farben m=0 hat Par 1 - eine Stufe bis zur Loesungsorientierung m=1`() {
        solver.minimalMoves(DreiFarbenLevel.board(mirrorSteps = 0)) shouldBe 1
    }

    @Test
    fun `Drei Farben m=2 hat Par 5 - Drehung nur vorwaerts, kein Gegenzug (Paragraf 6_1)`() {
        // Von m=2 zur einzigen Loesung m=1: (1 - 2) mod 6 = 5
        solver.minimalMoves(DreiFarbenLevel.board(mirrorSteps = 2)) shouldBe 5
    }

    // --- Randfaelle -------------------------------------------------------

    @Test
    fun `Bereits geloestes Brett liefert null - Kosten 0 prueft der Aufrufer separat (Paragraf 9_4)`() {
        // m=1 ist die EINZIGE Loesungsorientierung; jeder Vektor mit Kosten >= 1 verfehlt sie
        solver.minimalMoves(DreiFarbenLevel.board(mirrorSteps = 1)).shouldBeNull()
    }

    @Test
    fun `Brett ohne drehbare Elemente liefert null`() {
        val board =
            Board(
                radius = 2,
                elements =
                    mapOf(
                        HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
                        HexCoord(2, 0) to Element.Crystal(LightColor.GREEN),
                    ),
            )
        solver.minimalMoves(board).shouldBeNull()
    }

    @Test
    fun `Unloesbares Brett liefert null - Farbe kann ohne Prisma nicht wechseln`() {
        val board =
            Board(
                radius = 2,
                elements =
                    mapOf(
                        HexCoord(-2, 0) to Element.Source(LightColor.GREEN, Direction.EAST),
                        HexCoord(0, 0) to Element.Mirror(Orientation.ZERO),
                        HexCoord(2, 0) to Element.Crystal(LightColor.RED),
                    ),
            )
        solver.minimalMoves(board).shouldBeNull()
    }

    @Test
    fun `maxCost begrenzt die Suche - Par 5 wird mit maxCost 4 nicht gefunden`() {
        solver.minimalMoves(DreiFarbenLevel.board(mirrorSteps = 2), maxCost = 4).shouldBeNull()
    }

    // --- Exaktheit gegen unabhaengiges Orakel (feste Seeds) ----------------

    @Test
    fun `Solver stimmt auf zufaelligen kleinen Brettern mit der Vollenumeration ueberein`() {
        val random = Random(20260709L)
        repeat(60) {
            val board = randomSmallBoard(random)
            val expected = bruteForceMinimalMoves(board)
            val actual = solver.minimalMoves(board)
            actual shouldBe expected
        }
    }

    // --- Performance (Paragraf 9.4: Worst-Case muss in Testzeit laufen) ----

    @Test
    fun `Worst-Case 8 Drehbare ohne Loesung enumeriert alle Kosten bis 14 in Testzeit`() {
        // Unloesbar (gruene Quelle, roter Kristall) => der Solver muss ALLE
        // Vektoren mit Kosten <= 14 durchprobieren (~3*10^5, Paragraf 9.4)
        val elements = mutableMapOf<HexCoord, Element>()
        elements[HexCoord(-3, 0)] = Element.Source(LightColor.GREEN, Direction.EAST)
        elements[HexCoord(3, 0)] = Element.Crystal(LightColor.RED)
        listOf(
            HexCoord(-2, 0),
            HexCoord(-1, 0),
            HexCoord(0, 0),
            HexCoord(1, 0),
            HexCoord(2, 0),
            HexCoord(0, -1),
            HexCoord(0, 1),
            HexCoord(1, -1),
        ).forEachIndexed { index, cell -> elements[cell] = Element.Mirror(Orientation(index % 6)) }

        var result: Int? = 0
        val elapsed = measureTimeMillis { result = solver.minimalMoves(Board(3, elements)) }

        result.shouldBeNull()
        elapsed.toInt() shouldBeLessThan 60_000
    }

    /** Zufallsbrett R=2 mit Quelle, Kristall und 1..3 Drehbaren (k klein genug fuer 6^k-Orakel). */
    private fun randomSmallBoard(random: Random): Board {
        val cells = (-2..2).flatMap { q -> (-2..2).map { r -> HexCoord(q, r) } }.filter { it.isWithinRadius(2) }
        val free = cells.shuffled(random).toMutableList()
        val elements = mutableMapOf<HexCoord, Element>()
        elements[free.removeLast()] =
            Element.Source(LightColor(1 + random.nextInt(7)), Direction.entries.random(random))
        elements[free.removeLast()] = Element.Crystal(LightColor(1 + random.nextInt(7)))
        repeat(1 + random.nextInt(3)) {
            val orientation = Orientation(random.nextInt(Orientation.COUNT))
            elements[free.removeLast()] =
                if (random.nextBoolean()) Element.Mirror(orientation) else Element.Splitter(orientation)
        }
        return Board(2, elements)
    }
}
