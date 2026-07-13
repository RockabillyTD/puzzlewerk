package de.puzzlewerk.app.ui.game

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import de.puzzlewerk.game.board.HexCoord

/**
 * Vorberechnete Zeichenlisten des Spielfelds (ui-architektur.md §5/1:
 * KEINE Allokationen im Draw-Pfad). Alle Objekte entstehen EINMAL je
 * (UiState, Geometrie) in [buildBoardRenderSpec]; das Zeichnen bildet sie
 * nur noch auf Pixel ab.
 */
@Immutable
internal class BoardRenderSpec(
    /** Schicht 1: Raster und Zellflächen. */
    val base: DrawOps,
    /** Schicht 2: Strahl-Segmente (§13.2-Muster liegen als PathEffect vor). */
    val beams: List<LineSpec>,
    /** Schicht 3: Elemente, Symbole und Strahl-Chips. */
    val overlay: DrawOps,
)

/** Gebündelte, unveränderliche Zeichenoperationen einer Schicht. */
@Immutable
internal class DrawOps(
    val paths: List<PathSpec>,
    val circles: List<CircleSpec>,
    val lines: List<LineSpec>,
)

/** Fertig positionierter Pfad; [style] (Fill/Stroke) ist vorab alloziert. */
@Immutable
internal class PathSpec(
    val path: Path,
    val color: Color,
    val style: DrawStyle,
)

/** Kreis; [style] (Fill/Stroke) ist vorab alloziert. */
@Immutable
internal class CircleSpec(
    val center: Offset,
    val radius: Float,
    val color: Color,
    val style: DrawStyle = Fill,
)

/** Linie mit optionalem Strichmuster (§13.2). */
@Immutable
internal class LineSpec(
    val start: Offset,
    val end: Offset,
    val color: Color,
    val strokeWidth: Float,
    val pathEffect: PathEffect? = null,
)

/** Sammelbecken beim Aufbau; friert per [build] zu [DrawOps] ein. */
internal class DrawOpsBuilder {
    val paths: MutableList<PathSpec> = mutableListOf()
    val circles: MutableList<CircleSpec> = mutableListOf()
    val lines: MutableList<LineSpec> = mutableListOf()

    fun build(): DrawOps = DrawOps(paths.toList(), circles.toList(), lines.toList())
}

/**
 * Laufende Dreh-Animation (§12.3): das drehbare Element auf [cell] wird um
 * [angleOffsetRad] gegenüber seiner ZIEL-Orientierung versetzt gezeichnet. Die
 * Logik ist längst angewandt (Zielorientierung steht im UiState); dieser
 * Versatz lässt nur die Optik von der Start- zur Zielstellung nachlaufen.
 * `angleOffsetRad = 0` ⇒ Element steht am Ziel (Animation beendet).
 */
@Immutable
internal data class BoardSpin(
    val cell: HexCoord,
    val angleOffsetRad: Float,
)

/** Brett-Geometrie je (Radius, Canvas-Größe): Zellgröße plus Ursprung. */
@Immutable
internal class BoardGeometry(
    val cellSize: Float,
    val origin: Offset,
) {
    /** Zellzentrum von [coord] in Canvas-Pixeln (§2.4 plus Zentrierung). */
    fun center(coord: HexCoord): Offset =
        Offset(
            origin.x + HexGeometry.centerX(coord, cellSize),
            origin.y + HexGeometry.centerY(coord, cellSize),
        )

    companion object {
        /** Zentriert das Brett mit [radius] passend in [width]×[height]. */
        fun fit(
            radius: Int,
            width: Float,
            height: Float,
        ): BoardGeometry =
            BoardGeometry(
                cellSize = HexGeometry.fittingCellSize(radius, width, height),
                origin = Offset(width / 2f, height / 2f),
            )
    }
}

/** Zeichnet die drei Schichten. Draw-Pfad: nur Iteration, keine Allokation. */
internal fun DrawScope.drawBoard(spec: BoardRenderSpec) {
    drawOps(spec.base)
    drawLineSpecs(spec.beams)
    drawOps(spec.overlay)
}

private fun DrawScope.drawOps(ops: DrawOps) {
    for (i in 0 until ops.paths.size) {
        val p = ops.paths[i]
        drawPath(path = p.path, color = p.color, style = p.style)
    }
    for (i in 0 until ops.circles.size) {
        val c = ops.circles[i]
        drawCircle(color = c.color, radius = c.radius, center = c.center, style = c.style)
    }
    drawLineSpecs(ops.lines)
}

private fun DrawScope.drawLineSpecs(lines: List<LineSpec>) {
    for (i in 0 until lines.size) {
        val l = lines[i]
        drawLine(
            color = l.color,
            start = l.start,
            end = l.end,
            strokeWidth = l.strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = l.pathEffect,
        )
    }
}
