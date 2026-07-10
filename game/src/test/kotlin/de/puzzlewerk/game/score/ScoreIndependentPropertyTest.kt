package de.puzzlewerk.game.score

import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

private const val MIN_POINTS = 1000
private const val MAX_POINTS = 1500
private const val BONUS_FLOOR_OFFSET = 10
private const val TWO_STAR_SLACK = 3

/**
 * Unabhaengiger QS-Pass PW-2.3-QS: Invariante I5 (Design Paragraf 14) als
 * Kotest-Property ueber zufaellige (Zuege, Par)-Paare mit festen Seeds —
 * ergaenzend zur erschoepfenden Schleife des Entwicklers, hier mit grossem
 * Zugbereich bis 200 000.
 *
 * Bekannte Grenze: oberhalb von ca. Par + 42 949 673 Zuegen ueberlaeuft die
 * Bonus-Arithmetik (BUG PW-2.3-QS-B1, Regressionstest in ScoreValueTableTest);
 * der Arb-Bereich hier bleibt bewusst im vertraglich intakten Bereich.
 */
class ScoreIndependentPropertyTest {
    private val calculator: ScoreCalculator = DefaultScoreCalculator
    private val arbMoves = Arb.int(0..200_000)
    private val arbPar = Arb.int(1..14)

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `I5 - Punkte in 1000 bis 1500, Punkte und Sterne monoton nicht-steigend je Zusatzzug`() {
        // Block-Body: eine @Test-Methode mit Rueckgabewert (PropertyContext aus
        // checkAll) wuerde JUnit Jupiter STILL ignorieren (Befund PW-2.2-QS).
        runBlocking {
            checkAll(PropTestConfig(seed = 0x50573233515331L), arbMoves, arbPar) { moves, par ->
                val score = calculator.scoreFor(moves, par)
                val oneMore = calculator.scoreFor(moves + 1, par)

                score.points shouldBeInRange MIN_POINTS..MAX_POINTS
                score.stars shouldBeInRange 1..3
                oneMore.points shouldBeLessThanOrEqual score.points
                oneMore.stars shouldBeLessThanOrEqual score.stars
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Paragraf 7_2 - Plateaus und Sterne folgen exakt den Design-Schwellen fuer jede Kombination`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 0x50573233515332L), arbMoves, arbPar) { moves, par ->
                val score = calculator.scoreFor(moves, par)

                if (moves <= par) score shouldBe Score(points = MAX_POINTS, stars = 3)
                if (moves >= par + BONUS_FLOOR_OFFSET) score.points shouldBe MIN_POINTS
                val expectedStars =
                    when {
                        moves <= par -> 3
                        moves <= par + TWO_STAR_SLACK -> 2
                        else -> 1
                    }
                score.stars shouldBe expectedStars
            }
        }
    }
}
