package de.puzzlewerk.game.color

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LightColorTest {
    @Test
    fun `Farbcodes entsprechen der Tabelle aus Paragraf 3_1`() {
        LightColor.RED.bits shouldBe 1
        LightColor.GREEN.bits shouldBe 2
        LightColor.YELLOW.bits shouldBe 3
        LightColor.BLUE.bits shouldBe 4
        LightColor.MAGENTA.bits shouldBe 5
        LightColor.CYAN.bits shouldBe 6
        LightColor.WHITE.bits shouldBe 7
    }

    @Test
    fun `mixedWith ist das bitweise ODER ueber alle Farbpaare (Paragraf 3_2)`() {
        val all = (1..7).map { LightColor(it) }

        for (first in all) {
            for (second in all) {
                (first mixedWith second).bits shouldBe (first.bits or second.bits)
                // Symmetrie und Idempotenz der OR-Tabelle
                (first mixedWith second) shouldBe (second mixedWith first)
                (first mixedWith first) shouldBe first
            }
        }
    }

    @Test
    fun `Mischbeispiele aus dem Design`() {
        (LightColor.RED mixedWith LightColor.GREEN) shouldBe LightColor.YELLOW
        (LightColor.RED mixedWith LightColor.BLUE) shouldBe LightColor.MAGENTA
        (LightColor.GREEN mixedWith LightColor.BLUE) shouldBe LightColor.CYAN
        (LightColor.YELLOW mixedWith LightColor.BLUE) shouldBe LightColor.WHITE
    }

    @Test
    fun `filteredBy ist das bitweise UND mit Absorption als null (Paragraf 3_3)`() {
        // Zeilen der Filtertabelle: Strahl x Filter (Rot, Gruen, Blau)
        LightColor.RED.filteredBy(LightColor.RED) shouldBe LightColor.RED
        LightColor.RED.filteredBy(LightColor.GREEN).shouldBeNull()
        LightColor.RED.filteredBy(LightColor.BLUE).shouldBeNull()
        LightColor.YELLOW.filteredBy(LightColor.RED) shouldBe LightColor.RED
        LightColor.YELLOW.filteredBy(LightColor.BLUE).shouldBeNull()
        LightColor.MAGENTA.filteredBy(LightColor.BLUE) shouldBe LightColor.BLUE
        LightColor.CYAN.filteredBy(LightColor.GREEN) shouldBe LightColor.GREEN
        LightColor.WHITE.filteredBy(LightColor.RED) shouldBe LightColor.RED
        LightColor.WHITE.filteredBy(LightColor.GREEN) shouldBe LightColor.GREEN
        LightColor.WHITE.filteredBy(LightColor.BLUE) shouldBe LightColor.BLUE
    }

    @Test
    fun `contains prueft die Bit-Obermenge`() {
        LightColor.WHITE.contains(LightColor.RED).shouldBeTrue()
        LightColor.WHITE.contains(LightColor.YELLOW).shouldBeTrue()
        LightColor.YELLOW.contains(LightColor.BLUE).shouldBeFalse()
        LightColor.RED.contains(LightColor.RED).shouldBeTrue()
    }

    @Test
    fun `isPrimary gilt genau fuer Rot Gruen Blau`() {
        LightColor.RED.isPrimary.shouldBeTrue()
        LightColor.GREEN.isPrimary.shouldBeTrue()
        LightColor.BLUE.isPrimary.shouldBeTrue()
        LightColor.YELLOW.isPrimary.shouldBeFalse()
        LightColor.MAGENTA.isPrimary.shouldBeFalse()
        LightColor.CYAN.isPrimary.shouldBeFalse()
        LightColor.WHITE.isPrimary.shouldBeFalse()
    }

    @Test
    fun `components liefert die Primaerkomponenten in der Prisma-Reihenfolge R G B`() {
        LightColor.WHITE.components shouldBe listOf(LightColor.RED, LightColor.GREEN, LightColor.BLUE)
        LightColor.YELLOW.components shouldBe listOf(LightColor.RED, LightColor.GREEN)
        LightColor.MAGENTA.components shouldBe listOf(LightColor.RED, LightColor.BLUE)
        LightColor.CYAN.components shouldBe listOf(LightColor.GREEN, LightColor.BLUE)
        LightColor.BLUE.components shouldBe listOf(LightColor.BLUE)
    }

    @Test
    fun `Farbe 0 und Werte ueber 7 sind keine gueltigen Strahlfarben (I8)`() {
        shouldThrow<IllegalArgumentException> { LightColor(0) }
        shouldThrow<IllegalArgumentException> { LightColor(8) }
        LightColor.of(0).shouldBeNull()
        LightColor.of(8).shouldBeNull()
        LightColor.of(7) shouldBe LightColor.WHITE
    }
}
