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
