package de.puzzlewerk.app.ui.game

import androidx.compose.ui.geometry.Offset
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.Segment
import de.puzzlewerk.game.trace.TraceResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Tests auf Spec-Ebene ([buildBoardRenderSpec]): Strahlmuster §13.2,
 * Sockelring §12.3 und die Chip-Garantie für Mischfarben-Strahlen.
 * Robolectric nur wegen der android-gestützten Compose-Graphics-Typen
 * (Path/PathEffect); keine Compose-Rule/Activity nötig.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BoardRenderSpecTest {
    private val colors = BoardColors()
    private val geometry = BoardGeometry.fit(2, 400f, 400f)

    private fun specOf(
        segments: List<Segment>,
        elements: Map<HexCoord, Element> = emptyMap(),
    ): BoardRenderSpec {
        val trace = TraceResult(segments = segments, received = emptyMap(), solved = false, endpoints = emptyList())
        val state = boardUiState(Board(radius = 2, elements = elements), trace)
        return buildBoardRenderSpec(state, geometry, colors)
    }

    private fun midpoint(
        from: HexCoord,
        to: HexCoord,
    ): Offset {
        val a = geometry.center(from)
        val b = geometry.center(to)
        return Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }

    /** Chips sind an ihrer Hintergrund-Deckscheibe erkennbar (addChipAt). */
    private fun chipCenters(spec: BoardRenderSpec): List<Offset> =
        spec.overlay.circles.filter { it.color == colors.background }.map { it.center }

    private fun near(
        a: Offset,
        b: Offset,
    ): Boolean = abs(a.x - b.x) < 0.01f && abs(a.y - b.y) < 0.01f

    @Test
    fun `Primaerfarben tragen Linienmuster, Mischfarben laufen durchgezogen`() {
        val spec =
            specOf(
                listOf(
                    Segment(HexCoord(-1, 0), HexCoord(0, 0), LightColor.RED),
                    Segment(HexCoord(-1, 1), HexCoord(0, 1), LightColor.GREEN),
                    Segment(HexCoord(-1, -1), HexCoord(0, -1), LightColor.BLUE),
                    Segment(HexCoord(0, 2), HexCoord(1, 1), LightColor.WHITE),
                ),
            )
        assertNotNull("Rot = Strichlinie (§13.2)", spec.beams[0].pathEffect)
        assertNotNull("Grün = Punktlinie (§13.2)", spec.beams[1].pathEffect)
        assertNotNull("Blau = Strich-Punkt (§13.2)", spec.beams[2].pathEffect)
        assertNull("Mischfarbe = durchgezogen (§13.2)", spec.beams[3].pathEffect)
    }

    @Test
    fun `Drehbares Element erzeugt genau einen Sockelring`() {
        val spec = specOf(emptyList(), mapOf(HexCoord(0, 0) to Element.Mirror(Orientation(0))))
        assertEquals(1, spec.overlay.circles.count { it.color == colors.socket })
    }

    @Test
    fun `verzahnte Mischfarben-Strahlen erhalten beide mindestens einen Chip`() {
        // BFS-verzahnte Reihenfolge wie beim Tracer (§5.2): A1,B1,A2,B2,A3,B3 —
        // Regression MAJOR-1: globale Paritätsvergabe ließ Strahl B chiplos.
        val beamA =
            listOf(
                Segment(HexCoord(-2, 0), HexCoord(-1, 0), LightColor.WHITE),
                Segment(HexCoord(-1, 0), HexCoord(0, 0), LightColor.WHITE),
                Segment(HexCoord(0, 0), HexCoord(1, 0), LightColor.WHITE),
            )
        val beamB =
            listOf(
                Segment(HexCoord(-2, 1), HexCoord(-1, 1), LightColor.WHITE),
                Segment(HexCoord(-1, 1), HexCoord(0, 1), LightColor.WHITE),
                Segment(HexCoord(0, 1), HexCoord(1, 1), LightColor.WHITE),
            )
        val interleaved = listOf(beamA[0], beamB[0], beamA[1], beamB[1], beamA[2], beamB[2])
        val chips = chipCenters(specOf(interleaved))
        val midsA = beamA.map { midpoint(it.from, it.to) }
        val midsB = beamB.map { midpoint(it.from, it.to) }
        assertTrue("Strahl A braucht ≥ 1 Chip", chips.any { chip -> midsA.any { near(chip, it) } })
        assertTrue("Strahl B braucht ≥ 1 Chip", chips.any { chip -> midsB.any { near(chip, it) } })
        // Jedes zweite Segment je Strahlzug, beginnend mit dem ersten: 2 + 2.
        assertEquals(4, chips.size)
    }

    @Test
    fun `Ein-Segment-Mischstrahl erhaelt genau einen Chip`() {
        val segment = Segment(HexCoord(0, 0), HexCoord(1, 0), LightColor.MAGENTA)
        val chips = chipCenters(specOf(listOf(segment)))
        assertEquals(1, chips.size)
        assertTrue(near(chips.single(), midpoint(segment.from, segment.to)))
    }

    @Test
    fun `Primaerfarben-Segmente erhalten keine Chips`() {
        val chips = chipCenters(specOf(listOf(Segment(HexCoord(0, 0), HexCoord(1, 0), LightColor.RED))))
        assertEquals(0, chips.size)
    }
}
