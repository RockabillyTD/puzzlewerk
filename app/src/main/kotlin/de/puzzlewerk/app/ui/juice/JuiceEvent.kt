package de.puzzlewerk.app.ui.juice

/**
 * Eingangs-Ereignisse des Juice-Kerns (ADR-011). Der GameScreen/ViewModel
 * übersetzt `MoveResult` + `JuiceDelta` (ADR-012) in diese Ereignisse und hat
 * dabei bereits Hex-Zellen auf dp-Brettkoordinaten gemappt und
 * `LightColor`-Masken auf ARGB aufgelöst (Palette §13.4) — der Kern kennt
 * weder Hex-Geometrie noch :game-Typen.
 *
 * NICHT hier abgebildet (bewusste Abgrenzung, ADR-011): Stern-Einflug,
 * Overlay-Fade, Wackeln und die 150-ms-Drehanimation (Compose-Animationen,
 * §12.3/§13.10) sowie jede SFX-Auslösung (`AudioEngine`, ADR-010).
 */
internal sealed interface JuiceEvent {
    /**
     * Betreten des Spiel-Screens: setzt den Puls-Nullpunkt (§13.8a), den
     * [levelSeed] und die initialen Funken-Emitter (Partie-Start = Zug 0).
     * Löst KEINE Bursts aus (ADR-012: `before = null`).
     */
    data class ScreenEntered(
        val levelSeed: Long,
        val reduceMotion: Boolean,
        val endpoints: List<EndpointSpark>,
    ) : JuiceEvent

    /**
     * R44 kann mid-session umschalten (System-Einstellung): Folge-Spawns haben
     * Partikelzahl 0, bereits lebende Partikel laufen aus.
     */
    data class MotionPreferenceChanged(
        val reduceMotion: Boolean,
    ) : JuiceEvent

    /**
     * Gültige Drehung (§13.9): genau 3 Funken mit deterministischen Winkeln
     * 30°/150°/270° relativ zur NEUEN Orientierung (KEIN PRNG-Zug), 300 ms,
     * 90 dp/s. Das weiße 120-ms-Element-Overlay ist Compose (s. o.).
     *
     * @property xDp Zentrum des gedrehten Elements.
     * @property yDp Zentrum des gedrehten Elements.
     * @property orientationDegrees Neue Element-Orientierung in Grad —
     *   Bezugssystem der drei Abgangswinkel.
     */
    data class RotateFlash(
        val xDp: Float,
        val yDp: Float,
        val orientationDegrees: Float,
    ) : JuiceEvent

    /**
     * Neu erfüllte Kristalle eines Zugs (§13.9). [bursts] kommt bereits in
     * Kaskadenreihenfolge (aufsteigend r, dann q — Sortierung liefert
     * `JuiceDelta.newlyFulfilled`, ADR-012); der Stepper plant daraus
     * [ScheduledBurst]s mit 40-ms-Versatz und Kappe ab dem 5. Burst (R45)
     * und zieht je Burst die Partikelzahl P = 8 + nextInt(5) aus dem
     * Juice-PRNG (§13.9).
     */
    data class CrystalBursts(
        val moveNumber: Int,
        val bursts: List<BurstOrigin>,
    ) : JuiceEvent

    /**
     * Strahl-Endpunkte nach einem Zug (§13.8a): ersetzt die Menge der
     * kontinuierlichen Funken-Emitter vollständig. Reihenfolge = Reihenfolge
     * in `TraceResult.endpoints` (ADR-012) — sie bestimmt den `emitterIndex`.
     */
    data class EndpointsChanged(
        val moveNumber: Int,
        val endpoints: List<EndpointSpark>,
    ) : JuiceEvent

    /**
     * Lösender Zug (§13.10): Der Stepper berechnet t_fw aus der Kaskade
     * desselben Zugs (t_fw = 40 · (min(N, 5) − 1) ms), plant Flash (80 ms,
     * Alpha 0,35) und Feuerwerk (F = min(120, 60 + 12·K) Partikel) am Ort des
     * LETZTEN Kristall-Bursts. Sterne/Overlay laufen als Compose-Animationen
     * auf derselben Zeitachse (nicht hier).
     *
     * KONTRAKT (PW-4.6, Klärung des PW-4.4-Backlog-MINORs): [Solved] MUSS im
     * SELBEN `step()`-Aufruf NACH dem [CrystalBursts]-Ereignis DESSELBEN Zugs
     * (gleiche `moveNumber`) eingereiht werden — der Stepper leitet den
     * Feuerwerk-Ursprung aus frame-lokalem Kaskaden-Wissen ab und verwirft
     * Kaskaden fremder Züge. Ohne passende Kaskade fällt der Ursprung auf
     * (0, 0) und t_fw auf 0 zurück. Der Produzent (GameViewModel →
     * `offerJuiceEvents`) erfüllt das, indem er beide Ereignisse in einem
     * Rutsch in die [JuiceEventQueue] legt (ein `drain()` = ein Frame).
     *
     * @property moveNumber Der lösende Zug (Seed-Bestandteil, emitterIndex
     *   2000 laut ADR-011).
     * @property crystalCount K = Kristallzahl des Levels (Partikelformel).
     * @property paletteArgb Soll-Farben ALLER Kristalle in Brett-Reihenfolge
     *   (r, dann q) — Feuerwerkspartikel durchlaufen sie zyklisch (§13.10).
     */
    data class Solved(
        val moveNumber: Int,
        val crystalCount: Int,
        val paletteArgb: List<Int>,
    ) : JuiceEvent

    /**
     * R49: „Nochmal", „Weiter" oder Zurück-Navigation während laufender
     * Effekte — Partikel, geplante Bursts und Flash werden SOFORT verworfen
     * (kein Effekt-Überhang auf dem nächsten Brett).
     */
    data object Dismissed : JuiceEvent
}

/**
 * Ursprung eines Kristall-Bursts (§13.9).
 *
 * @property xDp Kristall-Zentrum (dp-Brettkoordinaten).
 * @property yDp Kristall-Zentrum (dp-Brettkoordinaten).
 * @property colorArgb SOLL-Farbe des Kristalls (ARGB, Palette §13.4).
 */
internal data class BurstOrigin(
    val xDp: Float,
    val yDp: Float,
    val colorArgb: Int,
)

/**
 * Strahl-Endpunkt als Emitter-Vorlage (§13.8a; Quelle:
 * `TraceResult.endpoints`, ADR-012 — Brett-Aus-Absorptionen kommen hier nie an).
 *
 * @property xDp Auftreffpunkt (dp-Brettkoordinaten).
 * @property yDp Auftreffpunkt (dp-Brettkoordinaten).
 * @property colorArgb Strahlfarbe des absorbierten Strahls (ARGB).
 */
internal data class EndpointSpark(
    val xDp: Float,
    val yDp: Float,
    val colorArgb: Int,
)
