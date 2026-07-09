package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LevelGeneratorApiTest {
    @Test
    fun `LevelGenerator ist als fun interface implementierbar`() {
        // Fake, der die Signatur demonstriert — der echte Generator (PW-2.5)
        // erfuellt zusaetzlich alle MUSS-Eigenschaften aus Paragraf 9.5.
        val generator =
            LevelGenerator { seed, tier ->
                LevelDefinition(
                    board = Board(radius = Board.MIN_RADIUS, elements = emptyMap()),
                    par = LevelDefinition.MIN_PAR,
                    tier = tier,
                    seed = seed,
                )
            }

        val level = generator.generate(seed = 42L, tier = Difficulty.D3)

        level.seed shouldBe 42L
        level.tier shouldBe Difficulty.D3
        // Referenzielle Transparenz der Signatur (I2): gleiche Eingaben, gleicher Wert
        generator.generate(42L, Difficulty.D3) shouldBe level
    }
}
