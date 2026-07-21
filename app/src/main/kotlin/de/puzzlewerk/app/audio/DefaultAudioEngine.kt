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
    /** `false` = Init fehlgeschlagen; alle Aufrufe degradieren zu Stille (C3). */
    val available: Boolean

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
 * Nebenläufigkeit (Review PW-4.8, MAJOR-1): Jedes `enterGame` erzeugt eine
 * eigene [MixerSession] (Senke, Mix-Puffer, Kern, Thread, `active`-Token).
 * Der Mixer-Thread prüft ausschließlich das Token SEINER Session — ein nach
 * `exitGame` verspätet auslaufender Thread kann eine Folge-Session daher
 * konstruktionsbedingt nie berühren; `exitGame` invalidiert nur und blockiert
 * höchstens [JOIN_TIMEOUT_MS] ms.
 *
 * @param mixerThreadFactory Seam für Tests: liefert dort einen nicht
 *   startenden Thread, die Tests pumpen die Session selbst.
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

    @Volatile private var stems: List<ShortArray?>? = null

    private var session: MixerSession? = null
    private var pendingMix: StemMix? = null
    private var laserActive = false
    private var sfxEnabled = false
    private var soundpoolIssueEmitted = false

    @Volatile private var focusLost = false

    @Volatile private var hostVisible = true

    override val issues: Flow<AudioIssue> = issuesFlow

    /** Test-Seam (JVM-Tests, ADR-010-Schichtung): die aktuell aktive Session. */
    internal val activeSession: MixerSession?
        get() = synchronized(lock) { session }

    override fun enterGame(
        musicEnabled: Boolean,
        sfxEnabled: Boolean,
    ): Unit =
        synchronized(lock) {
            this.sfxEnabled = sfxEnabled
            if (sfxEnabled && !sfxPlayer.available && !soundpoolIssueEmitted) {
                soundpoolIssueEmitted = true // MINOR-2: SFX degradieren nicht mehr still
                issuesFlow.tryEmit(AudioIssue.EngineUnavailable("soundpool"))
            }
            if (!musicEnabled || session != null) return // R48: Mixer wird NICHT erzeugt
            focusLost = false
            val newSink = sinkFactory()
            if (newSink == null) {
                issuesFlow.tryEmit(AudioIssue.EngineUnavailable("mixer"))
                return
            }
            val newSession = MixerSession(newSink)
            if (!focus.request { gained -> onFocusChange(newSession, gained) }) {
                newSink.release()
                issuesFlow.tryEmit(AudioIssue.EngineUnavailable("focus"))
                return
            }
            session = newSession
            newSession.thread = mixerThreadFactory(Runnable(newSession::runLoop)).also { it.start() }
        }

    override fun exitGame() {
        val old: MixerSession?
        synchronized(lock) {
            old = session
            session = null
            pendingMix = null
            laserActive = false
            focusLost = false // MINOR-1: R47 sperrt SFX nur in einer AKTIVEN Session
        }
        sfxPlayer.setLaserLoopActive(false)
        if (old != null) {
            old.active = false // Token-Invalidierung statt langem Join (MAJOR-1)
            old.sink.release() // löst auch einen blockierten write (Adapter fangen Fehler, C3)
            old.thread?.join(JOIN_TIMEOUT_MS)
        }
        focus.abandon()
    }

    override fun setStemMix(mix: StemMix): Unit =
        synchronized(lock) {
            val activeCore = session?.core
            if (activeCore == null) pendingMix = mix else activeCore.setTargets(mix)
        }

    override fun duckForSolve(): Unit = synchronized(lock) { session?.core?.startDuck() ?: Unit }

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
                session?.sink?.pause()
            } else if (!focusLost) {
                session?.sink?.play()
            }
        }

    override fun release() {
        exitGame()
        sfxPlayer.release()
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

    /**
     * Fokus-Callback der Session [owner]. Callbacks einer bereits per
     * `exitGame` invalidierten Session werden verworfen (BUG-PW4.9-1) —
     * sonst vergiftet ein VERSPÄTETER Verlust-Callback nach `abandon()` das
     * engine-globale `focusLost` und mutet Menü-SFX bis zum nächsten
     * `enterGame` (R47 sperrt SFX nur in einer AKTIVEN Session).
     */
    private fun onFocusChange(
        owner: MixerSession,
        gained: Boolean,
    ): Unit =
        synchronized(lock) {
            if (session !== owner) return
            focusLost = !gained
            if (gained) {
                if (hostVisible) session?.sink?.play()
                if (laserActive && sfxEnabled) sfxPlayer.setLaserLoopActive(true)
                issuesFlow.tryEmit(AudioIssue.FocusRegained)
            } else {
                session?.sink?.pause() // alle 4 Ebenen GEMEINSAM, Cursor bleibt stehen (R47)
                sfxPlayer.setLaserLoopActive(false)
                issuesFlow.tryEmit(AudioIssue.FocusLost)
            }
        }

    /**
     * Eine Mixer-Session = ein `enterGame` (MAJOR-1): eigener Sink, eigener
     * Mix-Puffer, eigener Kern und ein eigenes `active`-Token, das der
     * zugehörige Thread als EINZIGES Abbruchkriterium liest.
     */
    internal inner class MixerSession(
        val sink: PcmSink,
    ) {
        private val mixBuffer = ShortArray(MIX_BLOCK_FRAMES)

        @Volatile var active = true

        var core: StemMixerCore? = null
        var thread: Thread? = null

        /** Produktions-Thread-Body; Tests rufen [prepare]/[pump] direkt. */
        fun runLoop() {
            prepare()
            while (active) pump()
        }

        /** Schritt 1: Stems einmalig dekodieren (prozessweit gecacht), Kern aufbauen, Senke starten. */
        fun prepare() {
            val pcm = stems ?: decodeStems().also { stems = it }
            synchronized(lock) {
                if (!active) return // Session wurde während des Decodes beendet
                core =
                    StemMixerCore(pcm).also { newCore ->
                        pendingMix?.let(newCore::setTargets)
                        pendingMix = null
                    }
                if (!focusLost && hostVisible) sink.play()
            }
        }

        /** Schritt 2 (wiederholt): einen 20-ms-Block in den EIGENEN Sink mischen. */
        fun pump() {
            val audible =
                synchronized(lock) {
                    val mixNow = active && !focusLost && hostVisible
                    if (mixNow) core?.mixInto(mixBuffer, MIX_BLOCK_FRAMES)
                    mixNow
                }
            if (audible) sink.write(mixBuffer, MIX_BLOCK_FRAMES) else pausedBackoff()
        }

        private fun pausedBackoff() {
            runCatching { Thread.sleep(PAUSED_POLL_MS) }
        }
    }
}
