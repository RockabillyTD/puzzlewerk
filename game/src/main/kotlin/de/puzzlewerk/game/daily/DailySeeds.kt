package de.puzzlewerk.game.daily

import de.puzzlewerk.core.mix64
import de.puzzlewerk.game.level.Difficulty

// ASCII "PRISMA" — trennt den Daily-Seed-Raum vom Kampagnen-Seed-Raum (Design §10.1).
private const val PRISMA_SALT: Long = 0x505249534D41L

private const val DAYS_PER_WEEK: Long = 7L

// 1970-01-01 (epochDay 0) war ein Donnerstag = ISO-Tag 4; der Offset verschiebt
// die Woche so, dass floorMod direkt den 0-basierten ISO-Index liefert.
private const val EPOCH_THURSDAY_OFFSET: Long = 3L

/**
 * Seed des Täglichen Prismas für den Kalendertag [epochDay] (Design §10.1):
 * `mix64(epochDay xor "PRISMA")`.
 *
 * Designentscheidung (PW-2.1): Die API nimmt den `epochDay` als `Long`
 * (Tage seit 1970-01-01 im LOKALEN Gerätekalender, Wordle-Modell §10.1) statt
 * eines `java.time.LocalDate` — :game bleibt damit frei von JDK-Datums-Semantik
 * und Desugaring-Fragen; :app wandelt `LocalDate.toEpochDay()` über die
 * injizierte `WallClock` (Regel C2). Negative Werte (Datum vor 1970) sind
 * gültig und liefern definierte Seeds (R37).
 */
public fun dailySeed(epochDay: Long): Long = mix64(epochDay xor PRISMA_SALT)

/**
 * ISO-Wochentag (Mo=1 … So=7) des Tages [epochDay] — reine Ganzzahlarithmetik
 * ohne Kalender-API, für negative Werte definiert (floorMod, R37).
 */
public fun isoDayOfWeek(epochDay: Long): Int = (epochDay + EPOCH_THURSDAY_OFFSET).mod(DAYS_PER_WEEK).toInt() + 1

/**
 * Schwierigkeitsstufe des Täglichen Prismas für [epochDay]: Mo=D1 … So=D7
 * (Design §10.2). Zusammen mit [dailySeed] gilt:
 * `level = generateLevel(dailySeed(epochDay), dailyTier(epochDay))` (§10.1).
 */
public fun dailyTier(epochDay: Long): Difficulty = Difficulty.forIsoDayOfWeek(isoDayOfWeek(epochDay))
