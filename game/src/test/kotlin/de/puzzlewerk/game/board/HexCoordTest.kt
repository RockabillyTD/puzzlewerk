package de.puzzlewerk.game.board

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HexCoordTest {
    @Test
    fun `neighbor folgt der normativen Richtungstabelle aus Paragraf 2_2`() {
        val origin = HexCoord(q = 0, r = 0)

        origin.neighbor(Direction.EAST) shouldBe HexCoord(1, 0)
        origin.neighbor(Direction.NORTH_EAST) shouldBe HexCoord(1, -1)
        origin.neighbor(Direction.NORTH_WEST) shouldBe HexCoord(0, -1)
        origin.neighbor(Direction.WEST) shouldBe HexCoord(-1, 0)
        origin.neighbor(Direction.SOUTH_WEST) shouldBe HexCoord(-1, 1)
        origin.neighbor(Direction.SOUTH_EAST) shouldBe HexCoord(0, 1)
    }

    @Test
    fun `Hin- und Rueckschritt in Gegenrichtung heben sich auf`() {
        val start = HexCoord(q = 2, r = -1)

        for (direction in Direction.entries) {
            start.neighbor(direction).neighbor(direction.opposite) shouldBe start
        }
    }

    @Test
    fun `Kubik-Koordinate s ist minus q minus r`() {
        HexCoord(q = 0, r = 0).s shouldBe 0
        HexCoord(q = 2, r = -1).s shouldBe -1
        HexCoord(q = -3, r = 5).s shouldBe -2
    }

    @Test
    fun `ringIndex ist das Maximum der Betraege`() {
        HexCoord(q = 0, r = 0).ringIndex shouldBe 0
        HexCoord(q = 1, r = 0).ringIndex shouldBe 1
        HexCoord(q = 2, r = -2).ringIndex shouldBe 2
        HexCoord(q = -1, r = -1).ringIndex shouldBe 2
        HexCoord(q = 3, r = 2).ringIndex shouldBe 5
    }

    @Test
    fun `isWithinRadius entscheidet die Brettzugehoerigkeit an der Grenze`() {
        HexCoord(q = 2, r = 0).isWithinRadius(2).shouldBeTrue()
        HexCoord(q = 3, r = 0).isWithinRadius(2).shouldBeFalse()
        // (2, -1) hat ringIndex 2: q+r = 1, |q| = 2
        HexCoord(q = 2, r = -1).isWithinRadius(2).shouldBeTrue()
        // (-2, 3): max(2, 3, 1) = 3
        HexCoord(q = -2, r = 3).isWithinRadius(2).shouldBeFalse()
        HexCoord(q = -2, r = 3).isWithinRadius(3).shouldBeTrue()
    }

    @Test
    fun `Zellenzahl je Radius entspricht der Formel aus Paragraf 2_1`() {
        // Gegenprobe: Formel gegen explizite Aufzaehlung der Brettzellen.
        for (radius in 2..5) {
            val enumerated =
                (-radius..radius).sumOf { q ->
                    (-radius..radius).count { r -> HexCoord(q, r).isWithinRadius(radius) }
                }
            enumerated shouldBe Board.cellCount(radius)
        }
        Board.cellCount(2) shouldBe 19
        Board.cellCount(3) shouldBe 37
        Board.cellCount(4) shouldBe 61
        Board.cellCount(5) shouldBe 91
    }
}
