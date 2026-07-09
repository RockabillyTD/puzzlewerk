package de.puzzlewerk.game.color

/**
 * Füllzustand eines Kristalls nach einer trace-Auswertung (Design §4.7).
 * Für die UI: Zustand NIE nur über Farbe kommunizieren (Design §13.3).
 */
public enum class CrystalFill {
    /** `empfangen = 0`: kein Strahl trifft den Kristall. */
    DARK,

    /** `0 < empfangen ⊂ soll` (echte Bit-Teilmenge): es fehlen Komponenten. */
    PARTIAL,

    /** `empfangen = soll`: exakt erfüllt — nur dieser Zustand zählt zur Lösung (§5.4). */
    FULFILLED,

    /** `empfangen ∧ ¬soll ≠ 0`: Fremdkomponente empfangen — NICHT erfüllt (§4.7, R23). */
    OVERSATURATED,
}

/**
 * Klassifiziert das an einem Kristall [received] Licht gegen die Sollfarbe [required]
 * (Design §4.7). `received = null` bedeutet „kein Strahl empfangen" — das entspricht
 * dem fehlenden Eintrag in `TraceResult.received` (§5.4: fehlender Eintrag zählt als 0).
 */
public fun crystalFill(
    required: LightColor,
    received: LightColor?,
): CrystalFill =
    when {
        received == null -> CrystalFill.DARK
        received.bits and required.bits.inv() != 0 -> CrystalFill.OVERSATURATED
        received == required -> CrystalFill.FULFILLED
        else -> CrystalFill.PARTIAL
    }
