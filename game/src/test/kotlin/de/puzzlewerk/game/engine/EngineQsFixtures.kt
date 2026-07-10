package de.puzzlewerk.game.engine

import de.puzzlewerk.game.DreiFarbenLevel
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition

/**
 * Fixtures des unabhaengigen QS-Passes PW-2.3-QS.
 *
 * Zelle (1, 1) ist auf beiden Brettern ein "Koeder": Handverfolgung aller
 * sechs Spiegel-Orientierungen (Paragraf 4.2/5.2) zeigt, dass kein Strahl
 * (1, 1) je erreicht — vom Spiegel (0, 0) aus laufen die Strahlen fuer
 * m = 0..5 ueber (1,0)/(2,0), das Prisma bzw. den Kristall (1,-1), (0,-1)/(0,-2),
 * zurueck zur Quelle, (-1,1)/(-2,2) oder (0,1)/(0,2). Das Koeder-Element
 * liefert damit einen Rotate-Zug, der NIE den Loesungsstatus beeinflusst.
 */
object EngineQsFixtures {
    val source: HexCoord = DreiFarbenLevel.source
    val mirror: HexCoord = DreiFarbenLevel.mirror
    val prism: HexCoord = DreiFarbenLevel.prism
    val redCrystal: HexCoord = DreiFarbenLevel.redCrystal
    val decoy: HexCoord = HexCoord(1, 1)

    /** Start-Orientierung des Koeder-Splitters im [koederLevel]. */
    val decoyStart: Orientation = Orientation(2)

    /**
     * Drei-Farben-Level aus Design Paragraf 7.3 (Spiegel-Start m = 5, loesende
     * Orientierung EXAKT m = 1) plus Koeder-Splitter auf (1, 1).
     * Geloest gdw. Spiegel-Orientierung 1 — unabhaengig vom Koeder.
     */
    fun koederLevel(): LevelDefinition =
        LevelDefinition(
            board =
                DreiFarbenLevel.board().let { base ->
                    Board(base.radius, base.elements + (decoy to Element.Splitter(decoyStart)))
                },
            par = 2,
            tier = Difficulty.D2,
            seed = 0x51533233L,
        )

    /**
     * Minimal-Level (R42): EIN Rotate auf (0, 0) loest — m 0 -> 1 reflektiert
     * den Ost-Strahl nach Nordost auf den Kristall (1, -1) —, plus
     * Koeder-Spiegel auf (1, 1), den kein Strahl erreicht.
     */
    fun minimalMitKoeder(): LevelDefinition =
        LevelDefinition(
            board =
                Board(
                    radius = 2,
                    elements =
                        mapOf(
                            source to Element.Source(LightColor.RED, Direction.EAST),
                            mirror to Element.Mirror(Orientation.ZERO),
                            HexCoord(1, -1) to Element.Crystal(LightColor.RED),
                            decoy to Element.Mirror(Orientation.ZERO),
                        ),
                ),
            par = 1,
            tier = Difficulty.D1,
            seed = 0x51533234L,
        )
}
