package de.puzzlewerk.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.app.ui.navigation.Screen
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT
import kotlinx.coroutines.flow.Flow

// Wackel-Feedback für ungültige Züge (§12.3, „kurzes Wackeln"): px-Amplituden und
// Keyframe-Zeitpunkte als benannte Konstanten (kein Magic-Number im Draw-/Animationspfad).
private const val SHAKE_AMPLITUDE = 22f
private const val SHAKE_AMPLITUDE_HALF = 11f
private const val SHAKE_MS_1 = 80
private const val SHAKE_MS_2 = 160
private const val SHAKE_MS_3 = 240
private const val SHAKE_DURATION_MILLIS = 320

/**
 * Verdrahtung des Spiel-Screens (§12.3, ADR-006/008): baut das [GameViewModel]
 * über die request-parametrierte Factory, spiegelt Effects (Haptik/Wackeln,
 * Speicherfehler) und übersetzt „Weiter"/„Zurück" in Navigation. Tiefere
 * Composables sehen nur State + Intents (docs/ui-architektur.md §2).
 */
@Composable
internal fun GameRoute(
    request: LevelRequest,
    gameViewModelFactory: (LevelRequest) -> ViewModelProvider.Factory,
    onNavigate: (Screen) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GameViewModel = viewModel(factory = gameViewModelFactory(request))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val shake = remember { Animatable(0f) }
    val animationsEnabled = rememberAnimationsEnabled()
    val currentOnNavigate by rememberUpdatedState(onNavigate)

    GameEffectHandler(viewModel.effects, snackbarHostState, shake, animationsEnabled)

    val nextLevel = (request as? LevelRequest.Campaign)?.levelNumber?.takeIf { it < CAMPAIGN_LEVEL_COUNT }
    Box(modifier = modifier.fillMaxSize()) {
        GameScreen(
            state = state,
            onIntent = viewModel::onIntent,
            onNavigateBack = onNavigateBack,
            onNavigateNext = nextLevel?.let { n -> { currentOnNavigate(Screen.Game(LevelRequest.Campaign(n + 1))) } },
            // Das ganze Spielbild wackelt bei ungültigem Zug (dezent, respektiert Reduce-Motion).
            modifier = Modifier.graphicsLayer { translationX = shake.value },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

/** Sammelt die Einmal-Effects (§12.3): Haptik + Wackeln bei ungültig, Snackbar bei Speicherfehler. */
@Composable
private fun GameEffectHandler(
    effects: Flow<GameEffect>,
    snackbarHostState: SnackbarHostState,
    shake: Animatable<Float, *>,
    animationsEnabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val saveFailedMessage = stringResource(R.string.game_save_failed)
    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                GameEffect.InvalidMove -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress) // No-op-taugliche Senke
                    if (animationsEnabled) shakeOnce(shake)
                }
                is GameEffect.SaveFailed -> snackbarHostState.showSnackbar(saveFailedMessage)
            }
        }
    }
}

/** Ein einmaliges horizontales Wackeln als Feedback für ungültige Züge (§12.3). */
private suspend fun shakeOnce(shake: Animatable<Float, *>) {
    shake.snapTo(0f)
    shake.animateTo(
        targetValue = 0f,
        animationSpec =
            keyframes {
                durationMillis = SHAKE_DURATION_MILLIS
                SHAKE_AMPLITUDE at SHAKE_MS_1
                -SHAKE_AMPLITUDE at SHAKE_MS_2
                SHAKE_AMPLITUDE_HALF at SHAKE_MS_3
            },
    )
}

/**
 * Spiel-Screen §12.3 — reine Funktion des [GameUiState], vollständig
 * preview-fähig. Zustände: Laden, Spielend, Gelöst (Ergebnis-Overlay); einen
 * Verloren-Zustand gibt es nicht (§6.3). Undo/Reset sind unter dem Overlay
 * inaktiv und der Board-Tap ist abgeschaltet.
 */
@Composable
internal fun GameScreen(
    state: GameUiState,
    onIntent: (GameIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            GameHeader(state = state, onIntent = onIntent, onNavigateBack = onNavigateBack)
            GameBoard(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp),
            )
        }
        if (state.pendingResetConfirm) {
            ResetConfirmDialog(
                onConfirm = { onIntent(GameIntent.ConfirmReset) },
                onDismiss = { onIntent(GameIntent.DismissReset) },
            )
        }
        state.result?.let { result ->
            GameResultOverlay(
                result = result,
                onNext = onNavigateNext,
                onReplay = { onIntent(GameIntent.Replay) },
                onBack = onNavigateBack,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Brettbereich: Ladeindikator oder [BoardCanvas] mit Tap-Eingabe und
 * Dreh-Animation. [animationsEnabled] folgt „Animationen entfernen" (§13.6);
 * die pure Dreh-Erkennung ist separat testbar ([singleRotatedCell]).
 */
@Composable
private fun GameBoard(
    state: GameUiState,
    onIntent: (GameIntent) -> Unit,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = rememberAnimationsEnabled(),
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val board = state.board
        if (state.isLoading || board == null) {
            LoadingBoard()
        } else {
            BoardCanvas(
                state = board,
                modifier = Modifier.fillMaxSize(),
                spin = rememberRotationSpin(board, animationsEnabled),
                // Gelöst sperrt den Tap; zusätzlich blockt das Overlay Taps darüber.
                onCellTap = if (state.result != null) null else { coord -> onIntent(GameIntent.TapCell(coord)) },
            )
        }
    }
}

/** Kopfzeile (§12.3): „Züge X · Par Y" plus Undo/Reset/Zurück; gelöst ⇒ Undo/Reset inaktiv. */
@Composable
private fun GameHeader(
    state: GameUiState,
    onIntent: (GameIntent) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val active = state.result == null
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            TextButton(onClick = onNavigateBack) { Text(text = stringResource(R.string.game_back)) }
        }
        Text(
            text = stringResource(R.string.game_moves_par, state.moves, state.par),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onIntent(GameIntent.Undo) }, enabled = active && state.canUndo) {
            Text(text = stringResource(R.string.game_undo))
        }
        TextButton(onClick = { onIntent(GameIntent.Reset) }, enabled = active) {
            Text(text = stringResource(R.string.game_reset))
        }
    }
}

/** Ladezustand während der Levelgenerierung (§9.4, off-main): Spinner plus Text. */
@Composable
private fun LoadingBoard() {
    val description = stringResource(R.string.game_loading)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = description, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Reset-Bestätigung ab ≥ 5 Zügen (§12.3). Außenklick/Zurück = Abbruch. */
@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.game_reset_confirm_title)) },
        text = { Text(text = stringResource(R.string.game_reset_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(text = stringResource(R.string.game_reset_confirm_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.game_reset_confirm_cancel)) }
        },
    )
}

// ---- Previews (Fake-State, ohne ViewModel/Container) ----

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun GameScreenPlayingPreview() {
    PuzzlewerkTheme {
        GameScreen(
            state =
                GameUiState(
                    isLoading = false,
                    board = BoardSampleStates.exampleLevelStart,
                    moves = 3,
                    par = 5,
                    canUndo = true,
                ),
            onIntent = {},
            onNavigateBack = {},
            onNavigateNext = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GameScreenLoadingPreview() {
    PuzzlewerkTheme {
        GameScreen(state = GameUiState(), onIntent = {}, onNavigateBack = {}, onNavigateNext = null)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun GameScreenSolvedPreview() {
    PuzzlewerkTheme {
        GameScreen(
            state =
                GameUiState(
                    isLoading = false,
                    board = BoardSampleStates.exampleLevelSolved,
                    moves = 2,
                    par = 3,
                    result = GameResult(points = 1450, stars = 3, moves = 2, par = 3),
                ),
            onIntent = {},
            onNavigateBack = {},
            onNavigateNext = {},
        )
    }
}
