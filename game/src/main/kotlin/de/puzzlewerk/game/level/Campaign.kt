package de.puzzlewerk.game.level

import de.puzzlewerk.core.mix64

/** Anzahl der Kampagnenlevel (Design §11). */
public const val CAMPAIGN_LEVEL_COUNT: Int = 50

// ASCII "LEVEL" — trennt den Kampagnen-Seed-Raum vom Daily-Seed-Raum (Design §11.1).
private const val LEVEL_SALT: Long = 0x4C4556454CL

/**
 * Default-Seed für Kampagnenlevel [levelNumber] ∈ 1..[CAMPAIGN_LEVEL_COUNT]:
 * `mix64(n xor "LEVEL")` (Design §11.1). Maßgeblich sind die eingecheckten
 * Leveldaten; der Seed dient der Reproduktion und Kuratierung
 * („kuratiert = generiert + ausgewählt").
 *
 * Werte außerhalb 1..50 sind ein Programmierfehler (Regel C3).
 */
public fun campaignSeed(levelNumber: Int): Long {
    require(levelNumber in 1..CAMPAIGN_LEVEL_COUNT) {
        "Kampagnenlevel muss in 1..$CAMPAIGN_LEVEL_COUNT liegen, war $levelNumber"
    }
    return mix64(levelNumber.toLong() xor LEVEL_SALT)
}

// Obergrenzen der Levelbereiche je Tier (Design §11.3, fixiert in PW-3.8).
// D7 (47..50) ist der else-Zweig und braucht keine eigene Grenze.
private const val D1_MAX_LEVEL: Int = 6
private const val D2_MAX_LEVEL: Int = 12
private const val D3_MAX_LEVEL: Int = 21
private const val D4_MAX_LEVEL: Int = 29
private const val D5_MAX_LEVEL: Int = 39
private const val D6_MAX_LEVEL: Int = 46

/**
 * Tier des Kampagnenlevels [levelNumber] ∈ 1..[CAMPAIGN_LEVEL_COUNT] gemäß der
 * normativen Level→Tier-Zuordnung (Design §11.3, fixiert in PW-3.8):
 *
 * | n | 1–6 | 7–12 | 13–21 | 22–29 | 30–39 | 40–46 | 47–50 |
 * |---|-----|------|-------|-------|-------|-------|-------|
 * | Tier | D1 | D2 | D3 | D4 | D5 | D6 | D7 |
 *
 * Pure, totale Funktion auf 1..50, monoton nicht-fallend; benachbarte Level
 * unterscheiden sich um höchstens eine Tier-Stufe. Werte außerhalb 1..50 sind
 * ein Programmierfehler (Regel C3).
 */
public fun campaignTier(levelNumber: Int): Difficulty {
    require(levelNumber in 1..CAMPAIGN_LEVEL_COUNT) {
        "Kampagnenlevel muss in 1..$CAMPAIGN_LEVEL_COUNT liegen, war $levelNumber"
    }
    // Erste zutreffende Zeile gewinnt (§11.3, äquivalente Regel).
    return when {
        levelNumber <= D1_MAX_LEVEL -> Difficulty.D1
        levelNumber <= D2_MAX_LEVEL -> Difficulty.D2
        levelNumber <= D3_MAX_LEVEL -> Difficulty.D3
        levelNumber <= D4_MAX_LEVEL -> Difficulty.D4
        levelNumber <= D5_MAX_LEVEL -> Difficulty.D5
        levelNumber <= D6_MAX_LEVEL -> Difficulty.D6
        else -> Difficulty.D7 // 47..50
    }
}
