package de.puzzlewerk.game.generator

import de.puzzlewerk.core.RandomSource
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.trace.DefaultTracer
import de.puzzlewerk.game.trace.TraceResult

// D1-Farbspalte §9.2: „1 (Weiss o. Primaer)".
private val D1_SOURCE_COLORS = listOf(LightColor.WHITE, LightColor.RED, LightColor.GREEN, LightColor.BLUE)

/** Maximale Verwuerfelungs-Versuche je Konstruktion (Design §9.3 Schritt 8). */
private const val MAX_SCRAMBLE_TRIES = 20

/**
 * EIN Konstruktionsversuch nach §9.3 („Vorwaerts-Verlegung,
 * Rueckwaerts-Verwuerfelung"). [attempt] liefert `null`, wenn der Versuch
 * verworfen wird (Schritt 6/8) — der Aufrufer zaehlt Versuche und relaxiert
 * (§9.5/7). Alle Ziehungen kommen aus dem EINEN fortlaufenden PRNG-Strom (§8).
 *
 * Heuristik-Festlegungen (Implementierungsfreiheit laut §9.3, deterministisch):
 * - Zieh-Reihenfolge: Radius, Quellen, Drehbare, Splitter, Prismen, Filter,
 *   Portalpaare, Wand-Ziel; danach die Platzierungen.
 * - Platzierungs-Reihenfolge: Splitter → Prismen → Portale → Filter → Spiegel
 *   (strukturbildende Verzweigungen zuerst, Feinlenkung zuletzt).
 * - Mindest-Splitterzahl `max(0, minKristalle − Quellen)` sichert genug
 *   Strahlenden fuer das Kristall-Budget.
 * - „Farben im Level" (§9.2) = verschiedene Farben von Quellen, Kristallen
 *   und Filtern; Verstoss ⇒ Versuch verwerfen.
 */
internal class LevelConstruction(
    private val params: TierParameters,
    private val tier: Difficulty,
    private val seed: Long,
    private val random: RandomSource,
) {
    private val tracer = DefaultTracer
    private val elements = LinkedHashMap<HexCoord, Element>()
    private val radius: Int = drawFrom(params.radiusRange)
    private val maxOccupied: Int = Board.cellCount(radius) / 2 // harte Kappe §9.2: Belegung ≤ 50 %

    fun attempt(): GeneratedLevel? {
        val budget = drawBudget()
        val placed = placeSources(budget.sources) && placeRouting(budget) && placeCrystals()
        if (!placed) return null
        placeWalls(budget.walls)
        val solution = Board(radius, elements.toMap())
        return if (meetsQualityBars(solution)) {
            Scrambler(params.parRange, tier, seed, random).scramble(solution)
        } else {
            null
        }
    }

    private data class Budget(
        val sources: Int,
        val splitters: Int,
        val mirrors: Int,
        val prisms: Int,
        val filters: Int,
        val portalPairs: Int,
        val walls: Int,
    )

    private fun drawBudget(): Budget {
        val sources = drawFrom(params.sourceRange)
        val rotatables = drawFrom(params.rotatableRange)
        val minSplitters = (params.crystalRange.first - sources).coerceIn(0, rotatables)
        val splitters =
            if (params.palette.splitterAllowed) {
                minSplitters + random.nextInt(rotatables - minSplitters + 1)
            } else {
                0
            }
        return Budget(
            sources = sources,
            splitters = splitters,
            mirrors = rotatables - splitters,
            prisms = if (params.palette.prismAllowed) random.nextInt(2) else 0,
            filters = if (params.palette.filterAllowed) random.nextInt(2) else 0,
            portalPairs =
                if (params.palette.maxPortalPairs > 0) {
                    random.nextInt(params.palette.maxPortalPairs + 1)
                } else {
                    0
                },
            walls = drawFrom(params.wallRange),
        )
    }

    private fun drawFrom(range: IntRange): Int = range.first + random.nextInt(range.last - range.first + 1)

    /** §9.3 Schritt 2: Quellen im Randring, Emissionsrichtung strikt ins Brett. */
    private fun placeSources(count: Int): Boolean {
        repeat(count) {
            val free = ringCells(radius).filter { it !in elements }
            if (free.isEmpty()) return false
            val cell = free[random.nextInt(free.size)]
            val inward = Direction.entries.filter { cell.neighbor(it).ringIndex < radius }
            elements[cell] = Element.Source(drawSourceColor(), inward[random.nextInt(inward.size)])
        }
        return true
    }

    private fun drawSourceColor(): LightColor =
        if (params.palette.maxColors == 1) {
            D1_SOURCE_COLORS[random.nextInt(D1_SOURCE_COLORS.size)]
        } else {
            LightColor(random.nextInt(LightColor.WHITE.bits) + 1)
        }

    private enum class RoutingElement { SPLITTER, PRISM, PORTAL_PAIR, FILTER, MIRROR }

    /** §9.3 Schritt 3: Routing-Elemente nacheinander auf freie Strahlweg-Zellen. */
    private fun placeRouting(budget: Budget): Boolean {
        val plan =
            buildList {
                repeat(budget.splitters) { add(RoutingElement.SPLITTER) }
                repeat(budget.prisms) { add(RoutingElement.PRISM) }
                repeat(budget.portalPairs) { add(RoutingElement.PORTAL_PAIR) }
                repeat(budget.filters) { add(RoutingElement.FILTER) }
                repeat(budget.mirrors) { add(RoutingElement.MIRROR) }
            }
        return plan.all(::placeRoutingElement)
    }

    private fun placeRoutingElement(kind: RoutingElement): Boolean {
        val trace = tracer.trace(Board(radius, elements))
        val path = transitCells(trace, elements)
        if (path.isEmpty()) return false
        val cell = path[random.nextInt(path.size)]
        elements[cell] =
            when (kind) {
                RoutingElement.MIRROR -> Element.Mirror(Orientation(random.nextInt(Orientation.COUNT)))
                RoutingElement.SPLITTER -> Element.Splitter(Orientation(random.nextInt(Orientation.COUNT)))
                RoutingElement.PRISM -> Element.Prism
                RoutingElement.FILTER -> Element.Filter(passableFilterColor(trace, cell))
                RoutingElement.PORTAL_PAIR -> return placePortalPair(cell)
            }
        return true
    }

    /** Filterfarbe = Komponente des ankommenden Strahls — nie Voll-Absorption (§3.3). */
    private fun passableFilterColor(
        trace: TraceResult,
        cell: HexCoord,
    ): LightColor {
        val components = trace.segments.first { it.to == cell }.color.components
        return components[random.nextInt(components.size)]
    }

    /** Portal auf dem Strahlweg, Zwilling auf beliebiger freier Zelle (§4.6). */
    private fun placePortalPair(cell: HexCoord): Boolean {
        val free = boardCells(radius).filter { it != cell && it !in elements }
        if (free.isEmpty()) return false
        val pairId = elements.values.count { it is Element.Portal } / 2
        elements[cell] = Element.Portal(pairId)
        elements[free[random.nextInt(free.size)]] = Element.Portal(pairId)
        return true
    }

    /** §9.3 Schritt 4: Kristalle an die Enden offener Strahlwege, dann Sollfarben-Fixup. */
    private fun placeCrystals(): Boolean {
        var placed = 0
        while (placed < params.crystalRange.last) {
            val trace = tracer.trace(Board(radius, elements))
            val ends = terminalCells(trace, elements, radius)
            if (ends.isEmpty()) break
            val cell = ends[random.nextInt(ends.size)]
            elements[cell] = Element.Crystal(trace.segments.first { it.to == cell }.color)
            placed += 1
        }
        return placed >= params.crystalRange.first && refreshCrystalColors()
    }

    /**
     * Sollfarbe = OR der TATSAECHLICH ankommenden Farben (§9.3/4) — spaeter
     * platzierte Kristalle koennen fruehere Strahlwege gekappt haben; ein
     * dadurch dunkler Kristall verwirft den Versuch (§9.5/3).
     */
    private fun refreshCrystalColors(): Boolean {
        val received = tracer.trace(Board(radius, elements)).received
        val crystalCells = elements.filterValues { it is Element.Crystal }.keys.toList()
        for (cell in crystalCells) {
            elements[cell] = Element.Crystal(received[cell] ?: return false)
        }
        return true
    }

    /** §9.3 Schritt 5: Deko-Waende nur auf strahlfreien Zellen, verifiziert per Trace-Vergleich. */
    private fun placeWalls(target: Int) {
        var trace = tracer.trace(Board(radius, elements))
        repeat(target) {
            if (elements.size >= maxOccupied) return
            val touched = trace.segments.flatMapTo(HashSet()) { listOf(it.from, it.to) }
            val candidates = boardCells(radius).filter { it !in elements && it !in touched }
            if (candidates.isEmpty()) return
            val cell = candidates[random.nextInt(candidates.size)]
            elements[cell] = Element.Wall
            val after = tracer.trace(Board(radius, elements))
            if (after.received == trace.received && after.solved == trace.solved) {
                trace = after
            } else {
                elements.remove(cell) // Deko-Kandidat verwerfen (§9.3/5)
            }
        }
    }

    /** §9.3 Schritt 6 + Kappen: Loesungs-Check, Farbbudget, Belegung ≤ 50 %. */
    private fun meetsQualityBars(solution: Board): Boolean =
        tracer.trace(solution).solved &&
            distinctColorCount() <= params.palette.maxColors &&
            elements.size <= maxOccupied

    private fun distinctColorCount(): Int =
        elements.values
            .mapNotNull { element ->
                when (element) {
                    is Element.Source -> element.color
                    is Element.Crystal -> element.required
                    is Element.Filter -> element.color
                    else -> null
                }
            }.distinct()
            .size
}

/**
 * Rueckwaerts-Verwuerfelung (Design §9.3 Schritte 7–8): zieht je drehbarem
 * Element einen Offset `o_i ∈ 0..5` mit `Σ o_i` im Par-Zielbereich, verwirft
 * geloeste Startzustaende (R35) und validiert den exakten Par (§9.4) gegen
 * den Zielbereich — hoechstens [MAX_SCRAMBLE_TRIES] Versuche je Loesung.
 */
internal class Scrambler(
    private val parRange: IntRange,
    private val tier: Difficulty,
    private val seed: Long,
    private val random: RandomSource,
) {
    private val tracer = DefaultTracer
    private val solver = ParSolver(tracer)

    fun scramble(solution: Board): GeneratedLevel? {
        val rotatables =
            solution.elements.entries
                .mapNotNull { (cell, element) -> (element as? Element.Rotatable)?.let { cell to it } }
                .sortedWith(compareBy({ it.first.q }, { it.first.r }))
        repeat(MAX_SCRAMBLE_TRIES) {
            val offsets = IntArray(rotatables.size) { random.nextInt(Orientation.COUNT) }
            candidate(solution, rotatables, offsets)?.let { return it }
        }
        return null
    }

    private fun candidate(
        solution: Board,
        rotatables: List<Pair<HexCoord, Element.Rotatable>>,
        offsets: IntArray,
    ): GeneratedLevel? {
        // Ueberlauf-Denkprobe: Σ o_i ≤ 8·5 = 40, weit unter Int.MAX_VALUE
        if (offsets.sum() !in parRange) return null
        val start = startBoard(solution, rotatables, offsets)
        val par = if (tracer.trace(start).solved) null else solver.minimalMoves(start)
        return if (par != null && par in parRange) {
            GeneratedLevel(LevelDefinition(start, par, tier, seed), solution)
        } else {
            null
        }
    }

    /** Startorientierung `m_start = (m_loesung − o) mod 6` (§9.3/7). */
    private fun startBoard(
        solution: Board,
        rotatables: List<Pair<HexCoord, Element.Rotatable>>,
        offsets: IntArray,
    ): Board {
        val scrambled =
            rotatables.mapIndexed { i, (cell, element) ->
                val start = (element.orientation.steps - offsets[i]).mod(Orientation.COUNT)
                cell to element.withOrientation(Orientation(start))
            }
        return solution.copy(elements = solution.elements + scrambled)
    }
}
