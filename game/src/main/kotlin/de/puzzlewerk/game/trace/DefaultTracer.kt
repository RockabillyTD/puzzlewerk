package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element

/**
 * Referenz-Implementierung des [Tracer]-Vertrags nach dem normativen Algorithmus
 * aus Design §5.2/§5.3: BFS über Strahlzustände (Zelle, Richtung, Farbe) mit
 * visited-Set inklusive Farbe (R18), Quellenstart aufsteigend nach (q, dann r),
 * Splitter-Reihenfolge Transmission→Reflexion und Prisma-Reihenfolge R→G→B.
 *
 * Deterministisch ohne Zufall und ohne Seiteneffekte (C2); Terminierung über die
 * endliche Zustandsmenge (§5.3, Invariante I1).
 */
public object DefaultTracer : Tracer {
    override fun trace(board: Board): TraceResult = TraceRun(board).execute()
}

/** Strahlzustand (§5.1): ein Strahl verlässt [cell] in [direction] mit [color]. */
private data class BeamState(
    val cell: HexCoord,
    val direction: Direction,
    val color: LightColor,
)

/** Ein einzelner trace-Durchlauf; kapselt Queue, visited-Set und Akkumulatoren. */
private class TraceRun(
    private val board: Board,
) {
    private val queue = ArrayDeque<BeamState>()
    private val visited = HashSet<BeamState>()
    private val segments = mutableListOf<Segment>()
    private val received = mutableMapOf<HexCoord, LightColor>()
    private val endpoints = mutableListOf<BeamEndpoint>()
    private var steps = 0

    fun execute(): TraceResult {
        enqueueSources()
        while (queue.isNotEmpty()) {
            val beam = queue.removeFirst()
            if (!visited.add(beam)) continue
            steps += 1
            // §5.3: Diese Grenze ist per Konstruktion unerreichbar — Verletzung = Bug (C3)
            require(steps <= MAX_TRACE_STEPS) {
                "trace hat $MAX_TRACE_STEPS verarbeitete Zustände überschritten — Programmierfehler (§5.3)"
            }
            advance(beam)
        }
        return TraceResult(segments.toList(), received.toMap(), isSolved(), endpoints.toList())
    }

    /** Quellen in deterministischer Reihenfolge aufsteigend nach (q, dann r) (§5.2). */
    private fun enqueueSources() {
        board.elements.entries
            .mapNotNull { (cell, element) -> (element as? Element.Source)?.let { cell to it } }
            .sortedWith(compareBy({ it.first.q }, { it.first.r }))
            .forEach { (cell, source) -> queue.add(BeamState(cell, source.direction, source.color)) }
    }

    private fun advance(beam: BeamState) {
        val next = beam.cell.neighbor(beam.direction)
        if (next !in board) return // Brettrand absorbiert (§2.3)
        segments += Segment(from = beam.cell, to = next, color = beam.color)
        interact(next, beam)
    }

    private fun interact(
        next: HexCoord,
        beam: BeamState,
    ) {
        when (val element = board[next]) {
            null -> queue.add(BeamState(next, beam.direction, beam.color))
            is Element.Wall -> absorb(next, beam.color) // absorbiert (§4.8)
            is Element.Source -> absorb(next, beam.color) // Gehäuse ist opak (R01)
            is Element.Crystal -> collectAtCrystal(next, beam.color)
            is Element.Rotatable -> enqueueReflection(next, element, beam)
            is Element.Prism -> enqueueDispersion(next, beam)
            is Element.Filter -> enqueueFiltered(next, element.color, beam)
            is Element.Portal -> queue.add(BeamState(twinOf(next, element), beam.direction, beam.color))
        }
    }

    /**
     * On-Board-Absorption: registriert den Strahl-Endpunkt (§13.8a, ADR-012).
     * Brett-Aus-Absorptionen laufen NICHT hierüber (siehe [advance]) und
     * erzeugen bewusst keinen Eintrag.
     */
    private fun absorb(
        cell: HexCoord,
        color: LightColor,
    ) {
        endpoints += BeamEndpoint(cell, color)
    }

    /** Kristall sammelt per OR und absorbiert den Strahl (§4.7, Endpunkt §13.8a). */
    private fun collectAtCrystal(
        cell: HexCoord,
        color: LightColor,
    ) {
        received[cell] = received[cell]?.mixedWith(color) ?: color
        absorb(cell, color)
    }

    /** Spiegel §4.2 und Splitter §4.3: `d_out = (m − d_in) mod 6`, Parallelfall ohne Kopie (R05). */
    private fun enqueueReflection(
        cell: HexCoord,
        element: Element.Rotatable,
        beam: BeamState,
    ) {
        val reflected = reflect(element.orientation, beam.direction)
        when (element) {
            is Element.Mirror -> queue.add(BeamState(cell, reflected, beam.color))
            is Element.Splitter -> {
                queue.add(BeamState(cell, beam.direction, beam.color)) // 1) Transmission
                if (reflected != beam.direction) {
                    queue.add(BeamState(cell, reflected, beam.color)) // 2) Reflexionskopie
                }
            }
        }
    }

    /** Prisma §4.4: Rot +60°, Grün geradeaus, Blau −60° — normative Reihenfolge R, G, B (§5.2). */
    private fun enqueueDispersion(
        cell: HexCoord,
        beam: BeamState,
    ) {
        for (component in beam.color.components) {
            val outDirection =
                when (component) {
                    LightColor.RED -> beam.direction.rotatedBy(1)
                    LightColor.BLUE -> beam.direction.rotatedBy(-1)
                    else -> beam.direction
                }
            queue.add(BeamState(cell, outDirection, component))
        }
    }

    /** Filter §4.5: `c ∧ f`; leerer Schnitt (R11) absorbiert mit Endpunkt in Auftreff-Farbe (§13.8a). */
    private fun enqueueFiltered(
        cell: HexCoord,
        filter: LightColor,
        beam: BeamState,
    ) {
        val filtered = beam.color.filteredBy(filter)
        if (filtered == null) {
            absorb(cell, beam.color)
        } else {
            queue.add(BeamState(cell, beam.direction, filtered))
        }
    }

    /** Zwillingszelle des Portals (§4.6); Existenz garantiert die Vorbedingung §16.2/3. */
    private fun twinOf(
        cell: HexCoord,
        portal: Element.Portal,
    ): HexCoord =
        board.elements.entries
            .first { (candidate, element) ->
                candidate != cell && element is Element.Portal && element.pairId == portal.pairId
            }.key

    /** Lösungsbedingung §5.4: JEDER Kristall empfängt exakt seine Sollfarbe. */
    private fun isSolved(): Boolean =
        board.elements.all { (cell, element) ->
            element !is Element.Crystal || received[cell] == element.required
        }

    private fun reflect(
        orientation: Orientation,
        incoming: Direction,
    ): Direction = Direction.fromIndex((orientation.steps - incoming.index).mod(Direction.COUNT))
}
