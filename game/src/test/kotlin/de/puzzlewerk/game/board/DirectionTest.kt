package de.puzzlewerk.game.board

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DirectionTest {
    @Test
    fun `Index-Zuordnung folgt der Tabelle aus Paragraf 2_2`() {
        Direction.EAST.index shouldBe 0
        Direction.NORTH_EAST.index shouldBe 1
        Direction.NORTH_WEST.index shouldBe 2
        Direction.WEST.index shouldBe 3
        Direction.SOUTH_WEST.index shouldBe 4
        Direction.SOUTH_EAST.index shouldBe 5
        Direction.COUNT shouldBe Direction.entries.size
    }

    @Test
    fun `Delta-Werte entsprechen der Tabelle aus Paragraf 2_2`() {
        val deltas = Direction.entries.map { it.dq to it.dr }

        deltas shouldBe listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)
    }

    @Test
    fun `opposite ist d plus 3 modulo 6`() {
        for (direction in Direction.entries) {
            direction.opposite.index shouldBe (direction.index + 3).mod(6)
            direction.opposite.opposite shouldBe direction
        }
    }

    @Test
    fun `rotatedBy normalisiert auch negative und grosse Schrittzahlen`() {
        Direction.EAST.rotatedBy(1) shouldBe Direction.NORTH_EAST
        Direction.EAST.rotatedBy(5) shouldBe Direction.SOUTH_EAST
        Direction.EAST.rotatedBy(-1) shouldBe Direction.SOUTH_EAST
        Direction.SOUTH_EAST.rotatedBy(7) shouldBe Direction.EAST
        Direction.WEST.rotatedBy(0) shouldBe Direction.WEST
    }

    @Test
    fun `fromIndex liefert die Richtung zum normativen Index`() {
        for (index in 0..5) {
            Direction.fromIndex(index).index shouldBe index
        }
    }

    @Test
    fun `fromIndex ausserhalb 0 bis 5 ist ein Programmierfehler`() {
        shouldThrow<IllegalArgumentException> { Direction.fromIndex(-1) }
        shouldThrow<IllegalArgumentException> { Direction.fromIndex(6) }
    }

    @Test
    fun `ofIndex liefert null fuer Werte ausserhalb der Vertrauensgrenze`() {
        Direction.ofIndex(0) shouldBe Direction.EAST
        Direction.ofIndex(5) shouldBe Direction.SOUTH_EAST
        Direction.ofIndex(-1).shouldBeNull()
        Direction.ofIndex(6).shouldBeNull()
    }
}
