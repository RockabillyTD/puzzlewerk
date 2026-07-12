package de.puzzlewerk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dunkles Farbschema aus der Spielpalette §13.4 (Palette normativ, hier
 * unverändert). Kontraste gegen WCAG AA nachgerechnet (Review PW-3.3):
 * Blau/#F0F0F3 ≈ 4,58:1, Cyan/#101418 ≈ 7,58:1, Rot/#101418 ≈ 4,73:1 —
 * AA für Normaltext erfüllt. Magenta/#101418 ≈ 4,49:1 erfüllt AA nur für
 * großen Text und UI-Komponenten (≥ 3:1): tertiary/onTertiary NICHT für
 * Normaltext einsetzen (derzeit nirgends der Fall; finale Magenta-Ton-
 * Entscheidung im A11y-Pass PW-3.7, Backlog beim Orchestrator).
 */
private val puzzlewerkDarkColorScheme =
    darkColorScheme(
        primary = PrismaColors.blue,
        onPrimary = PrismaColors.white,
        secondary = PrismaColors.cyan,
        onSecondary = PrismaColors.background,
        tertiary = PrismaColors.magenta,
        onTertiary = PrismaColors.background,
        error = PrismaColors.red,
        onError = PrismaColors.background,
        background = PrismaColors.background,
        onBackground = PrismaColors.white,
        surface = surfaceDark,
        onSurface = PrismaColors.white,
        surfaceVariant = surfaceVariantDark,
        onSurfaceVariant = onSurfaceVariantDark,
        outline = outlineDark,
    )

/**
 * App-Theme: immer dunkel (§13.4 definiert ausschließlich eine dunkle
 * Palette; ein helles Schema wäre eine game-designer-Entscheidung).
 */
@Composable
fun PuzzlewerkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = puzzlewerkDarkColorScheme,
        typography = puzzlewerkTypography,
        content = content,
    )
}
