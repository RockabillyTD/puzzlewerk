package de.puzzlewerk.app.ui.home

import de.puzzlewerk.app.ui.navigation.LevelRequest

/**
 * Render-fertiger Zustand des Home-Screens (§12.2). Die drei Design-Zustände
 * bilden sich ab als: Erststart = [ContinueTarget.Level] mit Level 1,
 * normal = niedrigstes ungelöstes Level, alles gelöst = [ContinueTarget.AllSolved].
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val continueTarget: ContinueTarget = ContinueTarget.Level(1),
    /** Persistenz-Fehler als Wert (R43-Geist): definierte Meldung statt Crash. */
    val hasLoadError: Boolean = false,
)

/** Ziel des „Weiter"-Buttons (§12.2). */
sealed interface ContinueTarget {
    /** Startet das niedrigste ungelöste Kampagnenlevel. */
    data class Level(
        val levelNumber: Int,
    ) : ContinueTarget

    /** Alles gelöst — „Weiter" führt zur Levelauswahl (§12.2). */
    data object AllSolved : ContinueTarget
}

/** Nutzerabsichten; einziger Eingang ist [HomeViewModel.onIntent]. */
sealed interface HomeIntent {
    /** Tap auf „Weiter". */
    data object ContinueClicked : HomeIntent

    /** Tap auf „Levelauswahl". */
    data object LevelSelectClicked : HomeIntent
}

/** Einmal-Ereignisse; der Wurzel-Composable übersetzt sie in Navigation (ADR-008). */
sealed interface HomeEffect {
    /** Spiel-Screen mit [request] öffnen. */
    data class NavigateToGame(
        val request: LevelRequest,
    ) : HomeEffect

    /** Levelauswahl öffnen. */
    data object NavigateToLevelSelect : HomeEffect
}
