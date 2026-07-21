package de.puzzlewerk.app.audio

import kotlinx.coroutines.flow.Flow

/**
 * Audio-Fassade des Spiel-Screens (ADR-010): 4 synchrone Musik-Stems über einen
 * eigenen AudioTrack-Mixer plus SoundPool-SFX. Vertragsgrundlage ist das
 * Juice-Addendum docs/game-design.md §13.11 mit R46–R49.
 *
 * Harte Invarianten der Implementierung (PW-4.8):
 * - Die 4 Stems sind zu KEINEM Zeitpunkt gegeneinander verschoben (§13.11) —
 *   auch nicht nach Pause/Resume durch Audio-Fokus-Wechsel (R47).
 * - Fehler sind Werte auf [issues], niemals Exceptions Richtung Aufrufer (C3);
 *   jeder Audio-Fehler degradiert zu Stille, nie zu Crash oder blockierter
 *   Spiellogik (Audio ist nie der einzige Feedback-Kanal, §13/R48).
 * - Lautlos-Modus/Stummschalter werden nie umgangen (R48): USAGE_GAME,
 *   keine Alarm-/Ring-Streams.
 */
internal interface AudioEngine {
    /**
     * Beobachtbare Fehler-/Zustandsereignisse der Engine. Heißer Strom; Abnehmer
     * ist primär die Test-/Diagnoseseite, die UI reagiert NICHT sichtbar auf
     * Audio-Fehler (stille Degradation).
     */
    val issues: Flow<AudioIssue>

    /**
     * Betreten des Spiel-Screens: fordert — NUR bei [musicEnabled] — den
     * Audio-Fokus (GAIN) an und startet alle 4 Stems synchron ab Sample 0
     * (Loop endlos, §13.11). Im SFX-only-Betrieb wird KEIN Fokus angefordert:
     * kurze SFX dürfen fremde Musik nicht verdrängen. Initiale Lautstärken:
     * Ebene 1 = 100 %, Ebenen 2–4 laut erstem [setStemMix]-Aufruf.
     *
     * [musicEnabled] == `false` ⇒ Mixer/AudioTrack wird NICHT erzeugt (R48:
     * nicht gestartet statt stummgeschaltet); [sfxEnabled] == `false` ⇒
     * [playSfx] und [setLaserLoopActive] sind No-ops. Die Werte kommen aus den
     * beiden Settings-Schaltern „Musik"/„Soundeffekte" (§13.11).
     */
    fun enterGame(
        musicEnabled: Boolean,
        sfxEnabled: Boolean,
    )

    /**
     * Verlassen des Spiel-Screens (auch „Weiter"/„Nochmal", R49): Stems stoppen
     * sofort, bereits spielende SFX dürfen ausklingen, der Laser-Loop endet.
     */
    fun exitGame()

    /**
     * Setzt die Ziel-Lautstärken der 4 Ebenen; die Engine fadet jede Ebene
     * linear über 250 ms auf ihren Sollwert (§13.11, nie hart). Folgt dem
     * AKTUELLEN trace auch abwärts — keine Hysterese (R46).
     */
    fun setStemMix(mix: StemMix)

    /**
     * Duck-Envelope beim Lösen (§13.10/§13.11, Aufruf bei t_fw): alle aktiven
     * Ebenen in 50 ms auf 20 %, 500 ms halten, in 250 ms zurück. Der
     * Duck-Faktor multipliziert sich mit den Ebenen-Lautstärken (R50).
     */
    fun duckForSolve()

    /**
     * Spielt einen vorgeladenen Einmal-Effekt (SoundPool). No-op bei
     * deaktivierten Soundeffekten und während Audio-Fokus-Verlust (R47:
     * „keine neuen SFX").
     */
    fun playSfx(effect: SoundEffect)

    /**
     * Schaltet den leisen Dauer-Loop `sfx_laser_loop` (20 % Lautstärke, §13.11):
     * aktiv, solange mindestens ein Strahlsegment sichtbar ist.
     */
    fun setLaserLoopActive(active: Boolean)

    /**
     * Lebenszyklus der Host-Activity (PW-4.8, Ergänzung zum ADR-010-Vertrag):
     * `false` (onStop) pausiert den Mixer wie ein Fokus-Verlust — an derselben
     * Cursor-Position, ohne Versatz; `true` (onStart) setzt fort, sofern der
     * Audio-Fokus nicht verloren ist. Ohne laufende Stems ein No-op.
     */
    fun setHostVisible(visible: Boolean)

    /** Gibt alle Audio-Ressourcen frei (App-Ende/AppContainer, ADR-006). */
    fun release()
}

/**
 * Ziel-Lautstärken (0..1) der 4 Musik-Ebenen (§13.11). Die pure Zuordnung
 * Erfüllungsstand → Mix (mit L = erfüllte, K = alle Kristalle: Ebene 1 immer
 * 100 %; Ebene 2 bei L ≥ 1; Ebene 3 bei 2·L ≥ K; Ebene 4 bei
 * L ≥ max(1, K − 1); sonst jeweils 0 %) wird in PW-4.8 als
 * `StemMix.forProgress(fulfilled, crystalTotal)` implementiert und direkt
 * gegen die Tabelle plus R50 (K ≤ 2) getestet.
 *
 * @property stem1Urig Ebene 1 (music_stem1_urig) — Grundebene, immer 1,0.
 * @property stem2Kalimba Ebene 2 (music_stem2_kalimba) — 1,0 ab L ≥ 1.
 * @property stem3Bass Ebene 3 (music_stem3_bass) — 1,0 ab 2·L ≥ K.
 * @property stem4Modern Ebene 4 (music_stem4_modern) — 1,0 ab L ≥ max(1, K − 1).
 */
internal data class StemMix(
    val stem1Urig: Float,
    val stem2Kalimba: Float,
    val stem3Bass: Float,
    val stem4Modern: Float,
) {
    companion object {
        /** Grundzustand beim Betreten des Spiel-Screens: nur Ebene 1 (§13.11). */
        val BASE: StemMix = StemMix(1f, 0f, 0f, 0f)

        /**
         * Pure Zuordnung Erfüllungsstand → Ebenen-Lautstärken (Tabelle §13.11;
         * ohne Hysterese auch abwärts anwendbar, R46; kein Sonderfall-Code für
         * kleine Level, R50).
         *
         * @param fulfilled L = aktuell erfüllte Kristalle laut trace.
         * @param crystalTotal K = Kristallzahl des Levels (≥ 1).
         */
        fun forProgress(
            fulfilled: Int,
            crystalTotal: Int,
        ): StemMix {
            require(crystalTotal >= 1) { "K muss >= 1 sein, war $crystalTotal" }
            require(fulfilled in 0..crystalTotal) { "L muss in 0..K liegen, war $fulfilled" }
            return StemMix(
                stem1Urig = 1f,
                stem2Kalimba = if (fulfilled >= 1) 1f else 0f,
                stem3Bass = if (2 * fulfilled >= crystalTotal) 1f else 0f,
                stem4Modern = if (fulfilled >= maxOf(1, crystalTotal - 1)) 1f else 0f,
            )
        }
    }
}

/**
 * Die 12 Einmal-SFX aus §13.11 (Zuordnungstabelle); [resourceName] ist der
 * res/raw-Name ohne Endung. Der 13. Effekt `sfx_laser_loop` ist bewusst KEIN
 * Enum-Wert — er ist ein Dauerzustand ([AudioEngine.setLaserLoopActive]),
 * kein Einmal-Ereignis.
 */
internal enum class SoundEffect(
    val resourceName: String,
) {
    /** Gültige Drehung (§13.9). */
    ROTATE_TICK("sfx_rotate_tick"),

    /** Ungültiger Tap (R27, R32). */
    ROTATE_INVALID("sfx_rotate_invalid"),

    /** Zug bringt erstmals Licht auf ≥ 1 Kristall, ohne neue Erfüllung (§13.9). */
    BEAM_CONNECT("sfx_beam_connect"),

    /** Kristall neu erfüllt — 1. Burst der Kaskade (§13.9). */
    CRYSTAL_LIT("sfx_crystal_lit"),

    /** Combo-Burst 2 (§13.9, R45). */
    COMBO_UP1("sfx_combo_up1"),

    /** Combo-Burst 3 (§13.9, R45). */
    COMBO_UP2("sfx_combo_up2"),

    /** Combo-Burst ≥ 4 (§13.9, R45). */
    COMBO_UP3("sfx_combo_up3"),

    /** Lösung, bei t_fw (§13.10). */
    SOLVE_EXPLOSION("sfx_solve_explosion"),

    /** Stern 1 im Ergebnis-Overlay (§13.10). */
    STAR_1("sfx_star_1"),

    /** Stern 2 im Ergebnis-Overlay (§13.10). */
    STAR_2("sfx_star_2"),

    /** Stern 3 im Ergebnis-Overlay (§13.10). */
    STAR_3("sfx_star_3"),

    /** UI-Tap (Buttons, Navigation, Overlay). */
    UI_TAP("sfx_ui_tap"),
}

/**
 * Fehler-/Zustandsereignisse der [AudioEngine] — Werte statt Exceptions (C3).
 * Verhalten je Fall ist in ADR-010 normiert.
 */
internal sealed interface AudioIssue {
    /**
     * Audio-Fokus verloren (Anruf, andere App, Kopfhörer getrennt): alle 4
     * Stems pausieren GEMEINSAM an derselben Cursor-Position, neue SFX werden
     * unterdrückt (R47). Spiellogik und visuelles Feedback laufen weiter.
     */
    data object FocusLost : AudioIssue

    /**
     * Audio-Fokus zurückerhalten: gemeinsamer Wiedereinstieg aller Stems an
     * exakt der pausierten Position — weiterhin ohne Versatz (R47).
     */
    data object FocusRegained : AudioIssue

    /**
     * Ein Asset fehlt oder ließ sich nicht dekodieren: die betroffene Quelle
     * bleibt dauerhaft stumm (Stem wird als Stille gemischt bzw. SFX
     * übersprungen), alles andere spielt weiter.
     */
    data class AssetUnavailable(
        val resourceName: String,
    ) : AudioIssue

    /**
     * Engine-Initialisierung fehlgeschlagen (z. B. AudioTrack nicht
     * verfügbar): Musik bleibt für diese Session aus, SFX laufen — oder
     * umgekehrt; [stage] benennt die betroffene Stufe („mixer", „soundpool",
     * „decoder").
     */
    data class EngineUnavailable(
        val stage: String,
    ) : AudioIssue
}
