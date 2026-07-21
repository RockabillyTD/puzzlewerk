package de.puzzlewerk.app.ui.game

import androidx.compose.ui.geometry.Offset
import de.puzzlewerk.app.ui.juice.JuiceEvent
import de.puzzlewerk.app.ui.juice.JuiceEventQueue
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.trace.BeamEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Mapping-Tests GameScreen → Juice-Kern ([offerJuiceEvents], PW-4.6):
 * Koordinaten-Vertrag (Canvas-Pixel → dp über die Density), Palette §13.4 und
 * vor allem der [JuiceEvent.Solved]-KONTRAKT — Solved wird im selben Rutsch
 * NACH den CrystalBursts eingereiht. Robolectric nur wegen der Compose-
 * Graphics-Typen (Color/toArgb); keine Compose-Rule nötig.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameJuiceFeedbackTest {
    private val queue = JuiceEventQueue()

    /** Zellgröße 10 px, Ursprung (100, 100), Density 2 ⇒ Zelle (0,0) liegt bei (50 dp, 50 dp). */
    private val mapping =
        JuiceEventMapping(
            geometry = BoardGeometry(cellSize = 10f, origin = Offset(100f, 100f)),
            density = 2f,
            reduceMotion = false,
        )

    private val center = HexCoord(0, 0)

    @Test
    fun `MoveApplied reiht RotateFlash, CrystalBursts, EndpointsChanged und Solved in dieser Reihenfolge ein`() {
        val feedback =
            JuiceFeedback.MoveApplied(
                moveNumber = 3,
                rotatedCell = center,
                rotatedOrientationDegrees = 60f,
                newlyFulfilled = listOf(CrystalBurstData(center, LightColor.RED)),
                endpoints = listOf(BeamEndpoint(center, LightColor.RED)),
                solved = SolvedData(crystalCount = 2, paletteRequired = listOf(LightColor.RED, LightColor.GREEN)),
            )

        offerJuiceEvents(queue, feedback, mapping)

        val events = queue.drain()
        assertEquals(4, events.size)
        assertTrue(events[0] is JuiceEvent.RotateFlash)
        assertTrue(events[1] is JuiceEvent.CrystalBursts)
        assertTrue(events[2] is JuiceEvent.EndpointsChanged)
        // KONTRAKT JuiceEvent.Solved: im selben Frame NACH den CrystalBursts.
        val solved = events[3] as JuiceEvent.Solved
        assertEquals(3, solved.moveNumber)
        assertEquals(2, solved.crystalCount)
        assertEquals(2, solved.paletteArgb.size)
    }

    @Test
    fun `Zellzentren werden ueber Geometrie und Density nach dp gemappt, Farben nach ARGB`() {
        offerJuiceEvents(
            queue,
            JuiceFeedback.BoardEntered(levelSeed = 7L, endpoints = listOf(BeamEndpoint(center, LightColor.RED))),
            mapping,
        )

        val entered = queue.drain().single() as JuiceEvent.ScreenEntered
        assertEquals(7L, entered.levelSeed)
        val spark = entered.endpoints.single()
        assertEquals(50f, spark.xDp, 1e-4f) // 100 px Ursprung / Density 2
        assertEquals(50f, spark.yDp, 1e-4f)
        assertEquals(0xFFE5484D.toInt(), spark.colorArgb) // Rot §13.4
    }

    @Test
    fun `EffectsDismissed wird zu Dismissed (R49)`() {
        offerJuiceEvents(queue, JuiceFeedback.EffectsDismissed, mapping)

        assertEquals(listOf<JuiceEvent>(JuiceEvent.Dismissed), queue.drain())
    }
}
