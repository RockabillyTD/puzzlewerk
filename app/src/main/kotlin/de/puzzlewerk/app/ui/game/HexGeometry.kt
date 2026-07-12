package de.puzzlewerk.app.ui.game

import de.puzzlewerk.game.board.HexCoord
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pixel-Abbildung des pointy-top-Hex-Rasters (Design §2.4):
 * `x = size·√3·(q + r/2)`, `y = size·1.5·r` (Bildschirm-y nach unten).
 *
 * Reine Mathematik ohne Android-/Compose-Typen — als plain-JVM-Unit-Test
 * prüfbar (Roundtrip Pixel ↔ Axial). Die Spiellogik kennt nur Indizes,
 * keine Winkel (§2.4); alles Winkelhafte lebt ausschließlich hier in der UI.
 */
internal object HexGeometry {
    /** Vorberechnetes √3 (Breitenfaktor des pointy-top-Rasters, §2.4). */
    val SQRT3: Float = sqrt(3f)

    /** Zeilenhöhe in Vielfachen der Zellgröße (§2.4: `y = size·1.5·r`). */
    private const val ROW_HEIGHT: Float = 1.5f

    /** Ecken eines pointy-top-Hexagons liegen bei `60°·k − 30°` (Bildschirmwinkel). */
    private const val DEGREES_PER_CORNER: Float = 60f
    private const val CORNER_OFFSET_DEGREES: Float = -30f
    private const val DEG_TO_RAD: Float = (Math.PI / 180.0).toFloat()

    /** Zellzentrum-x zu [coord] bei Zellgröße [cellSize] (§2.4). */
    fun centerX(
        coord: HexCoord,
        cellSize: Float,
    ): Float = cellSize * SQRT3 * (coord.q + coord.r / 2f)

    /** Zellzentrum-y zu [coord] bei Zellgröße [cellSize] (§2.4, Bildschirm-y nach unten). */
    fun centerY(
        coord: HexCoord,
        cellSize: Float,
    ): Float = cellSize * ROW_HEIGHT * coord.r

    /**
     * Inverse Pixel-Abbildung: Bildschirmpunkt ([x], [y]) relativ zum
     * Brettursprung → Axial-Koordinate der nächstliegenden Zelle
     * (Kubik-Rundung; Grundlage der Tap-Zuordnung ab PW-3.5).
     */
    fun pixelToAxial(
        x: Float,
        y: Float,
        cellSize: Float,
    ): HexCoord {
        val fractionalR = y / (cellSize * ROW_HEIGHT)
        val fractionalQ = x / (cellSize * SQRT3) - fractionalR / 2f
        return roundAxial(fractionalQ, fractionalR)
    }

    /**
     * Größte Zellgröße, mit der ein Brett mit [radius] in [width]×[height]
     * passt. Ausdehnung der Brettsilhouette: Breite `√3·size·(2R+1)`,
     * Höhe `size·(3R+2)` (Zentrenspanne plus je eine halbe Zelle Rand).
     */
    fun fittingCellSize(
        radius: Int,
        width: Float,
        height: Float,
    ): Float = minOf(width / (SQRT3 * (2 * radius + 1)), height / (ROW_HEIGHT * 2 * radius + 2))

    /** x-Anteil der Hex-Ecke [corner] ∈ 0..5 relativ zum Zellzentrum. */
    fun cornerX(
        cellSize: Float,
        corner: Int,
    ): Float = cellSize * cos(cornerAngleRad(corner))

    /** y-Anteil der Hex-Ecke [corner] ∈ 0..5 relativ zum Zellzentrum. */
    fun cornerY(
        cellSize: Float,
        corner: Int,
    ): Float = cellSize * sin(cornerAngleRad(corner))

    /**
     * Alle Zellen eines Bretts mit [radius] in deterministischer Lesereihenfolge
     * (r aufsteigend, dann q aufsteigend; Zugehörigkeit nach §2.1:
     * `max(|q|, |r|, |q+r|) ≤ R`).
     */
    fun boardCells(radius: Int): List<HexCoord> =
        (-radius..radius).flatMap { r ->
            (maxOf(-radius, -radius - r)..minOf(radius, radius - r)).map { q -> HexCoord(q, r) }
        }

    private fun cornerAngleRad(corner: Int): Float = (DEGREES_PER_CORNER * corner + CORNER_OFFSET_DEGREES) * DEG_TO_RAD

    /** Kubik-Rundung: fraktionale Axial-Koordinate → nächstliegende Zelle. */
    private fun roundAxial(
        fractionalQ: Float,
        fractionalR: Float,
    ): HexCoord {
        val fractionalS = -fractionalQ - fractionalR
        var q = fractionalQ.roundToInt()
        var r = fractionalR.roundToInt()
        val s = fractionalS.roundToInt()
        val deltaQ = abs(q - fractionalQ)
        val deltaR = abs(r - fractionalR)
        val deltaS = abs(s - fractionalS)
        if (deltaQ > deltaR && deltaQ > deltaS) {
            q = -r - s
        } else if (deltaR > deltaS) {
            r = -q - s
        }
        return HexCoord(q, r)
    }
}
