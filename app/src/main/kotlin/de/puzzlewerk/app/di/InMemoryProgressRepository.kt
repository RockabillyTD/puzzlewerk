package de.puzzlewerk.app.di

import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.data.progress.CampaignProgress
import de.puzzlewerk.data.progress.ProgressRepository
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT
import de.puzzlewerk.game.score.Score
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Übergangs-Implementierung des [ProgressRepository]-Vertrags im Speicher:
 * korrekt gegenüber der Bestwert-Semantik (§7.2), aber ohne Persistenz über
 * den Prozess hinaus. Wird durch `DataStoreProgressRepository` (PW-3.2)
 * ersetzt, sobald PW-3.5/PW-3.6 verdrahten.
 */
internal class InMemoryProgressRepository : ProgressRepository {
    private val current = MutableStateFlow(CampaignProgress.EMPTY)

    override val progress: Flow<DataResult<CampaignProgress>> =
        current.map { DataResult.Success(it) }

    override suspend fun recordSolved(
        levelNumber: Int,
        result: Score,
    ): WriteResult {
        require(levelNumber in 1..CAMPAIGN_LEVEL_COUNT) {
            "Levelnummer muss in 1..$CAMPAIGN_LEVEL_COUNT liegen, war $levelNumber"
        }
        current.update { progress ->
            val best = progress.bestByLevel[levelNumber]
            if (best != null && best.points >= result.points) {
                // Nur ein BESSERES Ergebnis überschreibt (§7.2); sonst No-op.
                progress
            } else {
                CampaignProgress(progress.bestByLevel + (levelNumber to result))
            }
        }
        return WriteResult.Success
    }

    override suspend fun reset(): WriteResult {
        current.value = CampaignProgress.EMPTY
        return WriteResult.Success
    }
}
