package de.puzzlewerk.app.ui.juice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * JVM-Determinismus- und Verhaltenstests des Juice-Kerns (§13.13, ADR-011).
 * Reine `step`-Aufrufe mit festen dt-Folgen — kein Robolectric, keine Uhr.
 */
class DefaultJuiceStepperTest {
    private val stepper = DefaultJuiceStepper()

    private fun endpoint(
        x: Float,
        y: Float,
        argb: Int = 0xFFE5484D.toInt(),
    ) = EndpointSpark(x, y, argb)

    private fun origin(
        x: Float,
        y: Float,
        argb: Int = 0xFF30A46C.toInt(),
    ) = BurstOrigin(x, y, argb)

    private fun enter(
        seed: Long = 1L,
        reduceMotion: Boolean = false,
        endpoints: List<EndpointSpark> = emptyList(),
    ): JuiceState =
        stepper.step(JuiceState.EMPTY, listOf(JuiceEvent.ScreenEntered(seed, reduceMotion, endpoints)), dtMillis = 0L)

    private fun JuiceState.step(
        dtMillis: Long,
        vararg events: JuiceEvent,
    ): JuiceState = stepper.step(this, events.toList(), dtMillis)

    // --- Determinismus ------------------------------------------------------

    private fun scenario(): JuiceState {
        var s = enter(seed = 42L, endpoints = listOf(endpoint(10f, 20f), endpoint(30f, 40f)))
        s = s.step(16L, JuiceEvent.RotateFlash(50f, 60f, orientationDegrees = 90f))
        val palette = listOf(0xFFE5484D.toInt(), 0xFF3E63DD.toInt())
        s = s.step(120L, JuiceEvent.CrystalBursts(1, listOf(origin(5f, 5f), origin(6f, 6f), origin(7f, 7f))))
        s = s.step(90L, JuiceEvent.Solved(1, crystalCount = 3, paletteArgb = palette))
        repeat(40) { s = s.step(16L) }
        return s
    }

    @Test
    fun `gleiche Seed und dt-Folge liefern bit-identische Partikelzustaende`() {
        val a = scenario()
        val b = scenario()
        assertEquals(a.elapsedMillis, b.elapsedMillis)
        assertEquals(a.haloPulseFactor, b.haloPulseFactor, 0f)
        assertEquals(a.flashAlpha, b.flashAlpha, 0f)
        assertEquals(a.emitters, b.emitters)
        assertEquals(a.pendingBursts, b.pendingBursts)
        assertEquals(a.glows, b.glows)
        val pa = a.particles
        val pb = b.particles
        assertTrue("Partikel-Szenario muss nicht-leer sein", pa.count > 0)
        assertEquals(pa.count, pb.count)
        assertTrue(pa.xDp.contentEquals(pb.xDp))
        assertTrue(pa.yDp.contentEquals(pb.yDp))
        assertTrue(pa.sizeDp.contentEquals(pb.sizeDp))
        assertTrue(pa.alpha.contentEquals(pb.alpha))
        assertTrue(pa.colorArgb.contentEquals(pb.colorArgb))
        assertTrue(pa.vxDp.contentEquals(pb.vxDp))
        assertTrue(pa.vyDp.contentEquals(pb.vyDp))
        assertTrue(pa.gravityDpPerSec2.contentEquals(pb.gravityDpPerSec2))
        assertTrue(pa.alphaFadePerMillis.contentEquals(pb.alphaFadePerMillis))
    }

    @Test
    fun `andere Seed erzeugt andere Partikel-Positionen`() {
        val seedA = enter(seed = 1L).step(0L, JuiceEvent.CrystalBursts(1, listOf(origin(0f, 0f))))
        val seedB = enter(seed = 2L).step(0L, JuiceEvent.CrystalBursts(1, listOf(origin(0f, 0f))))
        assertTrue(seedA.particles.count > 0)
        assertNotEquals(seedA.particles.vxDp.toList(), seedB.particles.vxDp.toList())
    }

    // --- Kapazität ----------------------------------------------------------

    @Test
    fun `Kapazitaet wird nie ueberschritten (Property-Test)`() {
        val rng = Random(seed = 777L)
        var s = enter(seed = 9L, endpoints = List(8) { endpoint(it.toFloat(), it.toFloat()) })
        repeat(400) { frame ->
            val events =
                buildList {
                    if (rng.nextInt(3) == 0) {
                        add(
                            JuiceEvent.CrystalBursts(
                                frame,
                                List(rng.nextInt(1, 60)) { origin(it.toFloat(), it.toFloat()) },
                            ),
                        )
                    }
                    if (rng.nextInt(5) == 0) {
                        add(
                            JuiceEvent.Solved(
                                frame,
                                crystalCount = rng.nextInt(1, 7),
                                paletteArgb = listOf(0xFFFFFFFF.toInt()),
                            ),
                        )
                    }
                }
            s = stepper.step(s, events, dtMillis = rng.nextLong(1L, 40L))
            assertTrue("Frame $frame: ${s.particles.count} > $MAX_PARTICLES", s.particles.count <= MAX_PARTICLES)
        }
    }

    // --- Lebensdauer --------------------------------------------------------

    @Test
    fun `Lebensdauer-Ende entfernt Partikel`() {
        var s = enter(seed = 3L).step(0L, JuiceEvent.CrystalBursts(1, listOf(origin(0f, 0f))))
        assertTrue(s.particles.count in 8..12) // P = 8 + nextInt(5)
        repeat(70) { s = s.step(10L) } // > 600 ms Kristall-Lebensdauer
        assertEquals(0, s.particles.count)
    }

    // --- Reduce-Motion ------------------------------------------------------

    @Test
    fun `Reduce-Motion erzeugt exakt 0 Partikel, aber Zustandswechsel bleiben`() {
        var s = enter(seed = 5L, reduceMotion = true, endpoints = listOf(endpoint(1f, 1f), endpoint(2f, 2f)))
        val events =
            listOf<JuiceEvent>(
                JuiceEvent.RotateFlash(0f, 0f, 30f),
                JuiceEvent.CrystalBursts(1, listOf(origin(0f, 0f), origin(1f, 1f))),
                JuiceEvent.Solved(1, crystalCount = 2, paletteArgb = listOf(0xFFFFFFFF.toInt())),
            )
        s = stepper.step(s, events, dtMillis = 16L)
        repeat(60) {
            s = s.step(16L)
            assertEquals("Reduce-Motion: immer 0 Partikel", 0, s.particles.count)
        }
        // Timings bleiben: das Feuerwerk wurde geplant und der Flash-Fade lief an.
        assertEquals(1f, s.haloPulseFactor, 0f) // Puls statisch
    }

    @Test
    fun `Reduce-Motion plant Bursts trotz 0 Partikeln (Timings bleiben)`() {
        val s =
            enter(seed = 5L, reduceMotion = true)
                .step(0L, JuiceEvent.CrystalBursts(1, List(3) { origin(it.toFloat(), it.toFloat()) }))
        // 3 Bursts, erster (Offset 0) feuert sofort mit 0 Partikeln, 2 verbleiben geplant.
        assertEquals(2, s.pendingBursts.size)
        assertEquals(0, s.particles.count)
    }

    // --- Kaskaden-Timing ----------------------------------------------------

    @Test
    fun `Kaskade versetzt 40 ms und kappt ab dem 5 Burst`() {
        val bursts = List(6) { origin(it.toFloat(), it.toFloat()) }
        val s = enter(seed = 7L).step(0L, JuiceEvent.CrystalBursts(1, bursts))
        // Burst 1 (Offset 0) feuert sofort; die restlichen 5 tragen 40/80/120/160/160 ms.
        assertEquals(listOf(40L, 80L, 120L, 160L, 160L), s.pendingBursts.map { it.startAtMillis }.sorted())
        assertTrue("Erster Burst muss gefeuert haben", s.particles.count > 0)
    }

    @Test
    fun `Feuerwerk startet mit dem letzten Burst der Kaskade (t_fw)`() {
        // N = 3 Bursts ⇒ t_fw = 40·(min(3,5)-1) = 80 ms; Ursprung = letzter Burst (9,9).
        val s =
            enter(seed = 8L).step(
                0L,
                JuiceEvent.CrystalBursts(2, listOf(origin(1f, 1f), origin(5f, 5f), origin(9f, 9f))),
                JuiceEvent.Solved(2, crystalCount = 3, paletteArgb = listOf(0xFFFFFFFF.toInt())),
            )
        val firework = s.pendingBursts.single { it.kind == BurstKind.FIREWORK }
        assertEquals(80L, firework.startAtMillis)
        assertEquals(9f, firework.xDp, 0f)
        assertEquals(9f, firework.yDp, 0f)
        assertEquals(FIREWORK_EMITTER_INDEX_EXPECTED, firework.emitterIndex)
    }

    @Test
    fun `Feuerwerks-Partikelzahl folgt F = min(120, 60 + 12K)`() {
        fun fireworkCount(k: Int): Int {
            // Zwei Bursts ⇒ t_fw = 40 ms > 0, das Feuerwerk bleibt zum Auslesen geplant.
            val s =
                enter(seed = 8L).step(
                    0L,
                    JuiceEvent.CrystalBursts(1, listOf(origin(0f, 0f), origin(1f, 1f))),
                    JuiceEvent.Solved(1, crystalCount = k, paletteArgb = listOf(0xFFFFFFFF.toInt())),
                )
            return s.pendingBursts.single { it.kind == BurstKind.FIREWORK }.particleCount
        }
        assertEquals(72, fireworkCount(1))
        assertEquals(96, fireworkCount(3))
        assertEquals(120, fireworkCount(6)) // 60 + 72 = 132 → gekappt auf 120
    }

    // --- Glow-Burst (§13.9, PW-4.6 / ADR-011-Delta) -------------------------

    @Test
    fun `Kristall-Burst erzeugt beim Feuern einen Glow der in 250 ms auslaeuft`() {
        var s = enter(seed = 3L).step(0L, JuiceEvent.CrystalBursts(1, listOf(origin(4f, 6f, argb = 0xFF3E63DD.toInt()))))
        val fresh = s.glows.single()
        assertEquals(4f, fresh.xDp, 0f)
        assertEquals(6f, fresh.yDp, 0f)
        assertEquals(0xFF3E63DD.toInt(), fresh.colorArgb)
        assertEquals(0f, fresh.radiusDp, 0f) // Radius startet bei 0 (§13.9)
        assertEquals(0.8f, fresh.alpha, 1e-4f) // Alpha startet bei 0,8 (§13.9)
        s = s.step(125L)
        val mid = s.glows.single()
        assertEquals(14f, mid.radiusDp, 1e-3f) // Halbzeit: 28 dp · 0,5
        assertEquals(0.4f, mid.alpha, 1e-3f) // Halbzeit: 0,8 · 0,5
        s = s.step(125L)
        assertTrue("Glow läuft nach 250 ms aus", s.glows.isEmpty())
    }

    @Test
    fun `Glows entstehen erst beim Feuern des jeweiligen Kaskaden-Bursts`() {
        var s = enter(seed = 7L).step(0L, JuiceEvent.CrystalBursts(1, List(3) { origin(it.toFloat(), 0f) }))
        assertEquals(1, s.glows.size) // nur Burst 1 (Offset 0) hat gefeuert
        s = s.step(40L)
        assertEquals(2, s.glows.size) // Burst 2 nach 40 ms (§13.9)
    }

    @Test
    fun `Reduce-Motion erzeugt keinen Glow (R44 in Verbindung mit Paragraf 13_12)`() {
        var s =
            enter(seed = 5L, reduceMotion = true)
                .step(0L, JuiceEvent.CrystalBursts(1, listOf(origin(0f, 0f))))
        repeat(10) {
            assertTrue("kein Glow unter Reduce-Motion", s.glows.isEmpty())
            s = s.step(40L)
        }
    }

    // --- Halo-Puls ----------------------------------------------------------

    @Test
    fun `haloPulseFactor pulsiert phasensynchron mit 2 Hz ab t=0`() {
        val base = enter(seed = 1L)
        assertEquals(1f, base.haloPulseFactor, 1e-4f) // t = 0 ⇒ sin(0) = 0
        assertEquals(1.2f, base.step(125L).haloPulseFactor, 1e-3f) // Viertelperiode ⇒ Maximum
        assertEquals(1f, base.step(250L).haloPulseFactor, 1e-3f) // Halbperiode ⇒ Nulldurchgang
        assertEquals(0.8f, base.step(375L).haloPulseFactor, 1e-3f) // Dreiviertel ⇒ Minimum
    }

    @Test
    fun `haloPulseFactor ist unter Reduce-Motion konstant 1`() {
        val s = enter(seed = 1L, reduceMotion = true).step(125L)
        assertEquals(1f, s.haloPulseFactor, 0f)
    }

    // --- Flash --------------------------------------------------------------

    private fun solveSingle(reduceMotion: Boolean): JuiceState =
        enter(seed = 1L, reduceMotion = reduceMotion).step(
            0L,
            JuiceEvent.CrystalBursts(1, listOf(origin(0f, 0f))),
            JuiceEvent.Solved(1, crystalCount = 1, paletteArgb = listOf(0xFFFFFFFF.toInt())),
        )

    @Test
    fun `flashAlpha faellt normal von 0,35 auf 0 ueber 80 ms`() {
        var s = solveSingle(reduceMotion = false)
        assertEquals(0.35f, s.flashAlpha, 1e-4f) // t_fw, Flash-Start
        s = s.step(40L)
        assertEquals(0.175f, s.flashAlpha, 1e-4f) // Hälfte
        s = s.step(40L)
        assertEquals(0f, s.flashAlpha, 1e-4f) // Ende
    }

    @Test
    fun `flashAlpha faehrt unter Reduce-Motion 0 auf 0,15 auf 0 (Dreieck, 400 ms)`() {
        var s = solveSingle(reduceMotion = true)
        assertEquals(0f, s.flashAlpha, 1e-4f) // Anstiegsbeginn
        s = s.step(200L)
        assertEquals(0.15f, s.flashAlpha, 1e-4f) // Scheitel bei 200 ms
        s = s.step(200L)
        assertEquals(0f, s.flashAlpha, 1e-4f) // Ende bei 400 ms
    }

    // --- Emitter & Dismiss --------------------------------------------------

    @Test
    fun `Endpunkt-Emitter erzeugen 4 Funken pro Sekunde`() {
        val s = enter(seed = 2L, endpoints = listOf(endpoint(0f, 0f), endpoint(9f, 9f))).step(250L)
        assertEquals(2, s.particles.count) // 1 Funke je Emitter nach einem 250-ms-Intervall
        assertEquals(1, s.emitters.first().spawnedCount)
    }

    @Test
    fun `Dismissed verwirft Partikel, Bursts und Flash sofort`() {
        var s = solveSingle(reduceMotion = false)
        s = s.step(0L, JuiceEvent.CrystalBursts(2, List(5) { origin(it.toFloat(), it.toFloat()) }))
        assertTrue(s.particles.count > 0 || s.pendingBursts.isNotEmpty())
        s = s.step(0L, JuiceEvent.Dismissed)
        assertEquals(0, s.particles.count)
        assertTrue(s.pendingBursts.isEmpty())
        assertTrue(s.glows.isEmpty())
        assertEquals(0f, s.flashAlpha, 0f)
    }

    @Test
    fun `MotionPreferenceChanged schaltet Spawns mid-session auf 0`() {
        var s = enter(seed = 4L, endpoints = listOf(endpoint(0f, 0f)))
        s = s.step(0L, JuiceEvent.MotionPreferenceChanged(reduceMotion = true))
        repeat(20) { s = s.step(250L) }
        assertEquals(0, s.particles.count)
    }

    private companion object {
        const val FIREWORK_EMITTER_INDEX_EXPECTED = 2000
    }
}
