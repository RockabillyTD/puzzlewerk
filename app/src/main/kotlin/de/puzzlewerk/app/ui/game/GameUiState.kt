package de.puzzlewerk.app.ui.game

import androidx.compose.runtime.Immutable
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.game.board.HexCoord

// Das Spiel-MVI ist modul-intern (`internal`): [GameUiState] hält ein
// [BoardUiState] (PW-3.4, `internal`) — eine public API-Fläche würde einen
// internen Typ exponieren. Konsumenten (GameScreen, ViewModel-Factory) liegen
// alle in :app, sodass `internal` genügt und die Render-Wahrheit gekapselt bleibt.

/**
 * Render-fertiger Zustand des Spiel-Screens (§12.3). Einzige Wahrheit des
 * Screens (MVI, docs/ui-architektur.md §2); alle Ableitungen aus dem
 * `:game`-Domänenzustand trifft das [GameViewModel], nie ein Composable.
 *
 * @property isLoading Level wird generiert (§9.4, off-main) — Brett noch nicht da.
 * @property board Render-Zustand des Bretts oder `null`, solange geladen wird.
 * @property moves Zugzähler (Rohwert; die Kopfzeile formatiert via strings.xml).
 * @property par Minimale Zuganzahl des Levels (§7.1); `0`, solange geladen wird.
 * @property canUndo Undo möglich — es gibt Züge im Verlauf und die Partie läuft
 *   noch (§6.2/R32: Gelöst sperrt Undo).
 * @property pendingResetConfirm Reset-Bestätigung offen (§12.3: ab ≥ 5 Zügen).
 * @property result Ergebnis-Overlay-Daten, gesetzt genau dann, wenn gelöst (§7.2).
 * @property rotatedCell Zelle der letzten GÜLTIGEN Drehung — semantisches
 *   Signal für den Dreh-Blitz (§13.9, PW-4.6): `null` bei Undo/Reset/Partie-
 *   Start, die lösen keinen Blitz aus (Korrekturrunde MINOR-2).
 */
@Immutable
internal data class GameUiState(
    val isLoading: Boolean = true,
    val board: BoardUiState? = null,
    val moves: Int = 0,
    val par: Int = 0,
    val canUndo: Boolean = false,
    val pendingResetConfirm: Boolean = false,
    val result: GameResult? = null,
    val rotatedCell: HexCoord? = null,
)

/**
 * Vorformatierte Overlay-Daten einer GELÖSTEN Partie (§7.2, §12.3): das
 * ViewModel entpackt [de.puzzlewerk.game.score.Score] in Rohwerte, damit der
 * UiState keine `:game`-Domänenobjekte trägt.
 *
 * @property points Punkte 1000..1500 (§7.2).
 * @property stars Sterne 1..3 (§7.2).
 * @property moves Züge der Lösung (für „Züge X · Par Y").
 * @property par Par des Levels.
 */
@Immutable
internal data class GameResult(
    val points: Int,
    val stars: Int,
    val moves: Int,
    val par: Int,
)

/** Nutzerabsichten des Spiel-Screens; einziger Eingang ist [GameViewModel.onIntent]. */
internal sealed interface GameIntent {
    /** Tap auf eine Zelle: Drehversuch des Elements auf [coord] (§6.1). */
    data class TapCell(
        val coord: HexCoord,
    ) : GameIntent

    /** Letzten Drehzug zurücknehmen (§6.2). */
    data object Undo : GameIntent

    /** Reset anstoßen — ab ≥ 5 Zügen erst Bestätigung, sonst direkt (§12.3). */
    data object Reset : GameIntent

    /** Reset-Bestätigung bejaht (§12.3). */
    data object ConfirmReset : GameIntent

    /** Reset-Bestätigung abgebrochen (§12.3). */
    data object DismissReset : GameIntent

    /** „Nochmal spielen" aus dem Ergebnis-Overlay: frische Partie desselben Levels (§12.3, R32). */
    data object Replay : GameIntent
}

/** Einmal-Ereignisse; die Senke (Haptik/Ton/Fehlermeldung) ist Screen-Sache (PW-3.5b). */
internal sealed interface GameEffect {
    /** Wirkungsloser Zug (§6.1/§12.3): kurzes Wackeln + dezenter Ton. */
    data object InvalidMove : GameEffect

    /**
     * Das gelöste Kampagnenergebnis konnte nicht gespeichert werden
     * (Persistenz-Fehler als Wert, R43-Geist) — Screen zeigt eine definierte
     * Meldung, nie ein Crash.
     */
    data class SaveFailed(
        val failure: PersistenceFailure,
    ) : GameEffect
}
