package de.puzzlewerk.data.daily

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.data.store.EnvelopeSerializer
import de.puzzlewerk.data.store.StoreState
import de.puzzlewerk.data.store.dataResults
import de.puzzlewerk.data.store.overwriteWith
import de.puzzlewerk.data.store.updateLoaded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * [DailyStatsRepository] auf Basis eines typisierten DataStores mit
 * JSON-Envelope-Schema v1 (ADR-007). Serien-Semantik §10.3 liegt in
 * [DailyStatsState]; Korruption/Versionskonflikt erscheinen als
 * [DataResult.Failure] im Flow, Schreibversuche darauf schlagen als Wert
 * fehl — nur [reset] überschreibt einen solchen Bestand explizit.
 *
 * @param scope Coroutine-Scope des DataStores (Lebensdauer der App bzw. des
 *   Tests); Dispatcher-Wahl liegt beim Aufrufer (Composition Root, ADR-006).
 * @param produceFile liefert die Store-Datei im App-Sandbox-Verzeichnis (S2).
 */
class DataStoreDailyStatsRepository(
    scope: CoroutineScope,
    produceFile: () -> File,
) : DailyStatsRepository {
    private val store: DataStore<StoreState<DailyStatsState>> =
        DataStoreFactory.create(
            serializer = EnvelopeSerializer(DailySchemaV1, StoreState.Loaded(DailyStatsState.EMPTY)),
            scope = scope,
            produceFile = produceFile,
        )

    override val stats: Flow<DataResult<DailyStats>> =
        store.dataResults().map { result ->
            when (result) {
                is DataResult.Success -> DataResult.Success(result.value.toStats())
                is DataResult.Failure -> result
            }
        }

    override suspend fun recordPlayed(epochDay: Long): WriteResult =
        store.updateLoaded { current ->
            current.withPlayed(
                epochDay,
            )
        }

    override suspend fun recordSolved(
        epochDay: Long,
        record: DailyRecord,
    ): WriteResult {
        requireValidDailyRecord(record)
        return store.updateLoaded { current -> current.withSolved(epochDay, record) }
    }

    override suspend fun reset(): WriteResult = store.overwriteWith(DailyStatsState.EMPTY)
}
