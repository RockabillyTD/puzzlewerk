package de.puzzlewerk.game.level

import de.puzzlewerk.game.board.Board

/**
 * Vollständige, unveränderliche Levelbeschreibung (Design §16.1).
 *
 * Designentscheidung (PW-2.1): Es gibt KEIN separates `id`-Feld — die Identität
 * eines Levels ist bereits durch ([seed], [tier]) gegeben; Kampagnenlevel werden
 * in :data über ihre Levelnummer auf eine Definition abgebildet (§11.1), das
 * Tägliche Prisma ist über sein Datum identifiziert (§10.1). Ein freies id-Feld
 * würde die byte-identische Reproduzierbarkeit (I2) nur um eine redundante,
 * fehleranfällige Angabe erweitern.
 *
 * Wertebereiche (§16.2) prüft der [LevelValidator] an der Vertrauensgrenze —
 * Konstruktion erzwingt sie bewusst nicht (S4: korrupte Daten ⇒ Fehler als Wert,
 * nie Crash, R43).
 *
 * @property board Der verwürfelte STARTZUSTAND des Levels (§9.3 Schritt 7)
 *   inklusive der Start-Orientierungen aller drehbaren Elemente.
 * @property par Exakte minimale Zuganzahl, [MIN_PAR]..[MAX_PAR] (§7.1, I4).
 * @property tier Schwierigkeitsstufe, aus der das Level generiert wurde (§9.2).
 * @property seed 64-Bit-Seed der Generierung — `generateLevel(seed, tier)`
 *   reproduziert diese Definition byte-identisch (§9.1, I2).
 */
public data class LevelDefinition(
    val board: Board,
    val par: Int,
    val tier: Difficulty,
    val seed: Long,
) {
    public companion object {
        /** Kleinster gültiger Par-Wert: kein 0-Züge-Level (§7.1, §9.5/1, R35). */
        public const val MIN_PAR: Int = 1

        /** Größter gültiger Par-Wert (§7.1, Tier D7). */
        public const val MAX_PAR: Int = 14

        /** Mindestanzahl Quellen je Level (§16.2/3). */
        public const val MIN_SOURCES: Int = 1

        /** Höchstanzahl Quellen je Level (§4.1, §9.2, §16.2/3). */
        public const val MAX_SOURCES: Int = 3

        /** Mindestanzahl Kristalle je Level (§16.2/3). */
        public const val MIN_CRYSTALS: Int = 1

        /** Höchstanzahl Kristalle je Level (§4.7, §9.2, §16.2/3). */
        public const val MAX_CRYSTALS: Int = 6

        /** Höchstanzahl drehbarer Elemente je Level (§9.2, §16.2/3). */
        public const val MAX_ROTATABLES: Int = 8

        /** Höchstanzahl Portalpaare je Level (§4.6, §9.2); gültige Paar-IDs: 0..1. */
        public const val MAX_PORTAL_PAIRS: Int = 2
    }
}
