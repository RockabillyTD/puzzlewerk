package de.puzzlewerk.app.ui.juice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * PW-4.9-QS, Pflichtpunkt 7 (delegierte Review-MINORs aus PW-4.4): Bounds-/
 * Golden-Tests der bisher ungepinnten §13-Konstanten über die Spawn-Ausgabe —
 * Startgeschwindigkeiten über sqrt(vx² + vy²), Gravitation, Lebensdauern
 * (alphaFadePerMillis = 1/Lebensdauer), CRYSTAL_P_SPAN (P ∈ {8…12} inkl.
 * beider Ränder), Dreh-Funken-Winkel und Feuerwerks-Farbzyklus. Property-Tests
 * über viele feste Seeds, reine `step`-Aufrufe.
 */
class JuiceParticleBoundsQsTest {
    private val stepper = DefaultJuiceStepper()

    private fun enter(
        seed: Long,
        endpoints: List<EndpointSpark> = emptyList(),
    ): JuiceState =
        stepper.step(
            JuiceState.EMPTY,
            listOf(JuiceEvent.ScreenEntered(seed, false, endpoints)),
            dtMillis = 0L,
        )

    private fun ParticleSnapshot.speeds(): List<Float> = (0 until count).map { hypot(vxDp[it], vyDp[it]) }

    @Test
    fun `Kristall-Burst - Geschwindigkeit 80-160, Gravitation 240, Lebensdauer 600 ms, Groesse 3 dp`() {
        for (seed in 1L..80L) {
            val s = enter(seed).let { stepper.step(it, listOf(burst(1)), 0L) }
            assertTrue(s.particles.count > 0)
            s.particles.speeds().forEach { assertTrue("Speed $it (Seed $seed)", it in 80f..160f) }
            (0 until s.particles.count).forEach { i ->
                assertEquals(240f, s.particles.gravityDpPerSec2[i], 0f)
                assertEquals(1f / 600f, s.particles.alphaFadePerMillis[i], 1e-7f)
                assertEquals(3f, s.particles.sizeDp[i], 0f)
                assertEquals(RED, s.particles.colorArgb[i]) // Soll-Farbe des Kristalls (§13.9)
            }
        }
    }

    @Test
    fun `Kristall-P bleibt in 8 bis 12 und trifft beide Raender (CRYSTAL_P_SPAN)`() {
        val counts = (1L..300L).map { seed -> stepper.step(enter(seed), listOf(burst(1)), 0L).particles.count }
        counts.forEach { assertTrue("P = $it ausserhalb 8..12", it in 8..12) }
        assertTrue("Unterer Rand 8 nie getroffen — Span zu gross?", counts.contains(8))
        assertTrue("Oberer Rand 12 nie getroffen — Span zu klein?", counts.contains(12))
    }

    @Test
    fun `Feuerwerk - Geschwindigkeit 120-320, Gravitation 480, Lebensdauer 900-1400 ms, Farbzyklus`() {
        val palette = listOf(RED, GREEN, BLUE)
        for (seed in 1L..80L) {
            // Solved ohne Kaskade: t_fw = 0, das Feuerwerk feuert isoliert im selben Frame.
            val s = stepper.step(enter(seed), listOf(JuiceEvent.Solved(1, 6, palette)), 0L)
            assertEquals("F = min(120, 60 + 12*6)", 120, s.particles.count)
            s.particles.speeds().forEach { assertTrue("Speed $it (Seed $seed)", it in 120f..320f) }
            (0 until s.particles.count).forEach { i ->
                assertEquals(480f, s.particles.gravityDpPerSec2[i], 0f)
                val life = 1f / s.particles.alphaFadePerMillis[i]
                assertTrue("Lebensdauer $life ms (Seed $seed)", life in 900f..1400f)
                assertEquals("Palette zyklisch (r, dann q — §13.10)", palette[i % 3], s.particles.colorArgb[i])
            }
        }
    }

    @Test
    fun `Dreh-Funken - genau 3, exakt 90 dp pro s, Winkel 30-150-270 relativ, 300 ms, kein PRNG`() {
        val orientation = 60f
        val s = stepper.step(enter(1L), listOf(JuiceEvent.RotateFlash(9f, 9f, orientation)), 0L)
        assertEquals(3, s.particles.count)
        val expected =
            listOf(30f, 150f, 270f).map { base ->
                val rad = (orientation + base) * (PI.toFloat() / 180f)
                Pair(90f * cos(rad), 90f * sin(rad))
            }
        (0 until 3).forEach { i ->
            assertEquals("vx Funke $i", expected[i].first, s.particles.vxDp[i], 1e-3f)
            assertEquals("vy Funke $i", expected[i].second, s.particles.vyDp[i], 1e-3f)
            assertEquals(1f / 300f, s.particles.alphaFadePerMillis[i], 1e-7f)
            assertEquals(0f, s.particles.gravityDpPerSec2[i], 0f)
        }
    }

    @Test
    fun `Endpunkt-Funken - exakt 60 dp pro s radial, 2 dp, 400 ms, Strahlfarbe, keine Gravitation`() {
        for (seed in 1L..40L) {
            val entered = enter(seed, endpoints = listOf(EndpointSpark(3f, 4f, BLUE)))
            val s = stepper.step(entered, emptyList(), 250L)
            assertEquals(1, s.particles.count)
            assertEquals(60f, hypot(s.particles.vxDp[0], s.particles.vyDp[0]), 1e-3f)
            assertEquals(2f, s.particles.sizeDp[0], 0f)
            assertEquals(BLUE, s.particles.colorArgb[0])
            assertEquals(0f, s.particles.gravityDpPerSec2[0], 0f)
            assertEquals(1f / 400f, s.particles.alphaFadePerMillis[0], 1e-7f)
            // Spawn geschieht am Frame-Ende mit Alpha 1 (Integration erst im Folgeframe).
            assertEquals(1f, s.particles.alpha[0], 0f)
        }
    }

    private fun burst(moveNumber: Int): JuiceEvent.CrystalBursts =
        JuiceEvent.CrystalBursts(moveNumber, listOf(BurstOrigin(0f, 0f, RED)))

    private companion object {
        val RED = 0xFFE5484D.toInt()
        val GREEN = 0xFF30A46C.toInt()
        val BLUE = 0xFF3E63DD.toInt()
    }
}
