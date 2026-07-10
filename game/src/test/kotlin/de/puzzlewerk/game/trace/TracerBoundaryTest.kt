package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Grenzgeometrie des Hex-Rands (QS-Pass PW-2.2-QS, Design Paragraf 2_1/2_3,
 * R09, R19): Fuer JEDE Randzelle und JEDE der sechs Richtungen wird der
 * komplette Strahlweg gegen ein testeigenes Orakel geprueft — Zugehoerigkeit
 * `max(|q|, |r|, |q+r|) <= R` und Richtungs-Deltas sind hier unabhaengig von
 * der Produktions-API notiert. Deckt Ecken, Kantenlaeufe und diagonale
 * Austritte auf Minimal- (R=2) und Maximal-Radius (R=5) ab.
 */
class TracerBoundaryTest {
    /** Richtungs-Deltas aus der Tabelle Paragraf 2_2, unabhaengig vom Direction-Enum. */
    private val deltas = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

    private val red = LightColor.RED

    /** Testeigenes Zugehoerigkeits-Orakel: `max(|q|, |r|, |q+r|)` (Paragraf 2_1). */
    private fun ringOf(cell: HexCoord): Int = maxOf(abs(cell.q), abs(cell.r), abs(cell.q + cell.r))

    /** Alle Zellen des aeussersten Rings eines Bretts mit [radius]. */
    private fun borderCells(radius: Int): List<HexCoord> =
        (-radius..radius)
            .flatMap { q -> (-radius..radius).map { r -> HexCoord(q, r) } }
            .filter { ringOf(it) == radius }

    /** Orakel: gerader Strahlweg von [start] in Richtung [direction] bis zum Brettrand. */
    private fun expectedRay(
        start: HexCoord,
        direction: Int,
        radius: Int,
    ): List<Segment> {
        val segments = mutableListOf<Segment>()
        var cell = start
        while (true) {
            val next = HexCoord(cell.q + deltas[direction].first, cell.r + deltas[direction].second)
            if (ringOf(next) > radius) return segments
            segments += Segment(cell, next, red)
            cell = next
        }
    }

    private fun assertAllBorderRays(radius: Int) {
        for (cell in borderCells(radius)) {
            for (direction in 0..5) {
                val result =
                    trace(boardOf(radius, cell to Element.Source(red, Direction.fromIndex(direction))))

                withClue("R=$radius, Quelle=$cell, d=$direction") {
                    result.segments shouldBe expectedRay(cell, direction, radius)
                }
            }
        }
    }

    @Test
    fun `Minimal-Radius R=2 - jeder Randstrahl in jede Richtung exakt bis zum Rand (R19)`() {
        assertAllBorderRays(2)
    }

    @Test
    fun `Maximal-Radius R=5 - jeder Randstrahl in jede Richtung exakt bis zum Rand (R19)`() {
        assertAllBorderRays(5)
    }

    @Test
    fun `Durchmesserlauf - West-Ecke nach Ost-Ecke hat exakt 2R Segmente fuer alle Radien`() {
        for (radius in Board.MIN_RADIUS..Board.MAX_RADIUS) {
            val result =
                trace(boardOf(radius, HexCoord(-radius, 0) to Element.Source(red, Direction.EAST)))

            withClue("R=$radius") {
                result.segments.size shouldBe 2 * radius
                result.segments.last() shouldBe seg(radius - 1, 0, radius, 0, red)
            }
        }
    }
}
