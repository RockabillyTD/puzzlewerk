package de.puzzlewerk.game.generator

import de.puzzlewerk.core.RandomSource
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.DefaultTracer

/**
 * Unabhaengiges Par-Orakel: VOLLENUMERATION aller 6^k End-Orientierungsvektoren
 * (bewusst NICHT kostengeordnet wie der ParSolver, Paragraf 9.4) — minimale
 * Kosten Sigma (m_ziel − m_start) mod 6 ueber alle loesenden Vektoren mit
 * Kosten >= 1, oder null. Nur fuer kleine k (<= 4) gedacht.
 */
fun bruteForceMinimalMoves(board: Board): Int? {
    val rotatables =
        board.elements.entries
            .mapNotNull { (cell, element) -> (element as? Element.Rotatable)?.let { cell to it } }
            .sortedWith(compareBy({ it.first.q }, { it.first.r }))
    var best: Int? = null
    val targets = IntArray(rotatables.size)

    fun visit(index: Int) {
        if (index == rotatables.size) {
            val cost =
                rotatables.indices.sumOf { i ->
                    (targets[i] - rotatables[i].second.orientation.steps).mod(Orientation.COUNT)
                }
            if (cost == 0) return
            val elements = HashMap(board.elements)
            rotatables.forEachIndexed { i, (cell, element) ->
                elements[cell] = element.withOrientation(Orientation(targets[i]))
            }
            if (DefaultTracer.trace(Board(board.radius, elements)).solved) {
                best = minOf(best ?: cost, cost)
            }
            return
        }
        for (steps in 0 until Orientation.COUNT) {
            targets[index] = steps
            visit(index + 1)
        }
    }
    visit(0)
    return best
}

/** Anzahl Elemente eines Typs auf dem Brett. */
inline fun <reified T : Element> Board.countOf(): Int = elements.values.count { it is T }

/**
 * Test-Double: liefert die vorgegebenen nextInt-Werte der Reihe nach
 * (jeder Wert muss < bound sein). nextLong ist in diesen Tests ungenutzt.
 */
class ScriptedRandom(
    private val values: List<Int>,
) : RandomSource {
    private var index = 0

    override fun nextLong(): Long = error("nextLong wird von diesem Test-Double nicht unterstuetzt")

    override fun nextInt(untilExclusive: Int): Int {
        val value = values[index]
        index += 1
        check(value < untilExclusive) { "Skriptwert $value passt nicht zu bound $untilExclusive" }
        return value
    }
}

/** Kurzform: alle drehbaren Zellen eines Bretts, sortiert nach (q, r). */
fun rotatableCells(board: Board): List<HexCoord> =
    board.elements.entries
        .filter { it.value is Element.Rotatable }
        .map { it.key }
        .sortedWith(compareBy({ it.q }, { it.r }))
