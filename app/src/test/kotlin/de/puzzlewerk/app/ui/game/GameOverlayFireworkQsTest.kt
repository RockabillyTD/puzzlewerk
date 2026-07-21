package de.puzzlewerk.app.ui.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PW-4.9-QS, Pflichtpunkt 6 (Abbruch-Kanten §13.10/R49): (a) dokumentierender
 * IST-Pin der bekannten Recreation-Kante — nach Activity-/Prozess-Recreation
 * mit überlebendem Ergebnis spielt die frische Composition die komplette
 * Stern-Choreografie ERNEUT ab (inkl. SFX-Meldungen); (b) Repro des
 * Doppel-SFX-Kandidaten beim Reduce-Motion-Toggle mit offenem Overlay.
 * Manuelle Testuhr, Muster GameOverlayFireworkTest/GameStarAnimationTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class GameOverlayFireworkQsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val shownStars = mutableListOf<Int>()
    private var shownCount = 0

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

    private val result = GameResult(points = 1450, stars = 3, moves = 2, par = 3, fireworkStartMillis = 80L)

    /**
     * IST-Pin der im PW-4.7-Handover benannten Kante: Das ViewModel überlebt
     * die Recreation (result bleibt gesetzt), Composition und Animationszustand
     * sind frisch — die Sterne fliegen und melden ERNEUT (⇒ sfx_star_n doppelt
     * über die Partie gesehen). Kein Crash, Reihenfolge bleibt 1-2-3.
     * BEWERTUNG an das Gate (PW-4.10-Checkliste, Produktfrage Portrait-Lock/
     * configChanges): §13.10 definiert die Recreation-Kante nicht.
     */
    @Test
    fun `IST-Pin - nach Recreation spielt das Overlay die Stern-Choreografie erneut ab`() {
        val visible = mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzlewerkTheme {
                if (visible.value) {
                    GameResultOverlay(
                        result = result,
                        onNext = {},
                        onReplay = {},
                        onBack = {},
                        onStarShown = shownStars::add,
                        animationsEnabled = true,
                    )
                }
            }
        }
        advanceFrames(2)
        advanceMillis(1_200L) // alle Starts (200/350/500 ms) sicher durch
        assertEquals(listOf(1, 2, 3), shownStars)

        composeRule.runOnIdle { visible.value = false } // Recreation: Composition weg …
        advanceFrames(2)
        composeRule.runOnIdle { visible.value = true } // … und frisch wieder da, result unveraendert
        advanceFrames(2)
        advanceMillis(1_200L)
        assertEquals("frische Composition wiederholt die Choreografie", listOf(1, 2, 3, 1, 2, 3), shownStars)
    }

    /**
     * BUG-PW4.9-2 (behoben in PW-4.9-FIX): Der Reduce-Motion-Toggle bei
     * offenem Overlay startet den `LaunchedEffect(startMillis,
     * animationsEnabled)` in `rememberStarAppearance` neu — vor dem Fix
     * wartete ein bereits eingeflogener Stern seine Startzeit ERNEUT ab und
     * meldete `onShown` ein zweites Mal (⇒ doppeltes sfx_star_n). Fix: der
     * `shown`-Zustand überlebt den Effekt-Restart — genau EINE Meldung je
     * Stern (§13.11), nur die Kurve wechselt (§13.12: Audio unberührt).
     */
    @Test
    fun `RM-Toggle mit offenem Overlay darf den Stern-SFX nicht wiederholen`() {
        val animations = mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            rememberStarAppearance(
                startMillis = 100L,
                animationsEnabled = animations.value,
                onShown = { shownCount++ },
            )
        }
        advanceFrames(2)
        advanceMillis(400L) // Stern ist eingeflogen und gemeldet
        assertEquals(1, shownCount)

        composeRule.runOnIdle { animations.value = false } // System-Toggle mid-session (R44)
        advanceFrames(2)
        advanceMillis(400L)
        assertEquals("keine zweite Meldung nach dem Toggle", 1, shownCount)
    }
}
