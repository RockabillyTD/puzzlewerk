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

/** Vollbild-Flash (§13.10): additives Weiß, Alpha liefert der JuiceState. */
private val FLASH_COLOR = Color.White

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
 * Partikel-Layer und Vollbild-Flash aus dem [JuiceState]-Snapshot (§13.8a
 * Auftreff-Funken, §13.9 Burst-Partikel, §13.10 Feuerwerk/Flash) — additiv
 * über allen Brett-Schichten.
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
    if (juice.flashAlpha > 0f) {
        drawRect(color = FLASH_COLOR, alpha = juice.flashAlpha.coerceIn(0f, 1f), blendMode = BlendMode.Plus)
    }
}
