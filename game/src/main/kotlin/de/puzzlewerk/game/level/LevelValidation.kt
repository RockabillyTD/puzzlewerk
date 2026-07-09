package de.puzzlewerk.game.level

import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor

/**
 * Prüft eine [LevelDefinition] gegen ALLE Schema-Regeln aus Design §16.2
 * (Vertrauensgrenze, S4). Wird von :data beim Laden eingecheckter oder
 * gespeicherter Level aufgerufen; Verstöße sind Werte, nie Exceptions (R43).
 *
 * Nicht prüfbar und nicht nötig: Regel §16.2/2 (höchstens ein Element je Zelle)
 * ist durch `Map` strukturell erzwungen — das SERIALISIERUNGSFORMAT in :data
 * muss Duplikat-Schlüssel ablehnen. Farb-/Richtungs-/Orientierungsbereiche
 * (§16.2/4) erzwingen bereits die Typen; :data mappt rohe Zahlen über die
 * `of(...)`-Fabriken und meldet `null` als Schema-Fehler.
 *
 * Implementierung: Ticket PW-2.6.
 */
public fun interface LevelValidator {
    /**
     * Liefert [LevelValidationResult.Valid] gdw. alle Regeln erfüllt sind,
     * sonst [LevelValidationResult.Invalid] mit JEDEM gefundenen Verstoß
     * (vollständige Diagnose statt First-Fail).
     */
    public fun validate(level: LevelDefinition): LevelValidationResult
}

/** Ergebnis der Level-Validierung (Design §16.2; Fehler als Werte, Regel C3). */
public sealed interface LevelValidationResult {
    /** Alle Schema-Regeln erfüllt; [level] ist unverändert durchgereicht. */
    public data class Valid(
        val level: LevelDefinition,
    ) : LevelValidationResult

    /** Mindestens ein Verstoß; [violations] ist nie leer. */
    public data class Invalid(
        val violations: List<LevelViolation>,
    ) : LevelValidationResult
}

/** Einzelner Schema-Verstoß beim Laden eines Levels (Design §16.2, R43). */
public sealed interface LevelViolation {
    /** Regel 1: Brettradius außerhalb 2..5. */
    public data class RadiusOutOfRange(
        val radius: Int,
    ) : LevelViolation

    /** Regel 1: Zelle liegt außerhalb des Sechsecks `max(|q|,|r|,|q+r|) ≤ radius`. */
    public data class CoordOutsideBoard(
        val coord: HexCoord,
    ) : LevelViolation

    /** Regel 3: Quellenanzahl außerhalb 1..3. */
    public data class SourceCountOutOfRange(
        val count: Int,
    ) : LevelViolation

    /** Regel 3: Kristallanzahl außerhalb 1..6. */
    public data class CrystalCountOutOfRange(
        val count: Int,
    ) : LevelViolation

    /** Regel 3: mehr als 8 drehbare Elemente. */
    public data class TooManyRotatables(
        val count: Int,
    ) : LevelViolation

    /** Regel 3: Portal-Paar-ID außerhalb {0, 1}. */
    public data class PortalPairIdOutOfRange(
        val coord: HexCoord,
        val pairId: Int,
    ) : LevelViolation

    /** Regel 3: verwendete Paar-ID kommt nicht genau zweimal vor (Portal ohne Zwilling). */
    public data class PortalNotPaired(
        val pairId: Int,
        val count: Int,
    ) : LevelViolation

    /** Regel 4: Filterfarbe ist keine Primärfarbe (zulässig nur Rot, Grün, Blau). */
    public data class FilterNotPrimary(
        val coord: HexCoord,
        val color: LightColor,
    ) : LevelViolation

    /** Regel 4: Par außerhalb 1..14. */
    public data class ParOutOfRange(
        val par: Int,
    ) : LevelViolation
}
