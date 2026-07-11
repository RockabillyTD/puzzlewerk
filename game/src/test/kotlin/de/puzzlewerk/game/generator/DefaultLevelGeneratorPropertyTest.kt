package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.DefaultLevelValidator
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.level.LevelValidationResult
import de.puzzlewerk.game.trace.DefaultTracer
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

// Feste Seeds (deterministisch, Block-Bodies) — bewusst KEINE zufaelligen
// Testeingaben: der Generator selbst ist die zu testende Zufallsquelle.
private val PROPERTY_SEEDS = listOf(1L, 42L, 20260709L)

/**
 * MUSS-Eigenschaften aus Paragraf 9.5 als Properties ueber alle Tiers und
 * mehrere feste Seeds: Schema-Valid (I3-Schema), loesbar mit exakt Par Zuegen
 * (I4), Par im Tier-Zielbereich (R35-Untergrenze, 9.2), alle harten Kappen.
 *
 * Die gewaehlten Seeds generieren ohne Relaxierung (verifiziert): Par liegt
 * im UNRELAXIERTEN Tier-Bereich.
 */
class DefaultLevelGeneratorPropertyTest {
    private fun forAllLevels(assertions: (Difficulty, LevelDefinition) -> Unit) {
        for (tier in Difficulty.entries) {
            for (seed in PROPERTY_SEEDS) {
                assertions(tier, DefaultLevelGenerator.generate(seed, tier))
            }
        }
    }

    @Test
    fun `Jedes generierte Level besteht den DefaultLevelValidator (9_5-5)`() {
        forAllLevels { _, level ->
            DefaultLevelValidator.validate(level).shouldBeInstanceOf<LevelValidationResult.Valid>()
        }
    }

    @Test
    fun `Par liegt im Par-Zielbereich des Tiers und ist exakt der Solver-Wert (9_5-2, I4)`() {
        forAllLevels { tier, level ->
            level.par shouldBeInRange tierParameters(tier).parRange
            ParSolver(DefaultTracer).minimalMoves(level.board) shouldBe level.par
        }
    }

    @Test
    fun `Kein Level ist im Startzustand geloest (9_5-1, R35)`() {
        forAllLevels { _, level ->
            DefaultTracer.trace(level.board).solved.shouldBeFalse()
        }
    }

    @Test
    fun `Radius, Quellen, Kristalle und Drehbare liegen in den Tier-Bereichen (9_2)`() {
        forAllLevels { tier, level ->
            val params = tierParameters(tier)
            level.board.radius shouldBeInRange params.radiusRange
            level.board.countOf<Element.Source>() shouldBeInRange params.sourceRange
            level.board.countOf<Element.Crystal>() shouldBeInRange params.crystalRange
            level.board.elements.values.count { it.isRotatable } shouldBeInRange params.rotatableRange
        }
    }

    @Test
    fun `Harte Kappen - Drehbare bis 8, Portale gepaart bis 2 Paare, Belegung bis 50 Prozent (9_2)`() {
        forAllLevels { _, level ->
            level.board.elements.values.count { it.isRotatable } shouldBeLessThanOrEqual
                LevelDefinition.MAX_ROTATABLES
            level.board.elements.size shouldBeLessThanOrEqual Board.cellCount(level.board.radius) / 2
            val portalIds = level.board.elements.values.filterIsInstance<Element.Portal>().map { it.pairId }
            portalIds.distinct().size shouldBeLessThanOrEqual LevelDefinition.MAX_PORTAL_PAIRS
            portalIds.groupingBy { it }.eachCount().values.forEach { it shouldBe 2 }
        }
    }

    @Test
    fun `Farbbudget - verschiedene Farben von Quellen, Kristallen und Filtern bis Tier-Kappe (9_2)`() {
        forAllLevels { tier, level ->
            val colors =
                level.board.elements.values.mapNotNull { element ->
                    when (element) {
                        is Element.Source -> element.color
                        is Element.Crystal -> element.required
                        is Element.Filter -> element.color
                        else -> null
                    }
                }
            colors.distinct().size shouldBeLessThanOrEqual tierParameters(tier).palette.maxColors
        }
    }

    @Test
    fun `D1 nutzt nur Weiss oder Primaerfarben und ausschliesslich Spiegel als Drehbare (9_2)`() {
        for (seed in PROPERTY_SEEDS) {
            val level = DefaultLevelGenerator.generate(seed, Difficulty.D1)

            level.board.elements.values.forEach { element ->
                when (element) {
                    is Element.Source ->
                        (element.color.isPrimary || element.color == LightColor.WHITE) shouldBe true
                    is Element.Splitter, is Element.Prism, is Element.Filter, is Element.Portal ->
                        error("D1 erlaubt nur Spiegel als aktives Element, war $element")
                    else -> Unit
                }
            }
        }
    }
}
