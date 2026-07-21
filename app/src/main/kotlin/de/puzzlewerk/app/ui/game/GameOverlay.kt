package de.puzzlewerk.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme

/** Höchste Sternwertung (§7.2). */
private const val MAX_STARS = 3
private const val OVERLAY_MAX_WIDTH = 360

/**
 * Ergebnis-Overlay einer gelösten Partie (§7.2, §12.3, §13.10): Sterne
 * (mehrkanalig: Glyphe + Text) fliegen einzeln mit Bounce ein (Start
 * `t_fw + 120 + (n−1)·150 ms`, [GameResult.fireworkStartMillis]), Punkte,
 * „Züge X · Par Y" und die Aktionen Weiter / Nochmal / Zurück (Fade ab 500 ms,
 * interaktiv ab 600 ms — abgenommene V3-Abweichung: der 3. Stern darf beim
 * Freischalten noch fliegen). Deckt das Brett ab und verschluckt Taps darunter
 * (Undo/Reset bleiben so unerreichbar).
 *
 * @param onNext Nächstes Kampagnenlevel — `null` bei Daily oder Level 50 (kein „Weiter").
 * @param onReplay Frische Partie desselben Levels (GameIntent.Replay, nicht Reset — R32).
 * @param onBack Zurück zur Levelauswahl/Home.
 * @param onStarShown Stern n (1-basiert) ist eingeflogen — SFX-Senke sfx_star_n (§13.11).
 * @param animationsEnabled §13.12: `false` ⇒ Sterne ohne Bounce (150-ms-Fade), gleiche Zeitpunkte.
 */
@Composable
internal fun GameResultOverlay(
    result: GameResult,
    onNext: (() -> Unit)?,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onStarShown: (Int) -> Unit = {},
    animationsEnabled: Boolean = rememberAnimationsEnabled(),
) {
    Box(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f))
                // Blockiert jeden Tap, der sonst das Brett unter dem Overlay erreichte.
                .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.padding(24.dp).widthIn(max = OVERLAY_MAX_WIDTH.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ResultSummary(result = result, animationsEnabled = animationsEnabled, onStarShown = onStarShown)
                ResultActions(onNext = onNext, onReplay = onReplay, onBack = onBack)
            }
        }
    }
}

/**
 * Aktionsknöpfe des Overlays (§12.3, §13.10 Nr. 6): „Weiter" nur bei
 * vorhandenem [onNext], sonst Nochmal/Zurück. Alpha-Fade 500 → 600 ms und
 * Klick-Gate bis zur 600-ms-Frist ([rememberOverlayActionsAppearance]) — die
 * Knöpfe bleiben semantisch unverändert (kein enabled-Wechsel, TalkBack-Fläche
 * wie bisher), Klicks vor der Frist verpuffen wirkungslos.
 */
@Composable
private fun ResultActions(
    onNext: (() -> Unit)?,
    onReplay: () -> Unit,
    onBack: () -> Unit,
) {
    val appearance = rememberOverlayActionsAppearance()
    val gated = { action: () -> Unit -> if (appearance.interactive) action() }
    Column(
        modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = appearance.alpha() },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        onNext?.let {
            Button(onClick = { gated(it) }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.game_result_next))
            }
        }
        OutlinedButton(onClick = { gated(onReplay) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.game_result_replay))
        }
        TextButton(onClick = { gated(onBack) }, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.game_result_back))
        }
    }
}

/**
 * Text-/Sternblock des Overlays. Als ein TalkBack-Knoten zusammengefasst
 * (`mergeDescendants`); die Sterne-Glyphen sind von der Sprachausgabe
 * ausgenommen, weil die Wertung zusätzlich als Text „%d von 3 Sternen"
 * vorliegt (§13: Zustand nie nur über Farbe/Symbol allein).
 */
@Composable
private fun ResultSummary(
    result: GameResult,
    animationsEnabled: Boolean,
    onStarShown: (Int) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = stringResource(R.string.game_result_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        StarRow(result = result, animationsEnabled = animationsEnabled, onStarShown = onStarShown)
        Text(
            text = pluralStringResource(R.plurals.game_result_stars, result.stars, result.stars),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = pluralStringResource(R.plurals.game_result_points, result.points, result.points),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.game_moves_par, result.moves, result.par),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Sterne als Formkanal (§13): Anzahl gefüllter ★ kodiert die Wertung, nicht die
 * Farbe. Verdiente Sterne fliegen einzeln mit Bounce ein (§13.10 Nr. 5,
 * [rememberStarAppearance]); die Provider werden NUR in der
 * `graphicsLayer`-Lambda gelesen (keine Recomposition je Animations-Frame).
 * TalkBack-Semantik unverändert (PW-3.7-QS): die Glyphenzeile ist komplett
 * ausgenommen — die Textzeile „%d von 3 Sternen" trägt die Wertung.
 */
@Composable
private fun StarRow(
    result: GameResult,
    animationsEnabled: Boolean,
    onStarShown: (Int) -> Unit,
) {
    val filled = stringResource(R.string.game_stars_filled)
    val empty = stringResource(R.string.game_stars_empty)
    Row(modifier = Modifier.clearAndSetSemantics {}) {
        for (star in 1..MAX_STARS) {
            if (star <= result.stars) {
                val appearance =
                    rememberStarAppearance(
                        startMillis = starEntryStartMillis(result.fireworkStartMillis, star),
                        animationsEnabled = animationsEnabled,
                        onShown = { onStarShown(star) },
                    )
                Text(
                    text = filled,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier =
                        Modifier.graphicsLayer {
                            scaleX = appearance.scale()
                            scaleY = appearance.scale()
                            alpha = appearance.alpha()
                        },
                )
            } else {
                Text(text = empty, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun GameResultOverlayPreview() {
    PuzzlewerkTheme {
        GameResultOverlay(
            result = GameResult(points = 1450, stars = 3, moves = 2, par = 3),
            onNext = {},
            onReplay = {},
            onBack = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun GameResultOverlayNoNextPreview() {
    PuzzlewerkTheme {
        GameResultOverlay(
            result = GameResult(points = 1150, stars = 1, moves = 9, par = 4),
            onNext = null,
            onReplay = {},
            onBack = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
