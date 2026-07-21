package de.puzzlewerk.app.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

// Stern-Choreografie §13.10 Nr. 5: Stern n (n = 1…verdiente Sterne) startet bei
// t_fw + 120 + (n−1)·150 ms und fliegt mit Bounce ein (0 → 1,15 → 1,0, 220 ms).
private const val STAR_ENTRY_BASE_DELAY_MILLIS = 120L
private const val STAR_ENTRY_STEP_MILLIS = 150L
private const val STAR_BOUNCE_MILLIS = 220
private const val STAR_BOUNCE_PEAK = 1.15f
private const val STAR_BOUNCE_PEAK_AT_MILLIS = 150

/** Reduce-Motion (§13.12): Sterne erscheinen ohne Bounce, Fade 150 ms, GLEICHE Zeitpunkte. */
private const val STAR_RM_FADE_MILLIS = 150

// Overlay-Buttons (§13.10 Nr. 6 + abgenommene V3-Abweichung): Fade ab 500 ms,
// sichtbar UND interaktiv spätestens bei 600 ms — der 3. Stern darf noch fliegen.
private const val ACTIONS_FADE_START_MILLIS = 500
private const val ACTIONS_INTERACTIVE_AT_MILLIS = 600
private const val ACTIONS_FADE_MILLIS = ACTIONS_INTERACTIVE_AT_MILLIS - ACTIONS_FADE_START_MILLIS

/** Startzeitpunkt des [star]-ten Sterns (1-basiert) auf der Zug-Commit-Achse (§13.10 Nr. 5). */
internal fun starEntryStartMillis(
    fireworkStartMillis: Long,
    star: Int,
): Long = fireworkStartMillis + STAR_ENTRY_BASE_DELAY_MILLIS + (star - 1) * STAR_ENTRY_STEP_MILLIS

/**
 * Aktuelles Erscheinungsbild eines einfliegenden Sterns. [scale]/[alpha] sind
 * Provider, damit Konsumenten sie in der `graphicsLayer`-Lambda (Draw-/Layout-
 * Phase) lesen — ein Animations-Frame zeichnet nur neu, rekomponiert nie
 * (Handover-Auflage PW-4.6: keine Recomposition je Frame).
 */
internal class StarAppearance(
    val scale: () -> Float,
    val alpha: () -> Float,
)

/**
 * Treibt den Einflug EINES Sterns (§13.10 Nr. 5) deterministisch über die
 * Compose-Frame-Uhr (kein Handler, kein Sleep): bis [startMillis] unsichtbar,
 * dann [onShown] (SFX-Auslöser sfx_star_n, §13.11) und der Bounce
 * 0 → 1,15 → 1,0 über 220 ms. Reduce-Motion ([animationsEnabled] `false`,
 * §13.12): gleicher Zeitpunkt, statt Bounce ein 150-ms-Fade.
 *
 * R49-Abbruch: Verlässt das Overlay die Komposition („Nochmal"/„Weiter"/
 * Zurück), bricht der LaunchedEffect ab — ausstehende [onShown]-Aufrufe (und
 * damit Stern-SFX) entfallen ersatzlos.
 *
 * Einmaligkeit (BUG-PW4.9-2): [onShown] feuert höchstens EINMAL je
 * Composition (§13.11: genau eine Meldung je Stern) — der `shown`-Zustand
 * überlebt den Effekt-Restart beim Reduce-Motion-Toggle mit offenem Overlay
 * (§13.12: Audio von Reduce-Motion unberührt); nur die Kurve wechselt.
 */
@Composable
internal fun rememberStarAppearance(
    startMillis: Long,
    animationsEnabled: Boolean,
    onShown: () -> Unit,
): StarAppearance {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val currentOnShown by rememberUpdatedState(onShown)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(startMillis, animationsEnabled) {
        if (!shown) {
            awaitFrameClockMillis(startMillis)
            shown = true
            currentOnShown()
        }
        animateStarEntry(animationsEnabled, scale, alpha)
    }
    return remember(scale, alpha) { StarAppearance({ scale.value }, { alpha.value }) }
}

/** Einflug-Kurve: Bounce 0 → 1,15 → 1,0 (§13.10 Nr. 5) oder Reduce-Motion-Fade (§13.12). */
private suspend fun animateStarEntry(
    animationsEnabled: Boolean,
    scale: Animatable<Float, AnimationVector1D>,
    alpha: Animatable<Float, AnimationVector1D>,
) {
    if (animationsEnabled) {
        alpha.snapTo(1f)
        scale.animateTo(
            targetValue = 1f,
            animationSpec =
                keyframes {
                    durationMillis = STAR_BOUNCE_MILLIS
                    0f at 0
                    STAR_BOUNCE_PEAK at STAR_BOUNCE_PEAK_AT_MILLIS
                    1f at STAR_BOUNCE_MILLIS
                },
        )
    } else {
        scale.snapTo(1f)
        alpha.animateTo(1f, tween(STAR_RM_FADE_MILLIS, easing = LinearEasing))
    }
}

/**
 * Erscheinungsbild der Overlay-Aktionsknöpfe (§13.10 Nr. 6): Alpha-Fade
 * 500 → 600 ms, [interactive] exakt ab dem Fade-Ende (600-ms-Frist der
 * abgenommenen V3-Abweichung). [alpha] ist ein Provider für die
 * `graphicsLayer`-Lambda (keine Recomposition je Frame); [interactive]
 * rekomponiert genau einmal beim Freischalten.
 */
internal class OverlayActionsAppearance(
    val alpha: () -> Float,
    val interactive: Boolean,
)

/** Treibt den Button-Fade des Ergebnis-Overlays über die Frame-Uhr (§13.10 Nr. 6). */
@Composable
internal fun rememberOverlayActionsAppearance(): OverlayActionsAppearance {
    val alpha = remember { Animatable(0f) }
    var interactive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec =
                tween(
                    durationMillis = ACTIONS_FADE_MILLIS,
                    delayMillis = ACTIONS_FADE_START_MILLIS,
                    easing = LinearEasing,
                ),
        )
        interactive = true
    }
    return OverlayActionsAppearance(alpha = { alpha.value }, interactive = interactive)
}

/**
 * Wartet [millis] auf der Compose-Frame-Uhr (Animations-Loop statt `delay`):
 * unter der manuellen Testuhr wie unter `autoAdvance` deterministisch — die
 * Wartezeit zählt als laufende Animation und wird vom Test-Harness getrieben
 * (Learning PW-4.5/4.6: `delay` hinge an der echten Looper-Zeit).
 */
private suspend fun awaitFrameClockMillis(millis: Long) {
    if (millis <= 0L) return
    Animatable(0f).animateTo(1f, tween(durationMillis = millis.toInt(), easing = LinearEasing))
}
