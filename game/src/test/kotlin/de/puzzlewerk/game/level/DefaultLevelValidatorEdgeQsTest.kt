package de.puzzlewerk.game.level

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.allCells
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

private const val FAR_AWAY_OFFSET = 1_000
private const val FAR_AWAY_COUNT = 20_000
private const val OUTER_RING_CELLS_PER_RADIUS = 6

/**
 * Unabhaengiger QS-Pass (PW-2.4-QS) gegen Design Paragraf 16_2 und R43,
 * hergeleitet aus Design-Dokument und LevelViolation-KDoc — NICHT aus der
 * Implementierungsstruktur. Schwerpunkte: Int-Extremkoordinaten BEIDER Achsen
 * (Faelle, die Long-Denken brauchen), riesige Elementmengen, alle
 * Portal-ID-Raender, exakte Verstoss-Multimengen statt "enthaelt",
 * Determinismus bei adversarialer Map-Einfuegereihenfolge und minimale
 * Reparaturen (kein Ueber-Melden).
 */
class DefaultLevelValidatorEdgeQsTest {
    private val source = HexCoord(-1, 0) to Element.Source(LightColor.WHITE, Direction.EAST)
    private val crystal = HexCoord(1, 0) to Element.Crystal(LightColor.WHITE)

    private fun level(
        board: Board,
        par: Int = 1,
    ): LevelDefinition = LevelDefinition(board, par, Difficulty.D1, seed = 4242L)

    private fun violationsOf(level: LevelDefinition): List<LevelViolation> =
        DefaultLevelValidator
            .validate(level)
            .shouldBeInstanceOf<LevelValidationResult.Invalid>()
            .violations

    private fun LevelDefinition.withElements(elements: Map<HexCoord, Element>): LevelDefinition =
        copy(board = board.copy(elements = elements))

    // --- Fuzzing an der Vertrauensgrenze (S4): Extremkoordinaten -------------

    @Test
    fun `Extremkoordinaten beider Achsen werden trotz Int-Ueberlauf exakt gemeldet (S4, R43)`() {
        // (MIN, MIN) und (MAX, MAX): q + r laeuft in Int ueber ((MIN, MIN) ergaebe
        // scheinbar das Brettzentrum); (MIN, 0): abs(q) laeuft ueber; (MIN, MAX)
        // ergibt q + r = -1 und (MAX, MIN + 1) exakt q + r = 0 — dort verraten
        // NUR |q| und |r| die Zelle. Alle acht muessen als ausserhalb gelten.
        val extremes =
            listOf(
                HexCoord(Int.MIN_VALUE, Int.MIN_VALUE),
                HexCoord(Int.MIN_VALUE, 0),
                HexCoord(Int.MIN_VALUE, Int.MAX_VALUE),
                HexCoord(0, Int.MIN_VALUE),
                HexCoord(0, Int.MAX_VALUE),
                HexCoord(Int.MAX_VALUE, Int.MIN_VALUE + 1),
                HexCoord(Int.MAX_VALUE, 0),
                HexCoord(Int.MAX_VALUE, Int.MAX_VALUE),
            )
        val board = Board(2, mapOf(source, crystal) + extremes.associateWith { Element.Wall })

        val violations = shouldNotThrowAny { violationsOf(level(board)) }

        violations shouldContainExactlyInAnyOrder extremes.map { LevelViolation.CoordOutsideBoard(it) }
    }

    @Test
    fun `Brettgrenze exakt - randvolle Bretter sind Valid, der erste Ring ausserhalb wird komplett gemeldet`() {
        for (radius in Board.MIN_RADIUS..Board.MAX_RADIUS) {
            // Kein Ueber-Melden: ein zu 100 Prozent belegtes Brett (R41-Konstruktion)
            // ist schema-valide — jede Randzelle liegt noch innerhalb.
            val inside = allCells(radius).filterNot { it == source.first || it == crystal.first }
            val fullBoard = level(Board(radius, mapOf(source, crystal) + inside.associateWith { Element.Wall }))
            DefaultLevelValidator.validate(fullBoard) shouldBe LevelValidationResult.Valid(fullBoard)

            // Kein Unter-Melden: JEDE Zelle des ersten Rings ausserhalb wird einzeln gemeldet.
            val ring = allCells(radius + 1).filterNot { it.isWithinRadius(radius) }
            ring shouldHaveSize OUTER_RING_CELLS_PER_RADIUS * (radius + 1)
            val ringBoard = level(Board(radius, mapOf(source, crystal) + ring.associateWith { Element.Wall }))
            violationsOf(ringBoard) shouldContainExactlyInAnyOrder ring.map { LevelViolation.CoordOutsideBoard(it) }
        }
    }

    @Test
    fun `Skalar-Extremwerte - Radius Int MAX und Par Int MIN werden exakt einmal gemeldet`() {
        // Zellen werden laut LevelViolation-KDoc gegen den DEKLARIERTEN Radius
        // geprueft: unter Radius Int.MAX_VALUE sind (-1, 0) und (1, 0) innerhalb.
        violationsOf(level(Board(Int.MAX_VALUE, mapOf(source, crystal)))) shouldContainExactly
            listOf(LevelViolation.RadiusOutOfRange(Int.MAX_VALUE))
        violationsOf(level(Board(2, mapOf(source, crystal)), par = Int.MIN_VALUE)) shouldContainExactly
            listOf(LevelViolation.ParOutOfRange(Int.MIN_VALUE))
    }

    // --- Riesige Elementmengen ------------------------------------------------

    @Test
    fun `riesige Elementmenge - 20000 Zellen ausserhalb werden ohne Ausnahme einzeln gemeldet`() {
        val farAway = (0 until FAR_AWAY_COUNT).map { HexCoord(FAR_AWAY_OFFSET + it, 0) }
        val board = Board(2, mapOf(source, crystal) + farAway.associateWith { Element.Wall })

        val violations = shouldNotThrowAny { violationsOf(level(board)) }

        violations.groupingBy { it }.eachCount() shouldBe
            farAway.associate { LevelViolation.CoordOutsideBoard(it) to 1 }
    }

    @Test
    fun `maximal befuelltes Brett mit 90 Quellen meldet exakt eine SourceCountOutOfRange(90)`() {
        val sources =
            allCells(Board.MAX_RADIUS)
                .filterNot { it == crystal.first }
                .associateWith { Element.Source(LightColor.RED, Direction.EAST) }

        violationsOf(level(Board(Board.MAX_RADIUS, sources + crystal))) shouldContainExactly
            listOf(LevelViolation.SourceCountOutOfRange(90))
    }

    // --- Diagnose-Vollstaendigkeit: exakte Multimengen -------------------------

    /** Radius-3-Brett, das ALLE Anzahlgrenzen gleichzeitig nach OBEN verletzt (Gegenstueck zu 0-Anzahlen). */
    private fun countOverflowLevel(): LevelDefinition {
        val cells = allCells(3).iterator()

        fun place(
            count: Int,
            factory: () -> Element,
        ): Map<HexCoord, Element> = (1..count).associate { cells.next() to factory() }

        val elements =
            place(4) { Element.Source(LightColor.RED, Direction.EAST) } +
                place(7) { Element.Crystal(LightColor.BLUE) } +
                place(9) { Element.Mirror(Orientation.ZERO) } +
                place(3) { Element.Portal(pairId = 0) } +
                place(1) { Element.Portal(pairId = 1) }
        return level(Board(3, elements), par = 15)
    }

    @Test
    fun `alle Anzahl-Obergrenzen gleichzeitig ueberschritten - exakte Multimenge, kein First-Fail`() {
        violationsOf(countOverflowLevel()) shouldContainExactlyInAnyOrder
            listOf(
                LevelViolation.SourceCountOutOfRange(4),
                LevelViolation.CrystalCountOutOfRange(7),
                LevelViolation.TooManyRotatables(9),
                LevelViolation.PortalNotPaired(pairId = 0, count = 3),
                LevelViolation.PortalNotPaired(pairId = 1, count = 1),
                LevelViolation.ParOutOfRange(15),
            )
    }

    @Test
    fun `alle Portal-ID-Raender inklusive Int MIN - je ID Bereichs- UND Paarigkeitsdiagnose`() {
        val badPortals =
            mapOf(
                HexCoord(0, 0) to Int.MIN_VALUE,
                HexCoord(0, 1) to -1,
                HexCoord(0, -1) to 2,
                HexCoord(1, -1) to Int.MAX_VALUE,
            )
        val goodPair =
            mapOf(
                HexCoord(-1, 1) to Element.Portal(pairId = 1),
                HexCoord(1, 1) to Element.Portal(pairId = 1),
            )
        val elements = mapOf(source, crystal) + badPortals.mapValues { (_, id) -> Element.Portal(id) } + goodPair

        violationsOf(level(Board(2, elements))) shouldContainExactlyInAnyOrder
            badPortals.map { (coord, id) -> LevelViolation.PortalPairIdOutOfRange(coord, id) } +
            badPortals.values.map { LevelViolation.PortalNotPaired(pairId = it, count = 1) }
    }

    // --- Determinismus und kein Ueber-Melden -----------------------------------

    @Test
    fun `adversariale Map-Einfuegereihenfolge - byte-gleiche Violation-Liste`() {
        val entries =
            listOf<Pair<HexCoord, Element>>(
                HexCoord(4, 0) to Element.Wall,
                HexCoord(0, 4) to Element.Wall,
                HexCoord(0, 0) to Element.Filter(LightColor.YELLOW),
                HexCoord(0, 1) to Element.Filter(LightColor.WHITE),
                HexCoord(0, -1) to Element.Portal(pairId = 1),
                source,
                crystal,
            )
        val forward = level(Board(2, linkedMapOf(*entries.toTypedArray())))
        val backward = level(Board(2, linkedMapOf(*entries.reversed().toTypedArray())))

        // Map-Gleichheit ignoriert die Einfuegereihenfolge: dieselbe LevelDefinition ...
        backward shouldBe forward
        // ... muss dieselbe Violation-LISTE liefern (inklusive Reihenfolge), auch bei Wiederholung.
        DefaultLevelValidator.validate(backward) shouldBe DefaultLevelValidator.validate(forward)
        DefaultLevelValidator.validate(forward) shouldBe DefaultLevelValidator.validate(forward)
    }

    /** Genau vier unabhaengige Einzelverstoesse: Koordinate, Filterfarbe, Portal-Paarigkeit, Par. */
    private fun brokenLevel(): LevelDefinition =
        level(
            Board(
                2,
                mapOf(
                    source,
                    crystal,
                    HexCoord(3, 0) to Element.Wall,
                    HexCoord(0, 0) to Element.Filter(LightColor.YELLOW),
                    HexCoord(0, 1) to Element.Portal(pairId = 0),
                ),
            ),
            par = 15,
        )

    @Test
    fun `minimale Reparatur entfernt GENAU die zugehoerige Violation und keine andere`() {
        val broken = brokenLevel()
        val before = violationsOf(broken).groupingBy { it }.eachCount()
        val repairs: Map<LevelViolation, LevelDefinition> =
            mapOf(
                LevelViolation.ParOutOfRange(15) to broken.copy(par = 14),
                LevelViolation.CoordOutsideBoard(HexCoord(3, 0)) to
                    broken.withElements(broken.board.elements - HexCoord(3, 0)),
                LevelViolation.FilterNotPrimary(HexCoord(0, 0), LightColor.YELLOW) to
                    broken.withElements(broken.board.elements + (HexCoord(0, 0) to Element.Filter(LightColor.RED))),
                LevelViolation.PortalNotPaired(pairId = 0, count = 1) to
                    broken.withElements(broken.board.elements + (HexCoord(1, -1) to Element.Portal(pairId = 0))),
            )

        // Jede Reparatur adressiert einen tatsaechlich gemeldeten Verstoss ...
        before.keys shouldContainExactlyInAnyOrder repairs.keys
        // ... und entfernt AUSSCHLIESSLICH ihn (Multimengen-Differenz).
        repairs.forEach { (repaired, repairedLevel) ->
            violationsOf(repairedLevel).groupingBy { it }.eachCount() shouldBe (before - repaired)
        }
    }
}
