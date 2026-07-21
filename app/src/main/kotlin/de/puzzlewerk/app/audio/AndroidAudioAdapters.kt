package de.puzzlewerk.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.SoundPool
import androidx.core.content.ContextCompat
import de.puzzlewerk.app.R
import java.nio.ByteOrder

// ADR-010: USAGE_GAME, niemals Alarm-/Ring-Streams — der Lautlos-Modus gilt (R48).
private fun gameAttributes(contentType: Int): AudioAttributes =
    AudioAttributes
        .Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(contentType)
        .build()

private val STEM_RES_IDS: Map<String, Int> =
    mapOf(
        "music_stem1_urig" to R.raw.music_stem1_urig,
        "music_stem2_kalimba" to R.raw.music_stem2_kalimba,
        "music_stem3_bass" to R.raw.music_stem3_bass,
        "music_stem4_modern" to R.raw.music_stem4_modern,
    )

private const val DEQUEUE_TIMEOUT_US = 10_000L
private const val MAX_IDLE_SPINS = 1_000
private const val MAX_SFX_STREAMS = 6
private const val LASER_LOOP_VOLUME = 0.2f
private const val SINK_BUFFER_BLOCKS = 4
private const val BYTES_PER_SAMPLE = 2

/**
 * [StemDecoder] über MediaExtractor + MediaCodec (ADR-010, keine neue
 * Dependency): dekodiert ein Stem-OGG einmalig zu Mono-PCM und normiert auf
 * exakt [STEM_LOOP_FRAMES] Frames (Überhang fällt weg, Fehlbestand wird mit
 * Stille aufgefüllt) — Voraussetzung des sample-exakten Modulo-Loops.
 * Jeder Fehler wird zu `null` (⇒ AssetUnavailable), nie zur Exception (C3).
 */
internal class MediaCodecStemDecoder(
    private val context: Context,
) : StemDecoder {
    override fun decode(resourceName: String): ShortArray? = runCatching { decodeResource(resourceName) }.getOrNull()

    private fun decodeResource(resourceName: String): ShortArray? {
        val resId = STEM_RES_IDS[resourceName] ?: return null
        val extractor = MediaExtractor()
        try {
            context.resources.openRawResourceFd(resId).use { fd ->
                extractor.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            if (extractor.trackCount < 1) return null
            return decodeTrack(extractor)
        } finally {
            extractor.release()
        }
    }

    private fun decodeTrack(extractor: MediaExtractor): ShortArray? {
        val format = extractor.getTrackFormat(0)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        extractor.selectTrack(0)
        val codec = MediaCodec.createDecoderByType(mime)
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            return drainToMono(codec, extractor)
        } finally {
            codec.release()
        }
    }

    private fun drainToMono(
        codec: MediaCodec,
        extractor: MediaExtractor,
    ): ShortArray? {
        val out = ShortArray(STEM_LOOP_FRAMES)
        val info = MediaCodec.BufferInfo()
        var written = 0
        var inputDone = false
        var idleSpins = 0
        while (idleSpins < MAX_IDLE_SPINS) {
            if (!inputDone) inputDone = feedInput(codec, extractor)
            val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
            if (index >= 0) {
                idleSpins = 0
                written = copyOutput(codec, index, info.size, out, written)
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return out
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED &&
                codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) != STEM_SAMPLE_RATE
            ) {
                return null // falsche Rate würde den 756 000er-Loop brechen
            } else {
                idleSpins++
            }
        }
        return null // Codec-Stillstand: Asset gilt als nicht dekodierbar (C3)
    }

    private fun feedInput(
        codec: MediaCodec,
        extractor: MediaExtractor,
    ): Boolean {
        val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (index < 0) return false
        val buffer = requireNotNull(codec.getInputBuffer(index))
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            return true
        }
        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
        extractor.advance()
        return false
    }

    /** Kopiert einen Output-Block, mischt Stereo ggf. auf Mono herunter (ADR-010-Messung: Quellen sind mono). */
    private fun copyOutput(
        codec: MediaCodec,
        index: Int,
        byteSize: Int,
        out: ShortArray,
        written: Int,
    ): Int {
        val channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val samples =
            requireNotNull(codec.getOutputBuffer(index))
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()
        var cursor = written
        var remaining = byteSize / BYTES_PER_SAMPLE
        while (remaining >= channels && cursor < out.size) {
            var acc = 0
            repeat(channels) { acc += samples.get().toInt() }
            out[cursor] = (acc / channels).toShort()
            cursor++
            remaining -= channels
        }
        return cursor
    }
}

// Mono/44,1 kHz/16 Bit: die gemessene Asset-Eigenschaft (PW-4.8, PCM-Budget in ADR-010).
private fun monoOutputFormat(): AudioFormat =
    AudioFormat
        .Builder()
        .setSampleRate(STEM_SAMPLE_RATE)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()

/** [PcmSink] über einen einzelnen [AudioTrack] im Streaming-Modus; `null` bei Init-Fehler (C3). */
internal fun audioTrackSinkOrNull(): PcmSink? =
    runCatching {
        val minBytes =
            AudioTrack.getMinBufferSize(
                STEM_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minBytes <= 0) return null
        val track =
            AudioTrack
                .Builder()
                .setAudioAttributes(gameAttributes(AudioAttributes.CONTENT_TYPE_MUSIC))
                .setAudioFormat(monoOutputFormat())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minBytes, MIX_BLOCK_FRAMES * BYTES_PER_SAMPLE * SINK_BUFFER_BLOCKS))
                .build()
        AudioTrackSink(track)
    }.getOrNull()

private class AudioTrackSink(
    private val track: AudioTrack,
) : PcmSink {
    override fun play() {
        runCatching { track.play() }
    }

    override fun pause() {
        runCatching { track.pause() }
    }

    override fun write(
        buffer: ShortArray,
        frames: Int,
    ) {
        runCatching { track.write(buffer, 0, frames) }
    }

    override fun release() {
        runCatching { track.release() }
    }
}

/**
 * [SfxPlayer] über einen [SoundPool]: alle 13 SFX-Assets werden beim Bau
 * (App-Start, AppContainer) vorgeladen (ADR-010). Init-/Abspiel-Fehler
 * degradieren zu Stille (C3); das Demo-Mix-Asset wird NICHT geladen (§13.11).
 */
internal class SoundPoolSfxPlayer(
    context: Context,
) : SfxPlayer {
    private val pool: SoundPool? =
        runCatching {
            SoundPool
                .Builder()
                .setMaxStreams(MAX_SFX_STREAMS)
                .setAudioAttributes(gameAttributes(AudioAttributes.CONTENT_TYPE_SONIFICATION))
                .build()
        }.getOrNull()

    /** MINOR-2 (Review PW-4.8): Init-Fehler als Wert — die Engine meldet ihn auf `issues`. */
    override val available: Boolean
        get() = pool != null

    private val effectIds: Map<SoundEffect, Int> =
        SoundEffect.entries.associateWith { effect ->
            load(context, requireNotNull(SFX_RES_IDS[effect.resourceName]))
        }
    private val laserId: Int = load(context, R.raw.sfx_laser_loop)
    private var laserStreamId = 0

    private fun load(
        context: Context,
        resId: Int,
    ): Int = pool?.let { runCatching { it.load(context, resId, 1) }.getOrNull() } ?: 0

    override fun play(effect: SoundEffect) {
        val soundId = effectIds.getValue(effect)
        if (soundId != 0) pool?.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    override fun setLaserLoopActive(active: Boolean) {
        val activePool = pool ?: return
        if (active && laserStreamId == 0 && laserId != 0) {
            laserStreamId = activePool.play(laserId, LASER_LOOP_VOLUME, LASER_LOOP_VOLUME, 1, -1, 1f)
        } else if (!active && laserStreamId != 0) {
            activePool.stop(laserStreamId)
            laserStreamId = 0
        }
    }

    override fun release() {
        runCatching { pool?.release() }
    }

    private companion object {
        val SFX_RES_IDS: Map<String, Int> =
            mapOf(
                "sfx_rotate_tick" to R.raw.sfx_rotate_tick,
                "sfx_rotate_invalid" to R.raw.sfx_rotate_invalid,
                "sfx_beam_connect" to R.raw.sfx_beam_connect,
                "sfx_crystal_lit" to R.raw.sfx_crystal_lit,
                "sfx_combo_up1" to R.raw.sfx_combo_up1,
                "sfx_combo_up2" to R.raw.sfx_combo_up2,
                "sfx_combo_up3" to R.raw.sfx_combo_up3,
                "sfx_solve_explosion" to R.raw.sfx_solve_explosion,
                "sfx_star_1" to R.raw.sfx_star_1,
                "sfx_star_2" to R.raw.sfx_star_2,
                "sfx_star_3" to R.raw.sfx_star_3,
                "sfx_ui_tap" to R.raw.sfx_ui_tap,
            )
    }
}

/**
 * [FocusRequester] über [AudioManager] (R47): AUDIOFOCUS_GAIN nur für die
 * Stems; „Kopfhörer getrennt" (ACTION_AUDIO_BECOMING_NOISY) zählt als Verlust
 * ohne Rückkehr (ADR-010). Kein Fokus im SFX-only-Betrieb — das regelt die
 * Engine, die diesen Adapter nur bei aktiver Musik ruft.
 */
internal class AudioManagerFocusRequester(
    private val context: Context,
) : FocusRequester {
    private var request: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null

    override fun request(onFocusChange: (Boolean) -> Unit): Boolean {
        val manager = context.getSystemService(AudioManager::class.java) ?: return false
        val focusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(gameAttributes(AudioAttributes.CONTENT_TYPE_MUSIC))
                .setOnAudioFocusChangeListener { change ->
                    onFocusChange(change == AudioManager.AUDIOFOCUS_GAIN)
                }.build()
        if (manager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        request = focusRequest
        noisyReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    receiverContext: Context?,
                    intent: Intent?,
                ) {
                    onFocusChange(false)
                }
            }.also {
                ContextCompat.registerReceiver(
                    context,
                    it,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }
        return true
    }

    override fun abandon() {
        request?.let { context.getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(it) }
        request = null
        noisyReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        noisyReceiver = null
    }
}
