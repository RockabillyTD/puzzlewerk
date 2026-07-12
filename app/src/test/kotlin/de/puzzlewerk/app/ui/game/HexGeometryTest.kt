package de.puzzlewerk.app.ui.game

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Geometrie-Roundtrip Pixel ↔ Axial (§2.4) als reiner JVM-Test — kein
 * Robolectric nötig, [HexGeometry] ist bewusst Compose-frei (ADR-009/1).
 */
class HexGeometryTest {
    private val cellSizes = floatArrayOf(10f, 24f, 37.5f)

    @Test
    fun `Roundtrip trifft jedes Zellzentrum bis Radius 5 exakt`() {
        for (radius in Board.MIN_RADIUS..Board.MAX_RADIUS) {
            for (coord in HexGeometry.boardCells(radius)) {
                for (cellSize in cellSizes) {
                    val x = HexGeometry.centerX(coord, cellSize)
                    val y = HexGeometry.centerY(coord, cellSize)
                    assertEquals("($coord, size=$cellSize)", coord, HexGeometry.pixelToAxial(x, y, cellSize))
                }
            }
        }
    }

    @Test
    fun `Roundtrip bleibt bei Versatz innerhalb des Inkreises stabil`() {
        // Inkreis eines pointy-top-Hexagons: √3/2 · size ≈ 0,866 · size — 0,4 liegt sicher darin.
        val jitter = 0.4f
        val cellSize = 24f
        for (coord in HexGeometry.boardCells(Board.MAX_RADIUS)) {
            val x = HexGeometry.centerX(coord, cellSize)
            val y = HexGeometry.centerY(coord, cellSize)
            for (dx in floatArrayOf(-jitter, 0f, jitter)) {
                for (dy in floatArrayOf(-jitter, 0f, jitter)) {
                    val hit = HexGeometry.pixelToAxial(x + dx * cellSize, y + dy * cellSize, cellSize)
                    assertEquals("($coord, dx=$dx, dy=$dy)", coord, hit)
                }
            }
        }
    }

    @Test
    fun `boardCells zaehlt wie die Brettformel und bleibt im Radius`() {
        for (radius in Board.MIN_RADIUS..Board.MAX_RADIUS) {
            val cells = HexGeometry.boardCells(radius)
            assertEquals(Board.cellCount(radius), cells.size)
            assertEquals(cells.size, cells.toSet().size)
            assertTrue(cells.all { it.isWithinRadius(radius) })
        }
    }

    @Test
    fun `fittingCellSize haelt Brettsilhouette innerhalb der Flaeche`() {
        val width = 411f
        val height = 320f
        for (radius in Board.MIN_RADIUS..Board.MAX_RADIUS) {
            val size = HexGeometry.fittingCellSize(radius, width, height)
            assertTrue(size > 0f)
            val boardWidth = HexGeometry.SQRT3 * size * (2 * radius + 1)
            val boardHeight = size * (3 * radius + 2)
            assertTrue("Breite $boardWidth > $width", boardWidth <= width + 0.001f)
            assertTrue("Höhe $boardHeight > $height", boardHeight <= height + 0.001f)
        }
    }

    @Test
    fun `fittingCellSize ist straff - minimal groesser reisst eine Schranke`() {
        val width = 411f
        val height = 320f
        for (radius in Board.MIN_RADIUS..Board.MAX_RADIUS) {
            val oversized = HexGeometry.fittingCellSize(radius, width, height) * 1.001f
            val widthOver = HexGeometry.SQRT3 * oversized * (2 * radius + 1) > width
            val heightOver = oversized * (3 * radius + 2) > height
            assertTrue("R=$radius: 1,001-fache Zellgröße müsste eine Schranke reißen", widthOver || heightOver)
        }
    }

    @Test
    fun `Hex-Ecken liegen im Zellgroessen-Abstand mit Spitzen oben und unten`() {
        val cellSize = 24f
        val corners = (0 until 6).map { HexGeometry.cornerX(cellSize, it) to HexGeometry.cornerY(cellSize, it) }
        for ((x, y) in corners) {
            assertEquals("Ecke ($x, $y)", cellSize, sqrt(x * x + y * y), 0.001f)
        }
        // pointy-top (§2.1): genau eine Spitze bei (0, −size) und eine bei (0, +size).
        assertEquals(1, corners.count { (x, y) -> abs(x) < 0.001f && abs(y - cellSize) < 0.001f })
        assertEquals(1, corners.count { (x, y) -> abs(x) < 0.001f && abs(y + cellSize) < 0.001f })
    }

    @Test
    fun `Zellzentren benachbarter Zellen liegen exakt eine Kantenbreite auseinander`() {
        val cellSize = 20f
        val origin = HexCoord(0, 0)
        for (direction in Direction.entries) {
            val neighbor = origin.neighbor(direction)
            val dx = HexGeometry.centerX(neighbor, cellSize) - HexGeometry.centerX(origin, cellSize)
            val dy = HexGeometry.centerY(neighbor, cellSize) - HexGeometry.centerY(origin, cellSize)
            val distance = sqrt(dx * dx + dy * dy)
            assertEquals(HexGeometry.SQRT3 * cellSize, distance, 0.001f)
        }
    }
}
