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

    // --- dt-Ableitung mit Rest-Übertrag (Review MAJOR-1) --------------------

    @Test
    fun `echte 60-Hz-Deltas akkumulieren exakt 1000 ms je Wandsekunde`() {
        var consumed = 0L
        var total = 0L
        for (frame in 1..FRAMES_60_HZ) {
            val delta = consumeFrameDelta(consumed, frame * NANOS_60_HZ)
            consumed = delta.consumedNanos
            total += delta.dtMillis
        }
        assertEquals(
            "60 Frames à 16.666.667 ns = 1 Wandsekunde — ohne Rest-Übertrag nur 960 ms (Puls-Drift §13.8a)",
            1000L,
            total,
        )
    }

    @Test
    fun `echte 120-Hz-Deltas akkumulieren driftfrei`() {
        var consumed = 0L
        var total = 0L
        for (frame in 1..FRAMES_120_HZ) {
            val delta = consumeFrameDelta(consumed, frame * NANOS_120_HZ)
            consumed = delta.consumedNanos
            total += delta.dtMillis
        }
        // 120 · 8.333.333 ns = 999.999.960 ns ⇒ 999 ganze ms konsumiert, Rest < 1 ms in der Baseline.
        assertEquals(999L, total)
        assertTrue("Rest bleibt unter 1 ms", FRAMES_120_HZ * NANOS_120_HZ - consumed < 1_000_000L)
    }

    @Test
    fun `Clamp setzt die Baseline neu auf statt einen Nachhol-Rest zu bilden`() {
        val paused = consumeFrameDelta(0L, PAUSE_NANOS) // 5 s App-Background
        assertEquals(MAX_FRAME_DELTA_MILLIS, paused.dtMillis)
        assertEquals("Baseline springt auf den Frame — die Pause wird verworfen", PAUSE_NANOS, paused.consumedNanos)
        val next = consumeFrameDelta(paused.consumedNanos, PAUSE_NANOS + NANOS_60_HZ)
        assertEquals("Folge-Frame tickt wieder normal", 16L, next.dtMillis)
    }

    @Test
    fun `Uhr-Anomalie liefert dt 0 und setzt die Baseline neu`() {
        val delta = consumeFrameDelta(1_000_000L, 500_000L)
        assertEquals(0L, delta.dtMillis)
        assertEquals(500_000L, delta.consumedNanos)
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

    // --- Burst über die Queue (PW-4.6: ViewModel ist Produzent) -------------

    @Test
    fun `CrystalBursts ueber die Queue erzeugen Partikel und veroeffentlichten Glow`() {
        startDriver()
        queue.offer(JuiceEvent.ScreenEntered(levelSeed = 7L, reduceMotion = false, endpoints = emptyList()))
        queue.offer(JuiceEvent.CrystalBursts(1, listOf(BurstOrigin(10f, 10f, RED))))
        composeRule.mainClock.advanceTimeBy(BURST_PROBE_MILLIS)
        val state = juice.value
        assertTrue("Burst spawnt Partikel (P = 8..12, §13.9)", state.particles.count > 0)
        // Publikations-Filter rendersDifferently MUSS Glow-Frames schreiben (PW-4.5-Warnung).
        assertTrue("Glow ist im veröffentlichten Snapshot sichtbar", state.glows.isNotEmpty())
    }

    @Test
    fun `Reduce-Motion Burst erzeugt 0 Partikel und keinen Glow`() {
        startDriver()
        queue.offer(JuiceEvent.ScreenEntered(levelSeed = 7L, reduceMotion = true, endpoints = emptyList()))
        queue.offer(JuiceEvent.CrystalBursts(1, listOf(BurstOrigin(10f, 10f, RED))))
        composeRule.mainClock.advanceTimeBy(BURST_PROBE_MILLIS)
        assertEquals(0, juice.value.particles.count)
        assertTrue(juice.value.glows.isEmpty())
    }

    // --- Kapazitätsgrenze der Queue (PW-4.5-Security-MINOR-2) ---------------

    @Test
    fun `JuiceEventQueue verwirft ueber der Kapazitaet still und leert per drain`() {
        val bounded = JuiceEventQueue()
        repeat(MAX_PENDING_JUICE_EVENTS + 10) { bounded.offer(JuiceEvent.Dismissed) }
        assertEquals(MAX_PENDING_JUICE_EVENTS, bounded.drain().size)
        assertEquals("drain leert vollständig", 0, bounded.drain().size)
        bounded.offer(JuiceEvent.Dismissed) // nach dem Leeren nimmt sie wieder an
        assertEquals(1, bounded.drain().size)
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

        /** ~3 Frames nach dem Burst: Partikel (600 ms) und Glow (250 ms) leben noch. */
        private const val BURST_PROBE_MILLIS = 48L

        /** Echte Frame-Dauern in Nanosekunden (Review MAJOR-1: KEINE glatten 16 ms). */
        private const val NANOS_60_HZ = 16_666_667L
        private const val NANOS_120_HZ = 8_333_333L
        private const val FRAMES_60_HZ = 60
        private const val FRAMES_120_HZ = 120

        /** 5 s App-Background — weit über der 100-ms-Kappe. */
        private const val PAUSE_NANOS = 5_000_000_000L
    }
}
