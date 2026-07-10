package de.puzzlewerk.game.score

import de.puzzlewerk.game.level.LevelDefinition

private const val BASE_POINTS = 1000
private const val MAX_BONUS = 500
private const val PENALTY_PER_EXTRA_MOVE = 50

/** Ab `Par + 10` liegt der Bonus auf dem 0er-Plateau (§7.2) — mehr Mehrzüge ändern nichts. */
private const val MAX_PENALIZED_EXTRA_MOVES = MAX_BONUS / PENALTY_PER_EXTRA_MOVE
private const val THREE_STARS = 3
private const val TWO_STARS = 2
private const val ONE_STAR = 1
private const val TWO_STAR_SLACK = 3

/**
 * Normative Wertungsformel aus Design §7.1/§7.2:
 * `Score = 1000 + max(0, 500 − 50 · max(0, Züge − Par))`; Sterne ★★★ bei
 * `Züge ≤ Par`, ★★ bei `Züge ≤ Par + 3`, sonst ★.
 *
 * `Züge < Par` ist im Spiel unmöglich (Par ist minimal, §7.1), liefert aber
 * die volle Wertung — deckt R31 (bereits gelöst geladen ⇒ 0 Züge) ab.
 * Pure Funktion ohne Zeitfaktor (§7.2); Invariante I5: Punkte ∈ [1000, 1500],
 * monoton nicht-steigend in der Zugzahl.
 */
public object DefaultScoreCalculator : ScoreCalculator {
    override fun scoreFor(
        moves: Int,
        par: Int,
    ): Score {
        // Vorbedingungen aus dem KDoc-Vertrag: Verletzung = Programmierfehler (C3)
        require(moves >= 0) { "Zuege duerfen nicht negativ sein, waren $moves" }
        require(par in LevelDefinition.MIN_PAR..LevelDefinition.MAX_PAR) {
            "Par muss in ${LevelDefinition.MIN_PAR}..${LevelDefinition.MAX_PAR} liegen, war $par"
        }
        // BUG-Fix PW-2.3-QS-B1: Kappung VOR der Multiplikation macht
        // `50 · extraMoves` überlaufsicher und ist exakt, weil der Abzug ab
        // Par+10 ohnehin am Plateau endet (I5). `moves − par` selbst kann
        // nicht überlaufen (moves ≥ 0, par ∈ 1..14).
        val extraMoves = (moves - par).coerceIn(0, MAX_PENALIZED_EXTRA_MOVES)
        val bonus = MAX_BONUS - PENALTY_PER_EXTRA_MOVE * extraMoves
        val stars =
            when {
                moves <= par -> THREE_STARS
                moves <= par + TWO_STAR_SLACK -> TWO_STARS
                else -> ONE_STAR
            }
        return Score(points = BASE_POINTS + bonus, stars = stars)
    }
}
