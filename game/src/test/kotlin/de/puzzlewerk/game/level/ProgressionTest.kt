package de.puzzlewerk.game.level

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ProgressionTest {
    @Test
    fun `ohne Fortschritt sind genau Level 1 bis 3 offen (Paragraf 11_2)`() {
        (1..CAMPAIGN_LEVEL_COUNT).filter { isLevelUnlocked(it, 0) } shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `Level n ist offen gdw n kleinergleich hoechstesGeloestes plus 3`() {
        for (highest in 0..CAMPAIGN_LEVEL_COUNT) {
            val expected = (1..CAMPAIGN_LEVEL_COUNT).filter { it <= highest + 3 }
            (1..CAMPAIGN_LEVEL_COUNT).filter { isLevelUnlocked(it, highest) } shouldBe expected
        }
    }

    @Test
    fun `Puffergrenze exakt bei hoechstesGeloestes plus 3`() {
        isLevelUnlocked(10, 7) shouldBe true
        isLevelUnlocked(11, 7) shouldBe false
        // bereits gelöste und übersprungene Level bleiben offen
        isLevelUnlocked(1, 7) shouldBe true
        isLevelUnlocked(7, 7) shouldBe true
    }

    @Test
    fun `Levelnummern ausserhalb 1 bis 50 sind immer gesperrt`() {
        isLevelUnlocked(0, CAMPAIGN_LEVEL_COUNT) shouldBe false
        isLevelUnlocked(-1, CAMPAIGN_LEVEL_COUNT) shouldBe false
        isLevelUnlocked(CAMPAIGN_LEVEL_COUNT + 1, CAMPAIGN_LEVEL_COUNT) shouldBe false
        isLevelUnlocked(Int.MIN_VALUE, CAMPAIGN_LEVEL_COUNT) shouldBe false
        isLevelUnlocked(Int.MAX_VALUE, CAMPAIGN_LEVEL_COUNT) shouldBe false
    }

    @Test
    fun `alles geloest heisst alles offen`() {
        (1..CAMPAIGN_LEVEL_COUNT).all { isLevelUnlocked(it, CAMPAIGN_LEVEL_COUNT) } shouldBe true
    }

    @Test
    fun `kein Int-Ueberlauf bei extremen Eingaben`() {
        isLevelUnlocked(CAMPAIGN_LEVEL_COUNT, Int.MAX_VALUE) shouldBe true
        isLevelUnlocked(1, Int.MIN_VALUE) shouldBe false
    }
}
