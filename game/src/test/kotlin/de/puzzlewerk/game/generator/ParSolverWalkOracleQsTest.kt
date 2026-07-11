package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.trace.DefaultTracer
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.random.Random

private const val ORACLE_ITERATIONS = 90

// ASCII "PW25QSOR" — fester Property-Seed (keine unkontrollierte Zufaelligkeit).
private const val ORACLE_PROP_SEED = 0x5057323551534F52L

/**
 * I4 adversarial (PW-2.5-QS): Der ParSolver behauptet EXAKTE Minima. Gegenprobe
 * mit einem anders konstruierten Orakel — Breitensuche ueber ZUGFOLGEN
 * ([walkMinimalMoves], jeder Schritt ein +1-Rotate) statt Orientierungsvektor-
 * Enumeration. Stimmen beide auf vielen kleinen Zufallsbrettern (fester Seed)
 * ueberein, ist die Kosten-Definition Sigma (m_ziel - m_start) mod 6
 * (Paragraf 6.4/7.1, I9) durch zwei unabhaengige Wege belegt.
 */
class ParSolverWalkOracleQsTest {
    private val solver = ParSolver(DefaultTracer)
    private val primaries = listOf(LightColor.RED, LightColor.GREEN, LightColor.BLUE)

    private val arbBoard: Arb<Board> = arbitrary { rs -> randomBoard(rs.random) }

    // Opt-in noetig, weil der PropTestConfig-Konstruktor in Kotest 5.9 experimentelle
    // Default-Parameter traegt; der feste Seed ist fuer Reproduzierbarkeit Pflicht.
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `I4 - ParSolver stimmt mit dem Zugfolgen-BFS-Orakel auf Zufallsbrettern ueberein`() {
        // Block-Body statt Expression-Body: eine @Test-Methode mit Rueckgabewert
        // (PropertyContext aus checkAll) wuerde JUnit Jupiter STILL ignorieren.
        runBlocking {
            checkAll(
                PropTestConfig(seed = ORACLE_PROP_SEED, iterations = ORACLE_ITERATIONS),
                arbBoard,
            ) { board ->
                solver.minimalMoves(board) shouldBe walkMinimalMoves(board)
            }
        }
    }

    @Test
    fun `Kosten-Semantik - EIN beruehrtes Element kann Par 5 kosten, nicht 1 (Paragraf 6_1)`() {
        // Einzige Loesungsorientierung m=1; Start m=2 => (1 - 2) mod 6 = 5 Zuege.
        // Eine naive "Anzahl veraenderter Elemente"-Deutung ergaebe faelschlich 1.
        val board =
            Board(
                radius = 2,
                elements =
                    mapOf(
                        HexCoord(-2, 0) to Element.Source(LightColor.RED, Direction.EAST),
                        HexCoord(0, 0) to Element.Mirror(Orientation(2)),
                        HexCoord(1, -1) to Element.Crystal(LightColor.RED),
                    ),
            )

        solver.minimalMoves(board) shouldBe 5
        walkMinimalMoves(board) shouldBe 5
        bruteForceMinimalMoves(board) shouldBe 5
    }

    @Test
    fun `I4 - Par generierter Level D1 bis D3 stimmt mit dem Zugfolgen-BFS-Orakel (drei Seeds)`() {
        for (tier in listOf(Difficulty.D1, Difficulty.D2, Difficulty.D3)) {
            for (seed in listOf(7L, 99L, 20260711L)) {
                val level = QsLevels.of(seed, tier)

                walkMinimalMoves(level.board) shouldBe level.par
            }
        }
    }

    /** Zufallsbrett R=2 mit 1-2 Quellen/Kristallen, 1..4 Drehbaren und optional Prisma/Filter/Portal/Wand. */
    private fun randomBoard(random: Random): Board {
        val free = boardCells(2).shuffled(random).toMutableList()
        val elements = mutableMapOf<HexCoord, Element>()
        repeat(1 + random.nextInt(2)) {
            elements[free.removeLast()] =
                Element.Source(LightColor(1 + random.nextInt(7)), Direction.entries.random(random))
        }
        repeat(1 + random.nextInt(2)) {
            elements[free.removeLast()] = Element.Crystal(LightColor(1 + random.nextInt(7)))
        }
        repeat(1 + random.nextInt(4)) {
            val orientation = Orientation(random.nextInt(Orientation.COUNT))
            elements[free.removeLast()] =
                if (random.nextBoolean()) Element.Mirror(orientation) else Element.Splitter(orientation)
        }
        if (random.nextBoolean()) elements[free.removeLast()] = Element.Prism
        if (random.nextBoolean()) elements[free.removeLast()] = Element.Filter(primaries.random(random))
        if (random.nextBoolean()) {
            elements[free.removeLast()] = Element.Portal(0)
            elements[free.removeLast()] = Element.Portal(0)
        }
        if (random.nextBoolean()) elements[free.removeLast()] = Element.Wall
        return Board(2, elements)
    }
}
