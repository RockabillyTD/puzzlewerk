package de.puzzlewerk.data.progress

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.data.store.EnvelopeSerializer
import de.puzzlewerk.data.store.SCORE_POINTS_RANGE
import de.puzzlewerk.data.store.StoreState
import de.puzzlewerk.data.store.dataResults
import de.puzzlewerk.data.store.overwriteWith
import de.puzzlewerk.data.store.updateLoaded
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT
import de.puzzlewerk.game.score.Score
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * [ProgressRepository] auf Basis eines typisierten DataStores mit
 * JSON-Envelope-Schema v1 (ADR-007). Korruption/Versionskonflikt erscheinen
 * als [DataResult.Failure] im Flow; Schreibversuche auf einen solchen Bestand
 * schlagen als Wert fehl — nur [reset] überschreibt ihn explizit.
 *
 * Flow-Semantik (Review PW-3.2): Nach einem `PersistenceFailure.Io`-LESEfehler
 * beendet der [progress]-Flow — der Collector muss neu kollektieren.
 * Korruption/Versionskonflikt beenden den Flow nicht.
 *
 * @param scope Coroutine-Scope des DataStores (Lebensdauer der App bzw. des
 *   Tests); Dispatcher-Wahl liegt beim Aufrufer (Composition Root, ADR-006).
 * @param produceFile liefert die Store-Datei im App-Sandbox-Verzeichnis (S2).
 */
class DataStoreProgressRepository(
    scope: CoroutineScope,
    produceFile: () -> File,
) : ProgressRepository {
    private val store: DataStore<StoreState<CampaignProgress>> =
        DataStoreFactory.create(
            serializer = EnvelopeSerializer(ProgressSchemaV1, StoreState.Loaded(CampaignProgress.EMPTY)),
            scope = scope,
            produceFile = produceFile,
        )

    override val progress: Flow<DataResult<CampaignProgress>> = store.dataResults()

    override suspend fun recordSolved(
        levelNumber: Int,
        result: Score,
    ): WriteResult {
        requireValidCampaignResult(levelNumber, result)
        return store.updateLoaded { current -> current.withSolved(levelNumber, result) }
    }

    override suspend fun reset(): WriteResult = store.overwriteWith(CampaignProgress.EMPTY)
}

/**
 * §7.2: Überschreibt den Bestwert nur bei höherer Punktzahl (Punkte und
 * Sterne sind beide monoton in der Zugzahl); sonst No-op.
 */
internal fun CampaignProgress.withSolved(
    levelNumber: Int,
    result: Score,
): CampaignProgress {
    val best = bestByLevel[levelNumber]
    return if (best != null && best.points >= result.points) {
        this
    } else {
        CampaignProgress(bestByLevel + (levelNumber to result))
    }
}

/**
 * Vorbedingungen von `recordSolved` (Regel C3, Programmierfehler): Level in
 * 1..50; Punkte im §7.2-Bereich, damit nie ein Bestand entsteht, den der
 * Lademapper als Korruption abweisen müsste.
 */
internal fun requireValidCampaignResult(
    levelNumber: Int,
    result: Score,
) {
    require(levelNumber in 1..CAMPAIGN_LEVEL_COUNT) {
        "Kampagnenlevel muss in 1..$CAMPAIGN_LEVEL_COUNT liegen, war $levelNumber"
    }
    require(result.points in SCORE_POINTS_RANGE) {
        "Punkte müssen in $SCORE_POINTS_RANGE liegen (§7.2), waren ${result.points}"
    }
}
