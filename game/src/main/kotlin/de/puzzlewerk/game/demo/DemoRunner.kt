package de.puzzlewerk.game.demo

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.engine.GameEngine
import de.puzzlewerk.game.engine.GameState
import de.puzzlewerk.game.engine.Move
import de.puzzlewerk.game.engine.MoveResult
import de.puzzlewerk.game.engine.defaultGameEngine
import de.puzzlewerk.game.generator.DefaultLevelGenerator
import de.puzzlewerk.game.level.DefaultLevelValidator
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.level.LevelValidationResult
import de.puzzlewerk.game.score.DefaultScoreCalculator
import de.puzzlewerk.game.trace.DefaultTracer
import kotlin.math.abs

private const val DEFAULT_SEED: Long = 20260711L
private const val MAX_ARG_COUNT: Int = 2
private const val CELL_WIDTH: Int = 5
private const val USAGE: String = "Nutzung: runDemo [seed:64-Bit-Ganzzahl] [tier:D1..D7]"

/**
 * CLI-Demo-Runner (Phase-2-Gate-Kriterium, docs/plan.md Abschnitt 7): generiert
 * ein Level, validiert es, rendert das Brett als ASCII-Hex, findet eine Loesung
 * und spielt sie Zug fuer Zug ueber die Engine ab — inklusive Score und Sternen.
 *
 * `println` ist hier bewusst zulaessig: reiner Demonstrationscode am Prozessrand,
 * kein Logging in Spiellogik. Der Wrapper bleibt minimal; der testbare Kern ist
 * [runDemo] (deterministisch: gleiche Argumente ⇒ identische Ausgabe).
 */
public fun main(args: Array<String>) {
    println(runDemo(args.toList()))
}

/** Ergebnis der strikten Argument-Validierung — Fehler als Wert, nie Crash (S4-Geist, C3). */
internal sealed interface DemoArgs {
    /** Gueltig geparste Argumente. */
    data class Valid(
        val seed: Long,
        val tier: Difficulty,
    ) : DemoArgs

    /** Ablehnung mit definierter Fehlermeldung. */
    data class Invalid(
        val message: String,
    ) : DemoArgs
}

/** Testbarer Demo-Kern: liefert den vollstaendigen Report bzw. die Fehlermeldung als String. */
internal fun runDemo(args: List<String>): String {
    return when (val parsed = parseDemoArgs(args)) {
        is DemoArgs.Invalid -> parsed.message
        is DemoArgs.Valid -> demoReport(parsed.seed, parsed.tier)
    }
}

/** Strikte Validierung: Seed als Long (Default 20260711), Tier exakt `D1`..`D7` (Default D2). */
internal fun parseDemoArgs(args: List<String>): DemoArgs {
    if (args.size > MAX_ARG_COUNT) {
        return DemoArgs.Invalid("Zu viele Argumente (${args.size}). $USAGE")
    }
    val seed = if (args.isEmpty()) DEFAULT_SEED else args[0].toLongOrNull()
    val tier =
        if (args.size < MAX_ARG_COUNT) {
            Difficulty.D2
        } else {
            Difficulty.entries.firstOrNull { it.name == args[1] }
        }
    return when {
        seed == null -> DemoArgs.Invalid("Ungueltiger Seed '${args[0]}' - erwartet 64-Bit-Ganzzahl. $USAGE")
        tier == null -> DemoArgs.Invalid("Ungueltiges Tier '${args[1]}' - erwartet D1..D7. $USAGE")
        else -> DemoArgs.Valid(seed, tier)
    }
}

/** Baut den kompletten Report: Kopf, Validator-Check, Brett, Loesungslauf, Wertung. */
private fun demoReport(
    seed: Long,
    tier: Difficulty,
): String {
    val level = DefaultLevelGenerator.generate(seed, tier)
    return buildList {
        add("Puzzlewerk CLI-Demo - Seed $seed, Tier $tier")
        add("Validator: ${validationSummary(level)}")
        add("Brett (Radius ${level.board.radius}, Par ${level.par}):")
        addAll(renderBoard(level.board))
        addAll(solveReport(level))
    }.joinToString("\n")
}

private fun validationSummary(level: LevelDefinition): String {
    return when (val result = DefaultLevelValidator.validate(level)) {
        is LevelValidationResult.Valid -> "OK (alle Schema-Regeln aus Design-Abschnitt 16.2 erfuellt)"
        is LevelValidationResult.Invalid -> "VERSTOESSE: ${result.violations}"
    }
}

/** Eine Zeile je r (r = -Radius..Radius), Zellen als Kuerzel aus [cellToken], `.` = leer. */
internal fun renderBoard(board: Board): List<String> {
    val radius = board.radius
    return (-radius..radius).map { r ->
        val columns =
            (maxOf(-radius, -radius - r)..minOf(radius, radius - r)).joinToString(" ") { q ->
                cellToken(board[HexCoord(q, r)]).padEnd(CELL_WIDTH)
            }
        val indent = " ".repeat(abs(r) * (CELL_WIDTH + 1) / 2)
        "r=${r.toString().padStart(2)}: $indent${columns.trimEnd()}"
    }
}

/**
 * Legende: S=Quelle(+Richtung+Farbe), M=Spiegel(+m), T=Splitter(+m), P=Prisma,
 * F=Filter(+Farbe), O=Portal(+Paar-ID), K=Kristall(+Sollfarbe), W=Wand.
 * Farben als Komponenten-Buchstaben R/G/B (Weiss = RGB, Gelb = RG, ...).
 */
internal fun cellToken(element: Element?): String {
    return when (element) {
        null -> "."
        is Element.Rotatable -> rotatableToken(element)
        is Element.Source -> "S${element.direction.index}${colorLetters(element.color)}"
        Element.Prism -> "P"
        is Element.Filter -> "F${colorLetters(element.color)}"
        is Element.Portal -> "O${element.pairId}"
        is Element.Crystal -> "K${colorLetters(element.required)}"
        Element.Wall -> "W"
    }
}

private fun rotatableToken(element: Element.Rotatable): String {
    val letter = if (element is Element.Mirror) "M" else "T"
    return "$letter${element.orientation.steps}"
}

private fun colorLetters(color: LightColor): String {
    return buildString {
        if (color.contains(LightColor.RED)) append('R')
        if (color.contains(LightColor.GREEN)) append('G')
        if (color.contains(LightColor.BLUE)) append('B')
    }
}

/** Spielt die gefundene Loesung ueber die Engine ab und protokolliert jeden Zug. */
internal fun solveReport(level: LevelDefinition): List<String> {
    val deltas =
        findSolutionDeltas(level)
            ?: return listOf("FEHLER: keine Loesung bis Par ${level.par} gefunden (Widerspruch zu Design 9.5/2).")
    val engine = defaultGameEngine(DefaultTracer)
    val moves = deltas.flatMap { (cell, steps) -> List(steps) { cell } }
    val lines = mutableListOf("Loesung gefunden: ${moves.size} Zuege - spiele sie ueber die Engine ab.")
    val finalState = moves.fold(engine.newGame(level).state) { state, cell -> applyLogged(engine, state, cell, lines) }
    val score = DefaultScoreCalculator.scoreFor(moves = finalState.moveCount, par = level.par)
    lines +=
        "Geloest: ${finalState.solved} | Zuege: ${finalState.moveCount} | Par: ${level.par}" +
        " | Score: ${score.points} | Sterne: ${score.stars}"
    return lines
}

/**
 * Findet Ziel-Drehstufen je drehbarem Element per kostengeordneter Suche ueber
 * End-Orientierungsvektoren — erlaubt dank Kommutativitaet (Design 6.4). Der
 * Generator garantiert eine Loesung mit exakt `par` Zuegen (Design 9.5/2),
 * daher ist die Suche hart auf Kosten <= par begrenzt. Bewusst simpler
 * Demonstrationscode, KEIN Ersatz fuer den Par-Solver des Generators.
 */
private fun findSolutionDeltas(level: LevelDefinition): Map<HexCoord, Int>? {
    val cells =
        level.board.elements
            .filterValues { it is Element.Rotatable }
            .keys
            .sortedWith(compareBy({ it.q }, { it.r }))
    for (cost in 1..level.par) {
        val deltas = searchDeltas(level, cells, IntArray(cells.size), 0, cost)
        if (deltas != null) return cells.zip(deltas.asList()).toMap()
    }
    return null
}

/** Zaehlt Delta-Vektoren mit Restkosten [remaining] lexikografisch auf (Idee wie Design 9.4). */
private fun searchDeltas(
    level: LevelDefinition,
    cells: List<HexCoord>,
    deltas: IntArray,
    index: Int,
    remaining: Int,
): IntArray? {
    if (index == cells.size) {
        return if (remaining == 0 && isSolvedWith(level, cells, deltas)) deltas.copyOf() else null
    }
    for (delta in 0..minOf(remaining, Orientation.COUNT - 1)) {
        deltas[index] = delta
        searchDeltas(level, cells, deltas, index + 1, remaining - delta)?.let { return it }
    }
    deltas[index] = 0
    return null
}

private fun isSolvedWith(
    level: LevelDefinition,
    cells: List<HexCoord>,
    deltas: IntArray,
): Boolean {
    val elements = level.board.elements.toMutableMap()
    cells.forEachIndexed { index, cell ->
        val rotatable = elements.getValue(cell) as Element.Rotatable
        val target = Orientation((rotatable.orientation.steps + deltas[index]).mod(Orientation.COUNT))
        elements[cell] = rotatable.withOrientation(target)
    }
    return DefaultTracer.trace(Board(level.board.radius, elements)).solved
}

/** Ein Rotate ueber die Engine; Ablehnung waere ein Programmierfehler des Demos (C3). */
private fun applyLogged(
    engine: GameEngine,
    state: GameState,
    cell: HexCoord,
    lines: MutableList<String>,
): GameState {
    val result = engine.applyMove(state, Move.Rotate(cell))
    check(result is MoveResult.Applied) { "Demo-Zug unerwartet abgelehnt: $result" }
    val next = result.state
    lines += "Zug ${next.moveCount}: Rotate(q=${cell.q}, r=${cell.r}) -> m=${next.orientations.getValue(cell).steps}"
    return next
}
