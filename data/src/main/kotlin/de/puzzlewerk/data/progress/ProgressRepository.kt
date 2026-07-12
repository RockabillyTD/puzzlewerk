package de.puzzlewerk.data.progress

import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.game.score.Score
import kotlinx.coroutines.flow.Flow

/**
 * Gesamter Kampagnenfortschritt (Design §11, §12.4): je gelöstem Level das
 * beste Ergebnis. Level ohne Eintrag wurden nie gelöst.
 *
 * @property bestByLevel Levelnummer (1..50) → bestes Ergebnis. Enthält
 *   ausschließlich GELÖSTE Level; Scores liegen in 1000..1500, Sterne in 1..3
 *   (Design §7.2 — der Lademapper weist andere Werte als Korruption ab).
 */
data class CampaignProgress(
    val bestByLevel: Map<Int, Score>,
) {
    /**
     * Höchstes gelöstes Level, `0` ohne Fortschritt — Eingabe der
     * Freischaltregel §11.2 (`isLevelUnlocked` in :game entscheidet).
     */
    val highestSolvedLevel: Int get() = bestByLevel.keys.maxOrNull() ?: 0

    companion object {
        /** Fortschritt eines Erststarts: nichts gelöst. */
        val EMPTY: CampaignProgress = CampaignProgress(emptyMap())
    }
}

/**
 * Speichert den Kampagnenfortschritt dauerhaft (Design §7.2, §11, §12.4;
 * Persistenz-Semantik: ADR-007). Alle Fehler sind Werte (Regel C3).
 *
 * Implementierung: Ticket PW-3.2 (DataStore) + In-Memory-Fake für Tests.
 */
interface ProgressRepository {
    /**
     * Beobachtbarer Fortschritt; emittiert den aktuellen Bestand sofort und
     * danach bei jeder Änderung. Leerer Bestand (Erststart) ist
     * `Success(CampaignProgress.EMPTY)`; Korruption/Versionskonflikt kommen
     * als [DataResult.Failure] — die UI zeigt einen definierten Fehler und
     * bietet [reset] an (R43-Geist, nie Crash).
     */
    val progress: Flow<DataResult<CampaignProgress>>

    /**
     * Speichert das Ergebnis einer GELÖSTEN Kampagnenpartie. Überschreibt den
     * Bestand nur, wenn das neue Ergebnis besser ist (Design §7.2: höhere
     * Punktzahl; Punkte und Sterne sind beide monoton in der Zugzahl und
     * damit konsistent). Wiederholtes Speichern desselben Ergebnisses ist
     * ein No-op mit [WriteResult.Success].
     *
     * Vorbedingung (Programmierfehler, Regel C3): `levelNumber` in 1..50
     * ([de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT]).
     */
    suspend fun recordSolved(
        levelNumber: Int,
        result: Score,
    ): WriteResult

    /**
     * Setzt den GESAMTEN Kampagnenfortschritt auf [CampaignProgress.EMPTY]
     * zurück (§12.5 „Fortschritt zurücksetzen" — die doppelte Bestätigung
     * liegt in der UI). Daily-Statistik und Einstellungen bleiben unberührt.
     * Auch als Ausweg aus einem [PersistenceFailure]-Zustand definiert.
     */
    suspend fun reset(): WriteResult
}
