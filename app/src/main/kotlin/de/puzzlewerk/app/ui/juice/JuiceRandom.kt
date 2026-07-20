package de.puzzlewerk.app.ui.juice

import de.puzzlewerk.core.RandomSource
import de.puzzlewerk.core.SplitMix64Random
import de.puzzlewerk.core.mix64

/** Bits, die [nextUnit] aus einem 64-Bit-Zufallswert als Mantisse verwendet (24 Bit). */
private const val UNIT_SHIFT: Int = 40
private const val UNIT_DIVISOR: Float = (1 shl 24).toFloat()

/**
 * Produktions-Factory des Juice-PRNG (ADR-011/§13.13): SplitMix64 aus :core
 * (ADR-003). Jeder Aufruf liefert eine frische, allein durch [create]-Seed
 * bestimmte Quelle — nie geteilt (Determinismus unabhängig von Emitter-Reihenfolge).
 */
internal class DefaultJuiceRandomFactory : JuiceRandomFactory {
    override fun create(seed: Long): RandomSource = SplitMix64Random(seed)
}

/**
 * Normative Seed-Ableitung (ADR-011, löst §13.13 ein):
 * `mix64(mix64(mix64(levelSeed) + zugNummer) + emitterIndex)` — verkettete
 * Finalizer aus :core (ADR-003), pur und versionsstabil, ohne neue Konstanten.
 */
internal fun juiceSeed(
    levelSeed: Long,
    moveNumber: Int,
    emitterIndex: Int,
): Long = mix64(mix64(mix64(levelSeed) + moveNumber) + emitterIndex)

/** Zieht einen Float in `[0, 1)` (obere 24 Bit) — deterministisch, Spawn-only. */
internal fun RandomSource.nextUnit(): Float = (nextLong() ushr UNIT_SHIFT).toFloat() / UNIT_DIVISOR
