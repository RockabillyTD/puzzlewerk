package de.puzzlewerk.game.generator

import de.puzzlewerk.core.SplitMix64Random
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.DefaultLevelValidator
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.level.LevelValidationResult
import de.puzzlewerk.game.trace.DefaultTracer
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * GOLDEN-TESTS fuer das Fallback-Level "Spiegelweg" — EXAKT nach Design
 * Paragraf 9.5 Nr. 7: Quelle Weiss (−R,0) Richtung 0, Spiegel (0,0) mit
 * Loesungsorientierung m=0, Kristall Weiss (R,0), Scramble o = nextInt(5)+1,
 * Par = o.
 *
 * WARNUNG: Diese Werte sind ueber Implementierungen hinweg bindend (R36).
 * Eine Aenderung ist ein BREAKING des Generators — das Taegliche Prisma
 * (Paragraf 10) haengt an byte-identischer Reproduktion!
 */
class SpiegelwegFallbackTest {
    /** Erwartetes Fallback-Level fuer [radius] und Scramble-Offset [offset]. */
    private fun expectedLevel(
        radius: Int,
        offset: Int,
        tier: Difficulty,
        seed: Long,
    ): LevelDefinition =
        LevelDefinition(
            board =
                Board(
                    radius = radius,
                    elements =
                        mapOf(
                            HexCoord(-radius, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                            HexCoord(0, 0) to Element.Mirror(Orientation((-offset).mod(Orientation.COUNT))),
                            HexCoord(radius, 0) to Element.Crystal(LightColor.WHITE),
                        ),
                ),
            par = offset,
            tier = tier,
            seed = seed,
        )

    @Test
    fun `Golden - Spiegelweg fuer R=2 bis R=5 mit exakten Werten`() {
        for (radius in 2..5) {
            // Skriptwert 2 => o = 2 + 1 = 3 => Startorientierung (0 - 3) mod 6 = 3
            val generated = spiegelwegFallback(radius, Difficulty.D1, seed = 99L, random = ScriptedRandom(listOf(2)))

            generated.level shouldBe expectedLevel(radius, offset = 3, tier = Difficulty.D1, seed = 99L)
            generated.level.board.elements[HexCoord(0, 0)] shouldBe Element.Mirror(Orientation(3))
            generated.solutionBoard.elements[HexCoord(0, 0)] shouldBe Element.Mirror(Orientation.ZERO)
        }
    }

    @Test
    fun `Golden - SplitMix64 Seed 0 zieht o=5 - Par 5, Startorientierung 1`() {
        // Erste Ziehung von SplitMix64Random(0): nextInt(5) = 4 => o = 5 (ADR-003-Sequenz)
        val generated = spiegelwegFallback(radius = 2, tier = Difficulty.D3, seed = 0L, random = SplitMix64Random(0L))

        generated.level shouldBe expectedLevel(radius = 2, offset = 5, tier = Difficulty.D3, seed = 0L)
    }

    @Test
    fun `Jeder Offset 1 bis 5 - Par ist exakt o, Start ungeloest, Loesung loest (I3, I4)`() {
        for (offset in 1..5) {
            val script = ScriptedRandom(listOf(offset - 1))
            val generated = spiegelwegFallback(radius = 2, tier = Difficulty.D2, seed = 7L, random = script)

            generated.level.par shouldBe offset
            DefaultTracer.trace(generated.level.board).solved.shouldBeFalse()
            DefaultTracer.trace(generated.solutionBoard).solved.shouldBeTrue()
            // Par exakt: kostengeordneter Solver UND unabhaengige Vollenumeration
            ParSolver(DefaultTracer).minimalMoves(generated.level.board) shouldBe offset
            bruteForceMinimalMoves(generated.level.board) shouldBe offset
        }
    }

    @Test
    fun `Fallback besteht den DefaultLevelValidator auf jedem Radius (I3-Schema)`() {
        for (radius in 2..5) {
            val generated = spiegelwegFallback(radius, Difficulty.D7, seed = 1L, random = ScriptedRandom(listOf(4)))

            DefaultLevelValidator.validate(generated.level).shouldBeInstanceOf<LevelValidationResult.Valid>()
        }
    }
}
