package de.puzzlewerk.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Purer Mixer-Kern (ADR-010) deterministisch auf der JVM: sample-exakter
 * Loop über 756 000 Frames, linearer 250-ms-Fade, Duck-Envelope 50/500/250
 * mit Faktor 0,2 (§13.11) — kein echter Ton, keine Uhr, keine Threads.
 */
class StemMixerCoreTest {
    private companion object {
        const val TONE: Short = 10_000
    }

    private fun constantStem(
        value: Short,
        frames: Int = STEM_LOOP_FRAMES,
    ): ShortArray = ShortArray(frames) { value }

    private fun coreWithStem1(
        stem: ShortArray,
        loopFrames: Int = stem.size,
    ): StemMixerCore = StemMixerCore(listOf(stem, null, null, null), loopFrames)

    private fun StemMixerCore.advance(frames: Int) {
        val buffer = ShortArray(MIX_BLOCK_FRAMES)
        var left = frames
        while (left > 0) {
            val chunk = minOf(left, buffer.size)
            mixInto(buffer, chunk)
            left -= chunk
        }
    }

    @Test
    fun `Loop ist sample-exakt - Frame 756000 ist wieder Frame 0`() {
        val stem = ShortArray(STEM_LOOP_FRAMES)
        stem[0] = 111
        stem[STEM_LOOP_FRAMES - 1] = 222
        val core = coreWithStem1(stem)
        core.advance(STEM_LOOP_FRAMES - 1)
        val out = ShortArray(2)
        core.mixInto(out, 2)
        assertEquals(222.toShort(), out[0]) // letzter Frame des Loops
        assertEquals(111.toShort(), out[1]) // sample-exakter Wiedereinstieg bei 0
        assertEquals(1, core.cursor)
    }

    @Test
    fun `Ebene 1 spielt ohne Fade sofort mit voller Lautstaerke`() {
        val core = coreWithStem1(constantStem(TONE, frames = 100), loopFrames = 100)
        val out = ShortArray(10)
        core.mixInto(out, 10)
        assertEquals(TONE, out[0])
        assertEquals(TONE, out[9])
    }

    @Test
    fun `Fade laeuft linear und endet nach exakt 11025 Frames (250 ms)`() {
        val core =
            StemMixerCore(
                listOf(constantStem(0, 100), constantStem(TONE, 100), null, null),
                loopFrames = 100,
            )
        core.setTargets(StemMix(1f, 1f, 0f, 0f))
        assertEquals(0f, core.volumeAt(1), 0f)
        core.advance(FADE_FRAMES / 2)
        assertEquals(0.5f, core.volumeAt(1), 1e-3f)
        core.advance(FADE_FRAMES - FADE_FRAMES / 2 - 1)
        assertTrue(core.volumeAt(1) < 1f) // Frame 11024: noch nicht am Ziel
        core.advance(1)
        assertEquals(1f, core.volumeAt(1), 0f) // Frame 11025: exakt am Ziel
    }

    @Test
    fun `Fade abwaerts folgt sofort - keine Hysterese (R46)`() {
        val core = coreWithStem1(constantStem(TONE, 100), loopFrames = 100)
        core.setTargets(StemMix(1f, 0f, 0f, 0f))
        core.setTargets(StemMix(0f, 0f, 0f, 0f))
        core.advance(FADE_FRAMES)
        assertEquals(0f, core.volumeAt(0), 0f)
    }

    @Test
    fun `Duck-Envelope - 50 ms Attack auf 0_2, 500 ms halten, 250 ms zurueck`() {
        val core = coreWithStem1(constantStem(TONE, 100), loopFrames = 100)
        core.startDuck()
        assertEquals(1f, core.duckFactor(), 0f) // Retrigger-Start am aktuellen Faktor
        core.advance(DUCK_ATTACK_FRAMES)
        assertEquals(DUCK_LEVEL, core.duckFactor(), 0f) // nach 50 ms: 20 %
        core.advance(DUCK_HOLD_FRAMES)
        assertEquals(DUCK_LEVEL, core.duckFactor(), 0f) // Ende der Haltephase
        core.advance(DUCK_RELEASE_FRAMES - 1)
        assertTrue(core.duckFactor() < 1f) // ein Frame vor Ende
        core.advance(1)
        assertEquals(1f, core.duckFactor(), 0f) // exakt nach 800 ms vorbei
    }

    @Test
    fun `Duck-Faktor multipliziert sich mit der Ebenen-Lautstaerke (R50)`() {
        val core = coreWithStem1(constantStem(TONE, 100), loopFrames = 100)
        core.startDuck()
        core.advance(DUCK_ATTACK_FRAMES)
        val out = ShortArray(1)
        core.mixInto(out, 1)
        assertEquals((TONE * DUCK_LEVEL).toInt().toShort(), out[0])
    }

    @Test
    fun `Duck-Retrigger setzt klickfrei am aktuellen Faktor neu auf`() {
        val core = coreWithStem1(constantStem(TONE, 100), loopFrames = 100)
        core.startDuck()
        core.advance(DUCK_ATTACK_FRAMES + DUCK_HOLD_FRAMES + DUCK_RELEASE_FRAMES / 2)
        val midRelease = core.duckFactor()
        assertTrue(midRelease > DUCK_LEVEL && midRelease < 1f)
        core.startDuck()
        assertEquals(midRelease, core.duckFactor(), 0f) // kein Sprung
    }

    @Test
    fun `fehlende Stems werden als Stille gemischt (AssetUnavailable-Pfad)`() {
        val core = StemMixerCore(listOf(null, null, null, null), loopFrames = 100)
        val out = ShortArray(4) { 99 }
        core.mixInto(out, 4)
        assertEquals(listOf<Short>(0, 0, 0, 0), out.toList())
    }

    @Test
    fun `Summe wird geklammert statt uebersteuert`() {
        val loud = constantStem(30_000, 100)
        val core = StemMixerCore(listOf(loud, loud, null, null), loopFrames = 100)
        core.setTargets(StemMix(1f, 1f, 0f, 0f))
        core.advance(FADE_FRAMES)
        val out = ShortArray(1)
        core.mixInto(out, 1)
        assertEquals(Short.MAX_VALUE, out[0])
    }
}
