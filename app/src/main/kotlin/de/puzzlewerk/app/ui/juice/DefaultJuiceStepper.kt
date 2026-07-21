package de.puzzlewerk.app.ui.juice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// --- Kapazität & Zeit (von ParticleBuffer mitgenutzt) ---
internal const val MAX_PARTICLES: Int = 512
internal const val MILLIS_PER_SEC: Float = 1000f

// --- Auftreff-/Dreh-Funken (§13.8a / §13.9) ---
private const val SPARK_INTERVAL_MS: Long = 250L // 4 Funken/s
private const val SPARK_LIFE_MS: Float = 400f
private const val SPARK_SPEED: Float = 60f
private const val SPARK_SIZE: Float = 2f
private const val ROTATE_LIFE_MS: Float = 300f
private const val ROTATE_SPEED: Float = 90f
private val ROTATE_ANGLES: FloatArray = floatArrayOf(30f, 150f, 270f)
private val WHITE_ARGB: Int = 0xFFF0F0F3.toInt() // Laser-Kern-Weiß #F0F0F3 (§13.8a)

// --- Kristall-Burst (§13.9) ---
private const val CRYSTAL_P_BASE: Int = 8
private const val CRYSTAL_P_SPAN: Int = 5 // P = 8 + nextInt(5) ∈ {8..12}
private const val CRYSTAL_LIFE_MS: Float = 600f
private const val CRYSTAL_SPEED_MIN: Float = 80f
private const val CRYSTAL_SPEED_SPAN: Float = 80f // 80..160 dp/s
private const val CRYSTAL_GRAVITY: Float = 240f
private const val CRYSTAL_SIZE: Float = 3f
private const val CRYSTAL_EMITTER_BASE: Int = 1000

// --- Kaskade (§13.9 / R45) ---
private const val CASCADE_STEP_MS: Long = 40L
private const val CASCADE_CAP_INDEX: Int = 4 // ab dem 5. Burst (Index 4) gleichzeitig

// --- Feuerwerk (§13.10) ---
private const val FIREWORK_EMITTER_INDEX: Int = 2000
private const val FIREWORK_CAP: Int = 120
private const val FIREWORK_BASE: Int = 60
private const val FIREWORK_PER_K: Int = 12 // F = min(120, 60 + 12·K)
private const val FIREWORK_SPEED_MIN: Float = 120f
private const val FIREWORK_SPEED_SPAN: Float = 200f // 120..320 dp/s
private const val FIREWORK_LIFE_MIN_MS: Float = 900f
private const val FIREWORK_LIFE_SPAN_MS: Float = 500f // 0,9..1,4 s
private const val FIREWORK_GRAVITY: Float = 480f
private const val FIREWORK_SIZE: Float = 3f

// --- Flash (§13.10 / §13.12) ---
private const val FLASH_ALPHA: Float = 0.35f
private const val FLASH_MS: Long = 80L
private const val FLASH_RM_PEAK: Float = 0.15f
private const val FLASH_RM_MS: Long = 400L
private const val FLASH_RM_HALF_MS: Float = 200f

// --- Halo-Puls (§13.8a) ---
private const val PULSE_BASE: Float = 1f
private const val PULSE_AMP: Float = 0.2f
private const val PULSE_HZ: Float = 2f
private val PULSE_RAD_PER_MS: Float = (2.0 * PI * PULSE_HZ / MILLIS_PER_SEC).toFloat()
private val TWO_PI: Float = (2.0 * PI).toFloat()
private val DEG_TO_RAD: Float = (PI / 180.0).toFloat()

/** t = Kaskadenversatz des k-ten Bursts (§13.9): 40 ms je Burst, Kappe ab dem 5. */
private fun cascadeOffset(index: Int): Long = min(index, CASCADE_CAP_INDEX).toLong() * CASCADE_STEP_MS

/** Halo-Grundalpha-Faktor: 1 ± 0,2 sin(2π·2 Hz·t), phasensynchron ab t = 0; 1,0 unter Reduce-Motion. */
private fun haloPulseFactor(
    elapsedMillis: Long,
    reduceMotion: Boolean,
): Float = if (reduceMotion) PULSE_BASE else PULSE_BASE + PULSE_AMP * sin(PULSE_RAD_PER_MS * elapsedMillis.toFloat())

/** Flash-Alpha als Projektion der Restzeit: 0,35→0 über 80 ms; Reduce-Motion 0→0,15→0 über 400 ms. */
private fun flashAlpha(
    remainingMillis: Long,
    reduceMotion: Boolean,
): Float {
    if (remainingMillis <= 0L) return 0f
    if (!reduceMotion) return FLASH_ALPHA * (remainingMillis.toFloat() / FLASH_MS.toFloat())
    val elapsed = FLASH_RM_MS - remainingMillis
    val phase = if (elapsed < FLASH_RM_HALF_MS) elapsed else FLASH_RM_MS - elapsed
    return FLASH_RM_PEAK * (phase.toFloat() / FLASH_RM_HALF_MS)
}

/** Veränderlicher Rechen-Kontext eines Frames (nur intern; der Snapshot bleibt immutabel). */
private class Frame(state: JuiceState, val factory: JuiceRandomFactory) {
    var elapsed: Long = state.elapsedMillis
    var reduceMotion: Boolean = state.reduceMotion
    var levelSeed: Long = state.levelSeed
    var flashRemaining: Long = state.flashRemainingMillis
    val emitters: MutableList<SparkEmitter> = state.emitters.toMutableList()
    val pending: MutableList<ScheduledBurst> = state.pendingBursts.toMutableList()
    val buffer: ParticleBuffer = ParticleBuffer().apply { load(state.particles) }
    var cascade: CascadeInfo? = null

    fun setEmitters(
        moveNumber: Int,
        endpoints: List<EndpointSpark>,
    ) {
        emitters.clear()
        endpoints.forEachIndexed { i, ep ->
            emitters.add(SparkEmitter(ep.xDp, ep.yDp, ep.colorArgb, i, moveNumber, spawnedCount = 0))
        }
    }

    fun toState(
        halo: Float,
        flash: Float,
    ): JuiceState =
        JuiceState(
            elapsedMillis = elapsed,
            reduceMotion = reduceMotion,
            levelSeed = levelSeed,
            haloPulseFactor = halo,
            flashAlpha = flash,
            emitters = emitters.toList(),
            pendingBursts = pending.toList(),
            particles = buffer.toSnapshot(),
            flashRemainingMillis = flashRemaining,
        )
}

/** Ursprung des Feuerwerks: letzter Kristall-Burst der Kaskade DESSELBEN Zugs (§13.10). */
private class CascadeInfo(val moveNumber: Int, val count: Int, val lastX: Float, val lastY: Float)

/**
 * Reiner Juice-Kern (ADR-011, §13.13). Integriert lebende Partikel, arbeitet die
 * Frame-Ereignisse ein und zieht Zufall AUSSCHLIESSLICH beim Spawn (Spawn-only).
 * Determinismus: gleiche `(state, events, dt)` ⇒ bit-identischer Folge-Snapshot.
 */
internal class DefaultJuiceStepper(
    private val factory: JuiceRandomFactory = DefaultJuiceRandomFactory(),
) : JuiceStepper {
    override fun step(
        state: JuiceState,
        events: List<JuiceEvent>,
        dtMillis: Long,
    ): JuiceState {
        val frame = Frame(state, factory)
        frame.buffer.integrate(dtMillis)
        frame.flashRemaining = maxOf(0L, frame.flashRemaining - dtMillis)
        events.forEach { applyEvent(frame, it) }
        val prev = frame.elapsed
        val newElapsed = prev + dtMillis
        fireBursts(frame, newElapsed)
        emitSparks(frame, prev, newElapsed)
        frame.elapsed = newElapsed
        return frame.toState(
            haloPulseFactor(newElapsed, frame.reduceMotion),
            flashAlpha(frame.flashRemaining, frame.reduceMotion),
        )
    }

    private fun applyEvent(
        frame: Frame,
        event: JuiceEvent,
    ) {
        when (event) {
            is JuiceEvent.ScreenEntered -> onScreenEntered(frame, event)
            is JuiceEvent.MotionPreferenceChanged -> frame.reduceMotion = event.reduceMotion
            is JuiceEvent.RotateFlash -> onRotate(frame, event)
            is JuiceEvent.CrystalBursts -> onCrystalBursts(frame, event)
            is JuiceEvent.EndpointsChanged -> frame.setEmitters(event.moveNumber, event.endpoints)
            is JuiceEvent.Solved -> onSolved(frame, event)
            JuiceEvent.Dismissed -> onDismissed(frame)
        }
    }

    private fun onScreenEntered(
        frame: Frame,
        event: JuiceEvent.ScreenEntered,
    ) {
        frame.buffer.clear()
        frame.pending.clear()
        frame.flashRemaining = 0L
        frame.elapsed = 0L // Puls-Nullpunkt (§13.8a)
        frame.reduceMotion = event.reduceMotion
        frame.levelSeed = event.levelSeed
        frame.setEmitters(moveNumber = 0, endpoints = event.endpoints)
    }

    /** R49: kein Effekt-Überhang auf dem nächsten Brett — Partikel, Bursts, Flash und Emitter fort. */
    private fun onDismissed(frame: Frame) {
        frame.buffer.clear()
        frame.pending.clear()
        frame.emitters.clear()
        frame.flashRemaining = 0L
    }

    private fun onRotate(
        frame: Frame,
        event: JuiceEvent.RotateFlash,
    ) {
        if (frame.reduceMotion) return // 0 Partikel (R44); Compose-Overlay/Wackeln bleiben
        for (base in ROTATE_ANGLES) {
            val angle = (event.orientationDegrees + base) * DEG_TO_RAD
            val vx = ROTATE_SPEED * cos(angle)
            val vy = ROTATE_SPEED * sin(angle)
            frame.buffer.add(event.xDp, event.yDp, vx, vy, SPARK_SIZE, WHITE_ARGB, ROTATE_LIFE_MS, 0f)
        }
    }

    private fun onCrystalBursts(
        frame: Frame,
        event: JuiceEvent.CrystalBursts,
    ) {
        event.bursts.forEachIndexed { k, origin ->
            val emitterIndex = CRYSTAL_EMITTER_BASE + k
            val particleCount =
                if (frame.reduceMotion) {
                    0
                } else {
                    val rng = frame.factory.create(juiceSeed(frame.levelSeed, event.moveNumber, emitterIndex))
                    CRYSTAL_P_BASE + rng.nextInt(CRYSTAL_P_SPAN)
                }
            frame.pending.add(
                ScheduledBurst(
                    startAtMillis = frame.elapsed + cascadeOffset(k),
                    kind = BurstKind.CRYSTAL,
                    xDp = origin.xDp,
                    yDp = origin.yDp,
                    colorsArgb = listOf(origin.colorArgb),
                    particleCount = particleCount,
                    emitterIndex = emitterIndex,
                    moveNumber = event.moveNumber,
                ),
            )
        }
        val last = event.bursts.lastOrNull() ?: return
        frame.cascade = CascadeInfo(event.moveNumber, event.bursts.size, last.xDp, last.yDp)
    }

    private fun onSolved(
        frame: Frame,
        event: JuiceEvent.Solved,
    ) {
        val cascade = frame.cascade
        val count = cascade?.count ?: 1
        val particleCount =
            if (frame.reduceMotion) 0 else min(FIREWORK_CAP, FIREWORK_BASE + FIREWORK_PER_K * event.crystalCount)
        // startAtMillis = frame.elapsed + t_fw (t_fw = Kaskadenversatz des letzten Bursts).
        frame.pending.add(
            ScheduledBurst(
                startAtMillis = frame.elapsed + cascadeOffset(count - 1),
                kind = BurstKind.FIREWORK,
                xDp = cascade?.lastX ?: 0f,
                yDp = cascade?.lastY ?: 0f,
                colorsArgb = event.paletteArgb,
                particleCount = particleCount,
                emitterIndex = FIREWORK_EMITTER_INDEX,
                moveNumber = event.moveNumber,
            ),
        )
    }

    private fun fireBursts(
        frame: Frame,
        newElapsed: Long,
    ) {
        val remaining = ArrayList<ScheduledBurst>(frame.pending.size)
        for (burst in frame.pending) {
            when {
                burst.startAtMillis > newElapsed -> remaining.add(burst)
                burst.kind == BurstKind.CRYSTAL -> spawnCrystal(frame, burst)
                else -> spawnFirework(frame, burst)
            }
        }
        frame.pending.clear()
        frame.pending.addAll(remaining)
    }

    private fun spawnCrystal(
        frame: Frame,
        burst: ScheduledBurst,
    ) {
        if (burst.particleCount == 0) return
        val rng = frame.factory.create(juiceSeed(frame.levelSeed, burst.moveNumber, burst.emitterIndex))
        rng.nextInt(CRYSTAL_P_SPAN) // P-Zug bei Planung bereits gezogen (ADR-011) — Stream fortschalten
        val argb = burst.colorsArgb.first()
        repeat(burst.particleCount) {
            val angle = rng.nextUnit() * TWO_PI
            val speed = CRYSTAL_SPEED_MIN + rng.nextUnit() * CRYSTAL_SPEED_SPAN
            val vx = speed * cos(angle)
            val vy = speed * sin(angle)
            frame.buffer.add(burst.xDp, burst.yDp, vx, vy, CRYSTAL_SIZE, argb, CRYSTAL_LIFE_MS, CRYSTAL_GRAVITY)
        }
    }

    private fun spawnFirework(
        frame: Frame,
        burst: ScheduledBurst,
    ) {
        // Flash auch bei 0 Partikeln (R44-Fade) — die Firework-Startzeit ist der Flash-Trigger.
        frame.flashRemaining = if (frame.reduceMotion) FLASH_RM_MS else FLASH_MS
        if (burst.particleCount == 0 || burst.colorsArgb.isEmpty()) return
        val rng = frame.factory.create(juiceSeed(frame.levelSeed, burst.moveNumber, burst.emitterIndex))
        repeat(burst.particleCount) { i ->
            val speed = FIREWORK_SPEED_MIN + rng.nextUnit() * FIREWORK_SPEED_SPAN
            val angle = rng.nextUnit() * TWO_PI
            val life = FIREWORK_LIFE_MIN_MS + rng.nextUnit() * FIREWORK_LIFE_SPAN_MS
            val argb = burst.colorsArgb[i % burst.colorsArgb.size]
            val vx = speed * cos(angle)
            val vy = speed * sin(angle)
            frame.buffer.add(burst.xDp, burst.yDp, vx, vy, FIREWORK_SIZE, argb, life, FIREWORK_GRAVITY)
        }
    }

    /** Kontinuierliche Endpunkt-Funken: 4/s je Emitter über die globale 250-ms-Kadenz (§13.8a). */
    private fun emitSparks(
        frame: Frame,
        prevElapsed: Long,
        newElapsed: Long,
    ) {
        if (frame.reduceMotion || frame.emitters.isEmpty()) return // 0 Partikel (R44)
        val due = (newElapsed / SPARK_INTERVAL_MS - prevElapsed / SPARK_INTERVAL_MS).toInt()
        if (due <= 0) return
        for (i in frame.emitters.indices) {
            val e = frame.emitters[i]
            for (j in 0 until due) {
                // Pro-Funke-Seed-Ableitung (SparkEmitter-Vertrag): Emitter-Seed + Funken-Index.
                val seed = juiceSeed(frame.levelSeed, e.moveNumber, e.emitterIndex) + e.spawnedCount + j
                val angle = frame.factory.create(seed).nextUnit() * TWO_PI
                val vx = SPARK_SPEED * cos(angle)
                val vy = SPARK_SPEED * sin(angle)
                frame.buffer.add(e.xDp, e.yDp, vx, vy, SPARK_SIZE, e.colorArgb, SPARK_LIFE_MS, 0f)
            }
            frame.emitters[i] = e.copy(spawnedCount = e.spawnedCount + due)
        }
    }
}
