package de.puzzlewerk.app.ui.levelselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.progress.CampaignProgress
import de.puzzlewerk.data.progress.ProgressRepository
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT
import de.puzzlewerk.game.level.campaignTier
import de.puzzlewerk.game.level.isLevelUnlocked
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ViewModel der Levelauswahl (§12.4): beobachtet den Kampagnenfortschritt und
 * leitet daraus 50 Kacheln plus Kopf-Summen ab. Keine Spiellogik — die
 * Freischaltregel §11.2 (`isLevelUnlocked`) und die Level→Tier-Abbildung §11.3
 * (`campaignTier`) leben in :game; hier wird nur abgebildet.
 */
class LevelSelectViewModel(
    private val progressRepository: ProgressRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LevelSelectUiState())

    /** Einzige Wahrheit des Screens (MVI, docs/ui-architektur.md §2). */
    val state: StateFlow<LevelSelectUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<LevelSelectEffect>(Channel.BUFFERED)

    /** Einmal-Ereignisse für den Wurzel-Composable (Navigation). */
    val effects: Flow<LevelSelectEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            progressRepository.progress.collect { result ->
                mutableState.value =
                    when (result) {
                        is DataResult.Success -> successState(result.value)
                        is DataResult.Failure ->
                            LevelSelectUiState(isLoading = false, hasLoadError = true)
                    }
            }
        }
    }

    /** Einziger Eingang für Nutzerabsichten (MVI). */
    fun onIntent(intent: LevelSelectIntent) {
        when (intent) {
            is LevelSelectIntent.TileClicked -> onTileClicked(intent.levelNumber)
            LevelSelectIntent.ResetProgress -> viewModelScope.launch { progressRepository.reset() }
        }
    }

    private fun onTileClicked(levelNumber: Int) {
        val tile = mutableState.value.tiles.firstOrNull { it.levelNumber == levelNumber } ?: return
        // Gesperrte Kacheln sind in der UI nicht klickbar; ein dennoch (z. B.
        // gepuffert) eintreffender Intent bleibt bewusst wirkungslos.
        if (tile.state is TileState.Locked) return
        effectChannel.trySend(LevelSelectEffect.NavigateToGame(LevelRequest.Campaign(levelNumber)))
    }

    private fun successState(progress: CampaignProgress): LevelSelectUiState {
        val highest = progress.highestSolvedLevel
        val tiles =
            (1..CAMPAIGN_LEVEL_COUNT).map { n ->
                val best = progress.bestByLevel[n]
                val tileState =
                    when {
                        best != null -> TileState.Solved(stars = best.stars, points = best.points)
                        isLevelUnlocked(n, highest) -> TileState.Open
                        else -> TileState.Locked
                    }
                LevelTile(levelNumber = n, tier = campaignTier(n), state = tileState)
            }
        return LevelSelectUiState(
            isLoading = false,
            tiles = tiles,
            totalStars = progress.bestByLevel.values.sumOf { it.stars },
            totalScore = progress.bestByLevel.values.sumOf { it.points },
        )
    }
}
