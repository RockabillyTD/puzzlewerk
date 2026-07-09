package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Spiegel-, Splitter- und Quellen-Randfaelle R01-R06, R19, R21 (Design Paragraf 15). */
class DefaultTracerReflectionTest {
    private val red = LightColor.RED

    @Test
    fun `R01 - Strahl auf fremde Quellzelle wird absorbiert`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    // Fremde Quelle, deren eigener Strahl sofort das Brett verlaesst
                    HexCoord(0, 0) to Element.Source(LightColor.GREEN, Direction.NORTH_WEST),
                ),
            )

        val redSegments = result.segments.filter { it.color == red }
        redSegments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
            )
    }

    @Test
    fun `R02 - Spiegel parallel zum Strahl laesst ihn geradeaus passieren`() {
        // m = 2 * d_in mod 6 = 0 fuer einen Ost-Strahl
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Mirror(Orientation.ZERO),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
                seg(0, 0, 1, 0, red),
                seg(1, 0, 2, 0, red),
            )
    }

    @Test
    fun `R03 - senkrechter Spiegel reflektiert zurueck, Elemente wirken erneut aus der Gegenrichtung`() {
        // m = (2*0 + 3) mod 6 = 3; Filter auf dem Rueckweg wirkt ein zweites Mal
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                    HexCoord(-1, 0) to Element.Filter(red),
                    HexCoord(0, 0) to Element.Mirror(Orientation(3)),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, LightColor.WHITE),
                seg(-1, 0, 0, 0, red),
                seg(0, 0, -1, 0, red),
                // Rueckweg endet an der eigenen Quelle (R01)
                seg(-1, 0, -2, 0, red),
            )
    }

    @Test
    fun `R04 - geschlossener Spiegel-Zyklus terminiert ueber das visited-Set`() {
        // Splitter speist ein Spiegel-Dreieck; der Kreisstrahl trifft den Splitter
        // erneut mit bereits besuchten Zustaenden.
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-1, 2) to Element.Source(red, Direction.NORTH_EAST),
                    HexCoord(0, 1) to Element.Splitter(Orientation(1)),
                    HexCoord(1, 1) to Element.Mirror(Orientation(2)),
                    HexCoord(1, -1) to Element.Mirror(Orientation.ZERO),
                    HexCoord(-1, 1) to Element.Mirror(Orientation(4)),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-1, 2, 0, 1, red),
                seg(0, 1, 1, 0, red),
                seg(0, 1, 1, 1, red),
                seg(1, 0, 2, -1, red),
                seg(1, 1, 1, 0, red),
                seg(1, 0, 1, -1, red),
                seg(1, -1, 0, 0, red),
                seg(0, 0, -1, 1, red),
                seg(-1, 1, 0, 1, red),
            )
        result.received.shouldBeEmpty()
    }

    @Test
    fun `R05 - Splitter im Parallelfall erzeugt genau einen Ausgangsstrahl`() {
        // m = 0, d_in = 0: d_out == d_in, keine Duplikat-Kopie
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Splitter(Orientation.ZERO),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
                seg(0, 0, 1, 0, red),
                seg(1, 0, 2, 0, red),
            )
    }

    @Test
    fun `Splitter enqueued erst Transmission, dann Reflexion (Paragraf 5_2)`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Splitter(Orientation(1)),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
                // 1) Transmission geradeaus, 2) Reflexion nach Nordost
                seg(0, 0, 1, 0, red),
                seg(0, 0, 1, -1, red),
                seg(1, 0, 2, 0, red),
                seg(1, -1, 2, -2, red),
            )
    }

    @Test
    fun `Splitter mit Rueckreflexion - transmittierter Strahl weiter, reflektierter zurueck`() {
        // m = 3, d_in = 0: d_out = 3 (Rueckweg erlaubt, Paragraf 4.3)
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Splitter(Orientation(3)),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
                seg(0, 0, 1, 0, red),
                seg(0, 0, -1, 0, red),
                seg(1, 0, 2, 0, red),
                seg(-1, 0, -2, 0, red),
            )
    }

    @Test
    fun `R06 - Splitter-Kaskade bleibt endlich`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(-1, 0) to Element.Splitter(Orientation(2)),
                    HexCoord(0, 0) to Element.Splitter(Orientation(2)),
                    HexCoord(1, 0) to Element.Splitter(Orientation(2)),
                ),
            )

        // Hauptlinie (4 Kanten) plus drei Nordwest-Abzweige (1 + 2 + 2 Kanten)
        result.segments.size shouldBe 9
        result.segments.distinct().size shouldBe 9
    }

    @Test
    fun `R19 - Quelle am Rand zielt direkt aus dem Brett`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Crystal(red),
                ),
            )

        result.segments.shouldBeEmpty()
        result.received.shouldBeEmpty()
        result.solved.shouldBeFalse()
    }

    @Test
    fun `R21 - zwei exakt aufeinander gerichtete Quellen absorbieren sich gegenseitig`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(2, 0) to Element.Source(LightColor.GREEN, Direction.WEST),
                ),
            )

        val redSegments = result.segments.filter { it.color == red }
        val greenSegments = result.segments.filter { it.color == LightColor.GREEN }
        redSegments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
                seg(0, 0, 1, 0, red),
                seg(1, 0, 2, 0, red),
            )
        greenSegments shouldBe
            listOf(
                seg(2, 0, 1, 0, LightColor.GREEN),
                seg(1, 0, 0, 0, LightColor.GREEN),
                seg(0, 0, -1, 0, LightColor.GREEN),
                seg(-1, 0, -2, 0, LightColor.GREEN),
            )
        result.segments.size shouldBe 8
    }

    @Test
    fun `Quellen starten deterministisch aufsteigend nach q, dann r (Paragraf 5_2)`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(0, -1) to Element.Source(red, Direction.NORTH_WEST),
                    HexCoord(0, 1) to Element.Source(LightColor.GREEN, Direction.SOUTH_EAST),
                    HexCoord(-1, 0) to Element.Source(LightColor.BLUE, Direction.WEST),
                ),
            )

        // (-1,0) vor (0,-1) vor (0,1)
        result.segments shouldBe
            listOf(
                seg(-1, 0, -2, 0, LightColor.BLUE),
                seg(0, -1, 0, -2, red),
                seg(0, 1, 0, 2, LightColor.GREEN),
            )
    }
}
