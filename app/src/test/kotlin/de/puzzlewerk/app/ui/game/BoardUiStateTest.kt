package de.puzzlewerk.app.ui.game

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.CrystalFill
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.TraceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Abbildung `:game` → [BoardUiState] (reiner JVM-Test, ADR-009/1). */
class BoardUiStateTest {
    private val crystalCell = HexCoord(1, -2)

    private fun stateWithReceived(received: LightColor?): BoardUiState {
        val board = Board(radius = 2, elements = mapOf(crystalCell to Element.Crystal(LightColor.YELLOW)))
        val receivedMap = if (received == null) emptyMap() else mapOf(crystalCell to received)
        return boardUiState(board, TraceResult(segments = emptyList(), received = receivedMap, solved = false))
    }

    private fun crystalOf(state: BoardUiState): CrystalCellState =
        checkNotNull(state.cells.single { it.coord == crystalCell }.crystal)

    @Test
    fun `enthaelt alle Zellen des Bretts inklusive leerer in Lesereihenfolge`() {
        val state = stateWithReceived(null)
        assertEquals(Board.cellCount(2), state.cells.size)
        assertEquals(HexGeometry.boardCells(2), state.cells.map { it.coord })
        assertNull(state.cells.first { it.coord == HexCoord(0, 0) }.element)
    }

    @Test
    fun `Kristall ohne Empfang ist dunkel`() {
        assertEquals(CrystalFill.DARK, crystalOf(stateWithReceived(null)).fill)
    }

    @Test
    fun `Kristall mit Teilmenge ist teilerfuellt`() {
        val crystal = crystalOf(stateWithReceived(LightColor.RED))
        assertEquals(CrystalFill.PARTIAL, crystal.fill)
        assertEquals(LightColor.RED, crystal.received)
    }

    @Test
    fun `Kristall mit exakter Sollfarbe ist erfuellt`() {
        assertEquals(CrystalFill.FULFILLED, crystalOf(stateWithReceived(LightColor.YELLOW)).fill)
    }

    @Test
    fun `Kristall mit Fremdkomponente ist uebersaettigt`() {
        assertEquals(CrystalFill.OVERSATURATED, crystalOf(stateWithReceived(LightColor.WHITE)).fill)
    }

    @Test
    fun `Nicht-Kristall-Zellen tragen keinen Kristallzustand`() {
        // Lokale Fixture (kein Sample-State): Quelle + Spiegel, keine Kristalle.
        val board =
            Board(
                radius = 2,
                elements =
                    mapOf(
                        HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                        HexCoord(0, 0) to Element.Mirror(Orientation(5)),
                    ),
            )
        val state = boardUiState(board, TraceResult(segments = emptyList(), received = emptyMap(), solved = false))
        assertNull(state.cells.first { it.coord == HexCoord(0, 0) }.crystal)
        assertNull(state.cells.first { it.coord == HexCoord(-2, 0) }.crystal)
    }
}
