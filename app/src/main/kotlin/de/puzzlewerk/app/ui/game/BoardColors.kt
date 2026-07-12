package de.puzzlewerk.app.ui.game

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import de.puzzlewerk.game.color.LightColor

/**
 * Licht-Palette §13.4 in Bitmasken-Reihenfolge (Index = `bits − 1`):
 * Rot, Grün, Gelb, Blau, Magenta, Cyan, Weiß. Farbfehlsichtigkeits-taugliche
 * Töne; Verlässlichkeitsanker bleiben die Symbole (§13.1), nicht die Töne.
 */
private val DefaultBeamColors: List<Color> =
    listOf(
        // Rot (1)
        Color(0xFFE5484D),
        // Grün (2)
        Color(0xFF30A46C),
        // Gelb (3)
        Color(0xFFF5D90A),
        // Blau (4)
        Color(0xFF3E63DD),
        // Magenta (5)
        Color(0xFFD6409F),
        // Cyan (6)
        Color(0xFF00B5D8),
        // Weiß (7)
        Color(0xFFF0F0F3),
    )

// Neutral-Töne des Spielfelds (dunkles Grundthema §13.4).
private val DefaultBackground = Color(0xFF101418)
private val DefaultOutline = Color(0xFF2A3138)
private val DefaultElement = Color(0xFFE7EBEE)
private val DefaultSocket = Color(0xFFA8B4C0)
private val DefaultMuted = Color(0xFF39424C)

/**
 * Farbsatz des Spielfelds mit den §13.4-Defaults.
 *
 * BEWUSST lokal in `ui/game/` definiert: PW-3.3 baut parallel das Theme;
 * die Verdrahtung von [BoardColors] mit dem echten Theme passiert in PW-3.5
 * (Orchestrator-Entscheidung zur Datei-Disjunktheit der Tickets).
 *
 * @property background Dunkler Spielfeld-Hintergrund (§13.4: #101418).
 * @property outline Zellkontur des Hex-Rasters.
 * @property element Neutral-helle Elementzeichnung (Spiegel, Prisma, Konturen) —
 *   heller Ton für WCAG-AA-Kontrast auf [background].
 * @property socket Heller Sockelring: Markierung DREHBARER Elemente (§12.3).
 * @property muted Eingelassene Flächen (Wand, Quellgehäuse, dunkler Kristall) —
 *   nicht drehbare Elemente wirken „eingelassen" (§12.3).
 * @property beamColors Lichtfarben nach Bitmaske, Index `bits − 1` (§13.4).
 */
@Immutable
data class BoardColors(
    val background: Color = DefaultBackground,
    val outline: Color = DefaultOutline,
    val element: Color = DefaultElement,
    val socket: Color = DefaultSocket,
    val muted: Color = DefaultMuted,
    val beamColors: List<Color> = DefaultBeamColors,
) {
    init {
        require(beamColors.size == LightColor.WHITE.bits) {
            "beamColors braucht genau ${LightColor.WHITE.bits} Einträge (Bitmasken 1..7, §3.1)"
        }
    }

    /** Darstellungsfarbe der Lichtfarbe [color] (§13.4). */
    fun beam(color: LightColor): Color = beamColors[color.bits - 1]
}
