package de.puzzlewerk.app.ui.game

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import de.puzzlewerk.game.board.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pinnt den Compose-Dreh-Blitz (§13.9, Korrekturrunde MINOR-3): Peak ~0,6,
 * Dauer ~120 ms, kein Blitz ohne Animationen (§13.6/§13.12) und kein Blitz
 * ohne semantisches Signal (MINOR-2). Deterministisch über die manuelle
 * Compose-Testuhr (`autoAdvance = false` VOR `setContent`, Muster PW-4.5);
 * Frame-Raster 16 ms ⇒ Peak-Assertion mit Ein-Frame-Toleranz (0,6 − 0,08).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameRotationAnimationFlashTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var flash: BoardFlash? = null

    private fun startContent(
        cell: androidx.compose.runtime.State<HexCoord?>,
        animationsEnabled: Boolean,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            flash = rememberRotateFlash(BoardSampleStates.exampleLevelStart, cell.value, animationsEnabled)
        }
    }

    @Test
    fun `Dreh-Blitz startet nahe 0,6 und ist nach 120 ms beendet`() {
        val cell = mutableStateOf<HexCoord?>(null)
        startContent(cell, animationsEnabled = true)
        composeRule.mainClock.advanceTimeByFrame()
        assertNull("ohne Signal kein Blitz", flash)

        // Snapshot-Write MIT Apply-Notification (sonst sieht der Recomposer die
        // Invalidierung bei pausierter Uhr nie) — dann bis zum ersten
        // publizierten Animationswert vorspulen (je nach Dispatch 2–4 Frames).
        // Snapshot-Write mit Apply-Notification (runOnIdle); Recomposition und
        // Effekt-Start brauchen bei pausierter Uhr zusaetzlich echte Frames.
        composeRule.runOnIdle { cell.value = HexCoord(0, 0) }
        var frames = 0
        while (flash == null && frames < 8) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle() // Robolectric: gepostete Recomposer-Arbeit abarbeiten
            frames++
        }
        val started = flash
        assertNotNull("Blitz muss laufen", started)
        assertEquals(HexCoord(0, 0), started!!.cell)
        // Erster publizierter Animationswert = elapsed 0 => exakter Peak (deterministische Testuhr).
        assertEquals("Peak 0,6 (Paragraf 13.9)", 0.6f, started.alpha, 1e-3f)

        composeRule.mainClock.advanceTimeBy(48L) // mitten in der 120-ms-Laufzeit
        composeRule.waitForIdle()
        assertNotNull("mitten in der Laufzeit lebt der Blitz noch", flash)

        composeRule.mainClock.advanceTimeBy(160L) // deutlich > 120 ms
        composeRule.waitForIdle()
        assertNull("nach 120 ms ist der Blitz beendet", flash)
    }

    @Test
    fun `Ohne Animationen (Reduce-Motion) blitzt nichts`() {
        val cell = mutableStateOf<HexCoord?>(null)
        startContent(cell, animationsEnabled = false)
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.runOnIdle { cell.value = HexCoord(0, 0) }
        repeat(5) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }

        assertNull(flash)
    }
}
