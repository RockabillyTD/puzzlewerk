package de.puzzlewerk.app.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * Previews mit Fake-States (ohne ViewModel/Container, ui-architektur.md §8).
 * Hintergrund lokal aus [BoardColors] — Theme-Anbindung folgt in PW-3.5.
 */
@Composable
private fun BoardPreviewSurface(state: BoardUiState) {
    val colors = BoardColors()
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        BoardCanvas(state = state, modifier = Modifier.fillMaxSize(), colors = colors)
    }
}

@Preview(name = "Beispiel-Level §7.3 — Start (m=5)", widthDp = 360, heightDp = 400)
@Composable
private fun BoardCanvasStartPreview() {
    BoardPreviewSurface(BoardSampleStates.exampleLevelStart)
}

@Preview(name = "Beispiel-Level §7.3 — gelöst (m=1)", widthDp = 360, heightDp = 400)
@Composable
private fun BoardCanvasSolvedPreview() {
    BoardPreviewSurface(BoardSampleStates.exampleLevelSolved)
}

@Preview(name = "Alle Elemente + Kristallzustände", widthDp = 360, heightDp = 400)
@Composable
private fun BoardCanvasElementZooPreview() {
    BoardPreviewSurface(BoardSampleStates.elementZoo)
}

@Preview(name = "Landscape", widthDp = 640, heightDp = 320)
@Composable
private fun BoardCanvasLandscapePreview() {
    BoardPreviewSurface(BoardSampleStates.exampleLevelSolved)
}
