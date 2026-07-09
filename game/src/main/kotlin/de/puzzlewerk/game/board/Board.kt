package de.puzzlewerk.game.board

import de.puzzlewerk.game.element.Element

private const val CELLS_PER_RING: Int = 3

/**
 * Brettzustand: regelmäßiges Sechseck mit [radius] plus Elementbelegung
 * (Design §2.1, §16.1).
 *
 * Der Typ validiert bewusst NICHT selbst — an der Vertrauensgrenze prüft der
 * `LevelValidator` alle Schema-Regeln aus §16.2 und liefert Fehler als Werte
 * (S4, R43). Innerhalb des Prozesses ist ein regelverletzendes Brett ein
 * Programmierfehler.
 *
 * @property radius Brettradius; zulässig sind [MIN_RADIUS]..[MAX_RADIUS].
 * @property elements Elementbelegung; die Map erzwingt strukturell höchstens
 *   ein Element je Zelle (§16.2/2). Fehlender Eintrag = leere Zelle.
 */
public data class Board(
    val radius: Int,
    val elements: Map<HexCoord, Element>,
) {
    /** `true`, wenn [coord] auf dem Brett liegt (§2.1); außerhalb absorbiert der Rand (§2.3). */
    public operator fun contains(coord: HexCoord): Boolean = coord.isWithinRadius(radius)

    /** Element auf [coord], oder `null` (leer bzw. außerhalb des Bretts). */
    public operator fun get(coord: HexCoord): Element? = elements[coord]

    public companion object {
        /** Kleinster zulässiger Brettradius (Design §2.1). */
        public const val MIN_RADIUS: Int = 2

        /** Größter zulässiger Brettradius — harte Engine-Obergrenze (Design §2.1, R41). */
        public const val MAX_RADIUS: Int = 5

        /** Zellenzahl `3·R·(R+1) + 1` (Design §2.1): R=2 → 19 … R=5 → 91. */
        public fun cellCount(radius: Int): Int = CELLS_PER_RING * radius * (radius + 1) + 1
    }
}
