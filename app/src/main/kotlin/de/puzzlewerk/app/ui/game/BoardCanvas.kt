package de.puzzlewerk.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import de.puzzlewerk.app.R
import kotlin.math.roundToInt

/**
 * Statisches Spielfeld-Rendering (PW-3.4): reine State→Pixel-Funktion.
 *
 * Zeichnet Hex-Raster (§2.4), alle acht Elementtypen mit Formsymbolen (§13.1),
 * Strahl-Segmente mit Farb- UND Musterkanal (§13.2), Kristallzustände (§12.3)
 * und den Sockelring drehbarer Elemente. BEWUSST ohne Eingabe, ViewModel und
 * Animation — `pointerInput` und Dreh-Animation folgen in PW-3.5
 * (ui-architektur.md §5/3+5).
 *
 * Recomposition-/Draw-Hygiene (ui-architektur.md §5/1): Geometrie und
 * Zeichenlisten werden je (Radius, Canvas-Größe) bzw. je UiState `remember`t;
 * im Draw-Pfad wird nur noch iteriert, nichts alloziert.
 *
 * Barrierefreiheit: je Zelle ein Semantics-Knoten mit §13.5-Beschreibung —
 * TalkBack navigiert Zelle für Zelle über das Brett.
 */
@Composable
fun BoardCanvas(
    state: BoardUiState,
    modifier: Modifier = Modifier,
    colors: BoardColors = BoardColors(),
) {
    val boardDescription = stringResource(R.string.board_canvas)
    BoxWithConstraints(modifier = modifier.semantics { contentDescription = boardDescription }) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val geometry =
            remember(state.radius, widthPx, heightPx) {
                BoardGeometry.fit(state.radius, widthPx, heightPx)
            }
        val spec =
            remember(state, geometry, colors) {
                buildBoardRenderSpec(state, geometry, colors)
            }
        Canvas(modifier = Modifier.fillMaxSize()) { drawBoard(spec) }
        BoardCellSemantics(state = state, geometry = geometry)
    }
}

/**
 * Unsichtbare, zellgroße Semantics-Knoten über dem Canvas — einer je Zelle
 * (§13.5). In PW-3.5 werden dieselben Knoten um die Tap-Interaktion ergänzt;
 * die Zellgröße für ≥ 48-dp-Touch-Targets (§13.6) verantwortet dort der Screen.
 */
@Composable
private fun BoardCellSemantics(
    state: BoardUiState,
    geometry: BoardGeometry,
) {
    val cellWidthPx = geometry.cellSize * HexGeometry.SQRT3
    val cellSizeDp = with(LocalDensity.current) { cellWidthPx.toDp() }
    for (cell in state.cells) {
        val description = cellContentDescription(cell)
        val center = geometry.center(cell.coord)
        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(
                            (center.x - cellWidthPx / 2f).roundToInt(),
                            (center.y - cellWidthPx / 2f).roundToInt(),
                        )
                    }.size(cellSizeDp)
                    .semantics { contentDescription = description },
        )
    }
}
