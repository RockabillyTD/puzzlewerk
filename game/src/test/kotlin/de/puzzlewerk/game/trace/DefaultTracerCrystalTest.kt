package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Kristall-Randfaelle R20, R22-R26 (Design Paragraf 15) und Loesungsbedingung Paragraf 5.4. */
class DefaultTracerCrystalTest {
    private val red = LightColor.RED
    private val green = LightColor.GREEN
    private val blue = LightColor.BLUE

    @Test
    fun `R20 - Quelle direkt auf benachbarten Kristall, genau ein Segment`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(-1, 0) to Element.Crystal(red),
                ),
            )

        result.segments shouldBe listOf(seg(-2, 0, -1, 0, red))
        result.received shouldBe mapOf(HexCoord(-1, 0) to red)
        result.solved.shouldBeTrue()
    }

    @Test
    fun `R22 - Rot und Gruen mischen sich am Gelb-Kristall zu erfuellt`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(2, 0) to Element.Source(green, Direction.WEST),
                    HexCoord(0, 0) to Element.Crystal(LightColor.YELLOW),
                ),
            )

        result.received shouldBe mapOf(HexCoord(0, 0) to LightColor.YELLOW)
        result.solved.shouldBeTrue()
    }

    @Test
    fun `R23 - Fremdkomponente uebersaettigt den Kristall, nicht geloest`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(2, -2) to Element.Source(blue, Direction.SOUTH_EAST),
                    HexCoord(2, 0) to Element.Crystal(red),
                ),
            )

        // empfangen = Rot ODER Blau = Magenta, Soll = Rot: uebersaettigt
        result.received shouldBe mapOf(HexCoord(2, 0) to LightColor.MAGENTA)
        result.solved.shouldBeFalse()
    }

    @Test
    fun `R24 - Weiss-Kristall via einem Weiss-Strahl oder via R G B identisch erfuellt`() {
        val viaWhite =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                    HexCoord(0, 0) to Element.Crystal(LightColor.WHITE),
                ),
            )
        val viaComponents =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(2, 0) to Element.Source(green, Direction.WEST),
                    HexCoord(0, 2) to Element.Source(blue, Direction.NORTH_WEST),
                    HexCoord(0, 0) to Element.Crystal(LightColor.WHITE),
                ),
            )

        viaWhite.received shouldBe mapOf(HexCoord(0, 0) to LightColor.WHITE)
        viaComponents.received shouldBe mapOf(HexCoord(0, 0) to LightColor.WHITE)
        viaWhite.solved.shouldBeTrue()
        viaComponents.solved.shouldBeTrue()
    }

    @Test
    fun `R25 - dieselbe Farbe mehrfach am Kristall ist idempotent`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(2, 0) to Element.Source(red, Direction.WEST),
                    HexCoord(0, 0) to Element.Crystal(red),
                ),
            )

        result.received shouldBe mapOf(HexCoord(0, 0) to red)
        result.solved.shouldBeTrue()
    }

    @Test
    fun `R26 - Kristall absorbiert, dahinterliegender Kristall bleibt dunkel`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Crystal(red),
                    HexCoord(2, 0) to Element.Crystal(red),
                ),
            )

        result.received shouldBe mapOf(HexCoord(0, 0) to red)
        result.received shouldNotContainKey HexCoord(2, 0)
        // fehlender Eintrag = dunkel = nicht erfuellt
        result.solved.shouldBeFalse()
    }

    @Test
    fun `Teilerfuellung ist nicht geloest (Paragraf 5_4)`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Crystal(LightColor.YELLOW),
                ),
            )

        result.received shouldBe mapOf(HexCoord(0, 0) to red)
        result.solved.shouldBeFalse()
    }

    @Test
    fun `Wand absorbiert den Strahl vor dem Kristall (Paragraf 4_8)`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(red, Direction.EAST),
                    HexCoord(0, 0) to Element.Wall,
                    HexCoord(2, 0) to Element.Crystal(red),
                ),
            )

        result.segments shouldBe
            listOf(
                seg(-2, 0, -1, 0, red),
                seg(-1, 0, 0, 0, red),
            )
        result.received shouldNotContainKey HexCoord(2, 0)
        result.solved.shouldBeFalse()
    }

    @Test
    fun `Brett ohne Quelle liefert leeres Ergebnis, Kristall bleibt dunkel`() {
        val result = trace(boardOf(2, HexCoord(0, 0) to Element.Crystal(red)))

        result.segments shouldBe emptyList()
        result.received shouldBe emptyMap()
        result.solved.shouldBeFalse()
    }
}
