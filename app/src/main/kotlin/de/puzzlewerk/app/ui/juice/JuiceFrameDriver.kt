package de.puzzlewerk.app.ui.juice

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** Obergrenze des an [JuiceStepper.step] gereichten Frame-Deltas (Begründung: [clampFrameDelta]). */
internal const val MAX_FRAME_DELTA_MILLIS: Long = 100L

private const val NANOS_PER_MILLI: Long = 1_000_000L

/**
 * Kappt das rohe Frame-Delta auf `0..`[MAX_FRAME_DELTA_MILLIS] — Auflage aus dem
 * PW-4.4-Security-Audit (LOW-1): Nach App-Background/Prozess-Pause liefert der
 * erste Frame ein dt in Sekunden- bis Minuten-Größenordnung; `emitSparks` im
 * Stepper spawnt Funken proportional zur verstrichenen Zeit und würde die
 * gesamte Pause auf einen Schlag „nachholen" (Partikel-/CPU-Spitze beim
 * Wiedereintritt). 100 ms erlauben jeden realen Frame-Hänger (< 10 fps),
 * begrenzen den Nachhol-Spawn aber auf < 1 Funke je Emitter. Negative Deltas
 * (Uhr-Anomalien) werden auf 0 gekappt.
 */
internal fun clampFrameDelta(rawDtMillis: Long): Long = rawDtMillis.coerceIn(0L, MAX_FRAME_DELTA_MILLIS)

/**
 * Haupt-Thread-Postfach für [JuiceEvent]s an den Frame-Treiber: der GameScreen
 * (ab PW-4.6 gespeist aus ViewModel-Effects) legt Ereignisse ab, der Treiber
 * entnimmt sie EINMAL pro Frame und reicht sie gesammelt an
 * [JuiceStepper.step] (Listenreihenfolge = Verarbeitungsreihenfolge).
 * Kein Lock: Ablage und Entnahme laufen beide auf dem Compose-Haupt-Thread.
 */
internal class JuiceEventQueue {
    private val pending = mutableListOf<JuiceEvent>()

    /** Reiht [event] für den nächsten Frame ein. */
    fun offer(event: JuiceEvent) {
        pending.add(event)
    }

    /** Entnimmt alle wartenden Ereignisse in Eingangsreihenfolge. */
    fun drain(): List<JuiceEvent> {
        if (pending.isEmpty()) return emptyList()
        val drained = pending.toList()
        pending.clear()
        return drained
    }
}

/**
 * withFrameNanos-Treiber des Juice-Kerns (ADR-011): führt [stepper] pro
 * Choreographer-Frame um das geklammerte dt fort ([clampFrameDelta], LOW-1)
 * und veröffentlicht den [JuiceState]-Snapshot als [State] für den Draw-Pfad.
 *
 * Frame-Quelle ist bewusst `withInfiniteAnimationFrameNanos` (identisch zu
 * `withFrameNanos`, solange keine `InfiniteAnimationPolicy` installiert ist):
 * Der Compose-Test-Harness bricht so markierte Endlos-Loops bei laufender
 * `mainClock.autoAdvance` ab (kein `waitForIdle`-Hänger in bestehenden
 * Robolectric-Tests), während eine MANUELL getriebene Testuhr
 * (`autoAdvance = false` VOR `setContent`) den Treiber deterministisch
 * Frame für Frame fortschaltet — die Testoberfläche dieses Tickets.
 *
 * Recomposition-Hygiene: Der Snapshot wird nur veröffentlicht, wenn sich
 * render-relevante Werte ändern ([rendersDifferently]) — unter Reduce-Motion
 * ohne aktive Effekte invalidiert der Treiber also NICHTS. Konsumenten lesen
 * den [State] ausschließlich in der Draw-Phase (BoardCanvas), sodass ein
 * Frame-Update nur neu zeichnet, nie rekomponiert.
 */
@Composable
internal fun rememberJuiceFrameState(
    events: JuiceEventQueue,
    stepper: JuiceStepper = remember { DefaultJuiceStepper() },
): State<JuiceState> {
    val rendered = remember(events, stepper) { mutableStateOf(JuiceState.EMPTY) }
    LaunchedEffect(events, stepper) {
        var current = JuiceState.EMPTY
        var lastFrameNanos = Long.MIN_VALUE
        while (true) {
            withInfiniteAnimationFrameNanos { frameNanos ->
                val rawDt =
                    if (lastFrameNanos == Long.MIN_VALUE) 0L else (frameNanos - lastFrameNanos) / NANOS_PER_MILLI
                lastFrameNanos = frameNanos
                // dt-Kappe VOR step() — Begründung und Grenzwert: clampFrameDelta (LOW-1).
                current = stepper.step(current, events.drain(), clampFrameDelta(rawDt))
                if (rendersDifferently(rendered.value, current)) rendered.value = current
            }
        }
    }
    return rendered
}

/**
 * Render-relevanter Unterschied zweier Snapshots: lebende Partikel (Positionen
 * wandern jeden Frame), Halo-Puls oder Flash. `elapsedMillis`/Emitter allein
 * ändern das Bild nicht — solche Frames werden nicht veröffentlicht.
 */
private fun rendersDifferently(
    old: JuiceState,
    new: JuiceState,
): Boolean =
    old.particles.count > 0 ||
        new.particles.count > 0 ||
        old.haloPulseFactor != new.haloPulseFactor ||
        old.flashAlpha != new.flashAlpha
