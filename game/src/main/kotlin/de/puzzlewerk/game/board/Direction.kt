package de.puzzlewerk.game.board

/**
 * Die sechs Strahlrichtungen auf dem pointy-top-Hex-Raster (Design §2.2).
 *
 * Der normative [index] `d` entspricht dem mathematischen Winkel `d · 60°`
 * (x nach rechts, y nach oben); `d + 1` ist die nächste Richtung GEGEN den
 * Uhrzeigersinn. Zuordnung (Design-Tabelle §2.2):
 *
 * | d | Name | Δq | Δr |
 * |---|------|----|----|
 * | 0 | [EAST] | +1 | 0 |
 * | 1 | [NORTH_EAST] | +1 | −1 |
 * | 2 | [NORTH_WEST] | 0 | −1 |
 * | 3 | [WEST] | −1 | 0 |
 * | 4 | [SOUTH_WEST] | −1 | +1 |
 * | 5 | [SOUTH_EAST] | 0 | +1 |
 *
 * @property dq Axial-Verschiebung in q pro Schritt.
 * @property dr Axial-Verschiebung in r pro Schritt.
 */
public enum class Direction(
    public val dq: Int,
    public val dr: Int,
) {
    /** Ost, Winkel 0°. */
    EAST(1, 0),

    /** Nordost, Winkel 60°. */
    NORTH_EAST(1, -1),

    /** Nordwest, Winkel 120°. */
    NORTH_WEST(0, -1),

    /** West, Winkel 180°. */
    WEST(-1, 0),

    /** Südwest, Winkel 240°. */
    SOUTH_WEST(-1, 1),

    /** Südost, Winkel 300°. */
    SOUTH_EAST(0, 1),
    ;

    /** Normativer Richtungsindex `d ∈ 0..5` (Design §2.2). */
    public val index: Int get() = ordinal

    /** Gegenrichtung `(d + 3) mod 6` (Design §2.2). */
    public val opposite: Direction get() = rotatedBy(HALF_TURN)

    /**
     * Richtung nach [steps] 60°-Schritten gegen den Uhrzeigersinn; negative Werte
     * drehen im Uhrzeigersinn, das Ergebnis wird immer auf 0..5 normalisiert.
     * Deckt die Prisma-Ausgänge `d+1`/`d+5` (§4.4) und die Spiegelformel (§4.2) ab.
     */
    public fun rotatedBy(steps: Int): Direction = fromIndex((index + steps).mod(COUNT))

    public companion object {
        /** Anzahl der Strahlrichtungen (Design §2.2). */
        public const val COUNT: Int = 6

        private const val HALF_TURN: Int = 3

        /**
         * Richtung zum normativen Index [index].
         *
         * Werte außerhalb 0..5 sind ein Programmierfehler (Regel C3); an der
         * Vertrauensgrenze rohe Werte zuerst mit [ofIndex] prüfen (S4).
         */
        public fun fromIndex(index: Int): Direction {
            require(index in 0 until COUNT) { "Richtungsindex muss in 0..5 liegen, war $index" }
            return entries[index]
        }

        /** Richtung zum Index [index], oder `null` außerhalb 0..5 (Vertrauensgrenze, S4). */
        public fun ofIndex(index: Int): Direction? = if (index in 0 until COUNT) entries[index] else null
    }
}
