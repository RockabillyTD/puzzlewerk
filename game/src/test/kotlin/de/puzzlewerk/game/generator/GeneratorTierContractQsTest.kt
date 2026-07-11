package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.engine.defaultGameEngine
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

// Zehn feste QS-Seeds — bewusst disjunkt zu den Seeds der Bestandstests.
private val QS_SEEDS = listOf(101L, 202L, 303L, 404L, 505L, 606L, 707L, 808L, 909L, 1010L)

// D6/D7-Playthroughs sind teuer (Vektorsuche bis Kosten 14 bei k<=8) — Stichprobe.
private val DEEP_TIER_PLAYTHROUGH_SEEDS = listOf(101L, 505L, 1010L)

// Zusaetzlicher D7-Scan fuer die Relaxierungs-Statistik (R36-Umfeld).
private val D7_SCAN_SEEDS = (2001L..2040L).toList()

/**
 * Tier-Vertraege (Paragraf 9.2) und R35 fuer ALLE 7 Tiers ueber zehn frische
 * Seeds (PW-2.5-QS): Schema, Kappen, Par-Zielbereich bzw. dokumentierte
 * Relaxierung (Paragraf 9.5/7), Startzustand nie geloest — zusaetzlich ueber
 * die ECHTE Engine (newGame) statt nur trace — und Loesbarkeit in exakt Par
 * Zuegen per Engine-Playthrough (I4 Ende-zu-Ende).
 */
class GeneratorTierContractQsTest {
    private fun forAllQsLevels(assertions: (Difficulty, LevelDefinition) -> Unit) {
        for (tier in Difficulty.entries) {
            for (seed in QS_SEEDS) {
                assertions(tier, QsLevels.of(seed, tier))
            }
        }
    }

    @Test
    fun `9_2 - Radius, Quellen und Kristalle liegen IMMER in den Tier-Bereichen (Leiter aendert sie nie)`() {
        forAllQsLevels { tier, level ->
            val params = tierParameters(tier)
            level.board.radius shouldBeInRange params.radiusRange
            level.board.countOf<Element.Source>() shouldBeInRange params.sourceRange
            level.board.countOf<Element.Crystal>() shouldBeInRange params.crystalRange
        }
    }

    @Test
    fun `9_2 - harte Kappen - Drehbare bis 8, Belegung bis 50 Prozent, Portale exakt gepaart`() {
        forAllQsLevels { _, level ->
            val rotatables = level.board.elements.values.count { it.isRotatable }
            rotatables shouldBeLessThanOrEqual LevelDefinition.MAX_ROTATABLES
            level.board.elements.size shouldBeLessThanOrEqual Board.cellCount(level.board.radius) / 2
            val pairIds = level.board.elements.values.filterIsInstance<Element.Portal>().map { it.pairId }
            pairIds.distinct().size shouldBeLessThanOrEqual LevelDefinition.MAX_PORTAL_PAIRS
            pairIds.groupingBy { it }.eachCount().values.forEach { it shouldBe 2 }
        }
    }

    @Test
    fun `9_5-5 - jedes Level besteht den DefaultLevelValidator`() {
        forAllQsLevels { _, level ->
            DefaultLevelValidator.validate(level).shouldBeInstanceOf<LevelValidationResult.Valid>()
        }
    }

    @Test
    fun `R35 scharf - kein Level ist beim Engine-newGame ODER per trace geloest`() {
        val engine = defaultGameEngine(DefaultTracer)
        forAllQsLevels { _, level ->
            val start = engine.newGame(level)
            start.state.solved.shouldBeFalse()
            start.trace.solved.shouldBeFalse()
            start.state.moveCount shouldBe 0
            DefaultTracer.trace(level.board).solved.shouldBeFalse()
            level.par shouldBeInRange LevelDefinition.MIN_PAR..LevelDefinition.MAX_PAR
        }
    }

    @Test
    fun `9_5-7 - Relaxierungs-Statistik - Par im Tier-Bereich oder dokumentiert relaxiert`() {
        val signs = mutableMapOf<RelaxationSign, Int>()
        forAllQsLevels { tier, level ->
            val sign = relaxationSign(level)
            signs.merge(sign, 1, Int::plus)
            if (sign == RelaxationSign.PAR_OUTSIDE_TIER) {
                // Stufe 1 der Leiter erweitert den Zielbereich um hoechstens +-2.
                val base = tierParameters(tier).parRange
                val widenedFloor = (base.first - 2).coerceAtLeast(LevelDefinition.MIN_PAR)
                val widenedCeiling = (base.last + 2).coerceAtMost(LevelDefinition.MAX_PAR)
                level.par shouldBeInRange widenedFloor..widenedCeiling
            }
        }
        println("QS-RELAXATION[7 Tiers x ${QS_SEEDS.size} Seeds]=$signs")
        // Qualitaets-Messlatte: uebliche Seeds duerfen praktisch nie relaxieren.
        signs.getOrDefault(RelaxationSign.NONE, 0) shouldBe Difficulty.entries.size * QS_SEEDS.size
    }

    @Test
    fun `I4 Ende-zu-Ende - D1 bis D5 sind ueber die Engine in exakt Par Zuegen loesbar`() {
        for (tier in listOf(Difficulty.D1, Difficulty.D2, Difficulty.D3, Difficulty.D4, Difficulty.D5)) {
            for (seed in QS_SEEDS) {
                playThroughInParMoves(QsLevels.of(seed, tier))
            }
        }
    }

    @Test
    fun `I4 Ende-zu-Ende - D6 und D7 Stichprobe in exakt Par Zuegen loesbar`() {
        for (tier in listOf(Difficulty.D6, Difficulty.D7)) {
            for (seed in DEEP_TIER_PLAYTHROUGH_SEEDS) {
                playThroughInParMoves(QsLevels.of(seed, tier))
            }
        }
    }

    @Test
    fun `R36-Umfeld - D7-Seed-Scan zeigt keinen natuerlichen Spiegelweg-Fallback`() {
        // Black-box laesst sich der Fallback nicht erzwingen: dafuer muessten
        // 1000 Versuche JEDER Leiterstufe scheitern (Paragraf 9.5/7) — bei
        // einem gesunden Generator astronomisch unwahrscheinlich. Der Scan
        // dokumentiert stattdessen die natuerliche Relaxierungs-Quote auf D7.
        val signs = mutableMapOf<RelaxationSign, Int>()
        for (seed in D7_SCAN_SEEDS) {
            val level = QsLevels.of(seed, Difficulty.D7)
            signs.merge(relaxationSign(level), 1, Int::plus)
            level.par shouldBeInRange LevelDefinition.MIN_PAR..LevelDefinition.MAX_PAR
        }
        println("QS-D7-SCAN[${D7_SCAN_SEEDS.size} Seeds]=$signs")
        signs.getOrDefault(RelaxationSign.FALLBACK_SHAPE, 0) shouldBe 0
    }
}
