package de.puzzlewerk.data.daily

import de.puzzlewerk.game.score.Score

/**
 * §7.2-konsistente Test-Fixture: Punkte (`1000 + max(0, 500 − 50 · max(0,
 * Züge − Par))`) und Sterne (★★★ ⇔ Züge ≤ Par, ★★ ⇔ ≤ Par + 3, sonst ★)
 * werden aus [moves]/[par] abgeleitet — unmögliche Kombinationen sind damit
 * ausgeschlossen (Review PW-3.2).
 */
internal fun consistentDailyRecord(
    moves: Int,
    par: Int,
): DailyRecord {
    val points = 1000 + maxOf(0, 500 - 50 * maxOf(0, moves - par))
    val stars =
        when {
            moves <= par -> 3
            moves <= par + 3 -> 2
            else -> 1
        }
    return DailyRecord(moves = moves, par = par, score = Score(points = points, stars = stars))
}
