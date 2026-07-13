package de.puzzlewerk.data.daily

import de.puzzlewerk.data.store.SCORE_POINTS_RANGE
import de.puzzlewerk.game.level.LevelDefinition

/**
 * Interner Zustand der Daily-Statistik: die §10.3-Felder plus die Menge der
 * GESPIELTEN Tage — ohne sie wäre `recordPlayed` nicht pro Datum idempotent
 * (R38). `playedTotal`/`solvedTotal` sind abgeleitet (Mengen-Größen), damit
 * das Persistenzformat keine redundanten, korrumpierbaren Zähler trägt.
 */
internal data class DailyStatsState(
    val playedEpochDays: Set<Long>,
    val currentStreak: Int,
    val longestStreak: Int,
    val resultByEpochDay: Map<Long, DailyRecord>,
) {
    fun toStats(): DailyStats =
        DailyStats(
            playedTotal = playedEpochDays.size,
            solvedTotal = resultByEpochDay.size,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            resultByEpochDay = resultByEpochDay,
        )

    companion object {
        val EMPTY: DailyStatsState =
            DailyStatsState(
                playedEpochDays = emptySet(),
                currentStreak = 0,
                longestStreak = 0,
                resultByEpochDay = emptyMap(),
            )
    }
}

/** Erster Start des Tagesrätsels [epochDay]; idempotent pro Datum (R38). */
internal fun DailyStatsState.withPlayed(epochDay: Long): DailyStatsState =
    if (epochDay in playedEpochDays) {
        this
    } else {
        copy(playedEpochDays = playedEpochDays + epochDay)
    }

/**
 * Erstlösung des Tagesrätsels [epochDay] gemäß §10.3: Folgetag ⇒ Serie +1,
 * Lücke ⇒ Serie = 1, früherer Tag (R38, Datum rückwärts) ⇒ Serie unverändert,
 * Ergebnis zählt trotzdem. Existiert bereits ein Ergebnis für [epochDay],
 * ist der Aufruf ein No-op (R38: nur die Erstlösung zählt).
 */
internal fun DailyStatsState.withSolved(
    epochDay: Long,
    record: DailyRecord,
): DailyStatsState {
    if (epochDay in resultByEpochDay) return this
    val streak = streakAfterSolving(epochDay)
    return copy(
        currentStreak = streak,
        // §10.3: laengsteSerie ist ein positiver Rekord und fällt nie.
        longestStreak = maxOf(longestStreak, streak),
        resultByEpochDay = resultByEpochDay + (epochDay to record),
    )
}

private fun DailyStatsState.streakAfterSolving(epochDay: Long): Int {
    val lastSolved = resultByEpochDay.keys.maxOrNull() ?: return 1
    return when {
        // Sättigung statt Lade-Ablehnung (Review PW-3.2 #20-MINOR-1b): ein Bestand mit
        // Int.MAX_VALUE Serientagen wäre valide DATEN — nur das Inkrement hier könnte
        // überlaufen, also wird genau hier gesättigt statt beim Laden abgewiesen.
        epochDay == lastSolved + 1 -> if (currentStreak == Int.MAX_VALUE) currentStreak else currentStreak + 1
        epochDay > lastSolved -> 1
        else -> currentStreak
    }
}

/**
 * Vorbedingungen von `recordSolved` (Regel C3, Programmierfehler): Werte im
 * §7.1/§7.2-Bereich, damit nie ein Bestand entsteht, den der Lademapper als
 * Korruption abweisen müsste. `moves == 0` (R31) ist ein Datenfehler-Fall,
 * der nie persistiert wird.
 */
internal fun requireValidDailyRecord(record: DailyRecord) {
    require(record.moves >= 1) { "Züge müssen >= 1 sein, waren ${record.moves}" }
    require(record.par in LevelDefinition.MIN_PAR..LevelDefinition.MAX_PAR) {
        "Par muss in ${LevelDefinition.MIN_PAR}..${LevelDefinition.MAX_PAR} liegen (§7.1), war ${record.par}"
    }
    require(record.score.points in SCORE_POINTS_RANGE) {
        "Punkte müssen in $SCORE_POINTS_RANGE liegen (§7.2), waren ${record.score.points}"
    }
}
