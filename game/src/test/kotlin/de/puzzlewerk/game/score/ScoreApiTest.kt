package de.puzzlewerk.game.score

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

class ScoreApiTest {
    @Test
    fun `Score ist ein Wert mit struktureller Gleichheit`() {
        Score(points = 1500, stars = 3) shouldBe Score(1500, 3)
        Score(points = 1350, stars = 2) shouldNotBe Score(1350, 3)
        Score(points = 1000, stars = 1).copy(stars = 2) shouldBe Score(1000, 2)
    }

    @Test
    fun `ScoreCalculator ist als fun interface implementierbar`() {
        // Fake mit fester Wertung — die normative Formel implementiert PW-2.4.
        val calculator = ScoreCalculator { _, _ -> Score(points = 1500, stars = 3) }

        calculator.scoreFor(moves = 2, par = 2) shouldBe Score(1500, 3)
    }
}
