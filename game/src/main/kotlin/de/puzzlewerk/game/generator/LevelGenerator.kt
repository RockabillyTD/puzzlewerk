package de.puzzlewerk.game.generator

import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition

/**
 * Deterministischer Level-Generator (Design §9).
 *
 * Pure Funktion: gleiche `(seed, tier)` ⇒ byte-identische [LevelDefinition]
 * (Invariante I2, R34) — auf jedem Gerät und jeder JVM. Dafür verbindlich:
 * - Aller Zufall stammt aus EINEM fortlaufenden SplitMix64-Strom
 *   (`de.puzzlewerk.core.SplitMix64Random`, ADR-003), initialisiert mit `seed`.
 * - Ausschließlich Ganzzahlarithmetik und feste Iterationsordnungen (§8) —
 *   keine Floats, keine HashMap-Iterationsreihenfolgen.
 * - Alle MUSS-Eigenschaften aus §9.5 (Lösbarkeit, exakter Par im Tier-Bereich,
 *   Kappen aus §9.2, Relaxierungsleiter bis zum Fallback „Spiegelweg", R36).
 * - Referenzalgorithmus „Vorwärts-Verlegung, Rückwärts-Verwürfelung" (§9.3)
 *   mit Par-Solver über End-Orientierungsvektoren (§9.4, nutzt I9).
 *
 * Implementierung: Ticket PW-2.5.
 */
public fun interface LevelGenerator {
    /** Erzeugt das Level zu ([seed], [tier]) — terminiert IMMER (§9.5/7). */
    public fun generate(
        seed: Long,
        tier: Difficulty,
    ): LevelDefinition
}
