package de.puzzlewerk.core

// SplitMix64-Konstanten (Steele/Lea/Flood) als vorzeichenbehaftete Long-Literale —
// bit-identisch zu den unsigned Werten aus docs/game-design.md §8 (ADR-003).
private const val GOLDEN_GAMMA: Long = -0x61C8864680B583EBL // 0x9E3779B97F4A7C15
private const val MIX_MULTIPLIER_1: Long = -0x40A7B892E31B1A47L // 0xBF58476D1CE4E5B9
private const val MIX_MULTIPLIER_2: Long = -0x6B2FB644ECCEEE15L // 0x94D049BB133111EB
private const val MIX_SHIFT_1: Int = 30
private const val MIX_SHIFT_2: Int = 27
private const val MIX_SHIFT_3: Int = 31

/**
 * SplitMix64-Finalizer `mix64` (Design §8) — reine 64-Bit-Ganzzahlarithmetik.
 *
 * Basis der Seed-Ableitung für das Tägliche Prisma (§10.1) und die Kampagne (§11.1).
 * Achtung: `mix64(0) == 0`; die Aufrufer XOR-en deshalb domänenspezifische Salts ein.
 */
public fun mix64(value: Long): Long {
    var z = value
    z = (z xor (z ushr MIX_SHIFT_1)) * MIX_MULTIPLIER_1
    z = (z xor (z ushr MIX_SHIFT_2)) * MIX_MULTIPLIER_2
    return z xor (z ushr MIX_SHIFT_3)
}

/**
 * Normative [RandomSource]-Implementierung: SplitMix64 (ADR-003, Design §8).
 *
 * Gleicher [seed] erzeugt auf jedem Gerät, jeder JVM und jeder Kotlin-Version exakt
 * dieselbe Sequenz (Invariante I2, Randfall R34) — die Golden-Tests pinnen sie.
 * Bewusste Ausnahme von „val > var": ein PRNG ist inhärent zustandsbehaftet; die
 * Determinismus-Garantie hängt am Seed, nicht an Immutability.
 */
public class SplitMix64Random(seed: Long) : RandomSource {
    private var state: Long = seed

    override fun nextLong(): Long {
        state += GOLDEN_GAMMA
        return mix64(state)
    }

    override fun nextInt(untilExclusive: Int): Int {
        require(untilExclusive > 0) { "untilExclusive muss > 0 sein, war $untilExclusive" }
        return nextLong().mod(untilExclusive.toLong()).toInt()
    }
}
