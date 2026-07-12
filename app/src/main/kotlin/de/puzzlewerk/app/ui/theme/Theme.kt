package de.puzzlewerk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Dunkles Farbschema aus der Spielpalette §13.4. on-Farben sind gegen
 * WCAG AA geprüft: Blau/#F0F0F3 ≈ 5,2:1, Cyan/#101418 ≈ 7,4:1,
 * Magenta/#101418 ≈ 4,6:1, Rot/#101418 ≈ 4,9:1.
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
