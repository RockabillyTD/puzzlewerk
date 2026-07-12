package de.puzzlewerk.data.daily

import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.game.score.Score
import kotlinx.coroutines.flow.Flow

/**
 * Gewertetes Ergebnis EINES Tagesrätsels (Design §10.3, „ergebnisJeDatum").
 *
 * @property moves Zugzahl der Erstlösung (≥ 1; 0 nur im Datenfehler-Fall R31).
 * @property par Par des Tagesrätsels (1..14, §7.1).
 * @property score Punkte und Sterne der Erstlösung (§7.2).
 */
data class DailyRecord(
    val moves: Int,
    val par: Int,
    val score: Score,
)

/**
 * Serienstatistik des Täglichen Prismas (Design §10.3). Tage sind als
 * `epochDay` (Tage seit 1970-01-01 im LOKALEN Gerätekalender) kodiert —
 * dieselbe Konvention wie `dailySeed` in :game (§10.1, R37: negative Werte
 * sind gültig).
 *
 * @property playedTotal Anzahl Kalendertage, deren Rätsel gestartet wurde.
 * @property solvedTotal Anzahl Kalendertage mit gelöstem Rätsel.
 * @property currentStreak Aufeinanderfolgende gelöste Tage, endend am zuletzt
 *   gelösten Tag (§10.3).
 * @property longestStreak Positiver Allzeit-Rekord; fällt nie (§10.3).
 * @property resultByEpochDay Tag → Ergebnis der ERSTLÖSUNG (nur gelöste Tage,
 *   R38: kein Doppelzählen).
 */
data class DailyStats(
    val playedTotal: Int,
    val solvedTotal: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val resultByEpochDay: Map<Long, DailyRecord>,
) {
    companion object {
        /** Statistik vor dem ersten Daily: alles 0, keine Ergebnisse. */
        val EMPTY: DailyStats =
            DailyStats(
                playedTotal = 0,
                solvedTotal = 0,
                currentStreak = 0,
                longestStreak = 0,
                resultByEpochDay = emptyMap(),
            )
    }
}

/**
 * Speichert die Daily-Serienstatistik dauerhaft (Design §10.3;
 * Persistenz-Semantik: ADR-007). Alle Fehler sind Werte (Regel C3).
 *
 * Serien-Regeln (§10.3, bindend für die Implementierung):
 * - [recordSolved] am Tag `lastSolved + 1` ⇒ `currentStreak + 1`; an einem
 *   späteren Tag (Lücke) ⇒ `currentStreak = 1`; an einem FRÜHEREN Tag
 *   (Zeitzonen-/Uhrreise, R38) ⇒ Serie unverändert, Ergebnis zählt trotzdem.
 * - `longestStreak = max(longestStreak, currentStreak)`; keine
 *   Streak-Bestrafung, keine Warnungen (§10.3).
 *
 * Implementierung: Ticket PW-3.2 (DataStore) + In-Memory-Fake für Tests.
 */
interface DailyStatsRepository {
    /**
     * Beobachtbare Statistik; emittiert sofort und bei jeder Änderung.
     * Erststart ist `Success(DailyStats.EMPTY)`; Korruption/Versionskonflikt
     * kommen als [DataResult.Failure] (definierter Fehler, nie Crash).
     */
    val stats: Flow<DataResult<DailyStats>>

    /**
     * Zählt den ERSTEN Start des Rätsels vom Tag [epochDay] für
     * `playedTotal`. Idempotent pro Datum: erneutes Öffnen desselben Datums
     * (auch nach Zeitzonenwechsel, R38) verändert nichts.
     */
    suspend fun recordPlayed(epochDay: Long): WriteResult

    /**
     * Wertet die ERSTLÖSUNG des Rätsels vom Tag [epochDay]: legt [record]
     * unter dem Datum ab, erhöht `solvedTotal` und aktualisiert die
     * Serienfelder gemäß §10.3 (siehe Klassen-KDoc). Existiert für
     * [epochDay] bereits ein Ergebnis, ist der Aufruf ein No-op mit
     * [WriteResult.Success] (R38: nur die Erstlösung zählt). Eine laufende
     * Partie wird für IHR Startdatum gewertet (R39) — [epochDay] ist das
     * Datum des Rätsels, nicht das aktuelle.
     */
    suspend fun recordSolved(
        epochDay: Long,
        record: DailyRecord,
    ): WriteResult

    /**
     * Setzt die GESAMTE Daily-Statistik auf [DailyStats.EMPTY] zurück
     * (§12.5). Kampagnenfortschritt und Einstellungen bleiben unberührt.
     * Auch als Ausweg aus einem [DataResult.Failure]-Zustand definiert.
     */
    suspend fun reset(): WriteResult
}
