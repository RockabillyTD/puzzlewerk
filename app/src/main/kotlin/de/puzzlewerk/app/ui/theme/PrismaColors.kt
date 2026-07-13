package de.puzzlewerk.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Farbfehlsichtigkeits-taugliche Spielpalette aus docs/game-design.md §13.4.
 * Verlässlichkeitsanker sind die Formsymbole (§13.1), nie die Töne allein.
 */
object PrismaColors {
    val red = Color(0xFFE5484D)
    val green = Color(0xFF30A46C)
    val blue = Color(0xFF3E63DD)
    val yellow = Color(0xFFF5D90A)
    val magenta = Color(0xFFD6409F)
    val cyan = Color(0xFF00B5D8)
    val white = Color(0xFFF0F0F3)

    /** Dunkler App-Hintergrund (§13.4). */
    val background = Color(0xFF101418)
}

// Flächen- und Konturtöne über dem Hintergrund §13.4; Text bleibt
// PrismaColors.white (Kontrast > 15:1, WCAG AA).
internal val surfaceDark = Color(0xFF171C22)
internal val surfaceVariantDark = Color(0xFF222933)
internal val onSurfaceVariantDark = Color(0xFFC4CBD4)
internal val outlineDark = Color(0xFF8A929C)
