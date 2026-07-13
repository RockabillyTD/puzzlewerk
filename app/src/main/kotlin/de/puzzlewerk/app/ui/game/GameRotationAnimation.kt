package de.puzzlewerk.app.ui.game

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.element.Element

/** Dauer der Dreh-Animation (§12.3: 30°-Stufe „animiert ~150 ms"). */
private const val ROTATION_ANIMATION_MILLIS = 150

/**
 * Aktuelle Orientierungen (in 30°-Stufen) aller DREHBAREN Zellen des Bretts.
 * Rein aus dem [BoardUiState] abgeleitet (keine Spiellogik) — Grundlage der
 * Dreh-Erkennung.
 */
internal fun rotatableOrientations(board: BoardUiState): Map<HexCoord, Int> =
    board.cells
        .mapNotNull { cell -> (cell.element as? Element.Rotatable)?.let { cell.coord to it.orientation.steps } }
        .toMap()

/**
 * Die eine Zelle, deren Orientierung sich zwischen [previous] und [current]
 * geändert hat — oder `null`, wenn keine oder mehr als eine Zelle wechselte.
 * Genau eine Änderung ⇒ ein Drehzug (Rotate/Undo); mehrere ⇒ Reset (keine
 * Einzel-Animation). So löst ein ungültiger Tap (keine Änderung) keine
 * Animation aus.
 */
internal fun singleRotatedCell(
    previous: Map<HexCoord, Int>,
    current: Map<HexCoord, Int>,
): HexCoord? =
    current.entries
        .filter { previous[it.key] != it.value }
        .map { it.key }
        .singleOrNull()

/**
 * Treibt die Dreh-Animation (§12.3) rein visuell: erkennt aus aufeinander
 * folgenden [board]-Zuständen die gedrehte Zelle und lässt ihren Strich mit
 * einem `Animatable` von der Start- zur Zielstellung nachlaufen. Die Logik ist
 * längst angewandt (der UiState trägt die Zielorientierung); folgt während der
 * Animation ein weiterer Zug, springt die Optik auf den neuesten Stand und
 * läuft von dort nach.
 *
 * [animationsEnabled] `false` (System-Einstellung „Animationen entfernen",
 * §13.6) ⇒ kein Tween, kein Versatz: das Element steht sofort am Ziel.
 *
 * @return der aktuelle [BoardSpin] für [BoardCanvas] oder `null` (Ruhe/aus).
 */
@Composable
internal fun rememberRotationSpin(
    board: BoardUiState?,
    animationsEnabled: Boolean,
): BoardSpin? {
    val progress = remember { Animatable(1f) }
    var previous by remember { mutableStateOf<Map<HexCoord, Int>?>(null) }
    var spinningCell by remember { mutableStateOf<HexCoord?>(null) }
    LaunchedEffect(board, animationsEnabled) {
        val orientations = board?.let(::rotatableOrientations)
        val rotated =
            if (previous != null && orientations != null) {
                singleRotatedCell(previous!!, orientations)
            } else {
                null
            }
        previous = orientations
        if (rotated != null && animationsEnabled) {
            spinningCell = rotated
            progress.snapTo(0f)
            progress.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = ROTATION_ANIMATION_MILLIS))
            spinningCell = null
        }
    }
    val cell = spinningCell ?: return null
    if (!animationsEnabled) return null
    // Versatz: bei progress 0 steht das Element eine Stufe (−30°) vor dem Ziel,
    // bei 1 exakt am Ziel (offset 0). ORIENTATION_STEP_RAD = 30° in Bogenmaß.
    return BoardSpin(cell = cell, angleOffsetRad = -(1f - progress.value) * ORIENTATION_STEP_RAD)
}

/**
 * Ob Animationen laufen dürfen: liest die System-Einstellung
 * `ANIMATOR_DURATION_SCALE` (§13.6, „Animationen entfernen" ⇒ Skala 0). Als
 * Composable-Default injizierbar, damit Tests/Previews die Optik ohne Tween
 * fahren können.
 */
@Composable
internal fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
