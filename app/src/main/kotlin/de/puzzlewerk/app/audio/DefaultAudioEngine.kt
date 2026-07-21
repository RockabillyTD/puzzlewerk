package de.puzzlewerk.app.audio

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** res/raw-Namen der 4 Stems in [StemMix]-Reihenfolge (§13.11). */
internal val STEM_RESOURCE_NAMES: List<String> =
    listOf("music_stem1_urig", "music_stem2_kalimba", "music_stem3_bass", "music_stem4_modern")

/** Mix-Blockgröße: 20 ms bei 44,1 kHz (ADR-010). */
internal const val MIX_BLOCK_FRAMES: Int = 882

private const val ISSUE_BUFFER = 16
private const val PAUSED_POLL_MS = 20L
private const val JOIN_TIMEOUT_MS = 500L

/** Dekodiert ein res/raw-OGG einmalig zu Mono-PCM; `null` = Asset fehlt/korrupts (C3). */
internal fun interface StemDecoder {
    fun decode(resourceName: String): ShortArray?
}

/** Dünne PCM-Senke (AudioTrack in Produktion); Implementierungen werfen nie (C3). */
internal interface PcmSink {
    fun play()

    fun pause()

    /** Blockierender Schreibvorgang — er taktet den Mixer-Thread (ADR-010). */
    fun write(
        buffer: ShortArray,
        frames: Int,
    )

    fun release()
}

/** SoundPool-Fassade für die 12 Einmal-SFX plus den Laser-Loop (§13.11). */
internal interface SfxPlayer {
    fun play(effect: SoundEffect)

    fun setLaserLoopActive(active: Boolean)

    fun release()
}

/** Audio-Fokus (R47): [request] meldet Verlust/Rückkehr über den Callback. */
internal interface FocusRequester {
    /** Fordert AUDIOFOCUS_GAIN an; `false` = verweigert. Callback: `true` = Fokus zurück. */
    fun request(onFocusChange: (Boolean) -> Unit): Boolean

    fun abandon()
}

/**
 * Produktions-[AudioEngine] (ADR-010): EIN AudioTrack als Senke, ein
 * Mixer-Thread, der [StemMixerCore]-Blöcke schreibt; SoundPool für SFX.
 * Alle Android-Berührungen liegen hinter den vier Adapter-Schnittstellen —
 * die Engine selbst ist mit Fakes auf der JVM testbar.
 *
 * @param mixerThreadFactory Seam für Tests: liefert dort einen nicht
 *   startenden Thread, die Tests pumpen [prepareMixer]/[pumpOnce] selbst.
 */
internal class DefaultAudioEngine(
    private val decoder: StemDecoder,
    private val sinkFactory: () -> PcmSink?,
    private val sfxPlayer: SfxPlayer,
    private val focus: FocusRequester,
    private val mixerThreadFactory: (Runnable) -> Thread = { Thread(it, "pw-audio-mixer") },
) : AudioEngine {
    private val lock = Any()
    private val issuesFlow =
        MutableSharedFlow<AudioIssue>(
            extraBufferCapacity = ISSUE_BUFFER,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    private val mixBuffer = ShortArray(MIX_BLOCK_FRAMES)

    private var stems: List<ShortArray?>? = null
    private var core: StemMixerCore? = null
    private var pendingMix: StemMix? = null
    private var sink: PcmSink? = null
    private var mixerThread: Thread? = null
    private var laserActive = false
    private var sfxEnabled = false

    @Volatile private var running = false

    @Volatile private var focusLost = false

    @Volatile private var hostVisible = true

    override val issues: Flow<AudioIssue> = issuesFlow

    override fun enterGame(
        musicEnabled: Boolean,
        sfxEnabled: Boolean,
    ): Unit =
        synchronized(lock) {
            this.sfxEnabled = sfxEnabled
            if (!musicEnabled || running) return // R48: Mixer wird NICHT erzeugt
            focusLost = false
            val newSink = sinkFactory()
            if (newSink == null) {
                issuesFlow.tryEmit(AudioIssue.EngineUnavailable("mixer"))
                return
            }
            if (!focus.request(::onFocusChange)) {
                newSink.release()
                issuesFlow.tryEmit(AudioIssue.EngineUnavailable("focus"))
                return
            }
            sink = newSink
            running = true
            mixerThread =
                mixerThreadFactory(
                    Runnable {
                        prepareMixer()
                        while (running) pumpOnce()
                    },
                ).also { it.start() }
        }

    override fun exitGame() {
        val oldSink: PcmSink?
        val oldThread: Thread?
        synchronized(lock) {
            running = false
            oldSink = sink
            oldThread = mixerThread
            sink = null
            mixerThread = null
            core = null
            pendingMix = null
            laserActive = false
        }
        sfxPlayer.setLaserLoopActive(false)
        oldSink?.release() // löst auch einen blockierten write (Adapter fangen Fehler, C3)
        oldThread?.join(JOIN_TIMEOUT_MS)
        focus.abandon()
    }

    override fun setStemMix(mix: StemMix): Unit =
        synchronized(lock) {
            val activeCore = core
            if (activeCore == null) pendingMix = mix else activeCore.setTargets(mix)
        }

    override fun duckForSolve(): Unit = synchronized(lock) { core?.startDuck() ?: Unit }

    override fun playSfx(effect: SoundEffect) {
        if (sfxEnabled && !focusLost) sfxPlayer.play(effect) // R47/R48
    }

    override fun setLaserLoopActive(active: Boolean): Unit =
        synchronized(lock) {
            laserActive = active
            val effective = active && sfxEnabled && !focusLost
            // R48: bei deaktivierten Effekten erreicht kein Aufruf den Player.
            if (!active || effective) sfxPlayer.setLaserLoopActive(effective)
        }

    override fun setHostVisible(visible: Boolean): Unit =
        synchronized(lock) {
            hostVisible = visible
            if (!visible) {
                sink?.pause()
            } else if (running && !focusLost) {
                sink?.play()
            }
        }

    override fun release() {
        exitGame()
        sfxPlayer.release()
    }

    /** Mixer-Thread, Schritt 1: Stems einmalig dekodieren, Kern aufbauen, Senke starten. */
    internal fun prepareMixer() {
        val pcm = stems ?: decodeStems().also { stems = it }
        synchronized(lock) {
            if (!running) return
            core =
                StemMixerCore(pcm).also { newCore ->
                    pendingMix?.let(newCore::setTargets)
                    pendingMix = null
                }
            if (!focusLost && hostVisible) sink?.play()
        }
    }

    private fun decodeStems(): List<ShortArray?> {
        val decoded =
            STEM_RESOURCE_NAMES.map { name ->
                decoder.decode(name).also {
                    if (it == null) issuesFlow.tryEmit(AudioIssue.AssetUnavailable(name))
                }
            }
        if (decoded.all { it == null }) issuesFlow.tryEmit(AudioIssue.EngineUnavailable("decoder"))
        return decoded
    }

    /** Mixer-Thread, Schritt 2 (wiederholt): einen 20-ms-Block mischen und schreiben. */
    internal fun pumpOnce() {
        val target: PcmSink? =
            synchronized(lock) {
                val audible = running && !focusLost && hostVisible
                val activeSink = sink
                if (audible && activeSink != null) {
                    core?.mixInto(mixBuffer, MIX_BLOCK_FRAMES)
                    activeSink
                } else {
                    null
                }
            }
        if (target == null) pausedBackoff() else target.write(mixBuffer, MIX_BLOCK_FRAMES)
    }

    private fun pausedBackoff() {
        runCatching { Thread.sleep(PAUSED_POLL_MS) }
    }

    private fun onFocusChange(gained: Boolean): Unit =
        synchronized(lock) {
            focusLost = !gained
            if (gained) {
                if (running && hostVisible) sink?.play()
                if (laserActive && sfxEnabled) sfxPlayer.setLaserLoopActive(true)
                issuesFlow.tryEmit(AudioIssue.FocusRegained)
            } else {
                sink?.pause() // alle 4 Ebenen GEMEINSAM, Cursor bleibt stehen (R47)
                sfxPlayer.setLaserLoopActive(false)
                issuesFlow.tryEmit(AudioIssue.FocusLost)
            }
        }
}
