package de.puzzlewerk.game.level

/**
 * Schwierigkeitsstufen D1..D7 (Design §9.2). Die Tier-Parametertabelle
 * (Radius-, Quellen-, Element- und Par-Budgets) ist Implementierungsdetail
 * des Generators (PW-2.5); bindend ist §9.2.
 *
 * Wochenstaffel des Täglichen Prismas: Mo=D1 … So=D7 (Design §10.2).
 */
public enum class Difficulty {
    /** Einstieg: R=2, nur Spiegel, Par 1–3. */
    D1,

    /** + Splitter. */
    D2,

    /** + Prisma, R=3. */
    D3,

    /** + Filter. */
    D4,

    /** + Portal (1 Paar), R=3–4. */
    D5,

    /** Alle Elemente, ≤ 2 Portalpaare, R=4. */
    D6,

    /** Maximalkomplexität: Par 8–14 (Design §9.2, D7). */
    D7,
    ;

    public companion object {
        private const val DAYS_PER_WEEK: Int = 7

        /**
         * Tier für den ISO-Wochentag [isoDayOfWeek] ∈ 1..7 (Mo=1 … So=7, Design §10.2).
         * Werte außerhalb 1..7 sind ein Programmierfehler (Regel C3).
         */
        public fun forIsoDayOfWeek(isoDayOfWeek: Int): Difficulty {
            require(isoDayOfWeek in 1..DAYS_PER_WEEK) { "ISO-Wochentag muss in 1..7 liegen, war $isoDayOfWeek" }
            return entries[isoDayOfWeek - 1]
        }
    }
}
