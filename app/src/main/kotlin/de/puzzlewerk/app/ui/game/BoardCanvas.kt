package de.puzzlewerk.app.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.juice.JuiceState
import de.puzzlewerk.game.board.HexCoord
import kotlin.math.roundToInt

/**
 * Spielfeld-Rendering (PW-3.4) plus Tap-Eingabe und Dreh-Animation (PW-3.5b):
 * reine State→Pixel-Funktion mit optionaler Eingabe.
 *
 * Zeichnet Hex-Raster (§2.4), alle acht Elementtypen mit Formsymbolen (§13.1),
 * Strahl-Segmente mit Farb- UND Musterkanal (§13.2), Kristallzustände (§12.3)
 * und den Sockelring drehbarer Elemente.
 *
 * Eingabe (ui-architektur.md §5/3): Ist [onCellTap] gesetzt, übersetzt ein
 * `pointerInput` die Tap-Position über die inverse Pixel-Abbildung in eine
 * Axial-Koordinate und meldet sie — die Entscheidung „drehbar?" trifft danach
 * die Engine (R27), nicht dieser Code. Für TalkBack trägt zusätzlich jeder
 * Zell-Semantics-Knoten eine `onClick`-Aktion (§13.5: Zug per Doppeltipp).
 *
 * Animation (§12.3): [spin] versetzt genau ein drehbares Element optisch von
 * seiner Start- zur Zielstellung; die Logik ist bereits angewandt.
 *
 * Recomposition-/Draw-Hygiene (ui-architektur.md §5/1): Geometrie und
 * Zeichenlisten werden je (Radius, Canvas-Größe) bzw. je (UiState, spin)
 * `remember`t; im Draw-Pfad wird nur noch iteriert, nichts alloziert.
 *
 * Juice-Layer (PW-4.5, ADR-011): [juice] liefert den [JuiceState]-Snapshot des
 * Frame-Treibers (`rememberJuiceFrameState`). Er wird AUSSCHLIESSLICH in der
 * Draw-Phase gelesen — Puls, Partikel und Flash invalidieren damit nur das
 * Zeichnen, nie die Komposition. `null` = statisches Brett (Previews/Tests):
 * Laser mit Ruhe-Halo, keine Partikel.
 *
 * Vorbedingung: endliche Constraints in beiden Achsen (z. B. über
 * `Modifier.fillMaxSize()` oder feste Größe) — unbounded Messung wäre ein
 * Aufruffehler und wird per `check` abgewiesen.
 */
@Composable
internal fun BoardCanvas(
    state: BoardUiState,
    modifier: Modifier = Modifier,
    colors: BoardColors = BoardColors(),
    spin: BoardSpin? = null,
    juice: State<JuiceState>? = null,
    onCellTap: ((HexCoord) -> Unit)? = null,
) {
    val boardDescription = stringResource(R.string.board_canvas)
    BoxWithConstraints(modifier = modifier.semantics { contentDescription = boardDescription }) {
        check(constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            "BoardCanvas braucht endliche Constraints (z. B. fillMaxSize oder feste Größe)"
        }
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val geometry =
            remember(state.radius, widthPx, heightPx) {
                BoardGeometry.fit(state.radius, widthPx, heightPx)
            }
        val spec =
            remember(state, geometry, colors, spin) {
                buildBoardRenderSpec(state, geometry, colors, spin)
            }
        Canvas(modifier = Modifier.fillMaxSize().tapInput(geometry, state.radius, onCellTap)) {
            // Draw-Phase-Read: jedes Juice-Frame zeichnet nur neu, rekomponiert nicht.
            val juiceState = juice?.value ?: JuiceState.EMPTY
            drawBoard(spec, juiceState.haloPulseFactor)
            drawJuiceEffects(juiceState)
        }
        BoardCellSemantics(state = state, geometry = geometry, onCellTap = onCellTap)
    }
}

/**
 * Wandelt Taps auf das Brett in Zell-Koordinaten (ui-architektur.md §5/3): die
 * Zentrierung aus [geometry] abziehen, dann [HexGeometry.pixelToAxial] mit
 * derselben Zellgröße, und nur weitermelden, wenn die Zelle im Brett liegt
 * ([HexGeometry.isOnBoard]). No-op, wenn [onCellTap] `null` ist.
 */
private fun Modifier.tapInput(
    geometry: BoardGeometry,
    radius: Int,
    onCellTap: ((HexCoord) -> Unit)?,
): Modifier =
    if (onCellTap == null) {
        this
    } else {
        this.pointerInput(geometry, radius, onCellTap) {
            detectTapGestures { position ->
                val coord =
                    HexGeometry.pixelToAxial(
                        x = position.x - geometry.origin.x,
                        y = position.y - geometry.origin.y,
                        cellSize = geometry.cellSize,
                    )
                if (HexGeometry.isOnBoard(coord, radius)) onCellTap(coord)
            }
        }
    }

/** Zeilenabstand des Hex-Rasters in Zellgrößen (§2.4: `y = size·1.5·r`). */
private const val SEMANTICS_ROW_PITCH = 1.5f

/**
 * Unsichtbare Semantics-Knoten über dem Canvas — einer je Zelle (§13.5).
 * Knotenmaß: Zellbreite `√3·size` × Zeilenabstand `1,5·size` — damit
 * überlappen sich die Knoten vertikal NICHT (volle Zellhöhe `2·size` würde
 * benachbarte Reihen überdecken). Ist [onCellTap] gesetzt, trägt jeder Knoten
 * eine `onClick`-Aktion, sodass TalkBack den Drehzug per Doppeltipp auslöst
 * (§13.5). Die ≥ 48-dp-Touch-Targets verantwortet die Brettskalierung (§13.6).
 */
@Composable
private fun BoardCellSemantics(
    state: BoardUiState,
    geometry: BoardGeometry,
    onCellTap: ((HexCoord) -> Unit)?,
) {
    val cellWidthPx = geometry.cellSize * HexGeometry.SQRT3
    val cellHeightPx = geometry.cellSize * SEMANTICS_ROW_PITCH
    val density = LocalDensity.current
    val cellWidthDp = with(density) { cellWidthPx.toDp() }
    val cellHeightDp = with(density) { cellHeightPx.toDp() }
    val rotateLabel = stringResource(R.string.game_rotate_action)
    val tap = onCellTap
    for (cell in state.cells) {
        val center = geometry.center(cell.coord)
        val description = cellContentDescription(cell)
        val coord = cell.coord
        Box(
            modifier =
                Modifier
                    .offset { cellTopLeft(center, cellWidthPx, cellHeightPx) }
                    .size(width = cellWidthDp, height = cellHeightDp)
                    .semantics { cellNodeSemantics(description, coord, rotateLabel, tap) },
        )
    }
}

/** Semantik eines Zell-Knotens: §13.5-Beschreibung plus optionale Dreh-Aktion (TalkBack-Doppeltipp). */
private fun SemanticsPropertyReceiver.cellNodeSemantics(
    description: String,
    coord: HexCoord,
    rotateLabel: String,
    onCellTap: ((HexCoord) -> Unit)?,
) {
    contentDescription = description
    if (onCellTap != null) {
        onClick(label = rotateLabel) {
            onCellTap(coord)
            true
        }
    }
}

/** Linke obere Ecke des Zell-Semantics-Knotens (Zentrum minus halbe Knotengröße). */
private fun cellTopLeft(
    center: Offset,
    widthPx: Float,
    heightPx: Float,
): IntOffset = IntOffset((center.x - widthPx / 2f).roundToInt(), (center.y - heightPx / 2f).roundToInt())
