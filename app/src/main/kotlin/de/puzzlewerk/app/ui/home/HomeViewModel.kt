package de.puzzlewerk.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.progress.CampaignProgress
import de.puzzlewerk.data.progress.ProgressRepository
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ViewModel des Home-Screens (§12.2): beobachtet den Kampagnenfortschritt
 * und leitet daraus das „Weiter"-Ziel ab. Keine Spiellogik — nur
 * Datenableitung; die Freischaltregel §11.2 lebt in :game (PW-3.2).
 */
class HomeViewModel(
    progressRepository: ProgressRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeUiState())

    /** Einzige Wahrheit des Screens (MVI, docs/ui-architektur.md §2). */
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<HomeEffect>(Channel.BUFFERED)

    /** Einmal-Ereignisse für den Wurzel-Composable (Navigation). */
    val effects: Flow<HomeEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            progressRepository.progress.collect { result ->
                mutableState.value =
                    when (result) {
                        is DataResult.Success ->
                            HomeUiState(
                                isLoading = false,
                                continueTarget = continueTargetFor(result.value),
                            )
                        is DataResult.Failure ->
                            HomeUiState(
                                isLoading = false,
                                hasLoadError = true,
                            )
                    }
            }
        }
    }

    /** Einziger Eingang für Nutzerabsichten (MVI). */
    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ContinueClicked -> onContinueClicked()
            HomeIntent.LevelSelectClicked -> effectChannel.trySend(HomeEffect.NavigateToLevelSelect)
        }
    }

    private fun onContinueClicked() {
        val current = mutableState.value
        if (current.isLoading || current.hasLoadError) {
            // Button ist in diesen Zuständen deaktiviert; Intents, die dennoch
            // eintreffen (z. B. gepuffert), sind bewusst wirkungslos.
            return
        }
        val effect =
            when (val target = current.continueTarget) {
                is ContinueTarget.Level -> HomeEffect.NavigateToGame(LevelRequest.Campaign(target.levelNumber))
                ContinueTarget.AllSolved -> HomeEffect.NavigateToLevelSelect
            }
        effectChannel.trySend(effect)
    }

    private fun continueTargetFor(progress: CampaignProgress): ContinueTarget {
        val next = (1..CAMPAIGN_LEVEL_COUNT).firstOrNull { it !in progress.bestByLevel }
        return if (next == null) ContinueTarget.AllSolved else ContinueTarget.Level(next)
    }
}
