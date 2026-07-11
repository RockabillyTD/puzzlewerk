package de.puzzlewerk.game.generator

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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * Golden-Haerte (PW-2.5-QS): Die zwei gepinnten Generator-Goldens werden durch
 * UNABHAENGIGE Neuberechnung (frischer Generator-Aufruf) verifiziert — und zwar
 * FELD-FUER-FELD und als Typ-Multimenge statt ueber eine einzige
 * data-class-Gleichheit: aendert sich auch nur EINE Konstante (Koordinate,
 * Orientierung, Farbe, Par), schlaegt hier eine praezise benannte Zusicherung
 * fehl. Zusaetzlich wird der gepinnte Par gegen das Zugfolgen-BFS-Orakel
 * gegengerechnet (I4) und R35/Schema geprueft.
 *
 * WARNUNG: Wie DefaultLevelGeneratorGoldenTest — eine noetige Anpassung dieser
 * Werte ist ein BREAKING des Generators (I2/R34) und braucht ein ADR.
 */
class GeneratorGoldenIndependentQsTest {
    private val golden1 = DefaultLevelGenerator.generate(seed = 1L, tier = Difficulty.D1)
    private val golden2 = DefaultLevelGenerator.generate(seed = 42L, tier = Difficulty.D3)

    @Test
    fun `Golden 1 - Kopffelder und Elementbestand feld-fuer-feld`() {
        golden1.board.radius shouldBe 2
        golden1.par shouldBe 2
        golden1.tier shouldBe Difficulty.D1
        golden1.seed shouldBe 1L
        golden1.board.elements.size shouldBe 5
        golden1.board.elements[HexCoord(-1, -1)] shouldBe Element.Crystal(LightColor.WHITE)
        golden1.board.elements[HexCoord(-1, 1)] shouldBe Element.Mirror(Orientation(4))
        golden1.board.elements[HexCoord(0, 1)] shouldBe Element.Wall
        golden1.board.elements[HexCoord(0, 2)] shouldBe Element.Wall
        golden1.board.elements[HexCoord(2, -2)] shouldBe
            Element.Source(LightColor.WHITE, Direction.SOUTH_WEST)
    }

    @Test
    fun `Golden 1 - Par unabhaengig nachgerechnet, Start ungeloest, Schema valide`() {
        walkMinimalMoves(golden1.board) shouldBe 2
        DefaultTracer.trace(golden1.board).solved.shouldBeFalse()
        DefaultLevelValidator.validate(golden1).shouldBeInstanceOf<LevelValidationResult.Valid>()
    }

    @Test
    fun `Golden 2 - Kopffelder und Typ-Multimenge`() {
        golden2.board.radius shouldBe 3
        golden2.par shouldBe 3
        golden2.tier shouldBe Difficulty.D3
        golden2.seed shouldBe 42L
        golden2.board.elements.size shouldBe 10
        typeCounts(golden2) shouldBe
            mapOf("Crystal" to 3, "Mirror" to 2, "Splitter" to 2, "Prism" to 1, "Source" to 1, "Wall" to 1)
    }

    @Test
    fun `Golden 2 - Elemente feld-fuer-feld`() {
        golden2.board.elements[HexCoord(-3, 0)] shouldBe Element.Crystal(LightColor.GREEN)
        golden2.board.elements[HexCoord(-1, -2)] shouldBe Element.Crystal(LightColor.RED)
        golden2.board.elements[HexCoord(-1, 1)] shouldBe Element.Mirror(Orientation(3))
        golden2.board.elements[HexCoord(0, -3)] shouldBe Element.Crystal(LightColor.GREEN)
        golden2.board.elements[HexCoord(0, -1)] shouldBe Element.Splitter(Orientation(0))
        golden2.board.elements[HexCoord(0, 0)] shouldBe Element.Splitter(Orientation(4))
        golden2.board.elements[HexCoord(0, 1)] shouldBe Element.Prism
        golden2.board.elements[HexCoord(0, 3)] shouldBe
            Element.Source(LightColor.WHITE, Direction.NORTH_WEST)
        golden2.board.elements[HexCoord(2, -1)] shouldBe Element.Mirror(Orientation(3))
        golden2.board.elements[HexCoord(2, 0)] shouldBe Element.Wall
    }

    @Test
    fun `Golden 2 - Par unabhaengig nachgerechnet, Start ungeloest, Schema valide`() {
        walkMinimalMoves(golden2.board) shouldBe 3
        DefaultTracer.trace(golden2.board).solved.shouldBeFalse()
        DefaultLevelValidator.validate(golden2).shouldBeInstanceOf<LevelValidationResult.Valid>()
    }

    private fun typeCounts(level: LevelDefinition): Map<String, Int> =
        level.board.elements.values
            .groupingBy { it::class.simpleName ?: "?" }
            .eachCount()
}
