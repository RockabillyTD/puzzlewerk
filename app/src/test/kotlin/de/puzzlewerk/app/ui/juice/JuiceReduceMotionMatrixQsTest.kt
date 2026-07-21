package de.puzzlewerk.app.ui.juice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * PW-4.9-QS, Pflichtpunkt 3 (R44/§13.12): Reduce-Motion-Matrix — jeder
 * Effektpfad EINZELN (Dreh-Funken, Endpunkt-Funken, Kristall-Burst/Glow,
 * Feuerwerk/Flash) plus Mid-Session-Umschaltung in BEIDE Richtungen.
 * Kompose-Pfade (Sterne-Fade, Dreh-Blitz-Overlay, statischer Halo im Treiber)
 * sind in GameStarAnimationTest / GameRotationAnimationFlashTest /
 * JuiceFrameDriverTest gepinnt — hier ausschließlich der pure Stepper.
 */
class JuiceReduceMotionMatrixQsTest {
    private val stepper = DefaultJuiceStepper()

    private fun enter(
        reduceMotion: Boolean,
        endpoints: List<EndpointSpark> = emptyList(),
    ): JuiceState =
        stepper.step(
            JuiceState.EMPTY,
            listOf(JuiceEvent.ScreenEntered(11L, reduceMotion, endpoints)),
            dtMillis = 0L,
        )

    private fun JuiceState.step(
        dt: Long,
        vararg events: JuiceEvent,
    ): JuiceState = stepper.step(this, events.toList(), dt)

    @Test
    fun `RM einzeln - Dreh-Funken entfallen komplett`() {
        val s = enter(reduceMotion = true).step(0L, JuiceEvent.RotateFlash(0f, 0f, 0f))
        assertEquals(0, s.particles.count)
    }

    @Test
    fun `RM einzeln - Endpunkt-Emitter bleiben registriert, funken aber nie`() {
        var s = enter(reduceMotion = true, endpoints = List(3) { EndpointSpark(it.toFloat(), 0f, RED) })
        assertEquals("Emitter-Liste bleibt (Zustand, kein Spawn)", 3, s.emitters.size)
        repeat(8) { s = s.step(250L) }
        assertEquals(0, s.particles.count)
        assertEquals("spawnedCount rueckt nicht vor", 0, s.emitters.first().spawnedCount)
    }

    @Test
    fun `RM einzeln - Kaskaden-Zeitpunkte sind identisch zur Vollbewegung (R44)`() {
        val bursts = List(6) { BurstOrigin(it.toFloat(), 0f, RED) }
        val full = enter(reduceMotion = false).step(0L, JuiceEvent.CrystalBursts(1, bursts))
        val reduced = enter(reduceMotion = true).step(0L, JuiceEvent.CrystalBursts(1, bursts))
        assertEquals(
            "Kaskaden-Plan (40 ms, Kappe ab dem 5.) unveraendert",
            full.pendingBursts.map { it.startAtMillis },
            reduced.pendingBursts.map { it.startAtMillis },
        )
        assertEquals("aber 0 Partikel je Burst", 0, reduced.pendingBursts.sumOf { it.particleCount })
    }

    @Test
    fun `RM einzeln - Feuerwerk plant zum gleichen t_fw, spawnt aber nichts`() {
        val bursts = List(3) { BurstOrigin(it.toFloat(), 0f, RED) }
        val solved = JuiceEvent.Solved(1, crystalCount = 3, paletteArgb = listOf(RED))
        val full = enter(reduceMotion = false).step(0L, JuiceEvent.CrystalBursts(1, bursts), solved)
        val reduced = enter(reduceMotion = true).step(0L, JuiceEvent.CrystalBursts(1, bursts), solved)
        val fullFw = full.pendingBursts.single { it.kind == BurstKind.FIREWORK }
        val reducedFw = reduced.pendingBursts.single { it.kind == BurstKind.FIREWORK }
        assertEquals("t_fw identisch (R44: Zeitpunkte unveraendert)", fullFw.startAtMillis, reducedFw.startAtMillis)
        assertEquals(0, reducedFw.particleCount)
        assertTrue("Ursprung identisch", fullFw.xDp == reducedFw.xDp && fullFw.yDp == reducedFw.yDp)
    }

    @Test
    fun `Mid-Session RM aus - Spawns setzen wieder ein und der Puls traegt die volle Phase`() {
        var s = enter(reduceMotion = true, endpoints = listOf(EndpointSpark(0f, 0f, RED)))
        repeat(4) { s = s.step(100L) } // 400 ms unter RM: keine Funken
        assertEquals(0, s.particles.count)
        s = s.step(0L, JuiceEvent.MotionPreferenceChanged(reduceMotion = false))
        s = s.step(225L) // ueberquert die naechste 250-ms-Kadenz (625)
        assertTrue("Funken setzen wieder ein", s.particles.count > 0)
        // Phasen-Kontinuitaet: Faktor entspricht sin(2*pi*2Hz*elapsed) der GESAMT-Zeitachse.
        val expected = 1f + 0.2f * sin((2.0 * PI * 2.0 * (s.elapsedMillis / 1000.0)).toFloat())
        assertEquals("Puls-Nullpunkt bleibt ScreenEntered (kein Reset)", expected, s.haloPulseFactor, 1e-3f)
    }

    @Test
    fun `Mid-Session RM an - lebende Partikel laufen natuerlich aus statt zu verschwinden`() {
        var s = enter(reduceMotion = false).step(0L, JuiceEvent.CrystalBursts(1, listOf(BurstOrigin(0f, 0f, RED))))
        val alive = s.particles.count
        assertTrue(alive in 8..12)
        s = s.step(16L, JuiceEvent.MotionPreferenceChanged(reduceMotion = true))
        assertEquals("Bestand bleibt zunaechst (kein Sofort-Clear)", alive, s.particles.count)
        repeat(45) { s = s.step(16L) } // > 600 ms Kristall-Lebensdauer
        assertEquals("Bestand ist natuerlich gestorben", 0, s.particles.count)
    }

    /**
     * Dokumentierender IST-Pin: Schaltet R44 WÄHREND eines laufenden 80-ms-Flashs
     * um, wechselt die Alpha-Projektion sofort auf die 400-ms-Dreieckskurve —
     * die Restzeit (40 ms) wird dort als Spaetphase interpretiert (Alpha 0,03
     * statt 0,175). Kein Crash, kein negatives Alpha; §13 definiert diese
     * Uebergangskante nicht — Bewertung liegt beim Gate (PW-4.10-Checkliste).
     */
    @Test
    fun `IST-Pin - RM-Umschaltung mitten im Flash wechselt die Projektionskurve hart`() {
        var s =
            enter(reduceMotion = false).step(
                0L,
                JuiceEvent.CrystalBursts(1, listOf(BurstOrigin(0f, 0f, RED))),
                JuiceEvent.Solved(1, crystalCount = 1, paletteArgb = listOf(RED)),
            )
        assertEquals(0.35f, s.flashAlpha, 1e-4f)
        s = s.step(40L)
        assertEquals(0.175f, s.flashAlpha, 1e-4f)
        s = s.step(0L, JuiceEvent.MotionPreferenceChanged(reduceMotion = true))
        assertEquals("Restzeit 40 ms auf der Dreieckskurve: 0,15 * 40/200", 0.03f, s.flashAlpha, 1e-4f)
        s = s.step(40L)
        assertEquals("laeuft definiert auf 0 aus", 0f, s.flashAlpha, 1e-4f)
        assertTrue(s.flashAlpha >= 0f)
    }

    private companion object {
        val RED = 0xFFE5484D.toInt()
    }
}
