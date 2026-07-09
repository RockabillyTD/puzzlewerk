package de.puzzlewerk.core

import kotlin.random.Random

/**
 * Abstraktion über Zufall, damit Spiellogik deterministisch testbar bleibt (Regel C2).
 *
 * Produktionscode erhält eine [SeededRandom]-Instanz per Injektion; Tests können
 * denselben Seed wiederverwenden oder eine eigene Implementierung stellen.
 */
public interface RandomSource {

    /** Liefert eine Zufallszahl im Bereich `[0, untilExclusive)`. */
    public fun nextInt(untilExclusive: Int): Int
}

/**
 * Deterministische [RandomSource]: Gleicher [seed] erzeugt immer dieselbe Sequenz.
 */
public class SeededRandom(seed: Long) : RandomSource {

    private val random = Random(seed)

    override fun nextInt(untilExclusive: Int): Int = random.nextInt(untilExclusive)
}
