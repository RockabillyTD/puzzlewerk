package de.puzzlewerk.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.puzzlewerk.game.board.Board

/**
 * Wurzel-Composable der App.
 *
 * Phase 2: zeigt nur, dass die Modul-Verkabelung steht (:app ruft :game auf).
 * Der echte Spielfeld-Screen entsteht in Phase 3 nach docs/game-design.md.
 */
@Composable
fun PuzzlewerkApp() {
    val demoCellCount = Board.cellCount(Board.MIN_RADIUS)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = stringResource(R.string.phase2_status, demoCellCount),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PuzzlewerkAppPreview() {
    PuzzlewerkApp()
}
