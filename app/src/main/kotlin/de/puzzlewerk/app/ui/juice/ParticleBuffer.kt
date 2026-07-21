package de.puzzlewerk.app.ui.juice

/**
 * Veränderlicher Partikel-Pool (Structure-of-Arrays), ausschließlich intern in
 * [JuiceStepper.step] verwendet. Immutability gilt für den [JuiceState]-Snapshot,
 * NICHT für den Rechenpuffer im Step-Pfad (ADR-011: „Immutability schlägt Pooling
 * im Step-Pfad; Allokationsfreiheit gilt verbindlich nur für den Draw-Pfad").
 *
 * Kapazität [MAX_PARTICLES] deckt den §13-Worst-Case (Feuerwerk 120 + Kaskade
 * 6·12 + Dreh-Funken 3 + reichlich Endpunkt-Funken); §13 pinnt keine Einzelzahl,
 * die Invariante „nie überschritten" ist die Pflicht. [add] verwirft ab Kapazität.
 */
internal class ParticleBuffer {
    var count: Int = 0
        private set

    private val x = FloatArray(MAX_PARTICLES)
    private val y = FloatArray(MAX_PARTICLES)
    private val vx = FloatArray(MAX_PARTICLES)
    private val vy = FloatArray(MAX_PARTICLES)
    private val size = FloatArray(MAX_PARTICLES)
    private val alpha = FloatArray(MAX_PARTICLES)
    private val gravity = FloatArray(MAX_PARTICLES)
    private val fade = FloatArray(MAX_PARTICLES)
    private val color = IntArray(MAX_PARTICLES)

    /** Übernimmt die lebenden Partikel eines Snapshots (deep copy in den Pool). */
    fun load(snapshot: ParticleSnapshot) {
        val n = snapshot.count
        snapshot.xDp.copyInto(x, endIndex = n)
        snapshot.yDp.copyInto(y, endIndex = n)
        snapshot.vxDp.copyInto(vx, endIndex = n)
        snapshot.vyDp.copyInto(vy, endIndex = n)
        snapshot.sizeDp.copyInto(size, endIndex = n)
        snapshot.alpha.copyInto(alpha, endIndex = n)
        snapshot.gravityDpPerSec2.copyInto(gravity, endIndex = n)
        snapshot.alphaFadePerMillis.copyInto(fade, endIndex = n)
        snapshot.colorArgb.copyInto(color, endIndex = n)
        count = n
    }

    /** Löscht alle Partikel (R49 [JuiceEvent.Dismissed], Screen-Wechsel). */
    fun clear() {
        count = 0
    }

    /**
     * Spawnt einen Partikel mit Alpha 1 und Fade = 1/[lifeMillis]. Ab
     * [MAX_PARTICLES] wird still verworfen (Kapazitätsinvariante §13).
     */
    @Suppress("LongParameterList")
    fun add(
        px: Float,
        py: Float,
        pvx: Float,
        pvy: Float,
        sizeDp: Float,
        argb: Int,
        lifeMillis: Float,
        grav: Float,
    ) {
        if (count >= MAX_PARTICLES) return
        val i = count
        x[i] = px
        y[i] = py
        vx[i] = pvx
        vy[i] = pvy
        size[i] = sizeDp
        alpha[i] = 1f
        gravity[i] = grav
        fade[i] = 1f / lifeMillis
        color[i] = argb
        count = i + 1
    }

    /**
     * Integriert alle Partikel um [dtMillis] (semi-implizites Euler: Position aus
     * Geschwindigkeit, Geschwindigkeit aus Gravitation, Alpha linear) und entfernt
     * tote Partikel (Alpha ≤ 0) durch Kompaktierung. Deterministische Arithmetik.
     */
    fun integrate(dtMillis: Long) {
        val dtF = dtMillis.toFloat()
        val dtSec = dtF / MILLIS_PER_SEC
        var write = 0
        for (read in 0 until count) {
            val a = alpha[read] - fade[read] * dtF
            if (a <= 0f) continue
            x[write] = x[read] + vx[read] * dtSec
            y[write] = y[read] + vy[read] * dtSec
            vx[write] = vx[read]
            vy[write] = vy[read] + gravity[read] * dtSec
            size[write] = size[read]
            alpha[write] = a
            gravity[write] = gravity[read]
            fade[write] = fade[read]
            color[write] = color[read]
            write++
        }
        count = write
    }

    /** Friert den Pool zu einem unveränderlichen Snapshot exakter Länge ein. */
    fun toSnapshot(): ParticleSnapshot =
        ParticleSnapshot(
            count = count,
            xDp = x.copyOf(count),
            yDp = y.copyOf(count),
            sizeDp = size.copyOf(count),
            alpha = alpha.copyOf(count),
            colorArgb = color.copyOf(count),
            vxDp = vx.copyOf(count),
            vyDp = vy.copyOf(count),
            gravityDpPerSec2 = gravity.copyOf(count),
            alphaFadePerMillis = fade.copyOf(count),
        )
}
