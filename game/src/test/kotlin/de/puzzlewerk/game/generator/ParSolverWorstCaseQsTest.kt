package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.DefaultTracer
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

// Grosszuegiges Zeitbudget (PW-2.5-QS): Paragraf 9.4 verspricht "deutlich unter
// einer Sekunde" on-device — 10 s auf der Test-JVM ist eine weiche Obergrenze,
// die nur echte Regressionen faengt, keine CI-Schwankungen.
private const val BUDGET_MILLIS = 10_000L

/**
 * Performance-Regression des Par-Solvers (Paragraf 9.4) am WORST CASE:
 * 8 Drehbare (harte Kappe), darunter SPLITTER — die vermehren Strahlen und
 * machen jeden einzelnen trace teuer, waehrend der unloesbare Farb-Mismatch
 * (gruene Quelle, roter Kristall, kein Prisma) die VOLLE Enumeration aller
 * Kostenvektoren 1..14 erzwingt (~3*10^5 Traces).
 */
class ParSolverWorstCaseQsTest {
    private val solver = ParSolver(DefaultTracer)

    @Test
    fun `Worst Case - 8 Drehbare mit Splittern, volle Enumeration bis Kosten 14 unter 10 s`() {
        val board = worstCaseBoard()
        var result: Int? = 0

        val elapsed = measureTimeMillis { result = solver.minimalMoves(board) }

        println("QS-SOLVER-WORSTCASE-MILLIS=$elapsed")
        result.shouldBeNull()
        elapsed shouldBeLessThan BUDGET_MILLIS
    }

    @Test
    fun `Loesbarer 8-Drehbaren-Fall - Ergebnis exakt (eigene Vektorsuche) und im Zeitbudget`() {
        // Weisse Quelle, weisser Kristall: loesbar, sobald der letzte Spiegel
        // auf Durchlauf steht — die Gegenprobe laeuft ueber die EIGENE
        // Kostenstufen-Vektorsuche (findSolvingVector), nicht den Solver.
        val board = worstCaseBoard(sourceColor = LightColor.WHITE, crystalColor = LightColor.WHITE)
        var result: Int? = null

        val elapsed = measureTimeMillis { result = solver.minimalMoves(board) }

        println("QS-SOLVER-WORSTCASE-SOLVABLE-MILLIS=$elapsed result=$result")
        result shouldBe (1..14).firstOrNull { cost -> findSolvingVector(board, cost) != null }
        elapsed shouldBeLessThan BUDGET_MILLIS
    }

    private fun worstCaseBoard(
        sourceColor: LightColor = LightColor.GREEN,
        crystalColor: LightColor = LightColor.RED,
    ): Board {
        val elements = mutableMapOf<HexCoord, Element>()
        elements[HexCoord(-3, 0)] = Element.Source(sourceColor, Direction.EAST)
        elements[HexCoord(3, 0)] = Element.Crystal(crystalColor)
        listOf(HexCoord(-2, 0), HexCoord(-1, 0), HexCoord(0, 0), HexCoord(1, 0)).forEachIndexed { i, cell ->
            elements[cell] = Element.Splitter(Orientation(i % Orientation.COUNT))
        }
        listOf(HexCoord(2, 0), HexCoord(0, -1), HexCoord(0, 1), HexCoord(1, -1)).forEachIndexed { i, cell ->
            elements[cell] = Element.Mirror(Orientation((i + 2) % Orientation.COUNT))
        }
        return Board(3, elements)
    }
}
