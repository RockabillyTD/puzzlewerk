package de.puzzlewerk.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
 * Ergebnis-Overlay einer gelösten Partie (§7.2, §12.3): Sterne (mehrkanalig:
 * Glyphe + Text), Punkte, „Züge X · Par Y" und die Aktionen Weiter / Nochmal /
 * Zurück. Deckt das Brett ab und verschluckt Taps darunter (Undo/Reset bleiben
 * so unerreichbar).
 *
 * @param onNext Nächstes Kampagnenlevel — `null` bei Daily oder Level 50 (kein „Weiter").
 * @param onReplay Frische Partie desselben Levels (GameIntent.Replay, nicht Reset — R32).
 * @param onBack Zurück zur Levelauswahl/Home.
 */
@Composable
internal fun GameResultOverlay(
    result: GameResult,
    onNext: (() -> Unit)?,
    onReplay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
                ResultSummary(result = result)
                ResultActions(onNext = onNext, onReplay = onReplay, onBack = onBack)
            }
        }
    }
}

/** Aktionsknöpfe des Overlays (§12.3): „Weiter" nur bei vorhandenem [onNext], sonst Nochmal/Zurück. */
@Composable
private fun ResultActions(
    onNext: (() -> Unit)?,
    onReplay: () -> Unit,
    onBack: () -> Unit,
) {
    onNext?.let {
        Button(onClick = it, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.game_result_next))
        }
    }
    OutlinedButton(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.game_result_replay))
    }
    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
        Text(text = stringResource(R.string.game_result_back))
    }
}

/**
 * Text-/Sternblock des Overlays. Als ein TalkBack-Knoten zusammengefasst
 * (`mergeDescendants`); die Sterne-Glyphen sind von der Sprachausgabe
 * ausgenommen, weil die Wertung zusätzlich als Text „%d von 3 Sternen"
 * vorliegt (§13: Zustand nie nur über Farbe/Symbol allein).
 */
@Composable
private fun ResultSummary(result: GameResult) {
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
        StarRow(stars = result.stars)
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

/** Sterne als Formkanal (§13): Anzahl gefüllter ★ kodiert die Wertung, nicht die Farbe. */
@Composable
private fun StarRow(stars: Int) {
    val filled = stringResource(R.string.game_stars_filled)
    val empty = stringResource(R.string.game_stars_empty)
    val text =
        buildString {
            repeat(stars) { append(filled) }
            repeat(MAX_STARS - stars) { append(empty) }
        }
    // Glyphen von TalkBack ausnehmen — die Textzeile „%d von 3 Sternen" trägt die Wertung.
    Text(text = text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics {})
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
