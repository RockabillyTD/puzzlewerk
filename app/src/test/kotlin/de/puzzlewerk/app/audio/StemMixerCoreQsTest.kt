package de.puzzlewerk.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PW-4.9-QS, Pflichtpunkt 4 (§13.11, R46/R50): Ebenen-Schwellen als Property
 * über ALLE K ∈ 1..50 beidseitig (inkl. K = 50 an der 48-%/50-%-Kante) sowie
 * die im PW-4.8-Handover delegierten Duck-Kanten — Duck WÄHREND eines
 * laufenden Fades (Multiplikation, R50) und Duck-Retrigger in der
 * Attack-Phase (klickfrei). Deterministisch, keine Uhr, kein Ton.
 */
class StemMixerCoreQsTest {
    private companion object {
        const val TONE: Short = 10_000
        const val SMALL_LOOP = 100
    }

    private fun constantStem(value: Short): ShortArray = ShortArray(SMALL_LOOP) { value }

    private fun StemMixerCore.advance(frames: Int) {
        val buffer = ShortArray(MIX_BLOCK_FRAMES)
        var left = frames
        while (left > 0) {
            val chunk = minOf(left, buffer.size)
            mixInto(buffer, chunk)
            left -= chunk
        }
    }

    // --- Ebenen-Schwellen als Property (Tabelle §13.11) ---------------------

    @Test
    fun `Schwellen-Property - alle K bis 50, jede Ebene beidseitig exakt nach Tabelle`() {
        for (k in 1..50) {
            for (l in 0..k) {
                val mix = StemMix.forProgress(l, k)
                assertEquals("K=$k L=$l Ebene 1", 1f, mix.stem1Urig, 0f)
                assertEquals("K=$k L=$l Ebene 2", if (l >= 1) 1f else 0f, mix.stem2Kalimba, 0f)
                assertEquals("K=$k L=$l Ebene 3 (2L >= K)", if (2 * l >= k) 1f else 0f, mix.stem3Bass, 0f)
                val lastMissing = maxOf(1, k - 1)
                assertEquals("K=$k L=$l Ebene 4", if (l >= lastMissing) 1f else 0f, mix.stem4Modern, 0f)
            }
        }
    }

    @Test
    fun `K gleich 50 - 49 Prozent bleibt still, exakt 50 Prozent schaltet Ebene 3`() {
        assertEquals(0f, StemMix.forProgress(24, 50).stem3Bass, 0f) // 48 % < 50 %
        assertEquals(1f, StemMix.forProgress(25, 50).stem3Bass, 0f) // exakt 50 %
        assertEquals(0f, StemMix.forProgress(24, 49).stem3Bass, 0f) // 2*24 = 48 < 49
        assertEquals(1f, StemMix.forProgress(25, 49).stem3Bass, 0f) // 2*25 = 50 >= 49
    }

    // --- Duck-Kanten (PW-4.8-Handover) --------------------------------------

    @Test
    fun `Duck waehrend eines laufenden Fades multipliziert sich mit der Fade-Lautstaerke (R50)`() {
        val core = StemMixerCore(listOf(null, constantStem(TONE), null, null), SMALL_LOOP)
        core.setTargets(StemMix(1f, 1f, 0f, 0f)) // Ebene 2 fadet 0 -> 1
        core.advance(FADE_FRAMES / 4) // mitten im Fade (~25 %)
        core.startDuck()
        core.advance(DUCK_ATTACK_FRAMES / 2) // mitten in der Attack — Fade laeuft parallel weiter
        val vol = core.volumeAt(1)
        val duck = core.duckFactor()
        assertTrue("Fade laeuft noch: $vol", vol > 0f && vol < 1f)
        assertTrue("Duck greift schon: $duck", duck > DUCK_LEVEL && duck < 1f)
        val out = ShortArray(1)
        core.mixInto(out, 1)
        assertEquals("Sample = Ton * Fade-Volumen * Duck-Faktor", ((TONE * vol) * duck).toInt().toShort(), out[0])
    }

    @Test
    fun `Duck-Retrigger in der Attack-Phase setzt klickfrei am aktuellen Faktor auf`() {
        val core = StemMixerCore(listOf(constantStem(TONE), null, null, null), SMALL_LOOP)
        core.startDuck()
        core.advance(DUCK_ATTACK_FRAMES / 2)
        val midAttack = core.duckFactor()
        assertTrue("mitten im Abstieg", midAttack < 1f && midAttack > DUCK_LEVEL)
        core.startDuck() // Retrigger BEVOR die Attack durch ist
        assertEquals("kein Sprung beim Retrigger", midAttack, core.duckFactor(), 0f)
        core.advance(DUCK_ATTACK_FRAMES)
        assertEquals("Attack laeuft vom Retrigger-Punkt sauber auf 20 %", DUCK_LEVEL, core.duckFactor(), 0f)
        core.advance(DUCK_HOLD_FRAMES + DUCK_RELEASE_FRAMES)
        assertEquals("Envelope endet vollstaendig", 1f, core.duckFactor(), 0f)
    }

    @Test
    fun `Fade-Retarget mitten im Fade startet klickfrei an der aktuellen Lautstaerke`() {
        val core = StemMixerCore(listOf(null, constantStem(TONE), null, null), SMALL_LOOP)
        core.setTargets(StemMix(1f, 1f, 0f, 0f))
        core.advance(FADE_FRAMES / 2)
        val mid = core.volumeAt(1)
        assertEquals(0.5f, mid, 1e-3f)
        core.setTargets(StemMix(1f, 0f, 0f, 0f)) // R46: sofort abwaerts, kein Gedaechtnis
        assertEquals("Startpunkt = aktueller Wert (kein Klick)", mid, core.volumeAt(1), 1e-4f)
        core.advance(FADE_FRAMES)
        assertEquals(0f, core.volumeAt(1), 0f)
    }
}
