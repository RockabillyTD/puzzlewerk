package de.puzzlewerk.game.generator

import de.puzzlewerk.game.level.Difficulty

/**
 * Elementpalette eines Tiers (Design §9.2, Spalten „erlaubte Elemente" und
 * „Farben im Level"): welche optionalen Elementtypen der Generator verwenden
 * darf und wie viele verschiedene Farben das Level hoechstens traegt.
 */
internal data class ElementPalette(
    val splitterAllowed: Boolean,
    val prismAllowed: Boolean,
    val filterAllowed: Boolean,
    val maxPortalPairs: Int,
    val maxColors: Int,
)

/**
 * Generator-Budgets eines Tiers — exakt die Tabelle aus Design §9.2.
 * Die harten Kappen (Drehbare ≤ 8, Portalpaare ≤ 2, Quellen ≤ 3, Kristalle ≤ 6,
 * Belegung ≤ 50 %) gelten zusaetzlich immer und prueft die Konstruktion selbst.
 */
internal data class TierParameters(
    val radiusRange: IntRange,
    val sourceRange: IntRange,
    val crystalRange: IntRange,
    val rotatableRange: IntRange,
    val parRange: IntRange,
    val wallRange: IntRange,
    val palette: ElementPalette,
)

private val D1 =
    TierParameters(
        radiusRange = 2..2,
        sourceRange = 1..1,
        crystalRange = 1..2,
        rotatableRange = 1..2,
        parRange = 1..3,
        wallRange = 0..2,
        palette = ElementPalette(false, false, false, maxPortalPairs = 0, maxColors = 1),
    )
private val D2 =
    TierParameters(
        radiusRange = 2..2,
        sourceRange = 1..1,
        crystalRange = 2..2,
        rotatableRange = 2..3,
        parRange = 2..4,
        wallRange = 0..3,
        palette = ElementPalette(true, false, false, maxPortalPairs = 0, maxColors = 2),
    )
private val D3 =
    TierParameters(
        radiusRange = 3..3,
        sourceRange = 1..2,
        crystalRange = 2..3,
        rotatableRange = 3..4,
        parRange = 3..6,
        wallRange = 1..4,
        palette = ElementPalette(true, true, false, maxPortalPairs = 0, maxColors = 3),
    )
private val D4 =
    TierParameters(
        radiusRange = 3..3,
        sourceRange = 1..2,
        crystalRange = 3..3,
        rotatableRange = 4..5,
        parRange = 4..7,
        wallRange = 2..5,
        palette = ElementPalette(true, true, true, maxPortalPairs = 0, maxColors = 4),
    )
private val D5 =
    TierParameters(
        radiusRange = 3..4,
        sourceRange = 2..2,
        crystalRange = 3..4,
        rotatableRange = 5..6,
        parRange = 5..9,
        wallRange = 2..6,
        palette = ElementPalette(true, true, true, maxPortalPairs = 1, maxColors = 5),
    )
private val D6 =
    TierParameters(
        radiusRange = 4..4,
        sourceRange = 2..3,
        crystalRange = 4..5,
        rotatableRange = 6..7,
        parRange = 6..11,
        wallRange = 3..8,
        palette = ElementPalette(true, true, true, maxPortalPairs = 2, maxColors = 6),
    )
private val D7 =
    TierParameters(
        radiusRange = 4..4,
        sourceRange = 2..3,
        crystalRange = 5..6,
        rotatableRange = 7..8,
        parRange = 8..14,
        wallRange = 3..8,
        palette = ElementPalette(true, true, true, maxPortalPairs = 2, maxColors = 7),
    )

/** Tier-Parametertabelle (Design §9.2) — normativ, jede Aenderung bricht I2. */
internal fun tierParameters(tier: Difficulty): TierParameters =
    when (tier) {
        Difficulty.D1 -> D1
        Difficulty.D2 -> D2
        Difficulty.D3 -> D3
        Difficulty.D4 -> D4
        Difficulty.D5 -> D5
        Difficulty.D6 -> D6
        Difficulty.D7 -> D7
    }
