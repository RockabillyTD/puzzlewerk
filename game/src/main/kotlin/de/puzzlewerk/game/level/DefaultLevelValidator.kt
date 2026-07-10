package de.puzzlewerk.game.level

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.element.Element
import kotlin.math.abs

/**
 * Referenz-Implementierung des [LevelValidator]-Vertrags nach Design §16.2
 * (Vertrauensgrenze, Regel S4): prüft eine [LevelDefinition] gegen ALLE
 * Schema-Regeln und sammelt JEDEN Verstoß — vollständige Diagnose statt
 * First-Fail. Wirft niemals; korrupte Eingaben sind Werte (R43, Regel C3).
 *
 * Ein [LevelValidationResult.Valid]-Level ist insbesondere Tracer-sicher:
 * jedes Portal ist vollständig gepaart (die Zwillingssuche des Tracers kann
 * nicht fehlschlagen) und es existiert mindestens ein Kristall (kein Brett,
 * das leer-quantifiziert als „gelöst" gälte, §5.4).
 *
 * Deterministische Verstoß-Reihenfolge: Regelreihenfolge aus §16.2, innerhalb
 * einer Regel aufsteigend nach (q, dann r) bzw. nach Paar-ID.
 *
 * Die Koordinatenprüfung rechnet in Long-Arithmetik: `HexCoord.ringIndex`
 * liefe für `|q|` nahe `Int.MIN_VALUE` über (abs-Überlauf) und würde eine
 * manipulierte Zelle fälschlich als innerhalb einwerten — an der
 * Vertrauensgrenze ist das nicht akzeptabel (Denkprobe PW-2.3-B1).
 */
public object DefaultLevelValidator : LevelValidator {
    /** Jede verwendete Portal-Paar-ID muss auf genau zwei Zellen liegen (§4.6, §16.2/3). */
    private const val PORTAL_TWIN_COUNT: Int = 2

    private val byCoord = compareBy<HexCoord>({ it.q }, { it.r })

    override fun validate(level: LevelDefinition): LevelValidationResult {
        val violations =
            radiusViolations(level.board.radius) +
                coordViolations(level.board) +
                countViolations(level.board) +
                portalViolations(level.board) +
                filterViolations(level.board) +
                parViolations(level.par)
        return if (violations.isEmpty()) {
            LevelValidationResult.Valid(level)
        } else {
            LevelValidationResult.Invalid(violations)
        }
    }

    /** §16.2/1: `radius ∈ 2..5`. */
    private fun radiusViolations(radius: Int): List<LevelViolation> =
        if (radius in Board.MIN_RADIUS..Board.MAX_RADIUS) {
            emptyList()
        } else {
            listOf(LevelViolation.RadiusOutOfRange(radius))
        }

    /** §16.2/1: jede belegte Zelle erfüllt `max(|q|, |r|, |q+r|) ≤ radius` — geprüft gegen den ANGEGEBENEN Radius. */
    private fun coordViolations(board: Board): List<LevelViolation> =
        board.elements.keys
            .filterNot { it.isWithinRadiusOverflowSafe(board.radius) }
            .sortedWith(byCoord)
            .map { LevelViolation.CoordOutsideBoard(it) }

    /** Long-Arithmetik gegen abs-/Additions-Überlauf bei manipulierten Koordinaten (S4). */
    private fun HexCoord.isWithinRadiusOverflowSafe(radius: Int): Boolean {
        val longQ = q.toLong()
        val longR = r.toLong()
        return maxOf(abs(longQ), abs(longR), abs(longQ + longR)) <= radius
    }

    /** §16.2/3: Quellen 1..3, Kristalle 1..6, drehbare Elemente ≤ 8. */
    private fun countViolations(board: Board): List<LevelViolation> =
        buildList {
            val sources = board.elements.values.count { it is Element.Source }
            if (sources !in LevelDefinition.MIN_SOURCES..LevelDefinition.MAX_SOURCES) {
                add(LevelViolation.SourceCountOutOfRange(sources))
            }
            val crystals = board.elements.values.count { it is Element.Crystal }
            if (crystals !in LevelDefinition.MIN_CRYSTALS..LevelDefinition.MAX_CRYSTALS) {
                add(LevelViolation.CrystalCountOutOfRange(crystals))
            }
            val rotatables = board.elements.values.count { it.isRotatable }
            if (rotatables > LevelDefinition.MAX_ROTATABLES) {
                add(LevelViolation.TooManyRotatables(rotatables))
            }
        }

    /**
     * §16.2/3: Paar-IDs ∈ 0..1 und JEDE verwendete ID liegt auf genau zwei Zellen.
     * Die Paarigkeit wird auch für bereichsfremde IDs gemeldet (vollständige
     * Diagnose) — genau ein ungepaartes Portal ist die Konstruktion, die die
     * Zwillingssuche des Tracers crashen würde (Tracer-QS-Restrisiko, R43).
     */
    private fun portalViolations(board: Board): List<LevelViolation> {
        val portals =
            board.elements.entries
                .mapNotNull { (coord, element) -> (element as? Element.Portal)?.let { coord to it.pairId } }
                .sortedWith(compareBy(byCoord) { it.first })
        val outOfRange =
            portals
                .filterNot { (_, pairId) -> pairId in 0 until LevelDefinition.MAX_PORTAL_PAIRS }
                .map { (coord, pairId) -> LevelViolation.PortalPairIdOutOfRange(coord, pairId) }
        val unpaired =
            portals
                .groupingBy { (_, pairId) -> pairId }
                .eachCount()
                .filterValues { it != PORTAL_TWIN_COUNT }
                .entries
                .sortedBy { it.key }
                .map { (pairId, count) -> LevelViolation.PortalNotPaired(pairId, count) }
        return outOfRange + unpaired
    }

    /** §16.2/4: Filterfarben nur primär (Rot, Grün, Blau — §3.3). */
    private fun filterViolations(board: Board): List<LevelViolation> =
        board.elements.entries
            .mapNotNull { (coord, element) -> (element as? Element.Filter)?.let { coord to it.color } }
            .filterNot { (_, color) -> color.isPrimary }
            .sortedWith(compareBy(byCoord) { it.first })
            .map { (coord, color) -> LevelViolation.FilterNotPrimary(coord, color) }

    /** §16.2/4: `par ∈ 1..14`. */
    private fun parViolations(par: Int): List<LevelViolation> =
        if (par in LevelDefinition.MIN_PAR..LevelDefinition.MAX_PAR) {
            emptyList()
        } else {
            listOf(LevelViolation.ParOutOfRange(par))
        }
}
