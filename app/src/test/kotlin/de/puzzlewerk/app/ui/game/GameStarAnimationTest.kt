package de.puzzlewerk.app.ui.game

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import de.puzzlewerk.app.ui.juice.fireworkStartMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pinnt die Stern-Choreografie des Ergebnis-Overlays (§13.10 Nr. 5, PW-4.7):
 * Start bei `t_fw + 120 + (n−1)·150 ms`, Bounce 0 → 1,15 → 1,0 über 220 ms,
 * Reduce-Motion-Fade 150 ms zum GLEICHEN Zeitpunkt (§13.12), R49-Abbruch beim
 * Composition-Teardown. Deterministisch über die manuelle Compose-Testuhr
 * (`autoAdvance = false` VOR `setContent`, Muster PW-4.5/4.6); das 16-ms-
 * Frame-Raster verlangt Fenster- statt Punkt-Assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameStarAnimationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var appearance: StarAppearance? = null
    private var shownCount = 0

    private fun startContent(
        startMillis: Long,
        animationsEnabled: Boolean,
        visible: androidx.compose.runtime.State<Boolean>? = null,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            if (visible == null || visible.value) {
                appearance =
                    rememberStarAppearance(
                        startMillis = startMillis,
                        animationsEnabled = animationsEnabled,
                        onShown = { shownCount++ },
                    )
            }
        }
        // Composition + Effekt-Start brauchen bei pausierter Uhr echte Frames.
        advanceFrames(2)
    }

    private fun advanceFrames(frames: Int) {
        repeat(frames) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
    }

    private fun advanceMillis(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
        composeRule.waitForIdle()
    }

    @Test
    fun `Stern ist vor dem Start unsichtbar und meldet nichts`() {
        startContent(startMillis = 200L, animationsEnabled = true)

        advanceMillis(120L) // deutlich vor dem 200-ms-Start
        assertEquals(0f, appearance!!.scale(), 0f)
        assertEquals(0f, appearance!!.alpha(), 0f)
        assertEquals(0, shownCount)
    }

    @Test
    fun `Stern meldet den Einflug zum Startzeitpunkt und endet nach 220 ms Bounce exakt bei 1,0`() {
        startContent(startMillis = 200L, animationsEnabled = true)

        // Fenster um den Start (16-ms-Raster + Effekt-Startframes): bei 260 ms muss er laufen.
        advanceMillis(260L)
        assertEquals("SFX-Meldung genau einmal beim Einflug", 1, shownCount)
        assertEquals(1f, appearance!!.alpha(), 0f)

        // Overshoot: irgendwo in der Laufzeit übersteigt die Skalierung 1,0 (Peak 1,15).
        var maxScale = 0f
        repeat(16) {
            advanceFrames(1)
            maxScale = maxOf(maxScale, appearance!!.scale())
        }
        assertTrue("Bounce-Overshoot > 1,0 (Peak 1,15, Paragraf 13.10)", maxScale > 1.0f)

        advanceMillis(300L) // sicher hinter dem 220-ms-Bounce-Ende
        assertEquals("Endskalierung exakt 1,0", 1f, appearance!!.scale(), 0f)
        assertEquals("keine weitere Meldung", 1, shownCount)
    }

    @Test
    fun `Reduce-Motion erscheint ohne Bounce als 150-ms-Fade zum gleichen Zeitpunkt`() {
        startContent(startMillis = 200L, animationsEnabled = false)

        advanceMillis(120L)
        assertEquals("gleicher Startzeitpunkt (Paragraf 13.12)", 0f, appearance!!.alpha(), 0f)
        assertEquals(0, shownCount)

        advanceMillis(140L) // ~260 ms: eingeflogen, Fade läuft
        assertEquals("SFX auch unter Reduce-Motion (Audio unberührt)", 1, shownCount)
        assertEquals("nie skaliert (kein Bounce)", 1f, appearance!!.scale(), 0f)

        advanceMillis(220L) // sicher hinter dem 150-ms-Fade
        assertEquals(1f, appearance!!.alpha(), 0f)
    }

    @Test
    fun `Composition-Teardown vor dem Start bricht Einflug und SFX-Meldung ab (R49)`() {
        val visible = mutableStateOf(true)
        startContent(startMillis = 400L, animationsEnabled = true, visible = visible)

        advanceMillis(100L) // Overlay verschwindet VOR dem Stern-Start („Nochmal"/Zurück)
        composeRule.runOnIdle { visible.value = false }
        advanceFrames(2)

        advanceMillis(1_000L) // weit hinter allen Startzeitpunkten
        assertEquals("kein SFX nach Dismiss", 0, shownCount)
    }

    @Test
    fun `Startzeitpunkte folgen t_fw plus 120 plus 150 je Stern (Nachrechnung Paragraf 13_10)`() {
        // Beispiel Level 7.3: N = 3 ⇒ t_fw = 80 ms, Sterne bei 200/350/500 ms.
        val tFw = fireworkStartMillis(3)
        assertEquals(80L, tFw)
        assertEquals(200L, starEntryStartMillis(tFw, 1))
        assertEquals(350L, starEntryStartMillis(tFw, 2))
        assertEquals(500L, starEntryStartMillis(tFw, 3))
        // Worst Case: Kappe t_fw = 160 ms ⇒ 3. Stern startet bei 580 ms ≤ 600 ms.
        assertEquals(160L, fireworkStartMillis(9))
        assertEquals(580L, starEntryStartMillis(fireworkStartMillis(9), 3))
        // R31: kein lösender Zug ⇒ keine Kaskade ⇒ t_fw = 0 (kein negativer Versatz).
        assertEquals(0L, fireworkStartMillis(0))
        assertFalse(starEntryStartMillis(0L, 1) < 0L)
    }
}
