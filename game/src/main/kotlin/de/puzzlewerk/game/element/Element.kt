package de.puzzlewerk.game.element

import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor

/**
 * Inhalt einer Brettzelle (Design §4, §16.1). Jede Zelle enthält höchstens ein
 * Element oder ist leer (§2.3); der Brettrand verhält sich wie eine [Wall].
 *
 * Drehbarkeit ist eine Eigenschaft des Elementtyps, kein per-Instanz-Flag (§4):
 * In V1 sind ausschließlich [Mirror] und [Splitter] drehbar (Subtyp [Rotatable]).
 */
public sealed interface Element {
    /** `true`, wenn das Element per Rotate-Zug drehbar ist (Design §4, §6.1). */
    public val isRotatable: Boolean get() = this is Rotatable

    /**
     * Drehbare Elemente: tragen eine Orientierung `m ∈ 0..5` in 30°-Schritten
     * (Design §2.2, §4.2/§4.3).
     */
    public sealed interface Rotatable : Element {
        /** Aktuelle Orientierungsstufe `m`. */
        public val orientation: Orientation

        /** Kopie mit [orientation] — Basis für Zuganwendung (§6) und Generator-Scramble (§9.3/7). */
        public fun withOrientation(orientation: Orientation): Rotatable
    }

    /**
     * Lichtquelle (Design §4.1): emittiert genau einen Strahl der Farbe [color]
     * in Richtung [direction]. Ein Strahl, der eine Quellzelle TRIFFT, wird
     * absorbiert (R01). Nicht drehbar.
     *
     * @property color Emissionsfarbe ∈ 1..7.
     * @property direction Emissionsrichtung.
     */
    public data class Source(
        val color: LightColor,
        val direction: Direction,
    ) : Element

    /**
     * Beidseitiger Spiegel (Design §4.2), drehbar. Reflexionsregel:
     * `d_out = (m − d_in) mod 6`. Parallel- und Senkrecht-Sonderfälle liefert
     * die Formel von selbst (R02/R03) — kein Sondercode.
     */
    public data class Mirror(
        override val orientation: Orientation,
    ) : Rotatable {
        override fun withOrientation(orientation: Orientation): Mirror = copy(orientation = orientation)
    }

    /**
     * Strahlteiler (Design §4.3), drehbar. Transmission geradeaus PLUS Reflexionskopie
     * `d_out = (m − d_in) mod 6` — außer im Parallelfall `d_out = d_in`
     * (dann nur der gerade Strahl, R05). Keine Energie/Abschwächung (§4.3).
     */
    public data class Splitter(
        override val orientation: Orientation,
    ) : Rotatable {
        override fun withOrientation(orientation: Orientation): Splitter = copy(orientation = orientation)
    }

    /**
     * Prisma (Design §4.4): zerlegt jeden Strahl in seine Primärkomponenten —
     * Rot nach `(d_in+1) mod 6`, Grün geradeaus, Blau nach `(d_in+5) mod 6`.
     * Relativ zur Laufrichtung definiert, daher rotationsinvariant und nicht drehbar.
     */
    public data object Prism : Element

    /**
     * Farbfilter (Design §4.5): Strahl läuft geradeaus weiter mit `c ∧ f`;
     * Ergebnis 0 ⇒ absorbiert (R11). Richtungsunabhängig, nicht drehbar.
     *
     * @property color Filterfarbe — in V1 nur Primärfarben zulässig
     *   (Validierungsregel §16.2/4; der Typ erzwingt nur 1..7).
     */
    public data class Filter(
        val color: LightColor,
    ) : Element

    /**
     * Portal (Design §4.6): teleportiert Strahlen zum Zwilling mit gleicher
     * Richtung und Farbe; symmetrisch, richtungsunabhängig, nicht drehbar.
     *
     * @property pairId Paar-ID ∈ {0, 1}; jede verwendete ID muss auf genau zwei
     *   Zellen liegen (Validierungsregel §16.2/3 — ein Portal ohne Zwilling ist
     *   ein Ladefehler, R43).
     */
    public data class Portal(
        val pairId: Int,
    ) : Element

    /**
     * Kristall (Design §4.7): das Ziel. Sammelt eintreffende Strahlfarben per OR
     * und absorbiert jeden Strahl. Erfüllt gdw. `empfangen == soll` exakt (§5.4).
     *
     * @property required Sollfarbe ∈ 1..7.
     */
    public data class Crystal(
        val required: LightColor,
    ) : Element

    /** Wand (Design §4.8): absorbiert jeden Strahl; dient als Hindernis/Deko. */
    public data object Wall : Element
}
