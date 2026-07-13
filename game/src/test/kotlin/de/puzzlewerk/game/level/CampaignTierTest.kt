package de.puzzlewerk.game.level

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CampaignTierTest {
    @Test
    fun `Tier-Grenzen entsprechen exakt der Tabelle aus Paragraf 11_3`() {
        campaignTier(1) shouldBe Difficulty.D1
        campaignTier(6) shouldBe Difficulty.D1
        campaignTier(7) shouldBe Difficulty.D2
        campaignTier(12) shouldBe Difficulty.D2
        campaignTier(13) shouldBe Difficulty.D3
        campaignTier(21) shouldBe Difficulty.D3
        campaignTier(22) shouldBe Difficulty.D4
        campaignTier(29) shouldBe Difficulty.D4
        campaignTier(30) shouldBe Difficulty.D5
        campaignTier(39) shouldBe Difficulty.D5
        campaignTier(40) shouldBe Difficulty.D6
        campaignTier(46) shouldBe Difficulty.D6
        campaignTier(47) shouldBe Difficulty.D7
        campaignTier(50) shouldBe Difficulty.D7
    }

    @Test
    fun `komplette Abbildung 1 bis 50 ist lueckenlos und ueberlappungsfrei`() {
        // Unabhaengig aus der Verteilung D1×6, D2×6, D3×9, D4×8, D5×10, D6×7, D7×4
        // (Design §11.3, Summe 50) aufgebaut — bewusst NICHT die when-Bereiche kopiert.
        val distribution =
            listOf(
                Difficulty.D1 to 6,
                Difficulty.D2 to 6,
                Difficulty.D3 to 9,
                Difficulty.D4 to 8,
                Difficulty.D5 to 10,
                Difficulty.D6 to 7,
                Difficulty.D7 to 4,
            )
        val expected = distribution.flatMap { (tier, count) -> List(count) { tier } }

        expected.size shouldBe CAMPAIGN_LEVEL_COUNT
        (1..CAMPAIGN_LEVEL_COUNT).map { campaignTier(it) } shouldBe expected
    }

    @Test
    fun `Tier ist monoton nicht-fallend und springt hoechstens eine Stufe (Paragraf 11_3)`() {
        val tiers = (1..CAMPAIGN_LEVEL_COUNT).map { campaignTier(it) }
        tiers.zipWithNext().forEach { (a, b) ->
            (b.ordinal >= a.ordinal) shouldBe true
            (b.ordinal - a.ordinal <= 1) shouldBe true
        }
    }

    @Test
    fun `jedes Tier D1 bis D7 kommt mindestens einmal vor (Paragraf 11_3)`() {
        val vorhanden = (1..CAMPAIGN_LEVEL_COUNT).map { campaignTier(it) }.toSet()
        vorhanden shouldBe Difficulty.entries.toSet()
    }

    @Test
    fun `campaignTier ausserhalb 1 bis 50 ist ein Programmierfehler`() {
        shouldThrow<IllegalArgumentException> { campaignTier(0) }
        shouldThrow<IllegalArgumentException> { campaignTier(51) }
    }
}
