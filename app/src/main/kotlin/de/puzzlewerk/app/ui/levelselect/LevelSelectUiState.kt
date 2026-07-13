package de.puzzlewerk.app.ui.levelselect

import androidx.compose.runtime.Immutable
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.game.level.Difficulty

/**
 * Render-fertiger Zustand der Levelauswahl (§12.4). Enthält für alle 50
 * Kampagnenlevel je eine [LevelTile] sowie die Kopf-Summen. Die
 * Freischalt- und Tier-Ableitung (§11.2/§11.3) macht das [LevelSelectViewModel]
 * über :game — nie das Composable (docs/ui-architektur.md §2).
 *
 * `@Immutable`: alle Felder sind unveränderlich; die Zusage stabilisiert die
 * Recomposition (Regel 5, keine unnötigen Neuberechnungen der Kacheln).
 */
@Immutable
data class LevelSelectUiState(
    val isLoading: Boolean = true,
    /** Persistenz-Fehler als Wert (R43-Geist): definierte Meldung + Reset statt Crash. */
    val hasLoadError: Boolean = false,
    val tiles: List<LevelTile> = emptyList(),
    /** Summe der Sterne über alle gelösten Level (Kopfzeile §12.4). */
    val totalStars: Int = 0,
    /** Summe der besten Punkte über alle gelösten Level (Kopfzeile §12.4). */
    val totalScore: Int = 0,
)

/** Eine Kachel der Levelauswahl: Levelnummer, Tier-Anzeige und Zustand. */
@Immutable
data class LevelTile(
    val levelNumber: Int,
    /** Schwierigkeit des Levels (§11.3, `campaignTier`) — nur zur Anzeige. */
    val tier: Difficulty,
    val state: TileState,
)

/**
 * Zustand einer Kachel (§12.4). Nur [Open] und [Solved] sind tappbar;
 * [Locked] ist gesperrt (§11.2). Der Zustand wird visuell NIE nur über
 * Farbe kodiert (§13): Symbol/Text kommen hinzu (siehe Screen).
 */
sealed interface TileState {
    /** Gesperrt (§11.2): nicht spielbar. */
    data object Locked : TileState

    /** Freigeschaltet, aber noch ungelöst. */
    data object Open : TileState

    /** Gelöst: bestes Ergebnis (§7.2). */
    data class Solved(
        val stars: Int,
        val points: Int,
    ) : TileState
}

/** Nutzerabsichten; einziger Eingang ist [LevelSelectViewModel.onIntent]. */
sealed interface LevelSelectIntent {
    /** Tap auf eine Kachel; gesperrte Kacheln lösen dies nicht aus. */
    data class TileClicked(
        val levelNumber: Int,
    ) : LevelSelectIntent

    /** „Fortschritt zurücksetzen" im Fehlerzustand (§12.4/§12.5). */
    data object ResetProgress : LevelSelectIntent
}

/** Einmal-Ereignisse; der Wurzel-Composable übersetzt sie in Navigation (ADR-008). */
sealed interface LevelSelectEffect {
    /** Spiel-Screen mit [request] öffnen. */
    data class NavigateToGame(
        val request: LevelRequest,
    ) : LevelSelectEffect
}
