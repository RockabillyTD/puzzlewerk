package de.puzzlewerk.app.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import de.puzzlewerk.game.board.Direction
import kotlin.math.cos
import kotlin.math.sin

/** Bogenmaß eines 60°-Richtungsschritts (§2.2) bzw. 30°-Orientierungsschritts (§4.2). */
internal const val DIRECTION_STEP_RAD: Float = (Math.PI / 3.0).toFloat()
internal const val ORIENTATION_STEP_RAD: Float = (Math.PI / 6.0).toFloat()

private const val HEX_CORNERS: Int = 6

/** Dreieck-Eckwinkel (math.) für das Rot-Symbol ▲ (§13.1). */
private const val TRIANGLE_TOP_RAD: Float = (Math.PI / 2.0).toFloat()
private const val TRIANGLE_STEP_RAD: Float = (2.0 * Math.PI / 3.0).toFloat()

/** Halbe Seitenlänge des Blau-Symbols ■ relativ zur Symbolgröße (§13.1). */
private const val SQUARE_HALF_FACTOR: Float = 0.8f

/** Hängt ein pointy-top-Hexagon um [center] mit Eckradius [size] an [path] an. */
internal fun addHexagonTo(
    path: Path,
    center: Offset,
    size: Float,
) {
    path.moveTo(center.x + HexGeometry.cornerX(size, 0), center.y + HexGeometry.cornerY(size, 0))
    for (corner in 1 until HEX_CORNERS) {
        path.lineTo(center.x + HexGeometry.cornerX(size, corner), center.y + HexGeometry.cornerY(size, corner))
    }
    path.close()
}

/** Raute (Kristallkörper §12.3) um [center] mit Spitzenradius [radius]. */
internal fun diamondPath(
    center: Offset,
    radius: Float,
): Path =
    Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + radius, center.y)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - radius, center.y)
        close()
    }

/** Gleichseitiges Dreieck ▲ (Rot-Symbol §13.1, Prisma-Kontur) um [center]. */
internal fun trianglePath(
    center: Offset,
    radius: Float,
): Path =
    Path().apply {
        moveTo(rayX(center.x, TRIANGLE_TOP_RAD, radius), rayY(center.y, TRIANGLE_TOP_RAD, radius))
        lineTo(
            rayX(center.x, TRIANGLE_TOP_RAD + TRIANGLE_STEP_RAD, radius),
            rayY(center.y, TRIANGLE_TOP_RAD + TRIANGLE_STEP_RAD, radius),
        )
        lineTo(
            rayX(center.x, TRIANGLE_TOP_RAD + 2 * TRIANGLE_STEP_RAD, radius),
            rayY(center.y, TRIANGLE_TOP_RAD + 2 * TRIANGLE_STEP_RAD, radius),
        )
        close()
    }

/** Quadrat ■ (Blau-Symbol §13.1) um [center] mit Symbolgröße [size]. */
internal fun squarePath(
    center: Offset,
    size: Float,
): Path {
    val half = size * SQUARE_HALF_FACTOR
    return Path().apply {
        moveTo(center.x - half, center.y - half)
        lineTo(center.x + half, center.y - half)
        lineTo(center.x + half, center.y + half)
        lineTo(center.x - half, center.y + half)
        close()
    }
}

/**
 * Punkt im Abstand [distance] von [center] in Strahlrichtung [direction].
 * Mathematischer Winkel `d · 60°` (§2.2); Bildschirm-y nach unten ⇒ y negiert (§2.4).
 */
internal fun rayPoint(
    center: Offset,
    direction: Direction,
    distance: Float,
): Offset {
    val angle = direction.index * DIRECTION_STEP_RAD
    return Offset(rayX(center.x, angle, distance), rayY(center.y, angle, distance))
}

/** Versatz um Zellgrößen-Faktoren ([factorX], [factorY]) relativ zu [center]. */
internal fun offsetBy(
    center: Offset,
    factorX: Float,
    factorY: Float,
    cellSize: Float,
): Offset = Offset(center.x + factorX * cellSize, center.y + factorY * cellSize)

private fun rayX(
    centerX: Float,
    angleRad: Float,
    distance: Float,
): Float = centerX + distance * cos(angleRad)

private fun rayY(
    centerY: Float,
    angleRad: Float,
    distance: Float,
): Float = centerY - distance * sin(angleRad)
