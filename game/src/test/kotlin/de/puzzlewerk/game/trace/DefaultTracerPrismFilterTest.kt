package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Prisma- und Filter-Randfaelle R07-R13 (Design Paragraf 15). */
class DefaultTracerPrismFilterTest {
    private val red = LightColor.RED
    private val green = LightColor.GREEN
    private val blue = LightColor.BLUE

    private fun prismBoard(sourceColor: LightColor) =
        boardOf(
            2,
            HexCoord(-2, 0) to Element.Source(sourceColor, Direction.EAST),
            HexCoord(0, 0) to Element.Prism,
        )

    @Test
    fun `R07 - Rot wird am Prisma nur gebogen, plus 60 Grad`() {
        val result = trace(prismBoard(red))

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
                seg(0, 0, 1, -1, red),
                seg(1, -1, 2, -2, red),
            )
    }

    @Test
    fun `R07 - Gruen laeuft am Prisma geradeaus`() {
        val result = trace(prismBoard(green))

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, green),
                seg(-1, 0, 0, 0, green),
                seg(0, 0, 1, 0, green),
                seg(1, 0, 2, 0, green),
            )
    }

    @Test
    fun `R07 - Blau wird am Prisma nur gebogen, minus 60 Grad`() {
        val result = trace(prismBoard(blue))

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, blue),
                seg(-1, 0, 0, 0, blue),
                seg(0, 0, 0, 1, blue),
                seg(0, 1, 0, 2, blue),
            )
    }

    @Test
    fun `R08 - Gelb zerfaellt in genau Rot und Gruen, kein Blau`() {
        val result = trace(prismBoard(LightColor.YELLOW))

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, LightColor.YELLOW),
                seg(-1, 0, 0, 0, LightColor.YELLOW),
                // Prisma-Reihenfolge R, G (Paragraf 5.2); kein Blau-Strahl
                seg(0, 0, 1, -1, red),
                seg(0, 0, 1, 0, green),
                seg(1, -1, 2, -2, red),
                seg(1, 0, 2, 0, green),
            )
    }

    @Test
    fun `Weiss zerfaellt in drei Primaerstrahlen in der Reihenfolge R G B`() {
        val result = trace(prismBoard(LightColor.WHITE))

        result.segments.drop(2).take(3) shouldBe
            listOf(
                seg(0, 0, 1, -1, red),
                seg(0, 0, 1, 0, green),
                seg(0, 0, 0, 1, blue),
            )
        result.segments.size shouldBe 8
    }

    @Test
    fun `R09 - Prisma-Ausgaenge direkt aus dem Brett werden randlos absorbiert`() {
        // Prisma auf (1,-2): Weiss kommt aus Nordwest an; nur Rot (nach West) bleibt
        // auf dem Brett, Gruen und Blau zeigen direkt ins Aus.
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(1, 1) to Element.Source(LightColor.WHITE, Direction.NORTH_WEST),
                    HexCoord(1, -2) to Element.Prism,
                ),
            )

        result.segments shouldBe
            listOf(
                seg(1, 1, 1, 0, LightColor.WHITE),
                seg(1, 0, 1, -1, LightColor.WHITE),
                seg(1, -1, 1, -2, LightColor.WHITE),
                seg(1, -2, 0, -2, red),
            )
    }

    @Test
    fun `R10 - Prisma hinter Prisma biegt Primaerfarben nur noch`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                    HexCoord(0, 0) to Element.Prism,
                    // zweites Prisma auf dem Rot-Ausgang des ersten
                    HexCoord(1, -1) to Element.Prism,
                ),
            )

        // Rot betritt das zweite Prisma mit d_in = 1 und knickt auf d = 2 (Nordwest)
        result.segments.filter { it.from == HexCoord(1, -1) } shouldBe
            listOf(seg(1, -1, 1, -2, red))
        result.segments.size shouldBe 8
    }

    @Test
    fun `R11 - Filter auf falscher Farbe absorbiert, kein Strahl der Farbe 0`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Filter(blue),
                    HexCoord(2, 0) to Element.Crystal(red),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
            )
        result.received.shouldBeEmpty()
        result.solved.shouldBeFalse()
    }

    @Test
    fun `R12 - Weiss durch Rot-Filter laesst genau die Rot-Komponente passieren`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                    HexCoord(0, 0) to Element.Filter(red),
                    HexCoord(2, 0) to Element.Crystal(red),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, LightColor.WHITE),
                seg(-1, 0, 0, 0, LightColor.WHITE),
                seg(0, 0, 1, 0, red),
                seg(1, 0, 2, 0, red),
            )
        result.received shouldBe mapOf(HexCoord(2, 0) to red)
        result.solved.shouldBeTrue()
    }

    @Test
    fun `R13 - Strahl exakt in Filterfarbe passiert unveraendert`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(green, Direction.EAST),
                    HexCoord(0, 0) to Element.Filter(green),
                    HexCoord(2, 0) to Element.Crystal(green),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, green),
                seg(-1, 0, 0, 0, green),
                seg(0, 0, 1, 0, green),
                seg(1, 0, 2, 0, green),
            )
        result.received shouldBe mapOf(HexCoord(2, 0) to green)
        result.solved.shouldBeTrue()
    }
}
