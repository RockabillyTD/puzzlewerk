package de.puzzlewerk.game.level

// §11.2: Der 3-Level-Puffer verhindert harte Blockaden, ohne die Progression zu entwerten.
private const val UNLOCK_BUFFER: Int = 3

/**
 * Freischaltregel §11.2: Level [levelNumber] ist spielbar gdw.
 * `levelNumber ≤ highestSolvedLevel + 3`.
 *
 * Pure Funktion ohne Vorbedingungen: [levelNumber] außerhalb
 * `1..`[CAMPAIGN_LEVEL_COUNT] liefert immer `false`.
 * [highestSolvedLevel] `0` bedeutet „kein Fortschritt" (anfangs sind
 * genau Level 1–3 offen); Sterne/Score schalten nichts frei (§7.2).
 */
public fun isLevelUnlocked(
    levelNumber: Int,
    highestSolvedLevel: Int,
): Boolean =
    levelNumber in 1..CAMPAIGN_LEVEL_COUNT &&
        // Äquivalent zu levelNumber <= highestSolvedLevel + UNLOCK_BUFFER,
        // aber ohne Int-Überlauf bei extremem highestSolvedLevel.
        levelNumber - UNLOCK_BUFFER <= highestSolvedLevel
