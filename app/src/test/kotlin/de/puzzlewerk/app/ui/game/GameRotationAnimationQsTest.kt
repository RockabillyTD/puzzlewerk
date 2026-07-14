package de.puzzlewerk.app.ui.game

import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.element.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unabhängiger QS-Pass (PW-3.7-QS) für Reduce-Motion und die Dreh-Animation:
 *
 * - §13.6: „Animationen respektieren die System-Einstellung ‚Animationen
 *   entfernen'" — [rememberAnimationsEnabled] muss `ANIMATOR_DURATION_SCALE = 0`
 *   als AUS lesen, und [rememberRotationSpin] darf dann KEINEN optischen
 *   Versatz liefern (Element steht sofort am Ziel).
 * - §12.3: Bei aktiven Animationen läuft nach einem Drehzug tatsächlich ein
 *   Spin (~150 ms Tween) und endet wieder in Ruhe (Spin `null`).
 *
 * Deterministisch: Compose-`mainClock` statt echter Zeit (ADR-009
 * Flaky-Verbot), Robolectric-Settings statt Gerätezustand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameRotationAnimationQsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    /** Minimalbrett: genau ein drehbarer Spiegel auf (0,0) mit Orientierung [steps]. */
    private fun boardWith(steps: Int): BoardUiState =
        BoardUiState(
            radius = 2,
            cells = listOf(BoardCell(HexCoord(0, 0), Element.Mirror(Orientation(steps)))),
            beams = emptyList(),
        )

    /** §13.6: Systemskala 0 („Animationen entfernen") ⇒ Animationen AUS. */
    @Test
    fun animatorSkalaNullSchaltetAnimationenAus() {
        setAnimatorScale(0f)
        var enabled: Boolean? = null

        composeRule.setContent { enabled = rememberAnimationsEnabled() }

        composeRule.runOnIdle { assertEquals(false, enabled) }
    }

    /** §13.6-Gegenprobe: Standardskala 1 ⇒ Animationen AN. */
    @Test
    fun animatorSkalaEinsLaesstAnimationenAn() {
        setAnimatorScale(1f)
        var enabled: Boolean? = null

        composeRule.setContent { enabled = rememberAnimationsEnabled() }

        composeRule.runOnIdle { assertEquals(true, enabled) }
    }

    /** §13.6: Reduce-Motion ⇒ ein Drehzug erzeugt NIE einen Spin — das Element steht sofort am Ziel. */
    @Test
    fun reduceMotionErzeugtKeinenSpinNachDrehzug() {
        val board = mutableStateOf(boardWith(5))
        var spin: BoardSpin? = null
        composeRule.setContent { spin = rememberRotationSpin(board.value, animationsEnabled = false) }
        composeRule.waitForIdle()

        board.value = boardWith(0) // eine Zelle, eine Stufe gedreht (wie nach Rotate)
        // State-Write vom Testthread sichtbar machen (paused-clock-sicher, kein Wallclock-Wait).
        Snapshot.sendApplyNotifications()
        composeRule.waitForIdle()

        assertNull(spin)
    }

    /** §12.3: Mit aktiven Animationen läuft nach dem Drehzug ein Spin (~150 ms) und endet wieder in Ruhe. */
    @Test
    fun drehzugSpinntWaehrendDerAnimationUndEndetInRuhe() {
        val board = mutableStateOf(boardWith(5))
        var spin: BoardSpin? = null
        composeRule.setContent { spin = rememberRotationSpin(board.value, animationsEnabled = true) }
        composeRule.waitForIdle() // initiale Komposition: previous-Orientierungen gesetzt, kein Spin
        assertNull(spin)

        composeRule.mainClock.autoAdvance = false
        board.value = boardWith(0)
        // Bei stehender Testuhr werden Snapshot-Writes nicht automatisch gemeldet.
        Snapshot.sendApplyNotifications()
        var frames = 0
        while (spin == null && frames < MAX_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            frames++
        }
        assertNotNull("Während der ~150-ms-Dreh-Animation muss ein Spin anliegen (§12.3)", spin)
        assertEquals(HexCoord(0, 0), spin!!.cell)
        assertTrue("Optik läuft der Logik hinterher (Versatz ≤ 0)", spin!!.angleOffsetRad <= 0f)

        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertNull("Nach der Animation steht das Element exakt am Ziel (Spin aus)", spin)
    }

    private companion object {
        /** Obergrenze an Test-Frames bis zum Animationsstart — deterministische mainClock, kein Wallclock-Wait. */
        private const val MAX_FRAMES = 20

        /** Deutlich länger als die ~150-ms-Animation (§12.3), rein auf der Testuhr. */
        private const val SETTLE_MILLIS = 1_000L
    }
}
