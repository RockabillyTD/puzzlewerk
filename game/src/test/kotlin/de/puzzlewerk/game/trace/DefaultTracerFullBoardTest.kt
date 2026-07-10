package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val FULL_RADIUS = 5

/** R41: Maximal-Brett R=5 mit 100 Prozent belegten Zellen (Design Paragraf 15, 5.3). */
class DefaultTracerFullBoardTest {
    /**
     * Synthetisches Extrembrett: jede der 91 Zellen ist belegt, drei Quellen,
     * ein Portalpaar, Kristalle, Prismen, Splitter, Spiegel, Filter und Waende
     * in deterministischem Muster — jeder Strahl interagiert in jeder Zelle.
     */
    private fun fullyOccupiedBoard(): Board {
        val special: Map<HexCoord, Element> =
            mapOf(
                HexCoord(-5, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                HexCoord(5, 0) to Element.Source(LightColor.WHITE, Direction.WEST),
                HexCoord(0, -5) to Element.Source(LightColor.YELLOW, Direction.SOUTH_EAST),
                HexCoord(0, 1) to Element.Portal(0),
                HexCoord(0, -1) to Element.Portal(0),
                HexCoord(3, 0) to Element.Crystal(LightColor.WHITE),
                HexCoord(-3, 0) to Element.Crystal(LightColor.RED),
                HexCoord(0, 3) to Element.Filter(LightColor.BLUE),
            )
        val elements =
            allCells(FULL_RADIUS).associateWith { cell ->
                special[cell] ?: patternElement(cell)
            }

        elements.size shouldBe Board.cellCount(FULL_RADIUS)
        return Board(FULL_RADIUS, elements)
    }

    private fun patternElement(cell: HexCoord): Element =
        when ((cell.q * 7 + cell.r * 3).mod(5)) {
            0 -> Element.Splitter(Orientation((cell.q + cell.r).mod(6)))
            1 -> Element.Mirror(Orientation((cell.q - cell.r).mod(6)))
            2 -> Element.Prism
            3 -> Element.Filter(LightColor.GREEN)
            else -> Element.Wall
        }

    @Test
    fun `R41 - volles Maximal-Brett bleibt unter MAX_TRACE_STEPS und wirft nicht`() {
        val result = trace(fullyOccupiedBoard())

        // Obere Schranke aus Paragraf 5.3: hoechstens ein Segment je verarbeitetem Zustand
        val stateSpace = Board.cellCount(FULL_RADIUS) * Direction.COUNT * 7
        result.segments.size shouldBeLessThanOrEqual stateSpace
        result.segments.size shouldBeGreaterThan 0
        // I8: alle Farben in received liegen in 1..7
        result.received.values.all { it.bits in 1..7 }.shouldBeTrue()
    }

    @Test
    fun `R41 - volles Brett ist deterministisch reproduzierbar`() {
        val first = trace(fullyOccupiedBoard())
        val second = trace(fullyOccupiedBoard())

        second shouldBe first
    }
}
