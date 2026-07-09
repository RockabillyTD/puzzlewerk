package de.puzzlewerk.game.color

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CrystalFillTest {
    @Test
    fun `kein empfangenes Licht bedeutet dunkel`() {
        crystalFill(required = LightColor.YELLOW, received = null) shouldBe CrystalFill.DARK
    }

    @Test
    fun `echte Teilmenge bedeutet teilerfuellt`() {
        crystalFill(required = LightColor.YELLOW, received = LightColor.RED) shouldBe CrystalFill.PARTIAL
        crystalFill(required = LightColor.WHITE, received = LightColor.CYAN) shouldBe CrystalFill.PARTIAL
    }

    @Test
    fun `exakte Sollfarbe bedeutet erfuellt (R22, R24)`() {
        // R22: Rot + Gruen am Gelb-Kristall
        val mixed = LightColor.RED mixedWith LightColor.GREEN
        crystalFill(required = LightColor.YELLOW, received = mixed) shouldBe CrystalFill.FULFILLED

        // R24: Weiss-Kristall via Weiss-Strahl oder via R+G+B — identisches Ergebnis
        val recombined = LightColor.RED mixedWith LightColor.GREEN mixedWith LightColor.BLUE
        crystalFill(required = LightColor.WHITE, received = LightColor.WHITE) shouldBe CrystalFill.FULFILLED
        crystalFill(required = LightColor.WHITE, received = recombined) shouldBe CrystalFill.FULFILLED
    }

    @Test
    fun `Fremdkomponente bedeutet uebersaettigt und nie erfuellt (R23)`() {
        val redAndBlue = LightColor.RED mixedWith LightColor.BLUE
        crystalFill(required = LightColor.RED, received = redAndBlue) shouldBe CrystalFill.OVERSATURATED
        crystalFill(required = LightColor.YELLOW, received = LightColor.WHITE) shouldBe CrystalFill.OVERSATURATED
        // Fremdkomponente schlaegt Teilerfuellung: empfangen enthaelt Soll teilweise UND Fremdes
        crystalFill(required = LightColor.YELLOW, received = LightColor.MAGENTA) shouldBe CrystalFill.OVERSATURATED
    }

    @Test
    fun `mehrfacher Treffer derselben Farbe aendert nichts (R25, OR idempotent)`() {
        val once = LightColor.GREEN
        val twice = LightColor.GREEN mixedWith LightColor.GREEN

        crystalFill(required = LightColor.GREEN, received = twice) shouldBe
            crystalFill(required = LightColor.GREEN, received = once)
    }
}
