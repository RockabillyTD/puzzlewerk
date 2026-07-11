package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.trace.Tracer

/**
 * Par-Solver (Design §9.4): exakte minimale Zuganzahl per KOSTENGEORDNETER
 * Aufzaehlung ueber End-Orientierungsvektoren der drehbaren Elemente — dank
 * Kommutativitaet (§6.4, I9) ist keine Zugbaum-Suche noetig. Vektoren je
 * Kostenstufe werden lexikografisch aufsteigend aufgezaehlt (§9.4).
 */
internal class ParSolver(
    private val tracer: Tracer,
) {
    /**
     * Kleinste Kostensumme `c ∈ 1..maxCost`, mit der [board] loesbar ist, oder
     * `null`. Kosten 0 (Startzustand bereits geloest) prueft der Aufrufer
     * separat (§9.4, R35).
     */
    fun minimalMoves(
        board: Board,
        maxCost: Int = LevelDefinition.MAX_PAR,
    ): Int? {
        val rotatables =
            board.elements.entries
                .mapNotNull { (cell, element) -> (element as? Element.Rotatable)?.let { cell to it } }
                .sortedWith(compareBy({ it.first.q }, { it.first.r }))
        if (rotatables.isEmpty()) return null
        val search = OrientationSearch(tracer, board, rotatables)
        return (1..maxCost).firstOrNull { cost -> search.solvesWith(index = 0, remaining = cost) }
    }
}

/** Rekursive Vektor-Aufzaehlung; haelt EIN Arbeitsbrett statt ~3·10⁵ Kopien (Aufwand §9.4). */
private class OrientationSearch(
    private val tracer: Tracer,
    board: Board,
    private val rotatables: List<Pair<HexCoord, Element.Rotatable>>,
) {
    private val radius = board.radius
    private val working = HashMap(board.elements)
    private val offsets = IntArray(rotatables.size)

    fun solvesWith(
        index: Int,
        remaining: Int,
    ): Boolean =
        if (index == rotatables.lastIndex) {
            remaining < Orientation.COUNT && solvesWithLast(remaining)
        } else {
            (0..minOf(Orientation.COUNT - 1, remaining)).any { steps ->
                offsets[index] = steps
                solvesWith(index + 1, remaining - steps)
            }
        }

    private fun solvesWithLast(steps: Int): Boolean {
        offsets[rotatables.lastIndex] = steps
        rotatables.forEachIndexed { i, (cell, element) ->
            val target = (element.orientation.steps + offsets[i]).mod(Orientation.COUNT)
            working[cell] = element.withOrientation(Orientation(target))
        }
        return tracer.trace(Board(radius, working)).solved
    }
}
