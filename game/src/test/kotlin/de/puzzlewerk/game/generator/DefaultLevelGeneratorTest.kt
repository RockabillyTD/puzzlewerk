package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.trace.DefaultTracer
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * DefaultLevelGenerator (Paragraf 9.3): Determinismus (I2/R34), Loesbarkeit der
 * Konstruktions-Loesung (I3), exakter Par (I4) und die Relaxierungsleiter bis
 * zum Spiegelweg-Fallback (R36).
 */
class DefaultLevelGeneratorTest {
    @Test
    fun `I2 R34 - gleicher Seed und Tier liefern ueber mehrere Aufrufe identische Level`() {
        for (tier in Difficulty.entries) {
            val seed = 4711L + tier.ordinal
            val first = DefaultLevelGenerator.generate(seed, tier)
            repeat(3) {
                DefaultLevelGenerator.generate(seed, tier) shouldBe first
            }
        }
    }

    @Test
    fun `Verschiedene Seeds liefern verschiedene Level (Stichprobe)`() {
        val a = DefaultLevelGenerator.generate(1L, Difficulty.D1)
        val b = DefaultLevelGenerator.generate(2L, Difficulty.D1)
        (a == b).shouldBeFalse()
    }

    @Test
    fun `I3 - die unverwuerfelte Konstruktions-Loesung loest jedes Level via DefaultTracer`() {
        for (tier in Difficulty.entries) {
            for (seed in listOf(11L, 222L, 3333L)) {
                assertSolutionSolves(GeneratorRun(seed, tier).execute())
            }
        }
    }

    private fun assertSolutionSolves(generated: GeneratedLevel) {
        val solutionTrace = DefaultTracer.trace(generated.solutionBoard)
        solutionTrace.solved.shouldBeTrue()
        // Kein Kristall uebersaettigt oder dunkel (9.5/3): solved erzwingt
        // empfangen == soll fuer JEDEN Kristall — explizit gegengeprueft:
        generated.solutionBoard.elements.forEach { (cell, element) ->
            if (element is Element.Crystal) {
                solutionTrace.received[cell] shouldBe element.required
            }
        }
        // Loesungsbrett == Startbrett bis auf Orientierungen der Drehbaren
        rotatableCells(generated.solutionBoard) shouldBe rotatableCells(generated.level.board)
    }

    @Test
    fun `I4 - Par ist exakt minimal - unabhaengige Vollenumeration auf kleinen Brettern (D1, D2)`() {
        for (tier in listOf(Difficulty.D1, Difficulty.D2)) {
            for (seed in listOf(5L, 66L, 777L, 8888L)) {
                val level = DefaultLevelGenerator.generate(seed, tier)

                bruteForceMinimalMoves(level.board) shouldBe level.par
            }
        }
    }

    @Test
    fun `R36 - erschoepfte Relaxierungsleiter endet deterministisch im Spiegelweg-Fallback`() {
        // attemptsPerStage = 0 erschoepft jede Stufe sofort => letzte Stufe Fallback;
        // ohne vorherige Ziehungen ist o die ERSTE nextInt(5)-Ziehung des Seeds
        val generated = GeneratorRun(seed = 0L, tier = Difficulty.D7, attemptsPerStage = 0).execute()

        val radius = tierParameters(Difficulty.D7).radiusRange.first
        generated.level.board.elements.keys shouldBe
            setOf(HexCoord(-radius, 0), HexCoord(0, 0), HexCoord(radius, 0))
        generated.level.par shouldBeInRange 1..5
        generated.level.par shouldBe 5 // SplitMix64(0): nextInt(5) = 4 => o = 5
        DefaultTracer.trace(generated.level.board).solved.shouldBeFalse()
        DefaultTracer.trace(generated.solutionBoard).solved.shouldBeTrue()
    }

    @Test
    fun `Relaxierungsleiter - Stufe 0 Basis, Stufe 1 Par plusminus 2, danach Budget minus 1 bis 1`() {
        val base = tierParameters(Difficulty.D7)
        val ladder = relaxationLadder(base)

        ladder.first() shouldBe base
        ladder[1].parRange shouldBe 6..14 // 8-2 .. 14+2 geklemmt auf MAX_PAR
        ladder[1].rotatableRange shouldBe base.rotatableRange
        ladder[2].rotatableRange shouldBe 6..7
        ladder[2].palette.prismAllowed.shouldBeFalse()
        ladder[2].palette.maxPortalPairs shouldBe 0
        ladder.last().rotatableRange shouldBe 1..1
        // Jede Budgetstufe senkt um genau 1
        ladder.drop(2).forEachIndexed { index, stage ->
            stage.rotatableRange.last shouldBe base.rotatableRange.last - 1 - index
        }
    }

    @Test
    fun `Relaxierungsleiter klemmt den Par-Bereich unten auf MIN_PAR`() {
        val ladder = relaxationLadder(tierParameters(Difficulty.D1))

        ladder[1].parRange shouldBe LevelDefinition.MIN_PAR..5 // 1-2 geklemmt, 3+2
    }

    @Test
    fun `R35 - kein generiertes Level ist im Startzustand geloest`() {
        for (tier in Difficulty.entries) {
            for (seed in listOf(-1L, 0L, 1L, Long.MAX_VALUE)) {
                val level = DefaultLevelGenerator.generate(seed, tier)

                DefaultTracer.trace(level.board).solved.shouldBeFalse()
                level.par shouldBeInRange LevelDefinition.MIN_PAR..LevelDefinition.MAX_PAR
            }
        }
    }

    @Test
    fun `Extremseeds terminieren fuer jedes Tier ohne Ausnahme (R36-Denkprobe B1)`() {
        for (tier in Difficulty.entries) {
            for (seed in listOf(Long.MIN_VALUE, -1L, 0L, Long.MAX_VALUE)) {
                val level = DefaultLevelGenerator.generate(seed, tier)

                level.seed shouldBe seed
                level.tier shouldBe tier
                level.board.radius shouldBeIn listOf(2, 3, 4, 5)
            }
        }
    }
}
