package de.puzzlewerk.game.color

/**
 * Strahlfarbe als 3-Bit-Maske über den additiven Primärkomponenten (Design §3.1):
 * Bit 0 = Rot (1), Bit 1 = Grün (2), Bit 2 = Blau (4).
 *
 * Gültige Werte sind 1..7 — Code 0 („kein Licht") ist KEINE Strahlfarbe
 * (Invariante I8); Operationen, die 0 ergeben können, liefern deshalb `null`
 * (Absorption, siehe [filteredBy]).
 *
 * @property bits Bitmaske 1..7; andere Werte sind ein Programmierfehler
 *   (Regel C3) — an der Vertrauensgrenze [of] nutzen (S4).
 */
@JvmInline
public value class LightColor(
    public val bits: Int,
) {
    init {
        require(bits in MIN_BITS..MAX_BITS) { "Farbmaske muss in 1..7 liegen, war $bits" }
    }

    /** Additive Mischung = bitweises ODER (Design §3.2); idempotent und monoton. */
    public infix fun mixedWith(other: LightColor): LightColor = LightColor(bits or other.bits)

    /**
     * Subtraktives Filtern = bitweises UND (Design §3.3).
     * `null` bedeutet: der Strahl wird absorbiert (Ergebnis wäre Farbe 0, R11).
     */
    public fun filteredBy(filter: LightColor): LightColor? = of(bits and filter.bits)

    /** `true`, wenn alle Komponenten von [component] enthalten sind (Bit-Obermenge). */
    public fun contains(component: LightColor): Boolean = bits and component.bits == component.bits

    /** `true` für genau eine gesetzte Komponente: Rot, Grün oder Blau (Design §3.1). */
    public val isPrimary: Boolean get() = bits.countOneBits() == 1

    /**
     * Enthaltene Primärkomponenten in der normativen Prisma-Reihenfolge
     * Rot, Grün, Blau (Design §4.4, §5.2).
     */
    public val components: List<LightColor>
        get() = listOf(RED, GREEN, BLUE).filter { contains(it) }

    public companion object {
        /** Rot (Maske 1, primär, Symbol ▲). */
        public val RED: LightColor = LightColor(1)

        /** Grün (Maske 2, primär, Symbol ●). */
        public val GREEN: LightColor = LightColor(2)

        /** Gelb (Maske 3 = Rot+Grün, sekundär). */
        public val YELLOW: LightColor = LightColor(3)

        /** Blau (Maske 4, primär, Symbol ■). */
        public val BLUE: LightColor = LightColor(4)

        /** Magenta (Maske 5 = Rot+Blau, sekundär). */
        public val MAGENTA: LightColor = LightColor(5)

        /** Cyan (Maske 6 = Grün+Blau, sekundär). */
        public val CYAN: LightColor = LightColor(6)

        /** Weiß (Maske 7 = Rot+Grün+Blau, tertiär). */
        public val WHITE: LightColor = LightColor(7)

        private const val MIN_BITS: Int = 1
        private const val MAX_BITS: Int = 7

        /** Farbe zur Bitmaske [bits], oder `null` außerhalb 1..7 (Vertrauensgrenze, S4). */
        public fun of(bits: Int): LightColor? = if (bits in MIN_BITS..MAX_BITS) LightColor(bits) else null
    }
}
