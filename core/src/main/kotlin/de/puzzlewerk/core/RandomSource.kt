package de.puzzlewerk.core

/**
 * Abstraktion über Zufall, damit Spiellogik deterministisch testbar bleibt (Regel C2).
 *
 * Produktionscode erhält die normative Implementierung [SplitMix64Random] per Injektion
 * (ADR-003, docs/game-design.md §8); Tests können denselben Seed wiederverwenden oder
 * eine eigene Implementierung stellen.
 */
public interface RandomSource {
    /** Liefert den nächsten 64-Bit-Zufallswert. */
    public fun nextLong(): Long

    /**
     * Liefert eine Zufallszahl im Bereich `[0, untilExclusive)`.
     *
     * Normative Semantik ist `floorMod(nextLong(), untilExclusive)` (Design §8);
     * `untilExclusive <= 0` ist ein Programmierfehler (Regel C3).
     */
    public fun nextInt(untilExclusive: Int): Int
}
