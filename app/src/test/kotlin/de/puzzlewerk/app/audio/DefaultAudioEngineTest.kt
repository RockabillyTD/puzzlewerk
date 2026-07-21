package de.puzzlewerk.app.audio

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DefaultAudioEngine] gegen die vier Adapter-Fakes (ADR-010): kein echter
 * Ton, kein echter Thread — die Tests treiben die [DefaultAudioEngine.MixerSession]
 * über `prepare()`/`pump()` selbst und prüfen R47/R48/R49, die Fehlerwerte
 * (C3) sowie die Session-Token-Isolation (Review PW-4.8, MAJOR-1). Der
 * Mixer-Thread wird über die Factory stillgelegt.
 */
class DefaultAudioEngineTest {
    private companion object {
        const val TONE: Short = 10_000
    }

    private class FakeDecoder(
        val failing: Set<String> = emptySet(),
        val fill: (Int) -> Short = { TONE },
        val onDecode: (String) -> Unit = {},
    ) : StemDecoder {
        val decoded = mutableListOf<String>()

        override fun decode(resourceName: String): ShortArray? {
            decoded += resourceName
            onDecode(resourceName)
            if (resourceName in failing) return null
            return ShortArray(STEM_LOOP_FRAMES) { fill(it) }
        }
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

    private class FakeSfxPlayer(
        override val available: Boolean = true,
    ) : SfxPlayer {
        val played = mutableListOf<SoundEffect>()
        val laserCalls = mutableListOf<Boolean>()
        var released = false

        override fun play(effect: SoundEffect) {
            played += effect
        }

        override fun setLaserLoopActive(active: Boolean) {
            laserCalls += active
        }

        override fun release() {
            released = true
        }
    }

    private class FakeFocus(
        val granted: Boolean = true,
    ) : FocusRequester {
        var requested = false
        var abandoned = false
        var onFocusChange: ((Boolean) -> Unit)? = null

        override fun request(onFocusChange: (Boolean) -> Unit): Boolean {
            requested = true
            this.onFocusChange = onFocusChange
            return granted
        }

        override fun abandon() {
            abandoned = true
        }
    }

    private class Harness(
        decoder: FakeDecoder = FakeDecoder(),
        val focus: FakeFocus = FakeFocus(),
        sinkAvailable: Boolean = true,
        sfxAvailable: Boolean = true,
    ) {
        /** Ein Sink JE Session (MAJOR-1); bestehende Tests nutzen den ersten. */
        val sinks = mutableListOf<FakeSink>()
        val sink: FakeSink get() = sinks.first()
        val sfx = FakeSfxPlayer(available = sfxAvailable)
        var sinkRequested = false

        /** Thread-Bodies je Session in Startreihenfolge (Race-Test MAJOR-1). */
        val runnables = mutableListOf<Runnable>()
        val engine =
            DefaultAudioEngine(
                decoder = decoder,
                sinkFactory = {
                    sinkRequested = true
                    if (sinkAvailable) FakeSink().also { sinks += it } else null
                },
                sfxPlayer = sfx,
                focus = focus,
                mixerThreadFactory = { runnable ->
                    runnables += runnable
                    DormantThread(runnable)
                },
            )

        fun enterAndPrepare(
            musicEnabled: Boolean = true,
            sfxEnabled: Boolean = true,
        ) {
            engine.enterGame(musicEnabled, sfxEnabled)
            engine.activeSession?.prepare()
        }

        fun pump() {
            engine.activeSession?.pump()
        }
    }

    private class DormantThread(
        runnable: Runnable,
    ) : Thread(runnable) {
        override fun start() {
            // Tests pumpen selbst — kein echter Mixer-Thread (Determinismus).
        }
    }

    @Test
    fun `Musik AUS - kein Mixer, kein Fokus-Request (R48)`() {
        val harness = Harness()
        harness.engine.enterGame(musicEnabled = false, sfxEnabled = true)
        assertFalse(harness.sinkRequested)
        assertFalse(harness.focus.requested)
        harness.engine.setStemMix(StemMix.BASE) // No-ops duerfen nie crashen
        harness.engine.duckForSolve()
        harness.pump()
    }

    @Test
    fun `Musik AN - Fokus angefordert, Senke gestartet, Bloecke geschrieben`() {
        val harness = Harness()
        harness.enterAndPrepare()
        assertTrue(harness.focus.requested)
        assertEquals(1, harness.sink.playCount)
        harness.pump()
        assertEquals(1, harness.sink.writes.size)
        assertEquals(MIX_BLOCK_FRAMES, harness.sink.writes[0].size)
        assertEquals(TONE, harness.sink.writes[0][0]) // Ebene 1 sofort mit 100 %
    }

    @Test
    fun `Effekte AUS - playSfx und Laser-Loop erreichen den Player nicht (R48)`() {
        val harness = Harness()
        harness.enterAndPrepare(sfxEnabled = false)
        harness.engine.playSfx(SoundEffect.ROTATE_TICK)
        harness.engine.setLaserLoopActive(true)
        assertEquals(emptyList<SoundEffect>(), harness.sfx.played)
        assertEquals(emptyList<Boolean>(), harness.sfx.laserCalls)
    }

    @Test
    fun `Fokus-Verlust pausiert die Senke, stoppt den Laser und sperrt neue SFX (R47)`() =
        runTest {
            val harness = Harness()
            harness.engine.issues.test {
                harness.enterAndPrepare()
                harness.engine.setLaserLoopActive(true)
                requireNotNull(harness.focus.onFocusChange).invoke(false)
                assertEquals(AudioIssue.FocusLost, awaitItem())
                assertEquals(1, harness.sink.pauseCount)
                assertEquals(listOf(true, false), harness.sfx.laserCalls)
                harness.engine.playSfx(SoundEffect.UI_TAP)
                assertEquals(emptyList<SoundEffect>(), harness.sfx.played)
            }
        }

    @Test
    fun `Fokus-Rueckkehr setzt exakt an der pausierten Cursor-Position fort (R47)`() =
        runTest {
            val decoder = FakeDecoder(fill = { (it % 100).toShort() })
            val harness = Harness(decoder = decoder)
            harness.engine.issues.test {
                harness.enterAndPrepare()
                harness.pump()
                requireNotNull(harness.focus.onFocusChange).invoke(false)
                assertEquals(AudioIssue.FocusLost, awaitItem())
                requireNotNull(harness.focus.onFocusChange).invoke(true)
                assertEquals(AudioIssue.FocusRegained, awaitItem())
                assertEquals(2, harness.sink.playCount)
                harness.pump()
                val expected = (MIX_BLOCK_FRAMES % 100).toShort()
                assertEquals(expected, harness.sink.writes[1][0]) // kein Neustart bei 0
            }
        }

    @Test
    fun `exitGame - Stems stoppen, Fokus frei, Einmal-SFX klingen aus (R49)`() {
        val harness = Harness()
        harness.enterAndPrepare()
        harness.engine.setLaserLoopActive(true)
        harness.engine.playSfx(SoundEffect.CRYSTAL_LIT)
        harness.engine.exitGame()
        assertTrue(harness.sink.released)
        assertTrue(harness.focus.abandoned)
        assertEquals(listOf(true, false), harness.sfx.laserCalls) // Laser endet
        assertFalse(harness.sfx.released) // SoundPool lebt weiter: Ausklingen
        assertEquals(listOf(SoundEffect.CRYSTAL_LIT), harness.sfx.played)
    }

    @Test
    fun `exitGame setzt focusLost zurueck - Menue-SFX bleiben nicht stumm (MINOR-1)`() {
        val harness = Harness()
        harness.enterAndPrepare()
        requireNotNull(harness.focus.onFocusChange).invoke(false)
        harness.engine.exitGame()
        harness.engine.playSfx(SoundEffect.UI_TAP)
        assertEquals(listOf(SoundEffect.UI_TAP), harness.sfx.played)
    }

    @Test
    fun `Exit und Re-Enter waehrend des Decodes - alter Lauf beruehrt die neue Session nie (MAJOR-1)`() {
        lateinit var harness: Harness
        var raced = false
        val decoder =
            FakeDecoder(
                onDecode = {
                    if (!raced) {
                        raced = true // mitten im Erst-Decode: Exit + sofortiges Re-Enter
                        harness.engine.exitGame()
                        harness.engine.enterGame(musicEnabled = true, sfxEnabled = true)
                    }
                },
            )
        harness = Harness(decoder = decoder)
        harness.engine.enterGame(musicEnabled = true, sfxEnabled = true)
        harness.runnables[0].run() // Thread-Body der ALTEN Session A laeuft (verspaetet) durch
        val oldSink = harness.sinks[0]
        val newSink = harness.sinks[1]
        assertTrue(oldSink.released) // Session A wurde invalidiert und freigegeben
        assertEquals(0, oldSink.playCount) // A hat nach der Invalidierung nie gestartet
        assertEquals(0, newSink.playCount) // und die neue Session nie beruehrt
        assertEquals(emptyList<ShortArray>(), newSink.writes)
        val current = requireNotNull(harness.engine.activeSession)
        current.prepare() // Session B arbeitet normal weiter
        current.pump()
        assertEquals(1, newSink.playCount)
        assertEquals(1, newSink.writes.size)
        assertEquals(TONE, newSink.writes[0][0])
    }

    @Test
    fun `Senken-Ausfall wird zum Wert EngineUnavailable mixer (C3)`() =
        runTest {
            val harness = Harness(sinkAvailable = false)
            harness.engine.issues.test {
                harness.engine.enterGame(musicEnabled = true, sfxEnabled = true)
                assertEquals(AudioIssue.EngineUnavailable("mixer"), awaitItem())
                harness.pump() // stiller Betrieb, kein Crash
            }
        }

    @Test
    fun `SoundPool-Ausfall wird als EngineUnavailable soundpool gemeldet (MINOR-2)`() =
        runTest {
            val harness = Harness(sfxAvailable = false)
            harness.engine.issues.test {
                harness.engine.enterGame(musicEnabled = false, sfxEnabled = true)
                assertEquals(AudioIssue.EngineUnavailable("soundpool"), awaitItem())
                harness.engine.enterGame(musicEnabled = false, sfxEnabled = true)
                expectNoEvents() // nur einmal je Prozess gemeldet
            }
        }

    @Test
    fun `verweigerter Fokus laesst die Musik fuer diese Session aus`() =
        runTest {
            val harness = Harness(focus = FakeFocus(granted = false))
            harness.engine.issues.test {
                harness.engine.enterGame(musicEnabled = true, sfxEnabled = true)
                assertEquals(AudioIssue.EngineUnavailable("focus"), awaitItem())
                assertTrue(harness.sink.released)
            }
        }

    @Test
    fun `Dekodier-Ausfall einer Ebene - AssetUnavailable, der Rest spielt weiter`() =
        runTest {
            val harness = Harness(decoder = FakeDecoder(failing = setOf("music_stem2_kalimba")))
            harness.engine.issues.test {
                harness.enterAndPrepare()
                assertEquals(AudioIssue.AssetUnavailable("music_stem2_kalimba"), awaitItem())
                harness.pump()
                assertEquals(TONE, harness.sink.writes[0][0]) // Ebene 1 unbeeindruckt
            }
        }

    @Test
    fun `Totalausfall des Decoders meldet zusaetzlich EngineUnavailable decoder`() =
        runTest {
            val allStems = STEM_RESOURCE_NAMES.toSet()
            val harness = Harness(decoder = FakeDecoder(failing = allStems))
            harness.engine.issues.test {
                harness.enterAndPrepare()
                repeat(allStems.size) { awaitItem() } // 4x AssetUnavailable
                assertEquals(AudioIssue.EngineUnavailable("decoder"), awaitItem())
            }
        }

    @Test
    fun `Lebenszyklus - unsichtbar pausiert, sichtbar setzt fort`() {
        val harness = Harness()
        harness.enterAndPrepare()
        harness.engine.setHostVisible(false)
        assertEquals(1, harness.sink.pauseCount)
        harness.engine.setHostVisible(true)
        assertEquals(2, harness.sink.playCount)
    }

    @Test
    fun `setStemMix vor Mixer-Aufbau geht nicht verloren`() {
        val decoder = FakeDecoder(failing = setOf("music_stem1_urig"))
        val harness = Harness(decoder = decoder)
        harness.engine.enterGame(musicEnabled = true, sfxEnabled = true)
        harness.engine.setStemMix(StemMix(1f, 1f, 0f, 0f)) // vor prepare()
        harness.engine.activeSession?.prepare()
        var pumped = 0
        while (pumped * MIX_BLOCK_FRAMES < FADE_FRAMES + MIX_BLOCK_FRAMES) {
            harness.pump()
            pumped++
        }
        val lastWrite = harness.sink.writes.last()
        assertEquals(TONE, lastWrite[lastWrite.size - 1]) // Ebene 2 voll eingefadet
    }

    @Test
    fun `duckForSolve senkt die Summe auf 20 Prozent waehrend der Haltephase`() {
        val harness = Harness()
        harness.enterAndPrepare()
        harness.engine.duckForSolve()
        var pumped = 0
        while (pumped * MIX_BLOCK_FRAMES < DUCK_ATTACK_FRAMES + MIX_BLOCK_FRAMES) {
            harness.pump()
            pumped++
        }
        val lastWrite = harness.sink.writes.last()
        assertEquals((TONE * DUCK_LEVEL).toInt().toShort(), lastWrite[lastWrite.size - 1])
    }

    @Test
    fun `release gibt auch den SoundPool frei`() {
        val harness = Harness()
        harness.enterAndPrepare()
        harness.engine.release()
        assertTrue(harness.sink.released)
        assertTrue(harness.sfx.released)
    }
}
