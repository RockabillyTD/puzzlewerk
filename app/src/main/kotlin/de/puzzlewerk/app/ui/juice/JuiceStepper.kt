package de.puzzlewerk.app.ui.juice

import de.puzzlewerk.core.RandomSource

/**
 * Reine Übergangsfunktion des Juice-Layers (§13.13, C2-Pflicht; ADR-011).
 *
 * Vertrag:
 * - PUR: kein Uhr-, IO- oder Android-Zugriff; gleiche Eingaben
 *   `(state, events, dtMillis)` ⇒ bit-identischer Folgezustand. Feste
 *   dt-Folgen sind die Testoberfläche des test-engineers (§13.13).
 * - Zufall wird ausschließlich beim SPAWN gezogen (Emitter/Burst/Partikel-
 *   Entstehung), nie während der Integration; Quelle ist die injizierte
 *   [JuiceRandomFactory] mit der Seed-Ableitung aus ADR-011:
 *   `juiceSeed = mix64(mix64(mix64(levelSeed) + zugNummer) + emitterIndex)`.
 * - Kein Rückfluss: liest und verändert keinerlei Spiellogik-Zustand
 *   (strikte Trennung vom Spiel-PRNG, §13.13).
 *
 * Implementierung: PW-4.4 (Kern ohne Rendering); Rendering/Choreografie:
 * PW-4.5–4.7 (Ticket-Nummern laut 10-Punkte-Plan).
 */
internal fun interface JuiceStepper {
    /**
     * Führt den Effekt-Zustand um [dtMillis] fort und arbeitet [events] ein
     * (Reihenfolge der Liste = Verarbeitungsreihenfolge).
     *
     * @param state Snapshot des Vor-Frames (wird NIE mutiert).
     * @param events In diesem Frame eingetroffene Ereignisse; meist leer.
     * @param dtMillis Frame-Delta in Millisekunden (aus `withFrameNanos`);
     *   0 ist gültig und liefert einen zustandsgleichen Snapshot bis auf
     *   eingearbeitete [events].
     * @return Neuer unveränderlicher Snapshot für Renderer und Folge-Frame.
     */
    fun step(
        state: JuiceState,
        events: List<JuiceEvent>,
        dtMillis: Long,
    ): JuiceState
}

/**
 * Injektionspunkt für den Juice-PRNG (§13.13): Produktion nutzt
 * `SplitMix64Random` aus :core (ADR-003); Tests injizieren zählende oder
 * fixierte Quellen. Jeder Emitter erhält seine EIGENE, aus `juiceSeed`
 * erzeugte Quelle — nie eine geteilte Instanz (Determinismus unabhängig von
 * Emitter-Reihenfolge).
 */
internal fun interface JuiceRandomFactory {
    /** Erzeugt eine frische, deterministische Zufallsquelle für [seed]. */
    fun create(seed: Long): RandomSource
}
