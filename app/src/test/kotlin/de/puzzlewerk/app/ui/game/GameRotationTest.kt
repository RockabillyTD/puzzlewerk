package de.puzzlewerk.app.ui.game

import de.puzzlewerk.game.board.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reine JVM-Tests der Dreh-Erkennung (PW-3.5b, ohne Robolectric): die
 * Puffer-/Auswahllogik der Dreh-Animation ist von der Optik getrennt testbar
 * (Ticket: „testbar über die Buffer-Logik"). Ein ungültiger Tap (keine
 * Orientierungsänderung) und ein Reset (mehrere Änderungen) lösen KEINE
 * Einzel-Animation aus.
 */
class GameRotationTest {
    @Test
    fun `rotatableOrientations liest nur drehbare Zellen`() {
        val orientations = rotatableOrientations(BoardSampleStates.elementZoo)

        assertEquals(2, orientations[HexCoord(0, 0)]) // Splitter, Orientierung 2
        assertEquals(0, orientations[HexCoord(-2, 1)]) // Spiegel, Orientierung 0
        assertNull(orientations[HexCoord(-1, 1)]) // Wand ist nicht drehbar
    }

    @Test
    fun `singleRotatedCell erkennt genau eine Aenderung`() {
        val previous = mapOf(HexCoord(0, 0) to 0, HexCoord(1, 0) to 3)
        val current = mapOf(HexCoord(0, 0) to 1, HexCoord(1, 0) to 3)

        assertEquals(HexCoord(0, 0), singleRotatedCell(previous, current))
    }

    @Test
    fun `singleRotatedCell ignoriert einen wirkungslosen Tap`() {
        val same = mapOf(HexCoord(0, 0) to 0)

        assertNull(singleRotatedCell(same, same))
    }

    @Test
    fun `singleRotatedCell ignoriert einen Reset mit mehreren Aenderungen`() {
        val previous = mapOf(HexCoord(0, 0) to 1, HexCoord(1, 0) to 2)
        val current = mapOf(HexCoord(0, 0) to 0, HexCoord(1, 0) to 0)

        assertNull(singleRotatedCell(previous, current))
    }
}
