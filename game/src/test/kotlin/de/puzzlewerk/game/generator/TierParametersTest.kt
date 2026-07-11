package de.puzzlewerk.game.generator

import de.puzzlewerk.game.level.Difficulty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Pinnt die Tier-Parametertabelle exakt auf Design Paragraf 9.2 —
 * jede Abweichung aendert generierte Level (Daily!) und MUSS auffallen.
 */
class TierParametersTest {
    @Test
    fun `D1 - R2, 1 Quelle, 1-2 Kristalle, nur Spiegel, Par 1-3`() {
        tierParameters(Difficulty.D1) shouldBe
            TierParameters(
                radiusRange = 2..2,
                sourceRange = 1..1,
                crystalRange = 1..2,
                rotatableRange = 1..2,
                parRange = 1..3,
                wallRange = 0..2,
                palette =
                    ElementPalette(
                        splitterAllowed = false,
                        prismAllowed = false,
                        filterAllowed = false,
                        maxPortalPairs = 0,
                        maxColors = 1,
                    ),
            )
    }

    @Test
    fun `D2 - plus Splitter, 2 Kristalle, Par 2-4`() {
        tierParameters(Difficulty.D2) shouldBe
            TierParameters(
                radiusRange = 2..2,
                sourceRange = 1..1,
                crystalRange = 2..2,
                rotatableRange = 2..3,
                parRange = 2..4,
                wallRange = 0..3,
                palette =
                    ElementPalette(
                        splitterAllowed = true,
                        prismAllowed = false,
                        filterAllowed = false,
                        maxPortalPairs = 0,
                        maxColors = 2,
                    ),
            )
    }

    @Test
    fun `D3 - plus Prisma, R3, Par 3-6`() {
        tierParameters(Difficulty.D3) shouldBe
            TierParameters(
                radiusRange = 3..3,
                sourceRange = 1..2,
                crystalRange = 2..3,
                rotatableRange = 3..4,
                parRange = 3..6,
                wallRange = 1..4,
                palette =
                    ElementPalette(
                        splitterAllowed = true,
                        prismAllowed = true,
                        filterAllowed = false,
                        maxPortalPairs = 0,
                        maxColors = 3,
                    ),
            )
    }

    @Test
    fun `D4 - plus Filter, 3 Kristalle, Par 4-7`() {
        tierParameters(Difficulty.D4) shouldBe
            TierParameters(
                radiusRange = 3..3,
                sourceRange = 1..2,
                crystalRange = 3..3,
                rotatableRange = 4..5,
                parRange = 4..7,
                wallRange = 2..5,
                palette =
                    ElementPalette(
                        splitterAllowed = true,
                        prismAllowed = true,
                        filterAllowed = true,
                        maxPortalPairs = 0,
                        maxColors = 4,
                    ),
            )
    }

    @Test
    fun `D5 - plus Portal (1 Paar), R3-4, Par 5-9`() {
        tierParameters(Difficulty.D5) shouldBe
            TierParameters(
                radiusRange = 3..4,
                sourceRange = 2..2,
                crystalRange = 3..4,
                rotatableRange = 5..6,
                parRange = 5..9,
                wallRange = 2..6,
                palette =
                    ElementPalette(
                        splitterAllowed = true,
                        prismAllowed = true,
                        filterAllowed = true,
                        maxPortalPairs = 1,
                        maxColors = 5,
                    ),
            )
    }

    @Test
    fun `D6 - alle Elemente, bis 2 Portalpaare, Par 6-11`() {
        tierParameters(Difficulty.D6) shouldBe
            TierParameters(
                radiusRange = 4..4,
                sourceRange = 2..3,
                crystalRange = 4..5,
                rotatableRange = 6..7,
                parRange = 6..11,
                wallRange = 3..8,
                palette =
                    ElementPalette(
                        splitterAllowed = true,
                        prismAllowed = true,
                        filterAllowed = true,
                        maxPortalPairs = 2,
                        maxColors = 6,
                    ),
            )
    }

    @Test
    fun `D7 - Maximalkomplexitaet, 7-8 Drehbare, Par 8-14`() {
        tierParameters(Difficulty.D7) shouldBe
            TierParameters(
                radiusRange = 4..4,
                sourceRange = 2..3,
                crystalRange = 5..6,
                rotatableRange = 7..8,
                parRange = 8..14,
                wallRange = 3..8,
                palette =
                    ElementPalette(
                        splitterAllowed = true,
                        prismAllowed = true,
                        filterAllowed = true,
                        maxPortalPairs = 2,
                        maxColors = 7,
                    ),
            )
    }
}
