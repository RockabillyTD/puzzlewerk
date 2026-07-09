package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.random.Random

private const val COLOR_COUNT = 7

/**
 * Property-Tests fuer die Invarianten I1 und I8 (Design Paragraf 14) sowie
 * Reflexions-Involution, Portal-Symmetrie (R14/R15), Splitter-Kaskaden (R06)
 * und Zyklen (R04, R15-R18). Alle Zufallsquellen sind fest geseedet.
 */
class DefaultTracerPropertyTest {
    private val allColors = (1..COLOR_COUNT).map { LightColor(it) }
    private val primaries = listOf(LightColor.RED, LightColor.GREEN, LightColor.BLUE)

    private fun randomElement(random: Random): Element =
        when (random.nextInt(7)) {
            0 -> Element.Source(allColors.random(random), Direction.entries.random(random))
            1 -> Element.Mirror(Orientation(random.nextInt(Orientation.COUNT)))
            2 -> Element.Splitter(Orientation(random.nextInt(Orientation.COUNT)))
            3 -> Element.Prism
            4 -> Element.Filter(primaries.random(random))
            5 -> Element.Crystal(allColors.random(random))
            else -> Element.Wall
        }

    private val arbBoard: Arb<Board> =
        arbitrary { rs ->
            val random = rs.random
            val radius = random.nextInt(Board.MIN_RADIUS, Board.MAX_RADIUS + 1)
            val cells = allCells(radius).shuffled(random)
            val occupied = random.nextInt(1, cells.size + 1)
            val elements = mutableMapOf<HexCoord, Element>()
            elements[cells[0]] = Element.Source(allColors.random(random), Direction.entries.random(random))
            var index = 1
            if (occupied >= 3 && random.nextBoolean()) {
                elements[cells[1]] = Element.Portal(0)
                elements[cells[2]] = Element.Portal(0)
                index = 3
            }
            while (index < occupied) {
                elements[cells[index]] = randomElement(random)
                index++
            }
            Board(radius, elements)
        }

    // Opt-in noetig, weil der PropTestConfig-Konstruktor in Kotest 5.9 experimentelle
    // Default-Parameter traegt; der feste Seed ist fuer Reproduzierbarkeit Pflicht.
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `I1 und I8 - jeder Trace terminiert endlich, alle Farben in 1 bis 7`() =
        runBlocking {
            checkAll(PropTestConfig(seed = 0x505249534D41), arbBoard) { board ->
                val result = DefaultTracer.trace(board)

                val stateSpace = Board.cellCount(board.radius) * Direction.COUNT * COLOR_COUNT
                result.segments.size shouldBeLessThanOrEqual stateSpace
                result.segments.forEach { segment ->
                    segment.color.bits shouldBeInRange 1..COLOR_COUNT
                    (segment.from in board).shouldBeTrue()
                    (segment.to in board).shouldBeTrue()
                    Direction.entries.any { segment.from.neighbor(it) == segment.to }.shouldBeTrue()
                }
                result.received.forEach { (coord, color) ->
                    color.bits shouldBeInRange 1..COLOR_COUNT
                    (board[coord] is Element.Crystal).shouldBeTrue()
                }
                val expectedSolved =
                    board.elements.all { (coord, element) ->
                        element !is Element.Crystal || result.received[coord] == element.required
                    }
                result.solved shouldBe expectedSolved
            }
        }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Trace ist referenziell transparent - gleiche Bretter, gleiches Ergebnis`() =
        runBlocking {
            checkAll(PropTestConfig(seed = 4711), arbBoard) { board ->
                DefaultTracer.trace(board) shouldBe DefaultTracer.trace(board)
            }
        }

    @Test
    fun `Reflexions-Involution - alle 36 Kombinationen aus m und d_in (Paragraf 4_2)`() {
        for (m in 0 until Orientation.COUNT) {
            for (direction in Direction.entries) {
                val mirrorCell = HexCoord(0, 0).neighbor(direction)
                val result =
                    trace(
                        boardOf(
                            2,
                            HexCoord(0, 0) to Element.Source(LightColor.RED, direction),
                            mirrorCell to Element.Mirror(Orientation(m)),
                        ),
                    )

                val out = Direction.fromIndex((m - direction.index).mod(Direction.COUNT))
                result.segments[0] shouldBe Segment(HexCoord(0, 0), mirrorCell, LightColor.RED)
                result.segments[1] shouldBe Segment(mirrorCell, mirrorCell.neighbor(out), LightColor.RED)
                // Involution: dieselbe Spiegelformel auf d_out ergibt wieder d_in
                (m - out.index).mod(Direction.COUNT) shouldBe direction.index
            }
        }
    }

    @Test
    fun `Portal-Symmetrie - Teleport erhaelt Richtung und Farbe (R14, R15)`() {
        val random = Random(1337)
        repeat(100) {
            val radius = 3
            val cells = allCells(radius).shuffled(random)
            val portalA = cells[0]
            val portalB = cells[1]
            val direction = Direction.entries.random(random)
            val sourceCell = portalA.neighbor(direction.opposite)
            if (sourceCell == portalB || !sourceCell.isWithinRadius(radius)) return@repeat
            val color = allColors.random(random)

            val result =
                trace(
                    boardOf(
                        radius,
                        sourceCell to Element.Source(color, direction),
                        portalA to Element.Portal(0),
                        portalB to Element.Portal(0),
                    ),
                )

            result.segments[0] shouldBe Segment(sourceCell, portalA, color)
            val exit = portalB.neighbor(direction)
            if (exit.isWithinRadius(radius)) {
                result.segments shouldContain Segment(portalB, exit, color)
            }
        }
    }

    @Test
    fun `R06 und I1 - zufaellige Voll-Splitter-Bretter explodieren nicht`() {
        val random = Random(20260709)
        repeat(60) {
            val result = trace(fullRotatableBoard(random, splitter = true))

            // ein Segment je Zustand; eine Farbe: Zellen x 6 Richtungen
            result.segments.size shouldBeLessThanOrEqual Board.cellCount(3) * Direction.COUNT
        }
    }

    @Test
    fun `Zyklen R04 und R15-R18 - zufaellige Voll-Spiegel-Bretter terminieren`() {
        val random = Random(3141592)
        repeat(60) {
            val result = trace(fullRotatableBoard(random, splitter = false))

            result.segments.size shouldBeLessThanOrEqual Board.cellCount(3) * Direction.COUNT
        }
    }

    private fun fullRotatableBoard(
        random: Random,
        splitter: Boolean,
    ): Board {
        val radius = 3
        val elements: MutableMap<HexCoord, Element> =
            allCells(radius)
                .associateWith<HexCoord, Element> {
                    val orientation = Orientation(random.nextInt(Orientation.COUNT))
                    if (splitter) Element.Splitter(orientation) else Element.Mirror(orientation)
                }.toMutableMap()
        elements[HexCoord(-radius, 0)] = Element.Source(LightColor.RED, Direction.EAST)
        return Board(radius, elements)
    }
}
