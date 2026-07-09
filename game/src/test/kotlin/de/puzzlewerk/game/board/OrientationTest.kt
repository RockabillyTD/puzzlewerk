package de.puzzlewerk.game.board

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class OrientationTest {
    @Test
    fun `next dreht eine Stufe weiter und springt von 5 auf 0`() {
        Orientation(0).next() shouldBe Orientation(1)
        Orientation(4).next() shouldBe Orientation(5)
        Orientation(5).next() shouldBe Orientation(0)
    }

    @Test
    fun `previous ist die Umkehrung von next (Invariante I10)`() {
        for (steps in 0..5) {
            val orientation = Orientation(steps)
            orientation.next().previous() shouldBe orientation
            orientation.previous().next() shouldBe orientation
        }
    }

    @Test
    fun `sechs Stufen weiter ist die Ausgangslage (R30)`() {
        var orientation = Orientation(2)
        repeat(6) { orientation = orientation.next() }

        orientation shouldBe Orientation(2)
    }

    @Test
    fun `Konstruktor ausserhalb 0 bis 5 ist ein Programmierfehler`() {
        shouldThrow<IllegalArgumentException> { Orientation(-1) }
        shouldThrow<IllegalArgumentException> { Orientation(6) }
    }

    @Test
    fun `of liefert null fuer Werte ausserhalb der Vertrauensgrenze`() {
        Orientation.of(0) shouldBe Orientation.ZERO
        Orientation.of(5) shouldBe Orientation(5)
        Orientation.of(-1).shouldBeNull()
        Orientation.of(6).shouldBeNull()
    }

    @Test
    fun `ZERO ist die Stufe 0 und COUNT ist 6`() {
        Orientation.ZERO.steps shouldBe 0
        Orientation.COUNT shouldBe 6
    }
}
