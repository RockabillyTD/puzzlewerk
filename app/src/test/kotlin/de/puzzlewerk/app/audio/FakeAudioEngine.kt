package de.puzzlewerk.app.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Rein aufzeichnende [AudioEngine] für ViewModel-/Choreografie-Tests
 * (ADR-010; genutzt in PW-4.8 und im QS-Pass PW-4.9): kein Ton, keine
 * Threads — nur Historien der Aufrufe plus manuell emittierbare [issues].
 */
internal class FakeAudioEngine : AudioEngine {
    /** Ein `enterGame`-Aufruf mit seinen Schalterständen (§13.11). */
    data class EnterGameCall(
        val musicEnabled: Boolean,
        val sfxEnabled: Boolean,
    )

    private val issuesFlow = MutableSharedFlow<AudioIssue>(extraBufferCapacity = 16)

    override val issues: Flow<AudioIssue> = issuesFlow

    val enterGameCalls = mutableListOf<EnterGameCall>()
    var exitGameCount = 0
        private set

    /** Historie ALLER `setStemMix`-Sollwerte in Aufrufreihenfolge (R46-Folgen). */
    val stemMixHistory = mutableListOf<StemMix>()
    var duckCount = 0
        private set

    /** Alle `playSfx`-Aufrufe in Reihenfolge (SFX-Ketten R45). */
    val playedEffects = mutableListOf<SoundEffect>()
    var laserLoopActive = false
        private set

    /** JEDER `setLaserLoopActive`-Aufruf (PW-4.6: Dedup-Negativtests des Choreografen). */
    val laserLoopHistory = mutableListOf<Boolean>()
    val hostVisibleHistory = mutableListOf<Boolean>()
    var releaseCount = 0
        private set

    /** Testseite: Issue von außen einspeisen (z. B. FocusLost, R47). */
    suspend fun emitIssue(issue: AudioIssue) = issuesFlow.emit(issue)

    override fun enterGame(
        musicEnabled: Boolean,
        sfxEnabled: Boolean,
    ) {
        enterGameCalls += EnterGameCall(musicEnabled, sfxEnabled)
    }

    override fun exitGame() {
        exitGameCount++
    }

    override fun setStemMix(mix: StemMix) {
        stemMixHistory += mix
    }

    override fun duckForSolve() {
        duckCount++
    }

    override fun playSfx(effect: SoundEffect) {
        playedEffects += effect
    }

    override fun setLaserLoopActive(active: Boolean) {
        laserLoopActive = active
        laserLoopHistory += active
    }

    override fun setHostVisible(visible: Boolean) {
        hostVisibleHistory += visible
    }

    override fun release() {
        releaseCount++
    }
}
