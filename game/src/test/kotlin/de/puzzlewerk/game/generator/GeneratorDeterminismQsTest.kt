package de.puzzlewerk.game.generator

import de.puzzlewerk.game.level.Difficulty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

// Adversariale Seeds (PW-2.5-QS): Grenzen, Vorzeichen, die Bitmuster
// 0xAAAA_AAAA_AAAA_AAAA und 0x5555_5555_5555_5555 sowie der Daily-Salt
// (ASCII "PRISMA") selbst als Seed.
private val ADVERSARIAL_SEEDS =
    listOf(
        0L,
        1L,
        -1L,
        Long.MIN_VALUE,
        Long.MAX_VALUE,
        -0x5555555555555556L,
        0x5555555555555555L,
        0x505249534D41L,
    )

/**
 * I2/R34 byte-genau (PW-2.5-QS): gleicher Seed + Tier muss ueber frische
 * Generator-Aufrufe, ueber Wiederholungen und ueber verschachtelte
 * Fremd-Aufrufe hinweg die VOLLSTAENDIG identische LevelDefinition liefern —
 * verglichen per Datengleichheit UND per kanonischer, nach (q, r) sortierter
 * Serialisierung ([canonicalForm]), damit keine Map-Iterationsordnung das
 * Ergebnis schoenfaerben kann.
 */
class GeneratorDeterminismQsTest {
    @Test
    fun `I2 R34 - adversariale Seeds liefern fuer jedes Tier zweimal identische Level`() {
        for (tier in Difficulty.entries) {
            for (seed in ADVERSARIAL_SEEDS) {
                val first = DefaultLevelGenerator.generate(seed, tier)
                val second = DefaultLevelGenerator.generate(seed, tier)

                second shouldBe first
                canonicalForm(second) shouldBe canonicalForm(first)
                first.seed shouldBe seed
                first.tier shouldBe tier
            }
        }
    }

    @Test
    fun `I2 - wiederholte Aufrufe bleiben identisch, auch mit fremden Aufrufen dazwischen`() {
        val seed = -0x5555555555555556L
        val reference = canonicalForm(DefaultLevelGenerator.generate(seed, Difficulty.D4))

        repeat(5) { round ->
            // Fremder Aufruf dazwischen: deckt versteckten Zustand im Singleton auf.
            DefaultLevelGenerator.generate(seed + round + 1, Difficulty.entries[round % Difficulty.entries.size])

            canonicalForm(DefaultLevelGenerator.generate(seed, Difficulty.D4)) shouldBe reference
        }
    }

    @Test
    fun `I2 - Brett-Map zweier Generierungen ist feld-fuer-feld gleich (Seed PRISMA, D5)`() {
        val a = DefaultLevelGenerator.generate(0x505249534D41L, Difficulty.D5)
        val b = DefaultLevelGenerator.generate(0x505249534D41L, Difficulty.D5)

        val sortedKeys = a.board.elements.keys.sortedWith(compareBy({ it.q }, { it.r }))
        sortedKeys shouldBe b.board.elements.keys.sortedWith(compareBy({ it.q }, { it.r }))
        sortedKeys.forEach { cell ->
            b.board.elements[cell] shouldBe a.board.elements[cell]
        }
        a.par shouldBe b.par
    }

    // WARNUNG: Wie die Golden-Tests pinnt dieser Wert den Generator-Output
    // geraeteuebergreifend (I2, R34). Eine Aenderung ist ein BREAKING des
    // Generators und braucht eine bewusste Design-Entscheidung (ADR).
    @Test
    fun `Golden - kanonische Serialisierung fuer Seed -1 Tier D2 ist gepinnt`() {
        canonicalForm(DefaultLevelGenerator.generate(-1L, Difficulty.D2)) shouldBe
            "radius=2|par=3|tier=D2|seed=-1|-2,2=K1|-1,1=T1|0,0=T0|1,0=W|1,1=K1|2,-2=Q1d4"
    }
}
