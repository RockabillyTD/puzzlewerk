package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * GOLDEN-TESTS: gepinnte Generator-Outputs (Seed + Tier → exakte
 * LevelDefinition) gegen kuenftige Regressionen.
 *
 * WARNUNG: Eine Aenderung dieser Werte ist ein BREAKING des Generators!
 * Gleicher Seed + Tier MUSS fuer immer byte-identische Level liefern
 * (I2, R34) — das Taegliche Prisma (Paragraf 10.1) und alle eingecheckten
 * Kampagnen-Seeds (Paragraf 11.1) haengen daran. Wer diesen Test anpassen
 * muss, hat den Generator inkompatibel geaendert und braucht eine bewusste
 * Design-Entscheidung (ADR), KEIN stilles Update der Erwartungswerte.
 */
class DefaultLevelGeneratorGoldenTest {
    @Test
    fun `Golden 1 - Seed 1 Tier D1 - exakte LevelDefinition`() {
        DefaultLevelGenerator.generate(seed = 1L, tier = Difficulty.D1) shouldBe
            LevelDefinition(
                board =
                    Board(
                        radius = 2,
                        elements =
                            mapOf(
                                HexCoord(-1, -1) to Element.Crystal(LightColor.WHITE),
                                HexCoord(-1, 1) to Element.Mirror(Orientation(4)),
                                HexCoord(0, 1) to Element.Wall,
                                HexCoord(0, 2) to Element.Wall,
                                HexCoord(2, -2) to Element.Source(LightColor.WHITE, Direction.SOUTH_WEST),
                            ),
                    ),
                par = 2,
                tier = Difficulty.D1,
                seed = 1L,
            )
    }

    @Test
    fun `Golden 2 - Seed 42 Tier D3 - exakte LevelDefinition`() {
        DefaultLevelGenerator.generate(seed = 42L, tier = Difficulty.D3) shouldBe
            LevelDefinition(
                board =
                    Board(
                        radius = 3,
                        elements =
                            mapOf(
                                HexCoord(-3, 0) to Element.Crystal(LightColor.GREEN),
                                HexCoord(-1, -2) to Element.Crystal(LightColor.RED),
                                HexCoord(-1, 1) to Element.Mirror(Orientation(3)),
                                HexCoord(0, -3) to Element.Crystal(LightColor.GREEN),
                                HexCoord(0, -1) to Element.Splitter(Orientation(0)),
                                HexCoord(0, 0) to Element.Splitter(Orientation(4)),
                                HexCoord(0, 1) to Element.Prism,
                                HexCoord(0, 3) to Element.Source(LightColor.WHITE, Direction.NORTH_WEST),
                                HexCoord(2, -1) to Element.Mirror(Orientation(3)),
                                HexCoord(2, 0) to Element.Wall,
                            ),
                    ),
                par = 3,
                tier = Difficulty.D3,
                seed = 42L,
            )
    }
}
