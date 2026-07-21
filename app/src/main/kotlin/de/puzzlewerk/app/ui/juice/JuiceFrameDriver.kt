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
 * Ergebnis der puren dt-Ableitung eines Frames.
 *
 * @property dtMillis An [JuiceStepper.step] zu reichendes, bereits geklammertes
 *   Delta in ganzen Millisekunden.
 * @property consumedNanos Neue Baseline der KONSUMIERTEN Frame-Zeit — der
 *   Sub-Millisekunden-Rest des Frames bleibt darin stehen und zählt im
 *   Folge-Frame mit.
 */
internal class FrameDelta(
    val dtMillis: Long,
    val consumedNanos: Long,
)

/**
 * Pure dt-Ableitung mit Rest-Übertrag (Review PW-4.5, MAJOR-1): Es werden nur
 * GANZE Millisekunden konsumiert, der Sub-ms-Rest bleibt in der Baseline —
 * ohne diesen Übertrag verwirft die Ganzzahldivision bei echten 60 Hz
 * (16.666.667 ns) jeden Frame 0,666 ms: `elapsedMillis` akkumulierte nur
 * 960 ms je Wandsekunde, der Puls liefe mit ~1,92 statt exakt 2,0 Hz
 * (§13.8a) und die Funken-Kadenz mit ~3,84/s statt 4/s.
 *
 * Greift die dt-Kappe ([clampFrameDelta], LOW-1), springt die Baseline auf
 * [frameNanos]: die verworfene Pause darf NICHT als Nachhol-Rest weiterticken.
 * Uhr-Anomalien (Frame-Zeit vor der Baseline) liefern dt 0 und setzen die
 * Baseline ebenfalls neu auf.
 */
internal fun consumeFrameDelta(
    lastConsumedNanos: Long,
    frameNanos: Long,
): FrameDelta {
    val elapsedNanos = frameNanos - lastConsumedNanos
    if (elapsedNanos <= 0L) return FrameDelta(0L, frameNanos)
    val rawMillis = elapsedNanos / NANOS_PER_MILLI
    val dtMillis = clampFrameDelta(rawMillis)
    return if (dtMillis < rawMillis) {
        FrameDelta(dtMillis, frameNanos)
    } else {
        FrameDelta(dtMillis, lastConsumedNanos + dtMillis * NANOS_PER_MILLI)
    }
}

/**
 * Kapazitätsgrenze der [JuiceEventQueue] (PW-4.5-Security-MINOR-2, Härtung in
 * PW-4.6, seit das ViewModel Produzent ist): Die Queue wächst nur, wenn der
 * Frame-Loop steht (z. B. UI im Hintergrund) — 64 Einträge fassen weit mehr
 * Züge, als ein Mensch zwischen zwei Frames auslösen kann.
 */
internal const val MAX_PENDING_JUICE_EVENTS: Int = 64

/**
 * Haupt-Thread-Postfach für [JuiceEvent]s an den Frame-Treiber: der GameScreen
 * (ab PW-4.6 gespeist aus ViewModel-Effects) legt Ereignisse ab, der Treiber
 * entnimmt sie EINMAL pro Frame und reicht sie gesammelt an
 * [JuiceStepper.step] (Listenreihenfolge = Verarbeitungsreihenfolge).
 * Kein Lock: Ablage und Entnahme laufen beide auf dem Compose-Haupt-Thread.
 *
 * Überlauf ab [MAX_PENDING_JUICE_EVENTS]: SILENT-DROP des neuen Ereignisses —
 * dieselbe Degradationspolitik wie der ParticleBuffer (Effekte sind nie
 * tragender Feedback-Kanal, nie crashen). Coalescing wäre komplexer und
 * gewönne nichts: Der Fall tritt nur bei stehendem Frame-Loop auf, und der
 * nächste `drain()` leert ohnehin alles auf einmal.
 */
internal class JuiceEventQueue {
    private val pending = mutableListOf<JuiceEvent>()

    /** Reiht [event] für den nächsten Frame ein; voll ⇒ Silent-Drop (s. o.). */
    fun offer(event: JuiceEvent) {
        if (pending.size >= MAX_PENDING_JUICE_EVENTS) return
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
 * Choreographer-Frame um das geklammerte dt mit Sub-ms-Rest-Übertrag fort
 * ([consumeFrameDelta] — Clamp LOW-1 plus drift-freie 2-Hz-Pulsbasis) und
 * veröffentlicht den [JuiceState]-Snapshot als [State] für den Draw-Pfad.
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
        var consumedNanos = Long.MIN_VALUE
        while (true) {
            withInfiniteAnimationFrameNanos { frameNanos ->
                // Erster Frame setzt nur die Baseline (dt 0); danach pure dt-Ableitung
                // mit Kappe VOR step() (LOW-1) und Sub-ms-Rest-Übertrag (MAJOR-1).
                val delta =
                    if (consumedNanos == Long.MIN_VALUE) {
                        FrameDelta(0L, frameNanos)
                    } else {
                        consumeFrameDelta(consumedNanos, frameNanos)
                    }
                consumedNanos = delta.consumedNanos
                current = stepper.step(current, events.drain(), delta.dtMillis)
                if (rendersDifferently(rendered.value, current)) rendered.value = current
            }
        }
    }
    return rendered
}

/**
 * Render-relevanter Unterschied zweier Snapshots: lebende Partikel (Positionen
 * wandern jeden Frame), Glow-Bursts (Radius/Alpha wandern jeden Frame, PW-4.6),
 * Halo-Puls oder Flash. `elapsedMillis`/Emitter allein ändern das Bild nicht —
 * solche Frames werden nicht veröffentlicht.
 */
private fun rendersDifferently(
    old: JuiceState,
    new: JuiceState,
): Boolean =
    old.particles.count > 0 ||
        new.particles.count > 0 ||
        old.glows.isNotEmpty() ||
        new.glows.isNotEmpty() ||
        old.haloPulseFactor != new.haloPulseFactor ||
        old.flashAlpha != new.flashAlpha
