package de.puzzlewerk.data.progress

import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.game.score.Score
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * In-Memory-[ProgressRepository] für :app-Tests und Compose-Previews
 * (Ticket PW-3.2). Verhält sich semantisch wie die DataStore-Implementierung:
 * Schreibversuche auf einen per [failWith] gesetzten Fehlerbestand schlagen
 * als Wert fehl; [reset] ist der definierte Ausweg.
 */
class FakeProgressRepository(
    initial: CampaignProgress = CampaignProgress.EMPTY,
) : ProgressRepository {
    private val state = MutableStateFlow<DataResult<CampaignProgress>>(DataResult.Success(initial))

    override val progress: Flow<DataResult<CampaignProgress>> = state.asStateFlow()

    /** Simuliert einen Lesefehler (Korruption/Version/E-A) für Fehlerzustands-UI. */
    fun failWith(failure: PersistenceFailure) {
        state.value = DataResult.Failure(failure)
    }

    override suspend fun recordSolved(
        levelNumber: Int,
        result: Score,
    ): WriteResult {
        requireValidCampaignResult(levelNumber, result)
        val after =
            state.updateAndGet { current ->
                when (current) {
                    is DataResult.Success -> DataResult.Success(current.value.withSolved(levelNumber, result))
                    is DataResult.Failure -> current
                }
            }
        return when (after) {
            is DataResult.Success -> WriteResult.Success
            is DataResult.Failure -> WriteResult.Failure(after.failure)
        }
    }

    override suspend fun reset(): WriteResult {
        state.value = DataResult.Success(CampaignProgress.EMPTY)
        return WriteResult.Success
    }
}
