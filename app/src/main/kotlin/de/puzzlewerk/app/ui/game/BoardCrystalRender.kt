package de.puzzlewerk.app.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import de.puzzlewerk.game.color.CrystalFill
import de.puzzlewerk.game.color.LightColor

// Alle Maße relativ zur Zellgröße (dp-unabhängig; Faktoren statt Pixel).
private const val CRYSTAL_RADIUS = 0.62f
private const val CRYSTAL_SYMBOL_LIFT = -0.2f
private const val CRYSTAL_SYMBOL_SIZE = 0.13f
private const val AURA_RADIUS = 0.95f
private const val AURA_ALPHA = 0.3f
private const val CHECK_START_X = -0.2f
private const val CHECK_START_Y = 0.26f
private const val CHECK_MID_X = -0.07f
private const val CHECK_MID_Y = 0.4f
private const val CHECK_END_X = 0.2f
private const val CHECK_END_Y = 0.14f
private const val FOREIGN_ROW_Y = 0.34f
private const val FOREIGN_SYMBOL_SIZE = 0.1f
private const val STRIKE_HALF_WIDTH = 0.3f
private const val SYMBOL_SPACING = 2.4f

/**
 * Kristall- und Symbolzeichnung (§12.3, §13.1/§13.3) — aus dem
 * `BoardSpecBuilder` ausgegliedert (C4-Dateigrenze ≤ 300 Zeilen); schreibt
 * in denselben Overlay-[DrawOpsBuilder].
 *
 * @property overlay Ziel-Schicht der Zeichenoperationen.
 * @property colors Farbsatz des Spielfelds.
 * @property cellSize Zellgröße in Pixeln (Bezugsgröße aller Maße).
 * @property symbolStroke Vorab allozierter Stroke für Symbol-Umrisse.
 * @property elementStroke Vorab allozierter Stroke für Elementkonturen.
 * @property lineWidth Linienbreite für Haken/Durchstreichung in Pixeln.
 */
internal class BoardCrystalRender(
    private val overlay: DrawOpsBuilder,
    private val colors: BoardColors,
    private val cellSize: Float,
    private val symbolStroke: DrawStyle,
    private val elementStroke: DrawStyle,
    private val lineWidth: Float,
) {
    /**
     * Kristall §4.7/§12.3: Raute; erfüllt = Leuchtaura + Haken; Soll-Symbole
     * immer sichtbar, empfangene Komponenten gefüllt, fehlende als Umriss (§13.3).
     */
    fun addCrystal(
        state: CrystalCellState,
        center: Offset,
    ) {
        val fulfilled = state.fill == CrystalFill.FULFILLED
        val beamColor = colors.beam(state.required)
        if (fulfilled) {
            overlay.circles += CircleSpec(center, cellSize * AURA_RADIUS, beamColor.copy(alpha = AURA_ALPHA))
        }
        val body = diamondPath(center, cellSize * CRYSTAL_RADIUS)
        overlay.paths += PathSpec(body, if (fulfilled) beamColor else colors.muted, Fill)
        overlay.paths += PathSpec(body, colors.element, elementStroke)
        addSymbols(
            center = offsetBy(center, 0f, CRYSTAL_SYMBOL_LIFT, cellSize),
            shown = state.required,
            filledMask = state.received?.bits ?: 0,
            size = cellSize * CRYSTAL_SYMBOL_SIZE,
            filledTint = if (fulfilled) colors.background else null,
        )
        addCrystalExtras(state, center)
    }

    /**
     * Komponenten-Symbolzeile §13.1 (▲●■): gezeigt werden die Komponenten von
     * [shown]; gefüllt, wenn die Komponente in [filledMask] steckt, sonst Umriss.
     */
    fun addSymbols(
        center: Offset,
        shown: LightColor,
        filledMask: Int,
        size: Float,
        filledTint: Color? = null,
    ) {
        val components = shown.components
        val spacing = size * SYMBOL_SPACING
        val startX = center.x - spacing * (components.size - 1) / 2f
        for (i in components.indices) {
            val component = components[i]
            val filled = filledMask and component.bits != 0
            val tint = if (filled) filledTint ?: colors.beam(component) else colors.element
            addSymbol(Offset(startX + i * spacing, center.y), component, size, filled, tint)
        }
    }

    private fun addCrystalExtras(
        state: CrystalCellState,
        center: Offset,
    ) {
        when (state.fill) {
            CrystalFill.FULFILLED -> {
                val start = offsetBy(center, CHECK_START_X, CHECK_START_Y, cellSize)
                val mid = offsetBy(center, CHECK_MID_X, CHECK_MID_Y, cellSize)
                val end = offsetBy(center, CHECK_END_X, CHECK_END_Y, cellSize)
                overlay.lines += LineSpec(start, mid, colors.background, lineWidth)
                overlay.lines += LineSpec(mid, end, colors.background, lineWidth)
            }
            CrystalFill.OVERSATURATED -> addForeignComponents(state, center)
            else -> Unit
        }
    }

    /** Übersättigt §12.3: Fremdkomponenten klein darunter, durchgestrichen. */
    private fun addForeignComponents(
        state: CrystalCellState,
        center: Offset,
    ) {
        val received = state.received ?: return
        val foreign = LightColor.of(received.bits and state.required.bits.inv()) ?: return
        val row = offsetBy(center, 0f, FOREIGN_ROW_Y, cellSize)
        addSymbols(row, foreign, foreign.bits, cellSize * FOREIGN_SYMBOL_SIZE)
        overlay.lines +=
            LineSpec(
                start = offsetBy(row, -STRIKE_HALF_WIDTH, 0f, cellSize),
                end = offsetBy(row, STRIKE_HALF_WIDTH, 0f, cellSize),
                color = colors.element,
                strokeWidth = lineWidth,
            )
    }

    /** Ein Formsymbol §13.1: Rot = ▲, Grün = ●, Blau = ■ — Form kodiert die Komponente. */
    private fun addSymbol(
        center: Offset,
        component: LightColor,
        size: Float,
        filled: Boolean,
        tint: Color,
    ) {
        val style = if (filled) Fill else symbolStroke
        when (component.bits) {
            LightColor.GREEN.bits -> overlay.circles += CircleSpec(center, size, tint, style)
            LightColor.BLUE.bits -> overlay.paths += PathSpec(squarePath(center, size), tint, style)
            else -> overlay.paths += PathSpec(trianglePath(center, size), tint, style)
        }
    }
}
