package de.puzzlewerk.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PW-4.9-QS, Pflichtpunkt 4 (Audio-Kanten aus dem PW-4.8-Handover): Settings-
 * Matrix Musik×SFX inkl. Partie-Wechsel, Fokus-Verlust MITTEN im Stem-Fade
 * (Fortsetzung bit-identisch zur ununterbrochenen Referenz), Session-Token-
 * Kanten (Re-Enter während pump-Backoff, Doppel-exitGame, Fokus-Callback einer
 * invalidierten Session). Fakes nach dem Muster aus DefaultAudioEngineTest;
 * kein Thread läuft je an (DormantThread), Tests pumpen selbst.
 */
class DefaultAudioEngineQsTest {
    private companion object {
        const val TONE: Short = 10_000
    }

    private class FakeSink : PcmSink {
        var playCount = 0
        var pauseCount = 0
        var released = false
        val writes = mutableListOf<ShortArray>()

        override fun play() {
            playCount++
        }

        override fun pause() {
            pauseCount++
        }

        override fun write(
            buffer: ShortArray,
            frames: Int,
        ) {
            writes += buffer.copyOf(frames)
        }

        override fun release() {
            released = true
        }
    }

    private class FakeSfxPlayer : SfxPlayer {
        override val available = true
        val played = mutableListOf<SoundEffect>()
        val laserCalls = mutableListOf<Boolean>()

        override fun play(effect: SoundEffect) {
            played += effect
        }

        override fun setLaserLoopActive(active: Boolean) {
            laserCalls += active
        }

        override fun release() = Unit
    }

    private class FakeFocus : FocusRequester {
        var requested = false
        var abandonCount = 0
        var onFocusChange: ((Boolean) -> Unit)? = null

        override fun request(onFocusChange: (Boolean) -> Unit): Boolean {
            requested = true
            this.onFocusChange = onFocusChange
            return true
        }

        override fun abandon() {
            abandonCount++
        }
    }

    private class Harness {
        val sinks = mutableListOf<FakeSink>()
        val sfx = FakeSfxPlayer()
        val focus = FakeFocus()
        val engine =
            DefaultAudioEngine(
                decoder = { ShortArray(STEM_LOOP_FRAMES) { TONE } },
                sinkFactory = { FakeSink().also { sinks += it } },
                sfxPlayer = sfx,
                focus = focus,
                mixerThreadFactory = { DormantThread(it) },
            )

        fun enterAndPrepare(
            musicEnabled: Boolean = true,
            sfxEnabled: Boolean = true,
        ) {
            engine.enterGame(musicEnabled, sfxEnabled)
            engine.activeSession?.prepare()
        }

        fun pump(times: Int = 1) {
            repeat(times) { engine.activeSession?.pump() }
        }
    }

    private class DormantThread(
        runnable: Runnable,
    ) : Thread(runnable) {
        override fun start() {
            // Tests pumpen selbst (Determinismus, Muster DefaultAudioEngineTest).
        }
    }

    // --- Settings-Matrix Musik × SFX (R48) ----------------------------------

    @Test
    fun `Settings-Matrix - nur Musik AN erzeugt einen Mixer, nur SFX AN erreicht den Player`() {
        for (music in listOf(true, false)) {
            for (sfx in listOf(true, false)) {
                val h = Harness()
                h.enterAndPrepare(musicEnabled = music, sfxEnabled = sfx)
                h.engine.playSfx(SoundEffect.UI_TAP)
                h.engine.setLaserLoopActive(true)
                assertEquals("Musik=$music: Sink-Erzeugung", music, h.sinks.isNotEmpty())
                assertEquals("Musik=$music: Fokus-Anforderung", music, h.focus.requested)
                assertEquals("SFX=$sfx: playSfx", sfx, h.sfx.played.isNotEmpty())
                assertEquals("SFX=$sfx: Laser-Loop", sfx, h.sfx.laserCalls.contains(true))
            }
        }
    }

    @Test
    fun `Partie-Wechsel - enterGame nach exitGame startet eine frische Session ab Sample 0`() {
        val h = Harness()
        h.enterAndPrepare()
        h.pump(3)
        h.engine.exitGame()
        assertTrue(h.sinks[0].released)
        h.enterAndPrepare() // naechste Partie (R49: Stems starten neu)
        h.pump()
        assertEquals("eigener Sink je Session (MAJOR-1-Architektur)", 2, h.sinks.size)
        assertEquals(1, h.sinks[1].writes.size)
        assertEquals("Ebene 1 sofort wieder mit 100 %", TONE, h.sinks[1].writes[0][0])
    }

    // --- Fokus-Verlust mitten im Stem-Fade (R47 + §13.11) -------------------

    @Test
    fun `Fokus-Verlust mitten im Fade - Fortsetzung ist bit-identisch zur ununterbrochenen Referenz`() {
        val reference = Harness()
        reference.enterAndPrepare()
        reference.engine.setStemMix(StemMix(1f, 1f, 0f, 0f))
        reference.pump(6)

        val h = Harness()
        h.enterAndPrepare()
        h.engine.setStemMix(StemMix(1f, 1f, 0f, 0f))
        h.pump(3) // Fade ist nach 3 Bloecken (2646 von 11025 Frames) mitten im Anstieg
        requireNotNull(h.focus.onFocusChange).invoke(false)
        h.pump(2) // pausiert: kein Block darf geschrieben werden
        assertEquals("kein Write waehrend Fokus-Verlust", 3, h.sinks[0].writes.size)
        requireNotNull(h.focus.onFocusChange).invoke(true)
        h.pump(3)
        assertEquals(6, h.sinks[0].writes.size)
        for (block in 0 until 6) {
            assertTrue(
                "Block $block bit-identisch (Fade + Cursor frieren gemeinsam ein, R47)",
                reference.sinks[0].writes[block].contentEquals(h.sinks[0].writes[block]),
            )
        }
    }

    // --- Session-Token-Kanten (PW-4.8-Handover) -----------------------------

    @Test
    fun `Re-Enter waehrend pump-Backoff - der alte Pump beruehrt keine Session mehr`() {
        val h = Harness()
        h.enterAndPrepare()
        val oldSession = requireNotNull(h.engine.activeSession)
        h.engine.setHostVisible(false)
        oldSession.pump() // laeuft in den Backoff-Zweig (kein Write)
        h.engine.exitGame()
        h.engine.enterGame(musicEnabled = true, sfxEnabled = true)
        h.engine.setHostVisible(true)
        requireNotNull(h.engine.activeSession).prepare()
        oldSession.pump() // verspaeteter Pump der invalidierten Session A
        assertEquals("Session A hat nie geschrieben", 0, h.sinks[0].writes.size)
        assertEquals("Session B unberuehrt vom alten Pump", 0, h.sinks[1].writes.size)
        h.pump()
        assertEquals("Session B pumpt normal", 1, h.sinks[1].writes.size)
        assertEquals(TONE, h.sinks[1].writes[0][0])
    }

    @Test
    fun `Doppel-exitGame ist idempotent - kein Crash, SFX und Folge-Partie funktionieren`() {
        val h = Harness()
        h.enterAndPrepare()
        h.engine.exitGame()
        h.engine.exitGame() // zweiter Aufruf (z. B. Navigation + Lifecycle-Race)
        h.engine.playSfx(SoundEffect.UI_TAP)
        assertEquals(listOf(SoundEffect.UI_TAP), h.sfx.played)
        h.enterAndPrepare()
        h.pump()
        assertEquals(1, h.sinks[1].writes.size)
    }

    /**
     * BUG-PW4.9-1 (behoben in PW-4.9-FIX): Ein VERSPAETETER
     * Fokus-Verlust-Callback der bereits per `exitGame` invalidierten Session
     * setzte das engine-globale `focusLost` erneut auf `true`, obwohl keine
     * Session existierte und `focus.abandon()` längst gerufen wurde. Folge:
     * Menü-/UI-SFX stumm bis zum nächsten `enterGame` — die MINOR-1-Regression
     * (PW-4.8-Korrekturrunde) über die Callback-Schiene. Fix: der Callback ist
     * an das Session-Token seines `enterGame` gebunden; Callbacks
     * invalidierter Sessions werden verworfen (R47/R48).
     */
    @Test
    fun `Fokus-Callback einer invalidierten Session darf Menue-SFX nicht stummschalten`() {
        val h = Harness()
        h.enterAndPrepare()
        val staleCallback = requireNotNull(h.focus.onFocusChange)
        h.engine.exitGame()
        staleCallback(false) // System liefert den Verlust nach dem Abandon nach
        h.engine.playSfx(SoundEffect.UI_TAP)
        assertEquals("Menue-SFX trotz verspaetetem Callback", listOf(SoundEffect.UI_TAP), h.sfx.played)
    }

    @Test
    fun `Fokus-Callback einer invalidierten Session pausiert nichts und crasht nicht (IST-Pin)`() {
        val h = Harness()
        h.enterAndPrepare()
        val staleCallback = requireNotNull(h.focus.onFocusChange)
        val oldSink = h.sinks[0]
        h.engine.exitGame()
        val pausesAfterExit = oldSink.pauseCount
        staleCallback(false)
        staleCallback(true)
        assertEquals("kein Pause/Play auf dem freigegebenen Sink", pausesAfterExit, oldSink.pauseCount)
        assertFalse("Laser-Loop bleibt aus", h.sfx.laserCalls.lastOrNull() == true)
    }
}
