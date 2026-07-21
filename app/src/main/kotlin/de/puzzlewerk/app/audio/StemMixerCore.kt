package de.puzzlewerk.app.audio

/** Abtastrate aller Audio-Assets (an den OGG-Headern verifiziert, PW-4.8). */
internal const val STEM_SAMPLE_RATE: Int = 44_100

/** Sample-exakte Loop-Länge jedes Stems: 756 000 Frames = 17 1/7 s bei 112 BPM (§13.11). */
internal const val STEM_LOOP_FRAMES: Int = 756_000

/** Linearer 250-ms-Fade jedes Lautstärkewechsels (§13.11) in Frames. */
internal const val FADE_FRAMES: Int = 11_025

/** Duck-Envelope beim Lösen (§13.11): 50 ms auf 20 %, 500 ms halten, 250 ms zurück. */
internal const val DUCK_ATTACK_FRAMES: Int = 2_205
internal const val DUCK_HOLD_FRAMES: Int = 22_050
internal const val DUCK_RELEASE_FRAMES: Int = 11_025
internal const val DUCK_LEVEL: Float = 0.2f

private const val DUCK_TOTAL_FRAMES: Int = DUCK_ATTACK_FRAMES + DUCK_HOLD_FRAMES + DUCK_RELEASE_FRAMES
private const val PCM_MIN: Int = Short.MIN_VALUE.toInt()
private const val PCM_MAX: Int = Short.MAX_VALUE.toInt()

/**
 * Purer Mixer-Kern (ADR-010): mischt die 4 Mono-Stems über EINEN gemeinsamen
 * Loop-Cursor — Synchronität und sample-exaktes Loopen sind damit
 * Konstruktionseigenschaften, kein Geräteverhalten. Kein Android-Import,
 * keine Allokation im Mix-Pfad; bit-exakt auf der JVM testbar.
 *
 * Nebenläufigkeit: NICHT thread-sicher — alle Aufrufe serialisiert der
 * Besitzer ([DefaultAudioEngine] über sein Lock).
 *
 * @param stems Dekodiertes Mono-PCM je Ebene in StemMix-Reihenfolge; `null` =
 *   Asset fehlt und wird als Stille gemischt (ADR-010, AssetUnavailable).
 * @param loopFrames Loop-Länge in Frames; Tests verkleinern sie, Produktion
 *   nutzt [STEM_LOOP_FRAMES].
 */
internal class StemMixerCore(
    private val stems: List<ShortArray?>,
    private val loopFrames: Int = STEM_LOOP_FRAMES,
) {
    private val startVol = floatArrayOf(1f, 0f, 0f, 0f)
    private val targetVol = floatArrayOf(1f, 0f, 0f, 0f)
    private val fadePos = intArrayOf(FADE_FRAMES, FADE_FRAMES, FADE_FRAMES, FADE_FRAMES)
    private var duckPos = DUCK_TOTAL_FRAMES
    private var duckFrom = 1f

    /** Gemeinsame Cursor-Position aller 4 Ebenen in Frames (R47-Wiedereinstieg). */
    var cursor: Int = 0
        private set

    init {
        require(stems.size == startVol.size) { "Erwarte genau ${startVol.size} Stems" }
        require(loopFrames > 0) { "loopFrames muss > 0 sein" }
        require(stems.all { it == null || it.size >= loopFrames }) { "Jeder Stem braucht >= loopFrames Frames" }
    }

    /** Neue Ziel-Lautstärken: jede geänderte Ebene fadet ab JETZT linear über 250 ms (§13.11). */
    fun setTargets(mix: StemMix) {
        val targets = floatArrayOf(mix.stem1Urig, mix.stem2Kalimba, mix.stem3Bass, mix.stem4Modern)
        for (stem in targets.indices) {
            if (targets[stem] != targetVol[stem]) {
                startVol[stem] = volumeAt(stem)
                targetVol[stem] = targets[stem]
                fadePos[stem] = 0
            }
        }
    }

    /** Startet die Duck-Envelope; Retrigger setzt klickfrei am aktuellen Faktor neu auf (§13.11). */
    fun startDuck() {
        duckFrom = duckFactor()
        duckPos = 0
    }

    /** Aktuelle Lautstärke der Ebene [stem] (linear interpoliert). */
    fun volumeAt(stem: Int): Float =
        if (fadePos[stem] >= FADE_FRAMES) {
            targetVol[stem]
        } else {
            lerp(startVol[stem], targetVol[stem], fadePos[stem].toFloat() / FADE_FRAMES)
        }

    /** Aktueller Duck-Faktor (1,0 = inaktiv; multipliziert sich mit den Ebenen, R50). */
    fun duckFactor(): Float =
        when {
            duckPos >= DUCK_TOTAL_FRAMES -> 1f
            duckPos < DUCK_ATTACK_FRAMES ->
                lerp(duckFrom, DUCK_LEVEL, duckPos.toFloat() / DUCK_ATTACK_FRAMES)
            duckPos < DUCK_ATTACK_FRAMES + DUCK_HOLD_FRAMES -> DUCK_LEVEL
            else -> {
                val released = duckPos - DUCK_ATTACK_FRAMES - DUCK_HOLD_FRAMES
                lerp(DUCK_LEVEL, 1f, released.toFloat() / DUCK_RELEASE_FRAMES)
            }
        }

    /**
     * Mischt die nächsten [frames] Frames nach `out[0 until frames]`:
     * `out[n] = clamp(Σ stem[cursor] · vol · duck)`; der Cursor loopt per
     * Modulo-Arithmetik sample-exakt über [loopFrames] (ADR-010).
     */
    fun mixInto(
        out: ShortArray,
        frames: Int,
    ) {
        for (frame in 0 until frames) {
            val duck = duckFactor()
            var acc = 0f
            for (stem in stems.indices) {
                val pcm = stems[stem] ?: continue
                val volume = volumeAt(stem)
                if (volume > 0f) acc += pcm[cursor] * volume
            }
            out[frame] = (acc * duck).toInt().coerceIn(PCM_MIN, PCM_MAX).toShort()
            advanceFrame()
        }
    }

    private fun advanceFrame() {
        for (stem in fadePos.indices) {
            if (fadePos[stem] < FADE_FRAMES) fadePos[stem]++
        }
        if (duckPos < DUCK_TOTAL_FRAMES) duckPos++
        cursor = if (cursor + 1 == loopFrames) 0 else cursor + 1
    }
}

private fun lerp(
    from: Float,
    to: Float,
    fraction: Float,
): Float = from + (to - from) * fraction
