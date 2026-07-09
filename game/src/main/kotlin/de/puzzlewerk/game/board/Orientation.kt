package de.puzzlewerk.game.board

/**
 * Orientierung eines drehbaren Elements in 30°-Schritten, Stufen `m ∈ 0..5`
 * (Design §2.2, §4.2): Der Spiegel-/Splitterstrich liegt auf der Geraden mit
 * Winkel `m · 30°` durch das Zellzentrum.
 *
 * @property steps Orientierungsstufe 0..5; andere Werte sind ein
 *   Programmierfehler (Regel C3) — an der Vertrauensgrenze [of] nutzen (S4).
 */
@JvmInline
public value class Orientation(
    public val steps: Int,
) {
    init {
        require(steps in 0 until COUNT) { "Orientierung muss in 0..5 liegen, war $steps" }
    }

    /** Eine Drehstufe weiter: `(m + 1) mod 6` — Effekt des Rotate-Zugs (Design §6.1). */
    public fun next(): Orientation = Orientation((steps + 1).mod(COUNT))

    /** Eine Drehstufe zurück: `(m + 5) mod 6` — Effekt von Undo (Design §6.2, Invariante I10). */
    public fun previous(): Orientation = Orientation((steps + COUNT - 1).mod(COUNT))

    public companion object {
        /** Anzahl der Orientierungsstufen (Design §2.2). */
        public const val COUNT: Int = 6

        /** Orientierung `m = 0`. */
        public val ZERO: Orientation = Orientation(0)

        /** Orientierung zu [steps], oder `null` außerhalb 0..5 (Vertrauensgrenze, S4). */
        public fun of(steps: Int): Orientation? = if (steps in 0 until COUNT) Orientation(steps) else null
    }
}
