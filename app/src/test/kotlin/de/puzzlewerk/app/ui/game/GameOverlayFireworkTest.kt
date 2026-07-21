package de.puzzlewerk.app.ui.game

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * §13.10-Choreografie des Ergebnis-Overlays (PW-4.7) unter der manuellen
 * Testuhr: Overlay-Inhalte stehen sofort, die Aktionsknöpfe sind bis zur
 * 600-ms-Frist wirkungslos und danach bedienbar (abgenommene V3-Abweichung),
 * die Sterne melden ihre Einflüge einzeln in Reihenfolge (SFX-Senke §13.11)
 * und ein Dismiss vor dem Einflug unterdrückt jede Meldung (R49).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class GameOverlayFireworkTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val shownStars = mutableListOf<Int>()
    private var nextClicks = 0

    private fun string(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    private fun startOverlay(
        result: GameResult,
        visible: androidx.compose.runtime.State<Boolean>? = null,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzlewerkTheme {
                if (visible == null || visible.value) {
                    GameResultOverlay(
                        result = result,
                        onNext = { nextClicks++ },
                        onReplay = {},
                        onBack = {},
                        onStarShown = shownStars::add,
                        animationsEnabled = true,
                    )
                }
            }
        }
        advanceFrames(2) // Composition + Effekt-Start bei pausierter Uhr
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

    private val result = GameResult(points = 1450, stars = 3, moves = 2, par = 3, fireworkStartMillis = 80L)

    @Test
    fun `Overlay-Inhalt steht sofort, Knoepfe verpuffen vor 600 ms und wirken danach`() {
        startOverlay(result)

        // ≤ 600-ms-Frist trivial erfüllt: Wertung und Knopf sind sofort komponiert.
        composeRule.onNodeWithText(string(R.string.game_result_title)).assertIsDisplayed()
        val next = composeRule.onNodeWithText(string(R.string.game_result_next))
        next.assertIsDisplayed()

        advanceMillis(300L) // mitten im gesperrten Fenster (< 500 ms Fade-Start)
        next.performClick()
        composeRule.waitForIdle()
        assertEquals("Klick vor der 600-ms-Frist bleibt wirkungslos", 0, nextClicks)

        advanceMillis(340L) // > 600 ms seit Commit (inkl. Startframes) — Frist erreicht
        next.performClick()
        composeRule.waitForIdle()
        assertEquals("ab 600 ms sichtbar UND interaktiv (Paragraf 13.10 Nr. 6)", 1, nextClicks)
    }

    @Test
    fun `Sterne melden ihre Einfluege einzeln in Reihenfolge 1-2-3`() {
        startOverlay(result) // t_fw = 80 ⇒ Starts bei 200/350/500 ms

        advanceMillis(150L)
        assertEquals(emptyList<Int>(), shownStars)
        advanceMillis(80L) // ~230 ms + Startframes
        assertEquals(listOf(1), shownStars)
        advanceMillis(150L)
        assertEquals(listOf(1, 2), shownStars)
        advanceMillis(150L)
        assertEquals(listOf(1, 2, 3), shownStars)
    }

    @Test
    fun `Dismiss vor dem Einflug unterdrueckt alle Stern-Meldungen (R49)`() {
        val visible = mutableStateOf(true)
        startOverlay(result, visible = visible)

        advanceMillis(100L) // vor dem ersten Stern-Start (200 ms)
        composeRule.runOnIdle { visible.value = false }
        advanceFrames(2)

        advanceMillis(1_000L)
        assertEquals("kein Stern-SFX nach Dismiss", emptyList<Int>(), shownStars)
    }

    @Test
    fun `Weniger verdiente Sterne melden nur ihre eigenen Einfluege`() {
        startOverlay(GameResult(points = 1150, stars = 1, moves = 9, par = 4))

        advanceMillis(1_200L) // weit hinter allen möglichen Startzeitpunkten
        assertEquals(listOf(1), shownStars)
    }
}
