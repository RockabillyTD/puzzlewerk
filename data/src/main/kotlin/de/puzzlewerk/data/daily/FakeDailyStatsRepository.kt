package de.puzzlewerk.data.daily

import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.data.store.StoreState
import de.puzzlewerk.data.store.toDataResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.updateAndGet

/**
 * In-Memory-[DailyStatsRepository] für :app-Tests und Compose-Previews
 * (Ticket PW-3.2). Nutzt dieselbe §10.3-Serienlogik wie die
 * DataStore-Implementierung; Schreibversuche auf einen per [failWith]
 * gesetzten Fehlerbestand schlagen als Wert fehl, [reset] ist der Ausweg.
 */
class FakeDailyStatsRepository : DailyStatsRepository {
    private val state = MutableStateFlow<StoreState<DailyStatsState>>(StoreState.Loaded(DailyStatsState.EMPTY))

    override val stats: Flow<DataResult<DailyStats>> =
        state.asStateFlow().map { current -> current.mapLoaded { it.toStats() }.toDataResult() }

    /** Simuliert einen Lesefehler (Korruption/Version/E-A) für Fehlerzustands-UI. */
    fun failWith(failure: PersistenceFailure) {
        state.value = StoreState.Failed(failure)
    }

    override suspend fun recordPlayed(epochDay: Long): WriteResult = write { it.withPlayed(epochDay) }

    override suspend fun recordSolved(
        epochDay: Long,
        record: DailyRecord,
    ): WriteResult {
        requireValidDailyRecord(record)
        return write { it.withSolved(epochDay, record) }
    }

    override suspend fun reset(): WriteResult {
        state.value = StoreState.Loaded(DailyStatsState.EMPTY)
        return WriteResult.Success
    }

    private fun write(transform: (DailyStatsState) -> DailyStatsState): WriteResult =
        when (val after = state.updateAndGet { current -> current.mapLoaded(transform) }) {
            is StoreState.Loaded -> WriteResult.Success
            is StoreState.Failed -> WriteResult.Failure(after.failure)
        }
}

private fun <T, R> StoreState<T>.mapLoaded(transform: (T) -> R): StoreState<R> =
    when (this) {
        is StoreState.Loaded -> StoreState.Loaded(transform(value))
        is StoreState.Failed -> this
    }
