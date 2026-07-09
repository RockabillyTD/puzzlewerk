package de.puzzlewerk.game

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element

/**
 * Geteilte Test-Fixture: das durchgerechnete Beispiel-Level "Drei Farben"
 * aus Design Paragraf 7.3 (Radius 2, Quelle Weiss, ein drehbarer Spiegel,
 * Prisma, drei Primaerfarben-Kristalle).
 */
object DreiFarbenLevel {
    val source = HexCoord(-2, 0)
    val mirror = HexCoord(0, 0)
    val prism = HexCoord(1, -1)
    val redCrystal = HexCoord(1, -2)
    val greenCrystal = HexCoord(2, -2)
    val blueCrystal = HexCoord(2, -1)

    /** Brett mit Spiegel-Orientierung [mirrorSteps]; Startzustand ist m = 5 (Paragraf 7.3). */
    fun board(mirrorSteps: Int = 5): Board =
        Board(
            radius = 2,
            elements =
                mapOf(
                    source to Element.Source(LightColor.WHITE, Direction.EAST),
                    mirror to Element.Mirror(Orientation(mirrorSteps)),
                    prism to Element.Prism,
                    redCrystal to Element.Crystal(LightColor.RED),
                    greenCrystal to Element.Crystal(LightColor.GREEN),
                    blueCrystal to Element.Crystal(LightColor.BLUE),
                ),
        )
}
