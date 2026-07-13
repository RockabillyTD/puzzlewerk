package de.puzzlewerk.app.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import de.puzzlewerk.game.board.HexCoord
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
private const val SOURCE_RADIUS = 0.52f
private const val SOURCE_ARROW_LENGTH = 0.95f
private const val PRISM_RADIUS = 0.58f
private const val FILTER_RADIUS = 0.55f
private const val PORTAL_OUTER_RADIUS = 0.55f
private const val PORTAL_INNER_RADIUS = 0.34f
private const val PORTAL_TICK_HALF = 0.11f
private const val PORTAL_TICK_SPACING = 0.2f
private const val CHIP_RADIUS = 0.24f
private const val CHIP_SYMBOL_SIZE = 0.08f

// Strahlmuster §13.2 (relativ zur Zellgröße): Rot = Strich, Grün = Punkt,
// Blau = Strich-Punkt; Mischfarben durchgezogen (Chips siehe addMixedChips).
private val RED_DASH = floatArrayOf(0.42f, 0.28f)
private val GREEN_DOT = floatArrayOf(0.02f, 0.3f)
private val BLUE_DASH_DOT = floatArrayOf(0.4f, 0.24f, 0.02f, 0.24f)
private val SPLITTER_DASH = floatArrayOf(0.18f, 0.14f)

/** Skaliert ein Strichmuster (Zellgrößen-Faktoren) auf Pixel. */
private fun scaled(
    pattern: FloatArray,
    cellSize: Float,
): FloatArray = FloatArray(pattern.size) { pattern[it] * cellSize }

/** Mischfarben-Segment, vorgemerkt für die Chip-Vergabe (§13.2). */
private class MixedChipSegment(
    val color: LightColor,
    val from: HexCoord,
    val to: HexCoord,
    val mid: Offset,
)

/**
 * Übersetzt den [BoardUiState] EINMAL je (State, Geometrie, [spin]) in
 * Zeichenlisten. [spin] ist eine laufende Dreh-Animation (§12.3) oder `null`;
 * es versetzt nur das eine drehbare Element optisch, ändert sonst nichts.
 */
internal fun buildBoardRenderSpec(
    state: BoardUiState,
    geometry: BoardGeometry,
    colors: BoardColors,
    spin: BoardSpin? = null,
): BoardRenderSpec {
    val builder = BoardSpecBuilder(geometry, colors, spin)
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
    private val spin: BoardSpin?,
) {
    private val cellSize = geometry.cellSize
    private val overlay = DrawOpsBuilder()
    private val beams = mutableListOf<LineSpec>()
    private val gridPath = Path()
    private val wallPath = Path()
    private val mixedSegments = mutableListOf<MixedChipSegment>()

    private val outlineStroke = Stroke(width = cellSize * OUTLINE_WIDTH)
    private val elementStroke = Stroke(width = cellSize * ELEMENT_STROKE_WIDTH)
    private val socketStroke = Stroke(width = cellSize * SOCKET_WIDTH)
    private val symbolStroke = Stroke(width = cellSize * SYMBOL_STROKE_WIDTH)
    private val dashEffect = PathEffect.dashPathEffect(scaled(RED_DASH, cellSize))
    private val dotEffect = PathEffect.dashPathEffect(scaled(GREEN_DOT, cellSize))
    private val dashDotEffect = PathEffect.dashPathEffect(scaled(BLUE_DASH_DOT, cellSize))
    private val splitterEffect = PathEffect.dashPathEffect(scaled(SPLITTER_DASH, cellSize))

    private val crystals =
        BoardCrystalRender(
            overlay = overlay,
            colors = colors,
            cellSize = cellSize,
            symbolStroke = symbolStroke,
            elementStroke = elementStroke,
            lineWidth = cellSize * ELEMENT_LINE_WIDTH,
        )

    fun addCell(cell: BoardCell) {
        val center = geometry.center(cell.coord)
        addHexagonTo(gridPath, center, cellSize * CELL_OUTLINE_SCALE)
        val element = cell.element
        if (element != null) {
            addElement(element, cell.crystal, center, cell.coord)
        }
    }

    private fun addElement(
        element: Element,
        crystal: CrystalCellState?,
        center: Offset,
        coord: HexCoord,
    ) {
        when (element) {
            is Element.Wall -> addHexagonTo(wallPath, center, cellSize * WALL_SCALE)
            is Element.Source -> addSource(element, center)
            is Element.Rotatable -> addRotatable(element.orientation, element is Element.Splitter, center, coord)
            is Element.Prism ->
                overlay.paths += PathSpec(trianglePath(center, cellSize * PRISM_RADIUS), colors.element, elementStroke)
            is Element.Filter -> addFilter(element, center)
            is Element.Portal -> addPortal(element, center)
            // DARK-Fallback konsistent zur TalkBack-Beschreibung (BoardCellDescriptions):
            // auch ein handgebauter BoardCell ohne CrystalCellState rendert einen dunklen Kristall.
            is Element.Crystal ->
                crystals.addCrystal(crystal ?: CrystalCellState(element.required, null, CrystalFill.DARK), center)
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
            val mid = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
            mixedSegments += MixedChipSegment(segment.color, segment.from, segment.to, mid)
        }
    }

    fun build(): BoardRenderSpec {
        addMixedChips()
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

    /**
     * Symbol-Chips „etwa alle 2 Zellen" auf Mischfarben-Strahlen (§13.2) —
     * ORDNUNGSUNABHÄNGIG: Segmente werden je Farbe über gemeinsame Endpunkte
     * zu zusammenhängenden Strahlzügen gruppiert (Union-Find); jedes zweite
     * Segment eines Zugs — beginnend mit dem ersten — erhält einen Chip.
     * Damit trägt JEDER zusammenhängende Mischfarben-Strahl mindestens einen
     * Chip, auch bei BFS-verzahnter Segmentreihenfolge des Tracers (§5.2)
     * und bei Ein-Segment-Strahlen (§13-Grundregel: Farbe nie einziger Kanal).
     */
    private fun addMixedChips() {
        val parent = HashMap<Pair<Int, HexCoord>, Pair<Int, HexCoord>>()
        for (segment in mixedSegments) {
            val fromRoot = chipRoot(parent, segment.color.bits to segment.from)
            val toRoot = chipRoot(parent, segment.color.bits to segment.to)
            parent[fromRoot] = toRoot
        }
        val segmentIndexInBeam = HashMap<Pair<Int, HexCoord>, Int>()
        for (segment in mixedSegments) {
            val root = chipRoot(parent, segment.color.bits to segment.from)
            val index = segmentIndexInBeam.getOrDefault(root, 0)
            segmentIndexInBeam[root] = index + 1
            if (index % 2 == 0) {
                addChipAt(segment.mid, segment.color)
            }
        }
    }

    /** Union-Find-Wurzel des Endpunkt-Schlüssels (Farbe, Zelle). */
    private fun chipRoot(
        parent: Map<Pair<Int, HexCoord>, Pair<Int, HexCoord>>,
        key: Pair<Int, HexCoord>,
    ): Pair<Int, HexCoord> {
        var current = key
        var next = parent[current]
        while (next != null && next != current) {
            current = next
            next = parent[current]
        }
        return current
    }

    private fun addChipAt(
        mid: Offset,
        color: LightColor,
    ) {
        overlay.circles += CircleSpec(mid, cellSize * CHIP_RADIUS, colors.background)
        overlay.circles += CircleSpec(mid, cellSize * CHIP_RADIUS, colors.beam(color), symbolStroke)
        crystals.addSymbols(mid, color, color.bits, cellSize * CHIP_SYMBOL_SIZE)
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
        crystals.addSymbols(center, element.color, element.color.bits, cellSize * SYMBOL_SIZE)
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
        coord: HexCoord,
    ) {
        overlay.circles += CircleSpec(center, cellSize * SOCKET_RADIUS, colors.socket, socketStroke)
        // Dreh-Animation (§12.3): der Sockelring bleibt fix, nur der Strich läuft
        // von der Start- zur Zielstellung nach (angleOffsetRad ⇒ 0 am Ende).
        val spinOffset = if (spin?.cell == coord) spin.angleOffsetRad else 0f
        val angle = orientation.steps * ORIENTATION_STEP_RAD + spinOffset
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
        crystals.addSymbols(center, element.color, element.color.bits, cellSize * SYMBOL_SIZE)
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
}
