package de.puzzlewerk.app.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.CrystalFill
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.Segment
import kotlin.math.cos
import kotlin.math.sin

// Alle Maße relativ zur Zellgröße (dp-unabhängig; Faktoren statt Pixel).
private const val CELL_OUTLINE_SCALE = 0.96f
private const val WALL_SCALE = 0.92f
private const val OUTLINE_WIDTH = 0.05f
private const val ELEMENT_STROKE_WIDTH = 0.07f
private const val ELEMENT_LINE_WIDTH = 0.09f
private const val MIRROR_LINE_WIDTH = 0.14f
private const val MIRROR_HALF_LENGTH = 0.62f
private const val SOCKET_RADIUS = 0.82f
private const val SOCKET_WIDTH = 0.07f
private const val BEAM_WIDTH = 0.13f
private const val SYMBOL_STROKE_WIDTH = 0.035f
private const val SYMBOL_SIZE = 0.15f
private const val SYMBOL_SPACING = 2.4f
private const val SOURCE_RADIUS = 0.52f
private const val SOURCE_ARROW_LENGTH = 0.95f
private const val PRISM_RADIUS = 0.58f
private const val FILTER_RADIUS = 0.55f
private const val PORTAL_OUTER_RADIUS = 0.55f
private const val PORTAL_INNER_RADIUS = 0.34f
private const val PORTAL_TICK_HALF = 0.11f
private const val PORTAL_TICK_SPACING = 0.2f
private const val CRYSTAL_RADIUS = 0.62f
private const val CRYSTAL_SYMBOL_LIFT = -0.2f
private const val CRYSTAL_SYMBOL_SIZE = 0.13f
private const val AURA_RADIUS = 0.95f
private const val AURA_ALPHA = 0.3f
private const val CHIP_RADIUS = 0.24f
private const val CHIP_SYMBOL_SIZE = 0.08f
private const val CHECK_START_X = -0.2f
private const val CHECK_START_Y = 0.26f
private const val CHECK_MID_X = -0.07f
private const val CHECK_MID_Y = 0.4f
private const val CHECK_END_X = 0.2f
private const val CHECK_END_Y = 0.14f
private const val FOREIGN_ROW_Y = 0.34f
private const val FOREIGN_SYMBOL_SIZE = 0.1f
private const val STRIKE_HALF_WIDTH = 0.3f

// Strahlmuster §13.2 (relativ zur Zellgröße): Rot = Strich, Grün = Punkt,
// Blau = Strich-Punkt; Mischfarben durchgezogen (Chips siehe addChip).
private val RED_DASH = floatArrayOf(0.42f, 0.28f)
private val GREEN_DOT = floatArrayOf(0.02f, 0.3f)
private val BLUE_DASH_DOT = floatArrayOf(0.4f, 0.24f, 0.02f, 0.24f)
private val SPLITTER_DASH = floatArrayOf(0.18f, 0.14f)

/** Skaliert ein Strichmuster (Zellgrößen-Faktoren) auf Pixel. */
private fun scaled(
    pattern: FloatArray,
    cellSize: Float,
): FloatArray = FloatArray(pattern.size) { pattern[it] * cellSize }

/** Übersetzt den [BoardUiState] EINMAL je State/Geometrie in Zeichenlisten. */
internal fun buildBoardRenderSpec(
    state: BoardUiState,
    geometry: BoardGeometry,
    colors: BoardColors,
): BoardRenderSpec {
    val builder = BoardSpecBuilder(geometry, colors)
    for (cell in state.cells) {
        builder.addCell(cell)
    }
    for (segment in state.beams) {
        builder.addBeam(segment)
    }
    return builder.build()
}

/** Baut die drei Zeichenschichten; lebt nur für einen [buildBoardRenderSpec]-Lauf. */
private class BoardSpecBuilder(
    private val geometry: BoardGeometry,
    private val colors: BoardColors,
) {
    private val cellSize = geometry.cellSize
    private val overlay = DrawOpsBuilder()
    private val beams = mutableListOf<LineSpec>()
    private val gridPath = Path()
    private val wallPath = Path()
    private var mixedSegments = 0

    private val outlineStroke = Stroke(width = cellSize * OUTLINE_WIDTH)
    private val elementStroke = Stroke(width = cellSize * ELEMENT_STROKE_WIDTH)
    private val socketStroke = Stroke(width = cellSize * SOCKET_WIDTH)
    private val symbolStroke = Stroke(width = cellSize * SYMBOL_STROKE_WIDTH)
    private val dashEffect = PathEffect.dashPathEffect(scaled(RED_DASH, cellSize))
    private val dotEffect = PathEffect.dashPathEffect(scaled(GREEN_DOT, cellSize))
    private val dashDotEffect = PathEffect.dashPathEffect(scaled(BLUE_DASH_DOT, cellSize))
    private val splitterEffect = PathEffect.dashPathEffect(scaled(SPLITTER_DASH, cellSize))

    fun addCell(cell: BoardCell) {
        val center = geometry.center(cell.coord)
        addHexagonTo(gridPath, center, cellSize * CELL_OUTLINE_SCALE)
        val element = cell.element
        if (element != null) {
            addElement(element, cell.crystal, center)
        }
    }

    private fun addElement(
        element: Element,
        crystal: CrystalCellState?,
        center: Offset,
    ) {
        when (element) {
            is Element.Wall -> addHexagonTo(wallPath, center, cellSize * WALL_SCALE)
            is Element.Source -> addSource(element, center)
            is Element.Rotatable -> addRotatable(element.orientation, element is Element.Splitter, center)
            is Element.Prism ->
                overlay.paths += PathSpec(trianglePath(center, cellSize * PRISM_RADIUS), colors.element, elementStroke)
            is Element.Filter -> addFilter(element, center)
            is Element.Portal -> addPortal(element, center)
            is Element.Crystal -> crystal?.let { addCrystal(it, center) }
        }
    }

    /** Strahl-Segment §13.2: Farbe plus Linienmuster; Mischfarben durchgezogen mit Chips. */
    fun addBeam(segment: Segment) {
        val start = geometry.center(segment.from)
        val end = geometry.center(segment.to)
        val effect =
            when (segment.color.bits) {
                LightColor.RED.bits -> dashEffect
                LightColor.GREEN.bits -> dotEffect
                LightColor.BLUE.bits -> dashDotEffect
                else -> null
            }
        beams += LineSpec(start, end, colors.beam(segment.color), cellSize * BEAM_WIDTH, effect)
        if (effect == null) {
            addChip(segment.color, start, end)
        }
    }

    fun build(): BoardRenderSpec {
        val base =
            DrawOps(
                paths =
                    listOf(
                        PathSpec(wallPath, colors.muted, Fill),
                        PathSpec(gridPath, colors.outline, outlineStroke),
                    ),
                circles = emptyList(),
                lines = emptyList(),
            )
        return BoardRenderSpec(base = base, beams = beams.toList(), overlay = overlay.build())
    }

    /** Symbol-Chip „etwa alle 2 Zellen" (§13.2): deterministisch jedes zweite Mischfarben-Segment. */
    private fun addChip(
        color: LightColor,
        start: Offset,
        end: Offset,
    ) {
        mixedSegments += 1
        if (mixedSegments % 2 != 0) return
        val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
        overlay.circles += CircleSpec(mid, cellSize * CHIP_RADIUS, colors.background)
        overlay.circles += CircleSpec(mid, cellSize * CHIP_RADIUS, colors.beam(color), symbolStroke)
        addSymbols(mid, color, color.bits, cellSize * CHIP_SYMBOL_SIZE)
    }

    /** Quelle §4.1: eingelassenes Gehäuse, Emissionspfeil, Farbsymbole (§13.1). */
    private fun addSource(
        element: Element.Source,
        center: Offset,
    ) {
        overlay.circles += CircleSpec(center, cellSize * SOURCE_RADIUS, colors.muted)
        overlay.circles += CircleSpec(center, cellSize * SOURCE_RADIUS, colors.element, elementStroke)
        overlay.lines +=
            LineSpec(
                start = rayPoint(center, element.direction, cellSize * SOURCE_RADIUS),
                end = rayPoint(center, element.direction, cellSize * SOURCE_ARROW_LENGTH),
                color = colors.beam(element.color),
                strokeWidth = cellSize * BEAM_WIDTH,
            )
        addSymbols(center, element.color, element.color.bits, cellSize * SYMBOL_SIZE)
    }

    /**
     * Drehbares Element (§12.3: heller Sockelring als Drehbarkeits-Markierung).
     * Spiegel-/Splitterstrich auf der Geraden `m · 30°` (§4.2); Bildschirm-y
     * nach unten ⇒ y negiert. Splitter halbdurchlässig ⇒ gestrichelt.
     */
    private fun addRotatable(
        orientation: Orientation,
        dashed: Boolean,
        center: Offset,
    ) {
        overlay.circles += CircleSpec(center, cellSize * SOCKET_RADIUS, colors.socket, socketStroke)
        val angle = orientation.steps * ORIENTATION_STEP_RAD
        val dx = cos(angle) * cellSize * MIRROR_HALF_LENGTH
        val dy = -sin(angle) * cellSize * MIRROR_HALF_LENGTH
        overlay.lines +=
            LineSpec(
                start = Offset(center.x - dx, center.y - dy),
                end = Offset(center.x + dx, center.y + dy),
                color = colors.element,
                strokeWidth = cellSize * MIRROR_LINE_WIDTH,
                pathEffect = if (dashed) splitterEffect else null,
            )
    }

    /** Filter §4.5: Ring in Filterfarbe plus Farbsymbol (§13.1). */
    private fun addFilter(
        element: Element.Filter,
        center: Offset,
    ) {
        overlay.circles += CircleSpec(center, cellSize * FILTER_RADIUS, colors.beam(element.color), elementStroke)
        addSymbols(center, element.color, element.color.bits, cellSize * SYMBOL_SIZE)
    }

    /** Portal §4.6: Doppelring; Paar-ID als 1 bzw. 2 Markierungsstriche (nie nur Farbe). */
    private fun addPortal(
        element: Element.Portal,
        center: Offset,
    ) {
        overlay.circles += CircleSpec(center, cellSize * PORTAL_OUTER_RADIUS, colors.element, elementStroke)
        overlay.circles += CircleSpec(center, cellSize * PORTAL_INNER_RADIUS, colors.socket, symbolStroke)
        for (tick in 0..element.pairId) {
            val x = center.x + (tick - element.pairId / 2f) * cellSize * PORTAL_TICK_SPACING
            overlay.lines +=
                LineSpec(
                    start = Offset(x, center.y - cellSize * PORTAL_TICK_HALF),
                    end = Offset(x, center.y + cellSize * PORTAL_TICK_HALF),
                    color = colors.element,
                    strokeWidth = cellSize * ELEMENT_LINE_WIDTH,
                )
        }
    }

    /**
     * Kristall §4.7/§12.3: Raute; erfüllt = Leuchtaura + Haken; Soll-Symbole
     * immer sichtbar, empfangene Komponenten gefüllt, fehlende als Umriss (§13.3).
     */
    private fun addCrystal(
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

    private fun addCrystalExtras(
        state: CrystalCellState,
        center: Offset,
    ) {
        when (state.fill) {
            CrystalFill.FULFILLED -> {
                val start = offsetBy(center, CHECK_START_X, CHECK_START_Y, cellSize)
                val mid = offsetBy(center, CHECK_MID_X, CHECK_MID_Y, cellSize)
                val end = offsetBy(center, CHECK_END_X, CHECK_END_Y, cellSize)
                val width = cellSize * ELEMENT_LINE_WIDTH
                overlay.lines += LineSpec(start, mid, colors.background, width)
                overlay.lines += LineSpec(mid, end, colors.background, width)
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
                strokeWidth = cellSize * ELEMENT_LINE_WIDTH,
            )
    }

    /**
     * Komponenten-Symbolzeile §13.1 (▲●■): gezeigt werden die Komponenten von
     * [shown]; gefüllt, wenn die Komponente in [filledMask] steckt, sonst Umriss.
     */
    private fun addSymbols(
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
