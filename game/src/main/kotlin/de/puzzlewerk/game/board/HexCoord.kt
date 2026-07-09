package de.puzzlewerk.game.board

import kotlin.math.abs

/**
 * Axial-Koordinate `(q, r)` einer pointy-top-Hexzelle (Design §2.1).
 *
 * Eine Zelle gehört zu einem Brett mit Radius `R` gdw. `max(|q|, |r|, |q+r|) ≤ R`
 * (siehe [isWithinRadius]). Die Pixel-Abbildung ist reine Rendering-Sache (§2.4)
 * und gehört NICHT in dieses Modul.
 *
 * @property q Axial-Spaltenkoordinate.
 * @property r Axial-Zeilenkoordinate.
 */
public data class HexCoord(
    val q: Int,
    val r: Int,
) {
    /** Abgeleitete Kubik-Koordinate `s = −q − r` (Design §2.1). */
    public val s: Int get() = -q - r

    /**
     * Ring-Index `max(|q|, |r|, |q+r|)`: 0 = Brettzentrum, `R` = äußerster Ring.
     * Der Generator platziert Quellen auf Zellen mit `ringIndex == R` (§9.3 Schritt 2).
     */
    public val ringIndex: Int get() = maxOf(abs(q), abs(r), abs(q + r))

    /** Nachbarzelle in [direction]: `(q + Δq, r + Δr)` (Design §2.2). */
    public fun neighbor(direction: Direction): HexCoord = HexCoord(q + direction.dq, r + direction.dr)

    /** `true`, wenn die Zelle zu einem Brett mit Radius [radius] gehört (Design §2.1). */
    public fun isWithinRadius(radius: Int): Boolean = ringIndex <= radius
}
