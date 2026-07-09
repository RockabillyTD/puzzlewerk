package de.puzzlewerk.game.board

import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class BoardTest {
    // Beispiel-Level "Drei Farben" aus Design Paragraf 7.3 (Radius 2)
    private val board =
        Board(
            radius = 2,
            elements =
                mapOf(
                    HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                    HexCoord(0, 0) to Element.Mirror(Orientation(5)),
                    HexCoord(1, -1) to Element.Prism,
                    HexCoord(1, -2) to Element.Crystal(LightColor.RED),
                    HexCoord(2, -2) to Element.Crystal(LightColor.GREEN),
                    HexCoord(2, -1) to Element.Crystal(LightColor.BLUE),
                ),
        )

    @Test
    fun `contains prueft die Brettzugehoerigkeit`() {
        (HexCoord(0, 0) in board).shouldBeTrue()
        (HexCoord(2, -2) in board).shouldBeTrue()
        (HexCoord(3, 0) in board).shouldBeFalse()
        (HexCoord(0, 3) in board).shouldBeFalse()
    }

    @Test
    fun `get liefert das Element oder null fuer leere Zellen`() {
        board[HexCoord(1, -1)] shouldBe Element.Prism
        board[HexCoord(-1, 0)].shouldBeNull()
        board[HexCoord(9, 9)].shouldBeNull()
    }

    @Test
    fun `Radius-Grenzen und Zellenzahlen aus Paragraf 2_1`() {
        Board.MIN_RADIUS shouldBe 2
        Board.MAX_RADIUS shouldBe 5
        Board.cellCount(Board.MIN_RADIUS) shouldBe 19
        Board.cellCount(Board.MAX_RADIUS) shouldBe 91
    }

    @Test
    fun `Board ist ein Wert mit struktureller Gleichheit`() {
        board shouldBe board.copy()
        board.copy(radius = 3) shouldBe Board(3, board.elements)
    }
}
