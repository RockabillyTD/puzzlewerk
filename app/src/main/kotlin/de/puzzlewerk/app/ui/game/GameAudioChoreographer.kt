package de.puzzlewerk.app.ui.game

import de.puzzlewerk.app.audio.AudioEngine
import de.puzzlewerk.app.audio.SoundEffect
import de.puzzlewerk.app.audio.StemMix
import de.puzzlewerk.data.settings.SettingsRepository
import de.puzzlewerk.game.trace.JuiceDelta
import kotlinx.coroutines.flow.first

/**
 * Audio-Choreografie des Spiel-Screens (§13.11, ADR-010; PW-4.6): übersetzt
 * Partie-Ereignisse in [AudioEngine]-Aufrufe. KEINE Spiellogik — alle
 * Entscheidungen („neu erfüllt", L, K) kommen fertig aus `juiceDelta`
 * (:game, ADR-012); R48 (Schalter AUS ⇒ still) setzt die Engine selbst um.
 *
 * [engine] darf `null` liefern, solange die Engine nicht initialisiert ist
 * (nur in Kompositionen ohne Application-Start möglich, z. B. Container-
 * JVM-Tests) — dann degradieren alle Aufrufe still zu No-ops (C3: Audio ist
 * nie der einzige Feedback-Kanal).
 */
internal class GameAudioChoreographer(
    private val engine: () -> AudioEngine?,
    private val settingsRepository: SettingsRepository,
) {
    constructor(engine: AudioEngine, settingsRepository: SettingsRepository) :
        this({ engine }, settingsRepository)

    private var lastMix: StemMix? = null
    private var lastLaserActive: Boolean? = null

    /** Betreten des Spiel-Screens: `enterGame` mit den AKTUELLEN Settings-Schaltern (§13.11/R48). */
    suspend fun enter() {
        val settings = settingsRepository.settings.first()
        engine()?.enterGame(musicEnabled = settings.musicEnabled, sfxEnabled = settings.sfxEnabled)
        lastMix = null
        lastLaserActive = null
    }

    /** Verlassen des Spiel-Screens — auch „Nochmal"/„Weiter" (R49, AudioEngine-Vertrag). */
    fun exit() {
        engine()?.exitGame()
    }

    /** Wirkungsloser Zug (R27/R32): sfx_rotate_invalid (§13.11-Tabelle). */
    fun onInvalidMove() {
        engine()?.playSfx(SoundEffect.ROTATE_INVALID)
    }

    /**
     * Angewandter Zug bzw. Partie-Start: SFX-Kette nach der §13.11-Tabelle in
     * R45-Kaskadenreihenfolge (die 40-ms-Bildversätze macht der Stepper; SFX
     * feuern beim Zug-Commit), Stem-Tabelle ohne Hysterese (R46, nur bei
     * Fortschrittsänderung), Laser-Loop-Zustand sowie Solve-Explosion + Duck
     * bei t_fw ≈ Zug-Commit (§13.10; die Duck-Envelope fährt die Engine).
     */
    fun onApplied(
        delta: JuiceDelta,
        validRotation: Boolean,
        justSolved: Boolean,
        beamsVisible: Boolean,
    ) {
        val engine = engine() ?: return
        if (validRotation) engine.playSfx(SoundEffect.ROTATE_TICK)
        repeat(delta.comboSize) { index -> engine.playSfx(comboSfx(index)) }
        // §13.9: sfx_beam_connect nur, wenn KEIN Kristall neu erfüllt wurde.
        if (delta.newlyFulfilled.isEmpty() && delta.newlyLit.isNotEmpty()) {
            engine.playSfx(SoundEffect.BEAM_CONNECT)
        }
        if (justSolved) {
            engine.playSfx(SoundEffect.SOLVE_EXPLOSION)
            engine.duckForSolve()
        }
        val mix = StemMix.forProgress(fulfilled = delta.fulfilled.size, crystalTotal = delta.crystalTotal)
        if (mix != lastMix) {
            engine.setStemMix(mix)
            lastMix = mix
        }
        if (beamsVisible != lastLaserActive) {
            engine.setLaserLoopActive(beamsVisible)
            lastLaserActive = beamsVisible
        }
    }

    /** SFX des k-ten Kaskaden-Bursts (§13.9): lit, up1, up2, ab dem 4. up3. */
    private fun comboSfx(index: Int): SoundEffect =
        when (index) {
            0 -> SoundEffect.CRYSTAL_LIT
            1 -> SoundEffect.COMBO_UP1
            2 -> SoundEffect.COMBO_UP2
            else -> SoundEffect.COMBO_UP3
        }
}
