package de.puzzlewerk.app.ui.juice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PW-4.9-QS, Pflichtpunkt 2: Kapazitäts-Stress des Partikel-Pools. Der legale
 * §13-Worst-Case (Kaskade mit K = 6, Feuerwerk F = 120, Funken-Emitter,
 * Dreh-Funken) und ein Sättigungs-Missbrauch (Solved-Spam ohne Partikel-Tod)
 * dürfen [MAX_PARTICLES] = 512 NIE überschreiten — und die Sättigung muss die
 * Kappe tatsächlich erreichen, sonst prüft der Test nichts (Silent-Drop-Politik
 * des ParticleBuffer). Deterministisch, feste Seeds, keine Uhr.
 */
class JuiceCapacityQsTest {
    private val stepper = DefaultJuiceStepper()

    private fun enter(endpointCount: Int): JuiceState =
        stepper.step(
            JuiceState.EMPTY,
            listOf(
                JuiceEvent.ScreenEntered(
                    levelSeed = 7L,
                    reduceMotion = false,
                    endpoints = List(endpointCount) { EndpointSpark(it.toFloat(), it.toFloat(), WHITE) },
                ),
            ),
            dtMillis = 0L,
        )

    @Test
    fun `Legaler Worst-Case - K gleich 6 Kaskade plus Feuerwerk plus Emitter bleibt unter der Kappe`() {
        val bursts = List(6) { BurstOrigin(it.toFloat(), it.toFloat(), WHITE) }
        var s = enter(endpointCount = 8)
        s =
            stepper.step(
                s,
                listOf(
                    JuiceEvent.RotateFlash(0f, 0f, 0f),
                    JuiceEvent.CrystalBursts(1, bursts),
                    JuiceEvent.Solved(1, crystalCount = 6, paletteArgb = List(6) { WHITE }),
                ),
                dtMillis = 0L,
            )
        var peak = s.particles.count
        repeat(120) {
            s = stepper.step(s, emptyList(), dtMillis = 16L)
            peak = maxOf(peak, s.particles.count)
            assertTrue("Kappe verletzt: ${s.particles.count}", s.particles.count <= MAX_PARTICLES)
        }
        // Beim Feuerwerk-Start leben mindestens 6 Bursts (je >= 8) + 120 Feuerwerk + 3 Dreh-Funken.
        assertTrue("Worst-Case muss real Last erzeugen, Peak war $peak", peak >= 171)
    }

    @Test
    fun `Saettigungs-Missbrauch - Solved-Spam erreicht exakt 512 und ueberschreitet nie`() {
        var s = enter(endpointCount = 8)
        var peak = 0
        repeat(200) { frame ->
            val move = frame + 1
            s =
                stepper.step(
                    s,
                    listOf(
                        JuiceEvent.CrystalBursts(move, List(6) { BurstOrigin(it.toFloat(), 0f, WHITE) }),
                        JuiceEvent.Solved(move, crystalCount = 6, paletteArgb = List(6) { WHITE }),
                    ),
                    // dt kürzer als jede Lebensdauer: der Pool kann nur wachsen.
                    dtMillis = 8L,
                )
            peak = maxOf(peak, s.particles.count)
            assertTrue("Frame $frame: ${s.particles.count} > $MAX_PARTICLES", s.particles.count <= MAX_PARTICLES)
        }
        assertEquals("Sättigung muss die Kappe exakt erreichen (Silent-Drop, nie Crash)", MAX_PARTICLES, peak)
    }

    @Test
    fun `Nach Saettigung raeumt Dismissed vollstaendig und der Pool traegt wieder neue Spawns`() {
        var s = enter(endpointCount = 8)
        repeat(60) { frame ->
            val move = frame + 1
            s =
                stepper.step(
                    s,
                    listOf(
                        JuiceEvent.CrystalBursts(move, List(6) { BurstOrigin(it.toFloat(), 0f, WHITE) }),
                        JuiceEvent.Solved(move, crystalCount = 6, paletteArgb = List(6) { WHITE }),
                    ),
                    dtMillis = 8L,
                )
        }
        assertEquals(MAX_PARTICLES, s.particles.count)
        s = stepper.step(s, listOf(JuiceEvent.Dismissed), dtMillis = 0L)
        assertEquals("R49: Sättigung hinterlässt keinen Rest", 0, s.particles.count)
        s = stepper.step(s, listOf(JuiceEvent.CrystalBursts(99, listOf(BurstOrigin(0f, 0f, WHITE)))), dtMillis = 0L)
        assertTrue("Pool nimmt nach dem Räumen wieder an", s.particles.count in 8..12)
    }

    private companion object {
        val WHITE = 0xFFFFFFFF.toInt()
    }
}
