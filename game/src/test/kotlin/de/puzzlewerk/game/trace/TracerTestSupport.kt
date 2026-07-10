package de.puzzlewerk.game.trace

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element

/** Kurzform fuer Brett-Aufbau in Tracer-Tests. */
fun boardOf(
    radius: Int,
    vararg elements: Pair<HexCoord, Element>,
): Board = Board(radius, elements.toMap())

/** Kurzform fuer einen Trace mit der Referenz-Implementierung. */
fun trace(board: Board): TraceResult = DefaultTracer.trace(board)

/** Kurzform fuer ein Segment zwischen zwei Zellen. */
fun seg(
    fromQ: Int,
    fromR: Int,
    toQ: Int,
    toR: Int,
    color: LightColor,
): Segment = Segment(HexCoord(fromQ, fromR), HexCoord(toQ, toR), color)

/** Alle Zellen eines Bretts mit [radius] (Design Paragraf 2.1). */
fun allCells(radius: Int): List<HexCoord> =
    (-radius..radius).flatMap { q ->
        (-radius..radius).map { r -> HexCoord(q, r) }
    }.filter { it.isWithinRadius(radius) }
