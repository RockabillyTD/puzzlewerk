package de.puzzlewerk.game.trace

import de.puzzlewerk.game.DreiFarbenLevel
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Endpunkt-Semantik aus ADR-012 (Design Paragraf 13.8a): genau ein
 * [BeamEndpoint] je on-board absorbiertem Strahl (Wand, Quelle, Kristall,
 * Filter mit leerem Schnitt); Brett-Aus erzeugt KEINEN Eintrag. Reihenfolge =
 * normative Verarbeitungsreihenfolge (Paragraf 5.2, dieselbe wie segments).
 */
class DefaultTracerEndpointTest {
    @Test
    fun `Beispiel 13_8a - Level 7_3 nach Zug 2 hat genau drei Endpunkte an den Kristallen`() {
        val result = trace(DreiFarbenLevel.board(mirrorSteps = 1))

        // Spiegel und Prisma sind Durchgangs-, keine Endpunkte (13.8a);
        // Reihenfolge folgt der Prisma-Reihenfolge R, G, B (Paragraf 5.2).
        result.endpoints shouldBe
            listOf(
                BeamEndpoint(DreiFarbenLevel.redCrystal, LightColor.RED),
                BeamEndpoint(DreiFarbenLevel.greenCrystal, LightColor.GREEN),
                BeamEndpoint(DreiFarbenLevel.blueCrystal, LightColor.BLUE),
            )
    }

    @Test
    fun `Gegenbeispiel 13_8a - Level 7_3 Startzustand m=5 laeuft ins Brett-Aus - null Endpunkte`() {
        trace(DreiFarbenLevel.board(mirrorSteps = 5)).endpoints.shouldBeEmpty()
    }

    @Test
    fun `Level 7_3 nach Zug 1 m=0 - Parallelfall ins Brett-Aus - null Endpunkte`() {
        trace(DreiFarbenLevel.board(mirrorSteps = 0)).endpoints.shouldBeEmpty()
    }

    @Test
    fun `Wand absorbiert mit Endpunkt in Strahlfarbe (Paragraf 4_8)`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.CYAN, Direction.EAST),
                    HexCoord(0, 0) to Element.Wall,
                ),
            )

        result.endpoints shouldBe listOf(BeamEndpoint(HexCoord(0, 0), LightColor.CYAN))
    }

    @Test
    fun `Quellen-Gehaeuse absorbiert mit Endpunkt (R01)`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
                    HexCoord(2, 0) to Element.Source(LightColor.GREEN, Direction.WEST),
                ),
            )

        // Quellstart aufsteigend nach (q, dann r): der rote Strahl endet zuerst.
        result.endpoints shouldBe
            listOf(
                BeamEndpoint(HexCoord(2, 0), LightColor.RED),
                BeamEndpoint(HexCoord(-2, 0), LightColor.GREEN),
            )
    }

    @Test
    fun `Filter mit leerem Schnitt absorbiert mit Endpunkt in Auftreff-Farbe (R11)`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
                    HexCoord(0, 0) to Element.Filter(LightColor.GREEN),
                ),
            )

        result.endpoints shouldBe listOf(BeamEndpoint(HexCoord(0, 0), LightColor.RED))
    }

    @Test
    fun `Passierter Filter ist Durchgang - Endpunkt erst am Absorber dahinter`() {
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                    HexCoord(0, 0) to Element.Filter(LightColor.RED),
                    HexCoord(2, 0) to Element.Wall,
                ),
            )

        // Weiss passiert den Rot-Filter als Rot und endet erst an der Wand.
        result.endpoints shouldBe listOf(BeamEndpoint(HexCoord(2, 0), LightColor.RED))
    }

    @Test
    fun `Zwei Strahlen enden am selben Kristall - zwei Eintraege mit je eigener Farbe`() {
        val crystal = HexCoord(0, 0)
        val result =
            trace(
                boardOf(
                    2,
                    HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
                    HexCoord(2, 0) to Element.Source(LightColor.GREEN, Direction.WEST),
                    crystal to Element.Crystal(LightColor.YELLOW),
                ),
            )

        result.endpoints shouldBe
            listOf(
                BeamEndpoint(crystal, LightColor.RED),
                BeamEndpoint(crystal, LightColor.GREEN),
            )
        result.received[crystal] shouldBe LightColor.YELLOW
        result.solved.shouldBeTrue()
    }
}
