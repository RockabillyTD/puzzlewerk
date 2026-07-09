package de.puzzlewerk.game.daily

import de.puzzlewerk.core.mix64
import de.puzzlewerk.game.level.Difficulty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DailySeedsTest {
    @Test
    fun `Golden - dailySeed liefert gepinnte Werte (geraeteuebergreifend, R34)`() {
        // Referenzwerte aus unabhaengiger Implementierung (PW-2.1):
        dailySeed(epochDay = 20643L) shouldBe -5921777564767007749L // 2026-07-09
        dailySeed(epochDay = 0L) shouldBe -859297344199462558L // 1970-01-01
        dailySeed(epochDay = -1L) shouldBe 7597258621292954417L // 1969-12-31 (R37)
    }

    @Test
    fun `dailySeed folgt der Formel aus Paragraf 10_1`() {
        // mix64(epochDay xor ASCII "PRISMA")
        dailySeed(epochDay = 123L) shouldBe mix64(123L xor 0x505249534D41L)
    }

    @Test
    fun `isoDayOfWeek stimmt mit dem ISO-Kalender ueberein`() {
        isoDayOfWeek(epochDay = 0L) shouldBe 4 // 1970-01-01 war ein Donnerstag
        isoDayOfWeek(epochDay = 3L) shouldBe 7 // 1970-01-04, Sonntag
        isoDayOfWeek(epochDay = 4L) shouldBe 1 // 1970-01-05, Montag
        isoDayOfWeek(epochDay = 20643L) shouldBe 4 // 2026-07-09, Donnerstag
        isoDayOfWeek(epochDay = 20647L) shouldBe 1 // 2026-07-13, Montag
    }

    @Test
    fun `isoDayOfWeek ist fuer Daten vor 1970 definiert (R37)`() {
        isoDayOfWeek(epochDay = -1L) shouldBe 3 // 1969-12-31, Mittwoch
        isoDayOfWeek(epochDay = -4L) shouldBe 7 // 1969-12-28, Sonntag
    }

    @Test
    fun `dailyTier folgt der Wochenstaffel aus Paragraf 10_2`() {
        dailyTier(epochDay = 20647L) shouldBe Difficulty.D1 // Montag
        dailyTier(epochDay = 20643L) shouldBe Difficulty.D4 // Donnerstag
        dailyTier(epochDay = 20649L) shouldBe Difficulty.D3 // Mittwoch 2026-07-15
        dailyTier(epochDay = 20646L) shouldBe Difficulty.D7 // Sonntag 2026-07-12
    }

    @Test
    fun `gleicher Tag liefert immer denselben Seed (pure Funktion, I2)`() {
        dailySeed(epochDay = 20643L) shouldBe dailySeed(epochDay = 20643L)
        dailyTier(epochDay = 20643L) shouldBe dailyTier(epochDay = 20643L)
    }
}
