package de.puzzlewerk.app.ui.juice

import androidx.compose.runtime.State
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * Tests des withFrameNanos-Treibers (PW-4.5): dt-Clamp (LOW-1-Auflage aus dem
 * PW-4.4-Security-Audit), Event-Durchleitung, 2-Hz-Puls und der
 * Reduce-Motion-Pfad (R44/§13.7: kein Puls, keine Partikel).
 *
 * Deterministisch über die Compose-Testuhr: `mainClock.autoAdvance = false`
 * VOR `setContent` — nur so treibt die manuelle Uhr den
 * `withInfiniteAnimationFrameNanos`-Loop (bei autoAdvance bricht die
 * InfiniteAnimationPolicy des Harness den Loop ab; genau deshalb hängen
 * bestehende Tests mit laufendem Treiber nicht). Kein Sleep, keine Echtzeit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class JuiceFrameDriverTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val queue = JuiceEventQueue()
    private val recorder = RecordingStepper()
    private lateinit var juice: State<JuiceState>

    /** Delegiert an den echten Kern und protokolliert jedes an step() gereichte dt. */
    private class RecordingStepper : JuiceStepper {
        val dts = mutableListOf<Long>()
        private val inner = DefaultJuiceStepper()

        override fun step(
            state: JuiceState,
            events: List<JuiceEvent>,
            dtMillis: Long,
        ): JuiceState {
            dts.add(dtMillis)
            return inner.step(state, events, dtMillis)
        }
    }

    private fun startDriver() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { juice = rememberJuiceFrameState(events = queue, stepper = recorder) }
    }

    private fun endpoint() = EndpointSpark(xDp = 40f, yDp = 40f, colorArgb = RED)

    // --- dt-Clamp (Auflage LOW-1) -------------------------------------------

    @Test
    fun `clampFrameDelta kappt grosse und negative Deltas`() {
        assertEquals(0L, clampFrameDelta(-5L))
        assertEquals(0L, clampFrameDelta(0L))
        assertEquals(16L, clampFrameDelta(16L))
        assertEquals(MAX_FRAME_DELTA_MILLIS, clampFrameDelta(MAX_FRAME_DELTA_MILLIS))
        // App-Background-Szenario: Minuten Pause ⇒ genau EIN gekapptes Delta, kein Nachhol-Spawn.
        assertEquals(MAX_FRAME_DELTA_MILLIS, clampFrameDelta(10_000L))
        assertEquals(MAX_FRAME_DELTA_MILLIS, clampFrameDelta(Long.MAX_VALUE))
    }

    @Test
    fun `der Treiber reicht nie mehr als das gekappte Delta an step`() {
        startDriver()
        composeRule.mainClock.advanceTimeBy(5_000L, ignoreFrameDuration = true)
        assertTrue("Treiber muss gelaufen sein", recorder.dts.isNotEmpty())
        assertTrue(
            "Alle dt ≤ $MAX_FRAME_DELTA_MILLIS ms, war: ${recorder.dts.max()}",
            recorder.dts.all { it in 0L..MAX_FRAME_DELTA_MILLIS },
        )
    }

    // --- Puls & Event-Durchleitung ------------------------------------------

    @Test
    fun `Puls laeuft und Endpunkt-Emitter funken nach ScreenEntered`() {
        startDriver()
        queue.offer(JuiceEvent.ScreenEntered(levelSeed = 7L, reduceMotion = false, endpoints = listOf(endpoint())))
        composeRule.mainClock.advanceTimeBy(PULSE_PROBE_MILLIS)
        val state = juice.value
        assertTrue("Emitter aus ScreenEntered übernommen", state.emitters.isNotEmpty())
        assertTrue("Funken gespawnt (250-ms-Kadenz, §13.8a)", state.particles.count > 0)
        assertTrue(
            "2-Hz-Puls moduliert den Halo (§13.8a), war ${state.haloPulseFactor}",
            abs(state.haloPulseFactor - 1f) > PULSE_MIN_DEVIATION,
        )
    }

    // --- Reduce-Motion (R44/§13.7) ------------------------------------------

    @Test
    fun `Reduce-Motion rendert statischen Halo ohne Puls und ohne Funken`() {
        startDriver()
        queue.offer(JuiceEvent.ScreenEntered(levelSeed = 7L, reduceMotion = true, endpoints = listOf(endpoint())))
        composeRule.mainClock.advanceTimeBy(PULSE_PROBE_MILLIS)
        val state = juice.value
        assertEquals("statischer Halo: Puls-Faktor konstant 1,0 (R44)", 1f, state.haloPulseFactor, 0f)
        assertEquals("keine Funken unter Reduce-Motion (R44)", 0, state.particles.count)
        assertEquals("kein Flash", 0f, state.flashAlpha, 0f)
    }

    @Test
    fun `MotionPreferenceChanged schaltet mid-session auf Reduce-Motion um`() {
        startDriver()
        queue.offer(JuiceEvent.ScreenEntered(levelSeed = 7L, reduceMotion = false, endpoints = listOf(endpoint())))
        composeRule.mainClock.advanceTimeBy(PULSE_PROBE_MILLIS) // Puls + Funken laufen
        queue.offer(JuiceEvent.MotionPreferenceChanged(reduceMotion = true)) // GameScreen-Pfad (R44)
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS) // Bestand läuft aus (400-ms-Lebensdauer)
        val state = juice.value
        assertEquals("nach Umschalten: statischer Halo", 1f, state.haloPulseFactor, 0f)
        assertEquals("keine neuen Funken, Bestand ausgelaufen", 0, state.particles.count)
    }

    private companion object {
        /** Rot #E5484D (§13.4). */
        private val RED = 0xFFE5484D.toInt()

        /**
         * Probezeitpunkt ~375 ms: sicher hinter der ersten 250-ms-Funken-Kadenz
         * und in der Puls-Talsohle (sin ≈ −1 bei 2 Hz) — der Faktor weicht dort
         * für JEDE Frame-Rundung der Testuhr deutlich von 1 ab.
         */
        private const val PULSE_PROBE_MILLIS = 375L
        private const val PULSE_MIN_DEVIATION = 0.05f

        /** Deutlich länger als die 400-ms-Funken-Lebensdauer (§13.8a). */
        private const val SETTLE_MILLIS = 1_000L
    }
}
