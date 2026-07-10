package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.assertions.fail
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

private const val COLOR_MASKS = 7

/**
 * Unabhaengige Property-Tests (QS-Pass PW-2.2-QS) mit eigenem, schema-konformem
 * Brett-Generator (Kappen aus Paragraf 16_2: 1-3 Quellen, 1-6 Kristalle,
 * hoechstens 8 drehbare Elemente, Portal-IDs paarweise) und festen Seeds.
 *
 * Kern ist das solved-Orakel (Paragraf 5_4): `received` je Kristall wird
 * unabhaengig als OR aller in die Kristallzelle eintretenden Segmentfarben
 * nachgerechnet — nicht ueber die API-Logik.
 */
class TracerIndependentPropertyTest {
    /** Richtungs-Deltas aus der Tabelle Paragraf 2_2, unabhaengig vom Direction-Enum. */
    private val deltas = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

    private val primaries = listOf(LightColor.RED, LightColor.GREEN, LightColor.BLUE)

    private fun randomColor(random: Random): LightColor = LightColor(1 + random.nextInt(COLOR_MASKS))

    private fun randomRotatable(random: Random): Element {
        val orientation = Orientation(random.nextInt(Orientation.COUNT))
        return if (random.nextBoolean()) Element.Mirror(orientation) else Element.Splitter(orientation)
    }

    private fun randomPassive(random: Random): Element =
        when (random.nextInt(3)) {
            0 -> Element.Prism
            1 -> Element.Filter(primaries.random(random))
            else -> Element.Wall
        }

    private val arbBoard: Arb<Board> =
        arbitrary { rs ->
            val random = rs.random
            val radius = Board.MIN_RADIUS + random.nextInt(Board.MAX_RADIUS - Board.MIN_RADIUS + 1)
            val free = allCells(radius).shuffled(random).toMutableList()
            val elements = mutableMapOf<HexCoord, Element>()

            fun place(
                count: Int,
                factory: () -> Element,
            ) = repeat(count) { if (free.isNotEmpty()) elements[free.removeLast()] = factory() }

            place(1 + random.nextInt(3)) { Element.Source(randomColor(random), Direction.entries.random(random)) }
            place(1 + random.nextInt(6)) { Element.Crystal(randomColor(random)) }
            for (pairId in 0..1) {
                if (random.nextBoolean() && free.size >= 2) place(2) { Element.Portal(pairId) }
            }
            place(random.nextInt(9)) { randomRotatable(random) }
            place(random.nextInt(free.size + 1)) { randomPassive(random) }
            Board(radius, elements)
        }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `solved-Orakel - solved gdw jeder Kristall exakt seine Sollfarbe empfaengt (Paragraf 5_4)`() {
        // Block-Body statt Expression-Body: eine @Test-Methode mit Rueckgabewert
        // (PropertyContext aus checkAll) wuerde JUnit Jupiter STILL ignorieren.
        runBlocking {
            checkAll(PropTestConfig(seed = 0x5054322E325153), arbBoard) { board ->
                val result = trace(board)

                var oracleSolved = true
                board.elements.forEach { (cell, element) ->
                    if (element is Element.Crystal) {
                        // Orakel: OR aller Segmentfarben, die die Kristallzelle betreten
                        val expectedBits =
                            result.segments
                                .filter { it.to == cell }
                                .fold(0) { acc, segment -> acc or segment.color.bits }
                        (result.received[cell]?.bits ?: 0) shouldBe expectedBits
                        if (expectedBits != element.required.bits) oracleSolved = false
                    }
                }
                result.solved shouldBe oracleSolved
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Segment-Invarianten - Absorber senden nie, Quellen Filter Prismen farbtreu (I8, Paragraf 4)`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 20260709), arbBoard) { board ->
                val result = trace(board)

                result.segments.size shouldBeLessThanOrEqual
                    Board.cellCount(board.radius) * Direction.COUNT * COLOR_MASKS
                result.segments.distinct().size shouldBe result.segments.size
                result.segments.forEach { segment -> checkSegment(board, segment) }
                result.received.forEach { (cell, color) ->
                    (board[cell] is Element.Crystal).shouldBeTrue()
                    color.bits shouldBeInRange 1..COLOR_MASKS
                }
            }
        }
    }

    private fun checkSegment(
        board: Board,
        segment: Segment,
    ) {
        // Nachbarschaft ueber die testeigene Delta-Tabelle (Paragraf 2_2)
        deltas shouldContain (segment.to.q - segment.from.q to segment.to.r - segment.from.r)
        segment.color.bits shouldBeInRange 1..COLOR_MASKS
        when (val element = board[segment.from]) {
            is Element.Wall, is Element.Crystal ->
                // Waende und Kristalle absorbieren — nichts verlaesst sie (Paragraf 4_7/4_8)
                fail("Segment verlaesst absorbierendes Element auf ${segment.from}: $element")
            is Element.Source -> {
                segment.color shouldBe element.color
                segment.to shouldBe segment.from.neighbor(element.direction)
            }
            is Element.Filter -> (segment.color.bits and element.color.bits) shouldBe segment.color.bits
            is Element.Prism -> segment.color.isPrimary.shouldBeTrue()
            else -> Unit
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Einfuegereihenfolge der Element-Map ist ergebnisneutral (Paragraf 5_2 Determinismus)`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 0x4845584D4150), arbBoard) { board ->
                val reversed = Board(board.radius, board.elements.toList().reversed().toMap())

                trace(reversed) shouldBe trace(board)
            }
        }
    }
}
