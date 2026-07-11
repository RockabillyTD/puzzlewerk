package de.puzzlewerk.game.generator

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.engine.Move
import de.puzzlewerk.game.engine.MoveResult
import de.puzzlewerk.game.engine.defaultGameEngine
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.trace.DefaultTracer
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.concurrent.ConcurrentHashMap

// Unabhaengige QS-Werkzeuge (PW-2.5-QS). Bewusst ANDERS konstruiert als der
// ParSolver (Paragraf 9.4, Orientierungsvektor-Enumeration) und das
// Vollenumerations-Orakel aus GeneratorTestSupport.

/** BFS-Orakel-Kappe: 6^5 Zustaende; mehr Drehbare waeren ein Testdesign-Fehler. */
private const val MAX_WALK_ROTATABLES = 5

/**
 * Unabhaengiges Par-Orakel: Breitensuche ueber ZUGFOLGEN — jeder Schritt ist
 * genau ein Rotate um +1 Stufe (Paragraf 6.1). Die BFS-Tiefe ist damit direkt
 * die minimale ZUGZAHL im Sinne von Par (Paragraf 7.1); die Kosten-Definition
 * Sigma (m_ziel - m_start) mod 6 (Paragraf 6.4, I9) entsteht hier als
 * Suchtiefe, nicht als Formel. Kosten 0 (Start geloest) zaehlt wie beim
 * ParSolver nicht (Paragraf 9.4). Nur fuer k <= [MAX_WALK_ROTATABLES].
 */
fun walkMinimalMoves(
    board: Board,
    maxMoves: Int = LevelDefinition.MAX_PAR,
): Int? {
    val rotatables = sortedRotatables(board)
    if (rotatables.isEmpty()) return null
    check(rotatables.size <= MAX_WALK_ROTATABLES) { "BFS-Orakel nur fuer kleine k, war ${rotatables.size}" }
    return WalkSearch(board, rotatables).run(maxMoves)
}

/** Drehbare Zellen mit Element, aufsteigend nach (q, dann r) — wie der Solver ordnet. */
fun sortedRotatables(board: Board): List<Pair<HexCoord, Element.Rotatable>> =
    board.elements.entries
        .mapNotNull { (cell, element) -> (element as? Element.Rotatable)?.let { cell to it } }
        .sortedWith(compareBy({ it.first.q }, { it.first.r }))

/** Brett mit um [offsets] Stufen weitergedrehten Drehbaren (Reihenfolge wie [rotatables]). */
fun boardWithOffsets(
    board: Board,
    rotatables: List<Pair<HexCoord, Element.Rotatable>>,
    offsets: IntArray,
): Board {
    val elements = HashMap(board.elements)
    rotatables.forEachIndexed { i, (cell, element) ->
        val target = (element.orientation.steps + offsets[i]).mod(Orientation.COUNT)
        elements[cell] = element.withOrientation(Orientation(target))
    }
    return Board(board.radius, elements)
}

private class WalkSearch(
    private val board: Board,
    private val rotatables: List<Pair<HexCoord, Element.Rotatable>>,
) {
    private val visited = HashSet<Int>()

    fun run(maxMoves: Int): Int? {
        var frontier = listOf(IntArray(rotatables.size))
        visited += encode(frontier.first())
        for (depth in 1..maxMoves) {
            val next = mutableListOf<IntArray>()
            for (state in frontier) {
                expand(state, depth, next)?.let { return it }
            }
            frontier = next
        }
        return null
    }

    private fun expand(
        state: IntArray,
        depth: Int,
        next: MutableList<IntArray>,
    ): Int? {
        for (i in rotatables.indices) {
            val child = state.copyOf()
            child[i] = (child[i] + 1) % Orientation.COUNT
            if (visited.add(encode(child))) {
                if (DefaultTracer.trace(boardWithOffsets(board, rotatables, child)).solved) return depth
                next += child
            }
        }
        return null
    }

    private fun encode(state: IntArray): Int = state.fold(0) { acc, steps -> acc * Orientation.COUNT + steps }
}

/**
 * Lexikografisch erster Offsetvektor mit Kosten EXAKT [cost], der [board]
 * loest, oder null. Dient dem Engine-Playthrough: existiert kein Vektor mit
 * Kosten == Par, ist die Par-Angabe unhaltbar (I4).
 */
fun findSolvingVector(
    board: Board,
    cost: Int,
): IntArray? {
    val rotatables = sortedRotatables(board)
    if (rotatables.isEmpty()) return null
    val offsets = IntArray(rotatables.size)
    return if (searchVector(board, rotatables, offsets, index = 0, remaining = cost)) offsets else null
}

private fun searchVector(
    board: Board,
    rotatables: List<Pair<HexCoord, Element.Rotatable>>,
    offsets: IntArray,
    index: Int,
    remaining: Int,
): Boolean {
    if (index == offsets.lastIndex) {
        if (remaining >= Orientation.COUNT) return false
        offsets[index] = remaining
        return DefaultTracer.trace(boardWithOffsets(board, rotatables, offsets)).solved
    }
    return (0..minOf(Orientation.COUNT - 1, remaining)).any { steps ->
        offsets[index] = steps
        searchVector(board, rotatables, offsets, index + 1, remaining - steps)
    }
}

/**
 * Spielt [level] ueber die ECHTE Engine mit einem Loesungsvektor der Kosten
 * `level.par` durch (R35/I4 Ende-zu-Ende): Start ungeloest, kein
 * Zwischenzustand geloest (ein frueher geloester Zustand wuerde den naechsten
 * Rotate mit ALREADY_SOLVED ablehnen und den Test brechen), letzter Zug loest,
 * Zugzahl == Par.
 */
fun playThroughInParMoves(level: LevelDefinition) {
    val vector = findSolvingVector(level.board, level.par)
    vector.shouldNotBeNull()
    val engine = defaultGameEngine(DefaultTracer)
    var state = engine.newGame(level).state
    state.solved.shouldBeFalse()
    sortedRotatables(level.board).forEachIndexed { i, (cell, _) ->
        repeat(vector[i]) {
            val applied = engine.applyMove(state, Move.Rotate(cell)).shouldBeInstanceOf<MoveResult.Applied>()
            state = applied.state
        }
    }
    state.moveCount shouldBe level.par
    state.solved.shouldBeTrue()
}

/**
 * Kanonische, sortierte Text-Serialisierung einer [LevelDefinition] — Basis
 * fuer BYTE-GENAUE I2/R34-Vergleiche unabhaengig von Map-Iterationsordnung
 * und data-class-toString.
 */
fun canonicalForm(level: LevelDefinition): String =
    buildString {
        append("radius=").append(level.board.radius)
        append("|par=").append(level.par)
        append("|tier=").append(level.tier.name)
        append("|seed=").append(level.seed)
        level.board.elements.entries
            .sortedWith(compareBy({ it.key.q }, { it.key.r }))
            .forEach { (cell, element) ->
                append("|").append(cell.q).append(",").append(cell.r).append("=").append(describe(element))
            }
    }

private fun describe(element: Element): String =
    when (element) {
        is Element.Source -> "Q${element.color.bits}d${element.direction.index}"
        is Element.Mirror -> "S${element.orientation.steps}"
        is Element.Splitter -> "T${element.orientation.steps}"
        Element.Prism -> "P"
        is Element.Filter -> "F${element.color.bits}"
        is Element.Portal -> "O${element.pairId}"
        is Element.Crystal -> "K${element.required.bits}"
        Element.Wall -> "W"
    }

/** Black-box beobachtbares Relaxierungs-Indiz eines generierten Levels (Paragraf 9.5/7). */
enum class RelaxationSign {
    /** Alle beobachtbaren Werte liegen in den unrelaxierten Tier-Bereichen. */
    NONE,

    /** Par ausserhalb des Tier-Zielbereichs — Stufe 1 der Leiter (Par +-2). */
    PAR_OUTSIDE_TIER,

    /** Weniger Drehbare als das Tier-Minimum — Budget-Senkungs-Stufen. */
    BUDGET_REDUCED,

    /** Exakte Spiegelweg-Form — letzte Stufe der Leiter (R36). */
    FALLBACK_SHAPE,
}

/** Klassifiziert [level] nach den black-box sichtbaren Relaxierungs-Indizien. */
fun relaxationSign(level: LevelDefinition): RelaxationSign {
    val params = tierParameters(level.tier)
    val rotatables = level.board.elements.values.count { it.isRotatable }
    return when {
        isSpiegelwegShape(level.board) -> RelaxationSign.FALLBACK_SHAPE
        level.par !in params.parRange -> RelaxationSign.PAR_OUTSIDE_TIER
        rotatables < params.rotatableRange.first -> RelaxationSign.BUDGET_REDUCED
        else -> RelaxationSign.NONE
    }
}

private fun isSpiegelwegShape(board: Board): Boolean {
    val radius = board.radius
    return board.elements.keys == setOf(HexCoord(-radius, 0), HexCoord(0, 0), HexCoord(radius, 0)) &&
        board.elements[HexCoord(-radius, 0)] is Element.Source &&
        board.elements[HexCoord(0, 0)] is Element.Mirror &&
        board.elements[HexCoord(radius, 0)] is Element.Crystal
}

/**
 * Cache generierter Level fuer die QS-Suite: der Generator ist pur (I2),
 * Mehrfachgenerierung waere reine Laufzeit. Determinismus-Tests rufen den
 * Generator bewusst DIREKT auf, nie ueber diesen Cache.
 */
object QsLevels {
    private val cache = ConcurrentHashMap<Pair<Long, Difficulty>, LevelDefinition>()

    fun of(
        seed: Long,
        tier: Difficulty,
    ): LevelDefinition = cache.getOrPut(seed to tier) { DefaultLevelGenerator.generate(seed, tier) }
}
