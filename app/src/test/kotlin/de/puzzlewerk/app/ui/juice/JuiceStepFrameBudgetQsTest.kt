package de.puzzlewerk.app.ui.juice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PW-4.9-QS, Pflichtpunkt 5 (ADR-011-Rückfalltür): Frame-Budget-Smoke als
 * JVM-Microbenchmark — `step()` unter Maximallast (Pool auf [MAX_PARTICLES]
 * gesättigt + Kaskaden-/Feuerwerk-Events + 8 Emitter) muss auf dem CI-Runner
 * im MEDIAN unter der bewusst großzügigen 4-ms-Schwelle bleiben (ein
 * 60-Hz-Frame hat 16,6 ms; die JVM ist nicht ART, deshalb Median statt Max
 * und 4 ms statt Frame-Budget — dokumentierte Smoke-Grenze, kein Profiling).
 * Zusätzlich wird die Allokations-Größenordnung je step() GEMESSEN und als
 * Messwert ausgegeben (kein Gate — Zahl für den PW-4.9-Bericht/PW-4.10).
 */
class JuiceStepFrameBudgetQsTest {
    private val stepper = DefaultJuiceStepper()

    /** Sättigt den Pool deterministisch auf [MAX_PARTICLES] lebende Partikel. */
    private fun saturatedState(): JuiceState {
        var s =
            stepper.step(
                JuiceState.EMPTY,
                listOf(
                    JuiceEvent.ScreenEntered(
                        levelSeed = 1L,
                        reduceMotion = false,
                        endpoints = List(8) { EndpointSpark(it.toFloat(), 0f, WHITE) },
                    ),
                ),
                dtMillis = 0L,
            )
        var move = 0
        while (s.particles.count < MAX_PARTICLES) {
            move++
            s = stepper.step(s, loadEvents(move), dtMillis = 8L)
        }
        return s
    }

    private fun loadEvents(move: Int): List<JuiceEvent> =
        listOf(
            JuiceEvent.CrystalBursts(move, List(6) { BurstOrigin(it.toFloat(), 0f, WHITE) }),
            JuiceEvent.Solved(move, crystalCount = 6, paletteArgb = List(6) { WHITE }),
        )

    @Test
    fun `step unter Maximallast bleibt im Median unter 4 ms (Smoke, CI-Schwelle)`() {
        val state = saturatedState()
        assertEquals(MAX_PARTICLES, state.particles.count)
        repeat(WARMUP) { stepper.step(state, loadEvents(it), dtMillis = 16L) } // JIT-Warmup
        val nanos =
            LongArray(SAMPLES) { i ->
                val start = System.nanoTime()
                stepper.step(state, loadEvents(i), dtMillis = 16L)
                System.nanoTime() - start
            }
        nanos.sort()
        val median = nanos[SAMPLES / 2]
        val p95 = nanos[(SAMPLES * 95) / 100]
        println("JuiceStep-Budget: median=${median / 1_000} µs, p95=${p95 / 1_000} µs bei $MAX_PARTICLES Partikeln")
        assertTrue(
            "step()-Median ${median / 1_000} µs >= $BUDGET_MILLIS ms unter Volllast (ADR-011-Rueckfalltuer pruefen)",
            median < BUDGET_MILLIS * 1_000_000L,
        )
    }

    @Test
    fun `Allokations-Groessenordnung je step wird gemessen und ausgegeben (Messwert, kein Gate)`() {
        val state = saturatedState()
        // Reflektiv: Unit-Tests kompilieren gegen android.jar (ohne java.lang.management),
        // laufen aber auf der Hotspot-JVM — dort existiert com.sun.management.ThreadMXBean.
        val readAllocatedBytes = allocationReader()
        if (readAllocatedBytes == null) {
            println("JuiceStep-Allokation: kein Allokations-Support auf dieser JVM - Messung uebersprungen")
            return
        }
        repeat(WARMUP) { stepper.step(state, loadEvents(it), dtMillis = 16L) }
        val before = readAllocatedBytes()
        repeat(ALLOC_SAMPLES) { stepper.step(state, loadEvents(it), dtMillis = 16L) }
        val perStep = (readAllocatedBytes() - before) / ALLOC_SAMPLES
        // Erwartete Groessenordnung: Snapshot-Kopie 9 Arrays x 512 Eintraege (~20 KiB) + Listen.
        println("JuiceStep-Allokation: ~$perStep B je step() bei $MAX_PARTICLES Partikeln (Step-Pfad, ADR-011)")
        assertTrue("Messung muss plausibel sein (> 0)", perStep > 0)
    }

    /** Liefert einen Reader für die kumulierten Allokations-Bytes des Test-Threads, falls verfügbar. */
    private fun allocationReader(): (() -> Long)? =
        runCatching {
            val factory = Class.forName("java.lang.management.ManagementFactory")
            val bean = factory.getMethod("getThreadMXBean").invoke(null)
            val sunInterface = Class.forName("com.sun.management.ThreadMXBean")
            if (!sunInterface.isInstance(bean)) return null
            val method = sunInterface.getMethod("getCurrentThreadAllocatedBytes")
            val reader = { method.invoke(bean) as Long }
            reader.takeIf { it() >= 0L }
        }.getOrNull()

    private companion object {
        val WHITE = 0xFFFFFFFF.toInt()
        const val WARMUP = 400
        const val SAMPLES = 200
        const val ALLOC_SAMPLES = 100
        const val BUDGET_MILLIS = 4L
    }
}
