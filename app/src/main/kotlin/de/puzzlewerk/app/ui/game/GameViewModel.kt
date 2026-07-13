package de.puzzlewerk.app.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.data.progress.ProgressRepository
import de.puzzlewerk.game.daily.dailySeed
import de.puzzlewerk.game.daily.dailyTier
import de.puzzlewerk.game.engine.GameEngine
import de.puzzlewerk.game.engine.GameState
import de.puzzlewerk.game.engine.InvalidMoveReason
import de.puzzlewerk.game.engine.Move
import de.puzzlewerk.game.engine.MoveResult
import de.puzzlewerk.game.generator.LevelGenerator
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.level.campaignSeed
import de.puzzlewerk.game.level.campaignTier
import de.puzzlewerk.game.score.ScoreCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ab dieser Zugzahl verlangt Reset eine Bestätigung (Design §12.3). */
private const val RESET_CONFIRM_THRESHOLD = 5

/**
 * ViewModel des Spiel-Screens (§12.3, Kampagne UND Daily identisch). Übersetzt
 * `:game`-Züge in einen render-fertigen [GameUiState] und Einmal-Ereignisse in
 * [GameEffect] — KEINE Spiellogik hier (CLAUDE.md): Zug-, Trace- und
 * Score-Semantik liegen ausschließlich in `:game`, der Tracer wird nie direkt
 * berührt (ui-architektur §4, `MoveResult.Applied.trace`).
 *
 * Bewusste Phase-3-Eingrenzung (Abweichung von ui-architektur §3): Es wird KEIN
 * `DailyStatsRepository` injiziert — die Daily-Wertung ist Phase 4; Phase 3
 * speichert ausschließlich Kampagnenfortschritt (§10, §12.3).
 *
 * ViewModel-Zuschnitt konstruktor-injiziert (ADR-006), rein JVM-testbar; einziger
 * Android-Typ ist [ViewModel]. Levelgenerierung läuft auf [dispatcher], nie auf
 * Main (ui-architektur §3, §9.4).
 */
internal class GameViewModel(
    private val request: LevelRequest,
    private val engine: GameEngine,
    private val generator: LevelGenerator,
    private val scoreCalculator: ScoreCalculator,
    private val progressRepository: ProgressRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GameUiState())

    /** Einzige Wahrheit des Screens (MVI, docs/ui-architektur.md §2). */
    val state: StateFlow<GameUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<GameEffect>(Channel.BUFFERED)

    /** Einmal-Ereignisse (Haptik/Ton/Fehler); die Senke liegt im Screen (PW-3.5b). */
    val effects: Flow<GameEffect> = effectChannel.receiveAsFlow()

    /** Kanonischer Domänen-Cursor; `null`, solange das Level noch generiert wird. */
    private var gameState: GameState? = null

    init {
        viewModelScope.launch {
            val applied = withContext(dispatcher) { engine.newGame(generateLevel()) }
            onApplied(applied)
        }
    }

    /** Einziger Eingang für Nutzerabsichten (MVI). */
    fun onIntent(intent: GameIntent) {
        when (intent) {
            is GameIntent.TapCell -> applyMove(Move.Rotate(intent.coord))
            GameIntent.Undo -> applyMove(Move.Undo)
            GameIntent.Reset -> onReset()
            GameIntent.ConfirmReset -> onConfirmReset()
            GameIntent.DismissReset -> mutableState.update { it.copy(pendingResetConfirm = false) }
            GameIntent.Replay -> onReplay()
        }
    }

    /**
     * „Nochmal spielen" (§12.3): frische Partie DESSELBEN Levels. Bewusst NICHT
     * `Move.Reset` — ein gelöstes Brett lehnt Reset ab (R32), das Overlay hinge
     * sonst. `engine.newGame` bewertet nur `trace` (kein Neu-Generieren) und
     * setzt Zähler 0, Verlauf leer, Overlay weg.
     */
    private fun onReplay() {
        val level = gameState?.level ?: return // noch im Ladezustand: wirkungslos
        onApplied(engine.newGame(level))
    }

    /** Campaign(n) ⇒ (campaignSeed, campaignTier); Daily(d) ⇒ (dailySeed, dailyTier) (§10.1/§11.1). */
    private fun generateLevel(): LevelDefinition =
        when (val target = request) {
            is LevelRequest.Campaign ->
                generator.generate(
                    campaignSeed(target.levelNumber),
                    campaignTier(target.levelNumber),
                )
            is LevelRequest.Daily -> generator.generate(dailySeed(target.epochDay), dailyTier(target.epochDay))
        }

    private fun applyMove(move: Move) {
        val current = gameState ?: return // noch im Ladezustand: Eingaben wirkungslos
        when (val result = engine.applyMove(current, move)) {
            is MoveResult.Applied -> onApplied(result)
            is MoveResult.Invalid -> onInvalid(result.reason)
        }
    }

    private fun onApplied(applied: MoveResult.Applied) {
        val justSolved = applied.state.solved && gameState?.solved != true
        gameState = applied.state
        mutableState.value = renderState(applied)
        if (justSolved) persistIfCampaign(applied.state)
    }

    private fun onInvalid(reason: InvalidMoveReason) {
        // §6.3/R32: Ein gelöstes Brett lehnt jeden Zug ab — kein Effect-Spam.
        // Zusätzlich einen etwaig offenen Reset-Dialog schließen, damit er auf
        // einem zwischenzeitlich gelösten Brett nicht hängen bleibt (PW-3.5a-Fix).
        if (reason == InvalidMoveReason.ALREADY_SOLVED) {
            mutableState.update { it.copy(pendingResetConfirm = false) }
            return
        }
        effectChannel.trySend(GameEffect.InvalidMove)
    }

    private fun onReset() {
        val current = gameState ?: return
        // §6.3/R32: Gelöst sperrt Reset. Ohne diesen Riegel setzte ein Reset ≥ 5
        // Züge `pendingResetConfirm`, das folgende `Move.Reset` liefert aber
        // `Invalid(ALREADY_SOLVED)` ⇒ der Dialog bliebe hängen (PW-3.5a-Fix).
        if (current.solved) return
        if (current.moveCount >= RESET_CONFIRM_THRESHOLD) {
            mutableState.update { it.copy(pendingResetConfirm = true) }
        } else {
            applyMove(Move.Reset)
        }
    }

    private fun onConfirmReset() {
        if (!mutableState.value.pendingResetConfirm) return
        // Randfall: Brett nach dem Öffnen des Dialogs gelöst ⇒ nur Dialog schließen.
        if (gameState?.solved == true) {
            mutableState.update { it.copy(pendingResetConfirm = false) }
            return
        }
        applyMove(Move.Reset) // renderState räumt pendingResetConfirm ab
    }

    /** Übersetzt `MoveResult.Applied` → render-fertigen UiState (Board aus `.trace`, §12.3). */
    private fun renderState(applied: MoveResult.Applied): GameUiState {
        val current = applied.state
        return GameUiState(
            isLoading = false,
            board = boardUiState(current.currentBoard(), applied.trace),
            moves = current.moveCount,
            par = current.level.par,
            canUndo = current.moveCount > 0 && !current.solved,
            pendingResetConfirm = false,
            result = if (current.solved) gameResultFor(current) else null,
        )
    }

    private fun gameResultFor(current: GameState): GameResult {
        val score = scoreCalculator.scoreFor(current.moveCount, current.level.par)
        return GameResult(
            points = score.points,
            stars = score.stars,
            moves = current.moveCount,
            par = current.level.par,
        )
    }

    /** Nur Kampagne speichert (§12.3); Daily-Wertung ist Phase 4. Fehler ⇒ Effect (R43-Geist). */
    private fun persistIfCampaign(current: GameState) {
        val target = request as? LevelRequest.Campaign ?: return
        val score = scoreCalculator.scoreFor(current.moveCount, current.level.par)
        viewModelScope.launch {
            val result = progressRepository.recordSolved(target.levelNumber, score)
            if (result is WriteResult.Failure) effectChannel.trySend(GameEffect.SaveFailed(result.failure))
        }
    }
}
