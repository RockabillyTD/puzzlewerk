package de.puzzlewerk.game.engine

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.LevelDefinition

/**
 * Unveränderlicher Partie-Zustand (Design §16.1). Neue Zustände entstehen
 * ausschließlich über [GameEngine.applyMove] (`copy`-Semantik, Regel C1).
 *
 * @property level Die geladene, validierte Leveldefinition (Startzustand).
 * @property orientations Aktuelle Orientierung je drehbarer Zelle. Die
 *   Schlüsselmenge ist konstant und entspricht exakt den drehbaren Zellen des
 *   Levels; nach dem Laden identisch zu den Start-Orientierungen im Level-Brett.
 * @property history Gedrehte Zellen in Zugreihenfolge — Grundlage für Undo
 *   (§6.2). Es gilt immer `moveCount == history.size` (Invariante I6).
 * @property solved `true`, sobald das Brett gelöst ist (§5.4); die Partie nimmt
 *   dann keine Züge mehr an (§6.1, R32).
 */
public data class GameState(
    val level: LevelDefinition,
    val orientations: Map<HexCoord, Orientation>,
    val history: List<HexCoord>,
    val solved: Boolean,
) {
    /** Zugzähler == Verlaufslänge, nie negativ (Design §6.2, Invariante I6). */
    public val moveCount: Int get() = history.size

    /**
     * Effektives Brett: das Level-Startbrett mit den AKTUELLEN Orientierungen
     * aller drehbaren Elemente — die Eingabe für `trace` (Design §5, §6.4:
     * das Ergebnis hängt nur von den End-Orientierungen ab).
     */
    public fun currentBoard(): Board {
        val effective =
            level.board.elements.mapValues { (coord, element) ->
                val orientation = orientations[coord]
                if (element is Element.Rotatable && orientation != null) {
                    element.withOrientation(orientation)
                } else {
                    element
                }
            }
        return Board(radius = level.board.radius, elements = effective)
    }
}
