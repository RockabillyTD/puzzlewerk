package de.puzzlewerk.game.engine

import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.trace.Tracer

/**
 * Erzeugt die Standard-Implementierung des [GameEngine]-Vertrags (Design §6):
 * Rotate/Undo/Reset mit Gelöst-Sperre (§6.3), voller Undo-Rücknahme (§6.2,
 * Zähler −1 — keine versteckte Bestrafung) und den Ablehnungsgründen aus
 * R27/R28/R32 als Werte (C3).
 *
 * @param tracer Injizierte Strahlverfolgung (Regel C2). Je neuem Zustand wird
 *   das effektive Brett genau EINMAL berechnet und ausgewertet (§6.1, §12.3).
 */
public fun defaultGameEngine(tracer: Tracer): GameEngine = DefaultGameEngine(tracer)

private class DefaultGameEngine(
    private val tracer: Tracer,
) : GameEngine {
    override fun newGame(level: LevelDefinition): MoveResult.Applied {
        // R31: Ein bereits gelöst geladenes Level startet sofort als Gelöst mit 0 Zügen
        val trace = tracer.trace(level.board)
        val state =
            GameState(
                level = level,
                orientations = startOrientationsOf(level),
                history = emptyList(),
                solved = trace.solved,
            )
        return MoveResult.Applied(state, trace)
    }

    override fun applyMove(
        state: GameState,
        move: Move,
    ): MoveResult {
        // §6.3/R32: Gelöst sperrt Rotate, Undo UND Reset
        if (state.solved) return MoveResult.Invalid(InvalidMoveReason.ALREADY_SOLVED)
        return when (move) {
            is Move.Rotate -> rotate(state, move.cell)
            Move.Undo -> undo(state)
            Move.Reset -> reset(state)
        }
    }

    /** §6.1: `m → (m + 1) mod 6`, Zelle an den Verlauf, Zähler +1. */
    private fun rotate(
        state: GameState,
        cell: HexCoord,
    ): MoveResult {
        val current = state.orientations[cell] ?: return invalidRotation(state, cell)
        return applied(
            state.copy(
                orientations = state.orientations + (cell to current.next()),
                history = state.history + cell,
            ),
        )
    }

    /** R27: leere Zelle oder außerhalb des Bretts ⇒ NO_ELEMENT, sonst NOT_ROTATABLE. */
    private fun invalidRotation(
        state: GameState,
        cell: HexCoord,
    ): MoveResult.Invalid =
        if (state.level.board[cell] == null) {
            MoveResult.Invalid(InvalidMoveReason.NO_ELEMENT)
        } else {
            MoveResult.Invalid(InvalidMoveReason.NOT_ROTATABLE)
        }

    /** §6.2/I10: volle Rücknahme des letzten Zugs — `m → (m + 5) mod 6`, Zähler −1. */
    private fun undo(state: GameState): MoveResult {
        val cell = state.history.lastOrNull() ?: return MoveResult.Invalid(InvalidMoveReason.HISTORY_EMPTY) // R28
        return applied(
            state.copy(
                orientations = state.orientations + (cell to state.orientations.getValue(cell).previous()),
                history = state.history.dropLast(1),
            ),
        )
    }

    /** §6.2/R29: Startzustand des Levels, Verlauf leer — bei 0 Zügen ein gültiges No-op. */
    private fun reset(state: GameState): MoveResult =
        applied(state.copy(orientations = startOrientationsOf(state.level), history = emptyList()))

    /**
     * Wertet den neuen Zustand aus: effektives Brett und trace genau EINMAL je
     * Zug (Review-Hinweis NIT-3 zu `currentBoard()`), Gelöst-Wechsel nach §6.1.
     */
    private fun applied(state: GameState): MoveResult.Applied {
        val trace = tracer.trace(state.currentBoard())
        return MoveResult.Applied(state.copy(solved = trace.solved), trace)
    }

    /** Start-Orientierungen aller drehbaren Zellen aus dem Level-Brett (§16.1). */
    private fun startOrientationsOf(level: LevelDefinition): Map<HexCoord, Orientation> =
        level.board.elements.entries
            .mapNotNull { (cell, element) -> (element as? Element.Rotatable)?.let { cell to it.orientation } }
            .toMap()
}
