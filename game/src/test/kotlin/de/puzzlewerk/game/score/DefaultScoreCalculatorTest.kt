package de.puzzlewerk.game.score

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private const val MIN_POINTS = 1000
private const val MAX_POINTS = 1500
private const val MIN_PAR = 1
private const val MAX_PAR = 14
private const val BONUS_FLOOR_OFFSET = 10

/**
 * Normative Wertungsformel aus Design Paragraf 7.1/7.2 inklusive der
 * durchgerechneten Beispiele aus Paragraf 7.3 sowie R31, R33, R42 und
 * Invariante I5 (Paragraf 14).
 */
class DefaultScoreCalculatorTest {
    private val calculator: ScoreCalculator = DefaultScoreCalculator

    @Test
    fun `Golden Paragraf 7_3 - Drei Farben mit 2 Zuegen bei Par 2 ergibt 1500 und drei Sterne`() {
        calculator.scoreFor(moves = 2, par = 2) shouldBe Score(points = 1500, stars = 3)
    }

    @Test
    fun `Golden Paragraf 7_3 - Zweitbeispiel 7 Zuege bei Par 4 ergibt 1350 und zwei Sterne`() {
        calculator.scoreFor(moves = 7, par = 4) shouldBe Score(points = 1350, stars = 2)
    }

    @Test
    fun `R31 - 0 Zuege unter Par liefert volle Wertung 1500 und drei Sterne`() {
        calculator.scoreFor(moves = 0, par = 3) shouldBe Score(points = 1500, stars = 3)
    }

    @Test
    fun `R33 - Zugzahl weit ueber Par ergibt Sockel 1000 und einen Stern, nie negativ`() {
        calculator.scoreFor(moves = 60, par = 2) shouldBe Score(points = 1000, stars = 1)
    }

    @Test
    fun `R42 - Minimalfall 1 Zug bei Par 1 ergibt 1500 und drei Sterne`() {
        calculator.scoreFor(moves = 1, par = 1) shouldBe Score(points = 1500, stars = 3)
    }

    @Test
    fun `Sterngrenzen - Par plus 3 gibt zwei Sterne, Par plus 4 nur noch einen`() {
        calculator.scoreFor(moves = 5 + 3, par = 5).stars shouldBe 2
        calculator.scoreFor(moves = 5 + 4, par = 5).stars shouldBe 1
    }

    @Test
    fun `Bonusgrenzen - Par plus 9 gibt 1050, ab Par plus 10 bleibt der Sockel 1000`() {
        calculator.scoreFor(moves = 4 + 9, par = 4).points shouldBe 1050
        calculator.scoreFor(moves = 4 + 10, par = 4).points shouldBe 1000
        calculator.scoreFor(moves = 4 + 11, par = 4).points shouldBe 1000
    }

    @Test
    fun `I5 - Score liegt in 1000 bis 1500 und faellt monoton nicht-steigend mit Plateaus`() {
        // Erschoepfend statt zufaellig: alle gueltigen Par-Werte, Zuege bis weit
        // hinter das 1000er-Plateau (deterministisch und vollstaendig).
        for (par in MIN_PAR..MAX_PAR) {
            var previousPoints = MAX_POINTS
            for (moves in 0..par + 3 * BONUS_FLOOR_OFFSET) {
                val score = calculator.scoreFor(moves, par)

                score.points shouldBeInRange MIN_POINTS..MAX_POINTS
                score.stars shouldBeInRange 1..3
                // monoton NICHT-STEIGEND: Plateaus sind erlaubt, Anstieg nicht
                score.points shouldBeLessThanOrEqual previousPoints
                if (moves <= par) score.points shouldBe MAX_POINTS
                if (moves >= par + BONUS_FLOOR_OFFSET) score.points shouldBe MIN_POINTS
                previousPoints = score.points
            }
        }
    }

    @Test
    fun `Sterne sind monoton nicht-steigend in der Zugzahl`() {
        for (par in MIN_PAR..MAX_PAR) {
            var previousStars = 3
            for (moves in 0..par + 2 * BONUS_FLOOR_OFFSET) {
                val stars = calculator.scoreFor(moves, par).stars

                stars shouldBeLessThanOrEqual previousStars
                previousStars = stars
            }
        }
    }

    @Test
    fun `Vorbedingungen - negative Zuege oder Par ausserhalb 1 bis 14 sind Programmierfehler`() {
        shouldThrow<IllegalArgumentException> { calculator.scoreFor(moves = -1, par = 2) }
        shouldThrow<IllegalArgumentException> { calculator.scoreFor(moves = 3, par = 0) }
        shouldThrow<IllegalArgumentException> { calculator.scoreFor(moves = 3, par = MAX_PAR + 1) }
    }

    @Test
    fun `NIT-2 - Score erzwingt Sterne in 1 bis 3`() {
        shouldThrow<IllegalArgumentException> { Score(points = 1000, stars = 0) }
        shouldThrow<IllegalArgumentException> { Score(points = 1000, stars = 4) }
        Score(points = 1000, stars = 1).stars shouldBe 1
        Score(points = 1250, stars = 2).stars shouldBe 2
        Score(points = 1500, stars = 3).stars shouldBe 3
    }
}
