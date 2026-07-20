package de.puzzlewerk.app.ui.game

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.BeamEndpoint
import de.puzzlewerk.game.trace.Segment
import de.puzzlewerk.game.trace.TraceResult

/**
 * Handverlesene Fake-States für Previews und UI-Tests (PW-3.4). KEINE
 * Engine-/Tracer-Aufrufe in der UI (ui-architektur.md §4) — Segmente und
 * Empfangswerte sind aus dem durchgerechneten Design (§7.3) übernommen.
 */
internal object BoardSampleStates {
    private val exampleCrystals =
        mapOf(
            HexCoord(1, -2) to LightColor.RED,
            HexCoord(2, -2) to LightColor.GREEN,
            HexCoord(2, -1) to LightColor.BLUE,
        )

    /** Feste Elemente des Beispiel-Levels „Drei Farben" (§7.3, R = 2) ohne den Spiegel. */
    private val exampleElements: Map<HexCoord, Element> =
        mapOf<HexCoord, Element>(
            HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
            HexCoord(1, -1) to Element.Prism,
        ) + exampleCrystals.mapValues { (_, required) -> Element.Crystal(required) }

    /** §7.3 Startzustand (m = 5): Strahl läuft nach Südost ins Leere, alle Kristalle dunkel. */
    val exampleLevelStart: BoardUiState =
        boardUiState(
            board = exampleBoard(Orientation(5)),
            trace =
                TraceResult(
                    segments =
                        whiteSegments(
                            HexCoord(-2, 0) to HexCoord(-1, 0),
                            HexCoord(-1, 0) to HexCoord(0, 0),
                            HexCoord(0, 0) to HexCoord(0, 1),
                            HexCoord(0, 1) to HexCoord(0, 2),
                        ),
                    received = emptyMap(),
                    solved = false,
                    // Strahl läuft ins Brett-Aus ⇒ keine Endpunkte (§13.8a).
                    endpoints = emptyList(),
                ),
        )

    /** §7.3 nach Zug 2 (m = 1): Prisma zerlegt Weiß, alle drei Kristalle exakt erfüllt. */
    val exampleLevelSolved: BoardUiState =
        boardUiState(
            board = exampleBoard(Orientation(1)),
            trace =
                TraceResult(
                    segments =
                        whiteSegments(
                            HexCoord(-2, 0) to HexCoord(-1, 0),
                            HexCoord(-1, 0) to HexCoord(0, 0),
                            HexCoord(0, 0) to HexCoord(1, -1),
                        ) +
                            listOf(
                                Segment(HexCoord(1, -1), HexCoord(1, -2), LightColor.RED),
                                Segment(HexCoord(1, -1), HexCoord(2, -2), LightColor.GREEN),
                                Segment(HexCoord(1, -1), HexCoord(2, -1), LightColor.BLUE),
                            ),
                    received = exampleCrystals,
                    solved = true,
                    // §13.8a-Beispiel: genau drei Endpunkte an den Kristallen.
                    endpoints = exampleCrystals.map { (cell, color) -> BeamEndpoint(cell, color) },
                ),
        )

    /**
     * Schaufenster-Zustand: alle acht Elementtypen (§13.1, inkl. beider
     * Portal-Paare A und B) plus die vier Kristallzustände (§12.3) und ein
     * Mischfarben-Strahl mit Symbol-Chips (§13.2). Physikalisch NICHT
     * konsistent — reiner Render-Zustand.
     */
    val elementZoo: BoardUiState =
        boardUiState(
            board =
                Board(
                    radius = 2,
                    elements =
                        mapOf(
                            HexCoord(-2, 0) to Element.Source(LightColor.YELLOW, Direction.EAST),
                            HexCoord(0, 0) to Element.Splitter(Orientation(2)),
                            HexCoord(-2, 1) to Element.Mirror(Orientation(0)),
                            HexCoord(0, -2) to Element.Prism,
                            HexCoord(-1, -1) to Element.Filter(LightColor.RED),
                            HexCoord(-2, 2) to Element.Portal(0),
                            HexCoord(2, 0) to Element.Portal(0),
                            HexCoord(1, -2) to Element.Portal(1),
                            HexCoord(-1, 2) to Element.Portal(1),
                            HexCoord(-1, 1) to Element.Wall,
                            HexCoord(2, -2) to Element.Crystal(LightColor.WHITE),
                            HexCoord(2, -1) to Element.Crystal(LightColor.YELLOW),
                            HexCoord(1, 1) to Element.Crystal(LightColor.RED),
                            HexCoord(0, 2) to Element.Crystal(LightColor.GREEN),
                        ),
                ),
            trace =
                TraceResult(
                    segments =
                        listOf(
                            Segment(HexCoord(-2, 0), HexCoord(-1, 0), LightColor.YELLOW),
                            Segment(HexCoord(-1, 0), HexCoord(0, 0), LightColor.YELLOW),
                            Segment(HexCoord(0, 0), HexCoord(1, 0), LightColor.YELLOW),
                            Segment(HexCoord(1, 0), HexCoord(2, -1), LightColor.RED),
                            Segment(HexCoord(0, 0), HexCoord(0, 1), LightColor.GREEN),
                            Segment(HexCoord(0, 1), HexCoord(0, 2), LightColor.BLUE),
                            Segment(HexCoord(1, 0), HexCoord(1, 1), LightColor.RED),
                        ),
                    received =
                        mapOf(
                            HexCoord(2, -1) to LightColor.RED,
                            HexCoord(1, 1) to LightColor.RED,
                            HexCoord(0, 2) to LightColor.YELLOW,
                        ),
                    solved = false,
                    // Physikalisch inkonsistenter Schaufenster-Zustand: bewusst ohne Endpunkte.
                    endpoints = emptyList(),
                ),
        )

    private fun exampleBoard(mirror: Orientation): Board =
        Board(
            radius = 2,
            elements = exampleElements + (HexCoord(0, 0) to Element.Mirror(mirror)),
        )

    private fun whiteSegments(vararg edges: Pair<HexCoord, HexCoord>): List<Segment> =
        edges.map { (from, to) -> Segment(from, to, LightColor.WHITE) }
}
