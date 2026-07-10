package de.puzzlewerk.game.score

import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unabhaengiger QS-Pass PW-2.3-QS: die Punkteformel und Sterngrenzen aus
 * Design Paragraf 7.2 als HANDNOTIERTE Werte-Tabellen (Literale statt
 * Formel-Nachbau) fuer mehrere Par-Werte, jeweils Par−1 bis Par+11 —
 * plus die exakten Grenzen 1500/1000 und die Sterne-Schwellen
 * kleiner-gleich Par / Par+1 / Par+3 / Par+4 fuer ALLE gueltigen Par-Werte.
 */
class ScoreValueTableTest {
    private val calculator: ScoreCalculator = DefaultScoreCalculator

    private fun expect(
        moves: Int,
        par: Int,
        points: Int,
        stars: Int,
    ) {
        calculator.scoreFor(moves = moves, par = par) shouldBe Score(points = points, stars = stars)
    }

    @Test
    fun `Werte-Tabelle Par 1 - handnotiert von 0 Zuegen bis Par plus 11`() {
        expect(moves = 0, par = 1, points = 1500, stars = 3)
        expect(moves = 1, par = 1, points = 1500, stars = 3)
        expect(moves = 2, par = 1, points = 1450, stars = 2)
        expect(moves = 3, par = 1, points = 1400, stars = 2)
        expect(moves = 4, par = 1, points = 1350, stars = 2)
        expect(moves = 5, par = 1, points = 1300, stars = 1)
        expect(moves = 6, par = 1, points = 1250, stars = 1)
        expect(moves = 7, par = 1, points = 1200, stars = 1)
        expect(moves = 8, par = 1, points = 1150, stars = 1)
        expect(moves = 9, par = 1, points = 1100, stars = 1)
        expect(moves = 10, par = 1, points = 1050, stars = 1)
        expect(moves = 11, par = 1, points = 1000, stars = 1)
        expect(moves = 12, par = 1, points = 1000, stars = 1)
    }

    @Test
    fun `Werte-Tabelle Par 2 - handnotiert von Par minus 1 bis Par plus 11`() {
        expect(moves = 1, par = 2, points = 1500, stars = 3)
        expect(moves = 2, par = 2, points = 1500, stars = 3)
        expect(moves = 3, par = 2, points = 1450, stars = 2)
        expect(moves = 4, par = 2, points = 1400, stars = 2)
        expect(moves = 5, par = 2, points = 1350, stars = 2)
        expect(moves = 6, par = 2, points = 1300, stars = 1)
        expect(moves = 7, par = 2, points = 1250, stars = 1)
        expect(moves = 8, par = 2, points = 1200, stars = 1)
        expect(moves = 9, par = 2, points = 1150, stars = 1)
        expect(moves = 10, par = 2, points = 1100, stars = 1)
        expect(moves = 11, par = 2, points = 1050, stars = 1)
        expect(moves = 12, par = 2, points = 1000, stars = 1)
        expect(moves = 13, par = 2, points = 1000, stars = 1)
    }

    @Test
    fun `Werte-Tabelle Par 5 - handnotiert von Par minus 1 bis Par plus 11`() {
        expect(moves = 4, par = 5, points = 1500, stars = 3)
        expect(moves = 5, par = 5, points = 1500, stars = 3)
        expect(moves = 6, par = 5, points = 1450, stars = 2)
        expect(moves = 7, par = 5, points = 1400, stars = 2)
        expect(moves = 8, par = 5, points = 1350, stars = 2)
        expect(moves = 9, par = 5, points = 1300, stars = 1)
        expect(moves = 10, par = 5, points = 1250, stars = 1)
        expect(moves = 11, par = 5, points = 1200, stars = 1)
        expect(moves = 12, par = 5, points = 1150, stars = 1)
        expect(moves = 13, par = 5, points = 1100, stars = 1)
        expect(moves = 14, par = 5, points = 1050, stars = 1)
        expect(moves = 15, par = 5, points = 1000, stars = 1)
        expect(moves = 16, par = 5, points = 1000, stars = 1)
    }

    @Test
    fun `Werte-Tabelle Par 14 - Obergrenze D7, handnotiert von Par minus 1 bis Par plus 11`() {
        expect(moves = 13, par = 14, points = 1500, stars = 3)
        expect(moves = 14, par = 14, points = 1500, stars = 3)
        expect(moves = 15, par = 14, points = 1450, stars = 2)
        expect(moves = 16, par = 14, points = 1400, stars = 2)
        expect(moves = 17, par = 14, points = 1350, stars = 2)
        expect(moves = 18, par = 14, points = 1300, stars = 1)
        expect(moves = 19, par = 14, points = 1250, stars = 1)
        expect(moves = 20, par = 14, points = 1200, stars = 1)
        expect(moves = 21, par = 14, points = 1150, stars = 1)
        expect(moves = 22, par = 14, points = 1100, stars = 1)
        expect(moves = 23, par = 14, points = 1050, stars = 1)
        expect(moves = 24, par = 14, points = 1000, stars = 1)
        expect(moves = 25, par = 14, points = 1000, stars = 1)
    }

    @Test
    fun `Punktegrenzen exakt - 1500 bis einschliesslich Par, 1050 bei Par plus 9, 1000 ab Par plus 10`() {
        for (par in 1..14) {
            calculator.scoreFor(moves = par, par = par).points shouldBe 1500
            calculator.scoreFor(moves = par + 1, par = par).points shouldBe 1450
            calculator.scoreFor(moves = par + 9, par = par).points shouldBe 1050
            calculator.scoreFor(moves = par + 10, par = par).points shouldBe 1000
            calculator.scoreFor(moves = par + 11, par = par).points shouldBe 1000
        }
    }

    @Test
    fun `Sterne-Schwellen exakt - drei bis Par, zwei bei Par plus 1 und Par plus 3, eins ab Par plus 4`() {
        for (par in 1..14) {
            calculator.scoreFor(moves = par, par = par).stars shouldBe 3
            calculator.scoreFor(moves = par + 1, par = par).stars shouldBe 2
            calculator.scoreFor(moves = par + 3, par = par).stars shouldBe 2
            calculator.scoreFor(moves = par + 4, par = par).stars shouldBe 1
        }
    }

    @Test
    fun `I5-Regression - Score bleibt auch bei extremer Zugzahl in 1000 bis 1500`() {
        val score = calculator.scoreFor(moves = Int.MAX_VALUE, par = 14)

        score.points shouldBeInRange 1000..1500 // vor dem B1-Fix: 2250 (Int-Ueberlauf im Bonus)
    }
}
