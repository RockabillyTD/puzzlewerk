package de.puzzlewerk.game.daily

import de.puzzlewerk.game.generator.QsLevels
import de.puzzlewerk.game.generator.playThroughInParMoves
import de.puzzlewerk.game.level.DefaultLevelValidator
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelValidationResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

// Unabhaengige Niederschrift der SplitMix64-Finalizer-Konstanten aus Design
// Paragraf 8 (NICHT aus :core importiert — das ist der Sinn der Gegenprobe).
private const val QS_MIX_MULTIPLIER_1 = -0x40A7B892E31B1A47L // 0xBF58476D1CE4E5B9
private const val QS_MIX_MULTIPLIER_2 = -0x6B2FB644ECCEEE15L // 0x94D049BB133111EB
private const val QS_PRISMA_SALT = 0x505249534D41L // ASCII "PRISMA" (Paragraf 10.1)

// Testwoche: epochDay 20643 = 2026-07-09 (Donnerstag) bis 20649 = 2026-07-15.
private const val WEEK_START_EPOCH_DAY = 20643L
private const val DAYS_PER_WEEK = 7

// Erwartete Tiers Do..Mi laut Wochenstaffel Paragraf 10.2 (Mo=D1 .. So=D7).
private val EXPECTED_TIERS =
    listOf(
        Difficulty.D4,
        Difficulty.D5,
        Difficulty.D6,
        Difficulty.D7,
        Difficulty.D1,
        Difficulty.D2,
        Difficulty.D3,
    )

private fun qsMix64(value: Long): Long {
    var z = value
    z = (z xor (z ushr 30)) * QS_MIX_MULTIPLIER_1
    z = (z xor (z ushr 27)) * QS_MIX_MULTIPLIER_2
    return z xor (z ushr 31)
}

/**
 * Daily-Kette Ende-zu-Ende (PW-2.5-QS): dailySeed(epochDay) -> Generator ->
 * valides, ungeloestes, in exakt Par Zuegen loesbares Level — fuer eine volle
 * Woche ab epochDay 20643 (deckt damit ALLE 7 Tiers der Wochenstaffel,
 * Paragraf 10.1/10.2). Die Seed-Formel wird gegen eine UNABHAENGIG
 * niedergeschriebene mix64-Version gerechnet, nicht gegen :core.
 */
class DailyWeekChainQsTest {
    @Test
    fun `Seed-Formel und Wochenstaffel unabhaengig nachgerechnet (Paragraf 10_1, 10_2)`() {
        repeat(DAYS_PER_WEEK) { offset ->
            val epochDay = WEEK_START_EPOCH_DAY + offset

            dailySeed(epochDay) shouldBe qsMix64(epochDay xor QS_PRISMA_SALT)
            dailyTier(epochDay) shouldBe EXPECTED_TIERS[offset]
        }
    }

    @Test
    fun `Woche ab epochDay 20643 - jedes Daily ist valide, ungeloest und in Par Zuegen loesbar`() {
        repeat(DAYS_PER_WEEK) { offset ->
            val epochDay = WEEK_START_EPOCH_DAY + offset
            val tier = dailyTier(epochDay)
            val seed = dailySeed(epochDay)
            val level = QsLevels.of(seed, tier)

            DefaultLevelValidator.validate(level).shouldBeInstanceOf<LevelValidationResult.Valid>()
            // Identitaets-Roundtrip: (seed, tier) identifizieren das Daily (Paragraf 10.1)
            level.seed shouldBe seed
            level.tier shouldBe tier
            // R35 via Engine plus I4: exakt Par Zuege bis Geloest
            playThroughInParMoves(level)
        }
    }
}
