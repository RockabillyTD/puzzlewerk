package de.puzzlewerk.app.ui.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import de.puzzlewerk.app.ui.juice.JuiceState

// Laser-Look §13.8a — normative, absolute dp-Maße (bewusst NICHT zellrelativ).
private val CORE_WIDTH = 3.dp
private val HALO_WIDTH_OUTER = 12.dp
private val HALO_WIDTH_MID = 8.dp
private val HALO_WIDTH_INNER = 5.dp

// Alpha-Treppe der drei additiven Halo-Ringe (Canvas-only-Näherung des radialen
// Verlaufs aus §13.8a/ADR-011): die Summe am Kern beträgt 0,55, nach außen
// bleibt nur der äußerste Ring — Alpha läuft sichtbar von 55 % auf 0 aus.
private const val HALO_ALPHA_OUTER = 0.14f
private const val HALO_ALPHA_MID = 0.18f
private const val HALO_ALPHA_INNER = 0.23f

/** Kern-Weiß #F0F0F3 (§13.8a: der Kern ist bei JEDER Strahlfarbe weiß). */
private val CORE_COLOR = Color(0xFFF0F0F3)

// Glow-Burst §13.9 als 3-Stufen-Treppe konzentrischer Kreise (Canvas-Näherung
// des radialen Verlaufs, analog zur abgenommenen Halo-Treppe aus PW-4.5 —
// allokationsfreier Draw-Pfad statt frischer RadialGradient-Brushes je Frame).
// Additive Summe im Zentrum = 1,0 · Glow-Alpha; außen bleibt nur die äußerste
// Stufe — das Alpha läuft sichtbar von 0,8 auf 0 aus (§13.9).
private const val GLOW_ALPHA_OUTER = 0.27f
private const val GLOW_ALPHA_MID = 0.33f
private const val GLOW_ALPHA_INNER = 0.40f
private const val GLOW_RADIUS_MID = 0.66f
private const val GLOW_RADIUS_INNER = 0.33f

/**
 * Laser-Rendering der Strahl-Segmente (§13.8a, ADR-011 Canvas-only): je Strahl
 * ein additiver Halo in Strahlfarbe (drei Ringe, [BlendMode.Plus]), darüber der
 * weiße 3-dp-Kern, zuoberst der §13.2-Musterkanal (A11y: der Laser-Look ersetzt
 * KEINEN Kanal). Sekundärfarben tragen im Halo ihre Mischfarbe (Palette §13.4);
 * wo sich Halos kreuzen, mischt das additive Blending die Komponentenfarben
 * physikalisch sichtbar. [haloPulseFactor] moduliert NUR das Halo-Alpha
 * (2-Hz-Puls aus dem Stepper; konstant 1,0 unter Reduce-Motion, R44) — der
 * Kern pulsiert nicht (§13.8a).
 *
 * Draw-Pfad ohne Allokation: nur Iteration über vorberechnete [LineSpec]s;
 * Farben/Offsets sind Value-Klassen, Breiten reine Float-Rechnung.
 */
internal fun DrawScope.drawLaserBeams(
    beams: List<LineSpec>,
    haloPulseFactor: Float,
) {
    val outerPx = HALO_WIDTH_OUTER.toPx()
    val midPx = HALO_WIDTH_MID.toPx()
    val innerPx = HALO_WIDTH_INNER.toPx()
    val corePx = CORE_WIDTH.toPx()
    // Erst ALLE Halos (additiv, damit Kreuzungen mischen), dann alle Kerne,
    // dann die Muster — so liegt nie ein Halo über einem fremden Kern.
    for (i in 0 until beams.size) {
        val beam = beams[i]
        haloLine(beam, outerPx, HALO_ALPHA_OUTER * haloPulseFactor)
        haloLine(beam, midPx, HALO_ALPHA_MID * haloPulseFactor)
        haloLine(beam, innerPx, HALO_ALPHA_INNER * haloPulseFactor)
    }
    for (i in 0 until beams.size) {
        val beam = beams[i]
        drawLine(color = CORE_COLOR, start = beam.start, end = beam.end, strokeWidth = corePx, cap = StrokeCap.Round)
    }
    for (i in 0 until beams.size) {
        val beam = beams[i]
        val effect = beam.pathEffect ?: continue // Mischfarben: kein Muster — ihre Symbol-Chips liegen im Overlay
        drawLine(
            color = beam.color,
            start = beam.start,
            end = beam.end,
            strokeWidth = corePx,
            cap = StrokeCap.Round,
            pathEffect = effect,
        )
    }
}

/** Ein Halo-Ring: Strahlfarbe, additiv, Alpha bereits pulsmoduliert. */
private fun DrawScope.haloLine(
    beam: LineSpec,
    widthPx: Float,
    alpha: Float,
) {
    drawLine(
        color = beam.color,
        start = beam.start,
        end = beam.end,
        strokeWidth = widthPx,
        cap = StrokeCap.Round,
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.Plus,
    )
}

/**
 * Glow- und Partikel-Layer aus dem [JuiceState]-Snapshot (§13.9 Glow-Bursts +
 * Burst-Partikel, §13.8a Auftreff-Funken, §13.10 Feuerwerk) — additiv über
 * allen Brett-Schichten. Der Screen-Flash (§13.10: VOLLBILD) wird seit PW-4.7
 * NICHT mehr hier gezeichnet, sondern im GameScreen-Root ([SolveFlashOverlay])
 * — die Auflage MINOR-2 aus PW-4.5 (Brett-only-Flash) ist damit eingelöst.
 *
 * Koordinaten-Vertrag (für PW-4.6): `xDp`/`yDp` sind dp relativ zur LINKEN
 * OBEREN Ecke des BoardCanvas; gezeichnet wird mit `dp.toPx()` derselben
 * Density. Wer Events erzeugt, mappt Zellzentren also `pxPosition / density`.
 *
 * Draw-Pfad ohne Allokation: indexbasierte Iteration `0 until count` über die
 * SoA-Arrays; gelesen werden nur count/x/y/size/alpha/color (ADR-011) —
 * [Color]/[Offset]/Dp sind Value-Klassen, es entsteht kein Objekt.
 */
internal fun DrawScope.drawJuiceEffects(juice: JuiceState) {
    val glows = juice.glows
    for (i in 0 until glows.size) {
        val glow = glows[i]
        val radiusPx = glow.radiusDp.dp.toPx()
        if (radiusPx <= 0f || glow.alpha <= 0f) continue
        val center = Offset(glow.xDp.dp.toPx(), glow.yDp.dp.toPx())
        val color = Color(glow.colorArgb)
        glowRing(color, center, radiusPx, glow.alpha * GLOW_ALPHA_OUTER)
        glowRing(color, center, radiusPx * GLOW_RADIUS_MID, glow.alpha * GLOW_ALPHA_MID)
        glowRing(color, center, radiusPx * GLOW_RADIUS_INNER, glow.alpha * GLOW_ALPHA_INNER)
    }
    val particles = juice.particles
    for (i in 0 until particles.count) {
        drawCircle(
            color = Color(particles.colorArgb[i]),
            radius = particles.sizeDp[i].dp.toPx() / 2f,
            center = Offset(particles.xDp[i].dp.toPx(), particles.yDp[i].dp.toPx()),
            alpha = particles.alpha[i].coerceIn(0f, 1f),
            blendMode = BlendMode.Plus,
        )
    }
}

/** Eine Glow-Stufe: gefüllter Kreis in Kristallfarbe, additiv (§13.9/ADR-011). */
private fun DrawScope.glowRing(
    color: Color,
    center: Offset,
    radiusPx: Float,
    alpha: Float,
) {
    drawCircle(
        color = color,
        radius = radiusPx,
        center = center,
        alpha = alpha.coerceIn(0f, 1f),
        blendMode = BlendMode.Plus,
    )
}
