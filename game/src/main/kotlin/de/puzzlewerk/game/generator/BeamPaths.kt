package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.Segment
import de.puzzlewerk.game.trace.TraceResult

// Trace-Geometrie der Konstruktion (§9.3): alle Abfragen liefern nach (q, r)
// sortierte Listen — feste Iterationsordnungen sind Pflicht fuer I2 (§8).

private val byCoord = compareBy<HexCoord>({ it.q }, { it.r })

/** Alle Zellen eines Bretts mit [radius], aufsteigend nach (q, dann r) (§2.1). */
internal fun boardCells(radius: Int): List<HexCoord> =
    (-radius..radius).flatMap { q ->
        (-radius..radius).map { r -> HexCoord(q, r) }
    }.filter { it.isWithinRadius(radius) }

/** Randring-Zellen `ringIndex == radius` — Quellplaetze (§9.3 Schritt 2). */
internal fun ringCells(radius: Int): List<HexCoord> = boardCells(radius).filter { it.ringIndex == radius }

/** Laufrichtung eines Segments; Segmente verbinden immer Nachbarzellen (§5.1). */
internal fun directionOf(segment: Segment): Direction =
    Direction.entries.first { segment.from.neighbor(it) == segment.to }

/** Freie Zellen, durch die aktuell mindestens ein Strahl laeuft (§9.3 Schritt 3). */
internal fun transitCells(
    trace: TraceResult,
    elements: Map<HexCoord, Element>,
): List<HexCoord> =
    trace.segments
        .map { it.to }
        .distinct()
        .filter { it !in elements }
        .sortedWith(byCoord)

/**
 * Enden offener Strahlwege: letzte FREIE Zelle, bevor der Strahl vom Rand,
 * einer Wand oder einem Quellgehaeuse (R01) absorbiert wird (§9.3 Schritt 4).
 */
internal fun terminalCells(
    trace: TraceResult,
    elements: Map<HexCoord, Element>,
    radius: Int,
): List<HexCoord> =
    trace.segments
        .filter { it.to !in elements && absorbsBeam(it.to.neighbor(directionOf(it)), elements, radius) }
        .map { it.to }
        .distinct()
        .sortedWith(byCoord)

private fun absorbsBeam(
    cell: HexCoord,
    elements: Map<HexCoord, Element>,
    radius: Int,
): Boolean = !cell.isWithinRadius(radius) || elements[cell] is Element.Wall || elements[cell] is Element.Source
