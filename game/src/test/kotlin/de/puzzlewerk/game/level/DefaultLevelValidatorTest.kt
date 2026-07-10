package de.puzzlewerk.game.level

import de.puzzlewerk.game.DreiFarbenLevel
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.DefaultTracer
import de.puzzlewerk.game.trace.allCells
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * Unit-Tests fuer den DefaultLevelValidator gegen ALLE Regeln aus Design
 * Paragraf 16.2 (Vertrauensgrenze S4, Randfall R43): jede der neun
 * LevelViolation-Faelle wird einzeln provoziert, Grenzwerte beidseitig
 * geprueft, und korrupte Eingaben duerfen NIE werfen — Fehler sind Werte.
 */
class DefaultLevelValidatorTest {
    private val source = HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST)
    private val crystal = HexCoord(2, 0) to Element.Crystal(LightColor.WHITE)

    /** Radius-2-Brett mit einer Quelle und einem Kristall plus [extra]-Elementen. */
    private fun boardOf(
        radius: Int = 2,
        vararg extra: Pair<HexCoord, Element>,
    ): Board = Board(radius, mapOf(source, crystal, *extra))

    private fun level(
        board: Board,
        par: Int = 1,
    ): LevelDefinition = LevelDefinition(board, par, Difficulty.D1, seed = 42L)

    private fun violationsOf(level: LevelDefinition): List<LevelViolation> =
        DefaultLevelValidator
            .validate(level)
            .shouldBeInstanceOf<LevelValidationResult.Invalid>()
            .violations

    // --- Golden & Valid-Faelle ---------------------------------------------

    @Test
    fun `Golden - Drei-Farben-Level aus Paragraf 7_3 ist Valid`() {
        val threeColors = LevelDefinition(DreiFarbenLevel.board(), par = 2, tier = Difficulty.D1, seed = 7L)

        DefaultLevelValidator.validate(threeColors) shouldBe LevelValidationResult.Valid(threeColors)
    }

    @Test
    fun `Valid reicht das Level unveraendert durch`() {
        val candidate = level(boardOf())

        val result = DefaultLevelValidator.validate(candidate).shouldBeInstanceOf<LevelValidationResult.Valid>()

        result.level shouldBe candidate
    }

    @Test
    fun `Radius-Grenzwerte 2 und 5 sind gueltig`() {
        DefaultLevelValidator.validate(level(boardOf(radius = 2))) shouldBe
            LevelValidationResult.Valid(level(boardOf(radius = 2)))
        DefaultLevelValidator.validate(level(boardOf(radius = 5))) shouldBe
            LevelValidationResult.Valid(level(boardOf(radius = 5)))
    }

    // --- Regel 1: Radius und Koordinaten -----------------------------------

    @Test
    fun `RadiusOutOfRange - Radius 6 wird gemeldet, Zellen innerhalb bleiben unbeanstandet`() {
        violationsOf(level(boardOf(radius = 6))) shouldContainExactly
            listOf(LevelViolation.RadiusOutOfRange(6))
    }

    @Test
    fun `RadiusOutOfRange - Radius 1 meldet zusaetzlich alle Zellen ausserhalb`() {
        violationsOf(level(boardOf(radius = 1))) shouldContainExactly
            listOf(
                LevelViolation.RadiusOutOfRange(1),
                LevelViolation.CoordOutsideBoard(HexCoord(-2, 0)),
                LevelViolation.CoordOutsideBoard(HexCoord(2, 0)),
            )
    }

    @Test
    fun `CoordOutsideBoard - Zellen ausserhalb des Sechsecks werden einzeln und sortiert gemeldet`() {
        val board =
            boardOf(
                radius = 2,
                HexCoord(3, 0) to Element.Wall,
                // Diagonale (2, 1): max-Norm |q+r| = 3 > 2 — ausserhalb trotz |q|, |r| kleiner-gleich 2
                HexCoord(2, 1) to Element.Wall,
            )

        violationsOf(level(board)) shouldContainExactly
            listOf(
                LevelViolation.CoordOutsideBoard(HexCoord(2, 1)),
                LevelViolation.CoordOutsideBoard(HexCoord(3, 0)),
            )
    }

    @Test
    fun `CoordOutsideBoard - Int-MIN-Koordinate wird trotz abs-Ueberlauf erkannt (Denkprobe PW-2_3-B1)`() {
        // HexCoord.ringIndex liefert fuer q = Int.MIN_VALUE wegen abs-Ueberlauf 0 („Zentrum");
        // der Validator muss die Zelle dennoch als ausserhalb melden (Vertrauensgrenze S4).
        val board = boardOf(radius = 2, HexCoord(Int.MIN_VALUE, 0) to Element.Wall)

        violationsOf(level(board)) shouldContainExactly
            listOf(LevelViolation.CoordOutsideBoard(HexCoord(Int.MIN_VALUE, 0)))
    }

    // --- Regel 3: Anzahlen ---------------------------------------------------

    @Test
    fun `SourceCountOutOfRange - 0 Quellen werden gemeldet`() {
        violationsOf(level(Board(2, mapOf(crystal)))) shouldContainExactly
            listOf(LevelViolation.SourceCountOutOfRange(0))
    }

    @Test
    fun `SourceCountOutOfRange - 4 Quellen werden gemeldet, 3 sind gueltig`() {
        val threeMore =
            arrayOf(
                HexCoord(0, -2) to Element.Source(LightColor.RED, Direction.SOUTH_EAST),
                HexCoord(0, 2) to Element.Source(LightColor.GREEN, Direction.NORTH_EAST),
                HexCoord(2, -2) to Element.Source(LightColor.BLUE, Direction.WEST),
            )

        violationsOf(level(boardOf(2, *threeMore))) shouldContainExactly
            listOf(LevelViolation.SourceCountOutOfRange(4))
        DefaultLevelValidator.validate(level(boardOf(2, *threeMore.copyOfRange(0, 2))))
            .shouldBeInstanceOf<LevelValidationResult.Valid>()
    }

    @Test
    fun `CrystalCountOutOfRange - 0 Kristalle werden gemeldet (kristallloses Brett waere vakuum-solved)`() {
        val board = Board(2, mapOf(source))

        // Dokumentation des Risikos: ohne Kristalle meldet trace solved == true (Paragraf 5_4,
        // leere Allquantifizierung) — genau deshalb MUSS der Validator MIN_CRYSTALS erzwingen.
        DefaultTracer.trace(board).solved.shouldBeTrue()
        violationsOf(level(board)) shouldContainExactly
            listOf(LevelViolation.CrystalCountOutOfRange(0))
    }

    @Test
    fun `CrystalCountOutOfRange - 7 Kristalle werden gemeldet, 6 sind gueltig`() {
        val sixMore =
            arrayOf(
                HexCoord(0, 0) to Element.Crystal(LightColor.RED),
                HexCoord(1, 0) to Element.Crystal(LightColor.GREEN),
                HexCoord(0, 1) to Element.Crystal(LightColor.BLUE),
                HexCoord(1, -1) to Element.Crystal(LightColor.YELLOW),
                HexCoord(0, -1) to Element.Crystal(LightColor.CYAN),
                HexCoord(-1, 0) to Element.Crystal(LightColor.MAGENTA),
            )

        violationsOf(level(boardOf(2, *sixMore))) shouldContainExactly
            listOf(LevelViolation.CrystalCountOutOfRange(7))
        DefaultLevelValidator.validate(level(boardOf(2, *sixMore.copyOfRange(0, 5))))
            .shouldBeInstanceOf<LevelValidationResult.Valid>()
    }

    @Test
    fun `TooManyRotatables - 9 drehbare Elemente werden gemeldet, 8 sind gueltig`() {
        val nine =
            allCells(2)
                .filterNot { it == source.first || it == crystal.first }
                .take(9)
                .mapIndexed { index, coord ->
                    val orientation = Orientation(index % Orientation.COUNT)
                    coord to if (index % 2 == 0) Element.Mirror(orientation) else Element.Splitter(orientation)
                }.toTypedArray()

        violationsOf(level(boardOf(2, *nine))) shouldContainExactly
            listOf(LevelViolation.TooManyRotatables(9))
        DefaultLevelValidator.validate(level(boardOf(2, *nine.copyOfRange(0, 8))))
            .shouldBeInstanceOf<LevelValidationResult.Valid>()
    }

    // --- Regel 3: Portale -----------------------------------------------------

    @Test
    fun `PortalPairIdOutOfRange - vollstaendiges Paar mit ID 2 meldet beide Zellen, keine Paarigkeitsklage`() {
        val board =
            boardOf(
                2,
                HexCoord(0, 0) to Element.Portal(pairId = 2),
                HexCoord(0, 1) to Element.Portal(pairId = 2),
            )

        violationsOf(level(board)) shouldContainExactly
            listOf(
                LevelViolation.PortalPairIdOutOfRange(HexCoord(0, 0), 2),
                LevelViolation.PortalPairIdOutOfRange(HexCoord(0, 1), 2),
            )
    }

    @Test
    fun `PortalPairIdOutOfRange - einzelnes Portal mit negativer ID meldet Bereich UND fehlenden Zwilling`() {
        val board = boardOf(2, HexCoord(0, 1) to Element.Portal(pairId = -1))

        violationsOf(level(board)) shouldContainExactly
            listOf(
                LevelViolation.PortalPairIdOutOfRange(HexCoord(0, 1), -1),
                LevelViolation.PortalNotPaired(pairId = -1, count = 1),
            )
    }

    @Test
    fun `PortalNotPaired - einzelnes Portal wird gemeldet`() {
        violationsOf(level(boardOf(2, HexCoord(0, 1) to Element.Portal(pairId = 0)))) shouldContainExactly
            listOf(LevelViolation.PortalNotPaired(pairId = 0, count = 1))
    }

    @Test
    fun `PortalNotPaired - dreifach belegte Paar-ID wird gemeldet`() {
        val board =
            boardOf(
                2,
                HexCoord(0, 0) to Element.Portal(pairId = 1),
                HexCoord(0, 1) to Element.Portal(pairId = 1),
                HexCoord(0, -1) to Element.Portal(pairId = 1),
            )

        violationsOf(level(board)) shouldContainExactly
            listOf(LevelViolation.PortalNotPaired(pairId = 1, count = 3))
    }

    @Test
    fun `zwei vollstaendige Portalpaare mit IDs 0 und 1 sind gueltig`() {
        val board =
            boardOf(
                2,
                HexCoord(0, 0) to Element.Portal(pairId = 0),
                HexCoord(0, 1) to Element.Portal(pairId = 0),
                HexCoord(1, 0) to Element.Portal(pairId = 1),
                HexCoord(0, -1) to Element.Portal(pairId = 1),
            )

        DefaultLevelValidator.validate(level(board)).shouldBeInstanceOf<LevelValidationResult.Valid>()
    }

    @Test
    fun `Tracer-Restrisiko - Portal ohne Zwilling im Strahlweg crasht den Tracer, der Validator faengt es`() {
        // Exakt die Konstruktion aus dem Tracer-QS-Restrisiko: die Quelle schiesst
        // direkt in ein ungepaartes Portal — die Zwillingssuche wirft. :data ruft
        // deshalb IMMER zuerst den Validator (ADR-004, S4); Invalid-Level erreichen
        // den Tracer nie.
        val board = boardOf(2, HexCoord(0, 0) to Element.Portal(pairId = 0))

        shouldThrow<NoSuchElementException> { DefaultTracer.trace(board) }
        violationsOf(level(board)) shouldContainExactly
            listOf(LevelViolation.PortalNotPaired(pairId = 0, count = 1))
    }

    // --- Regel 4: Filterfarbe und Par ----------------------------------------

    @Test
    fun `FilterNotPrimary - Sekundaer- und Tertiaerfarben werden gemeldet`() {
        violationsOf(level(boardOf(2, HexCoord(0, 0) to Element.Filter(LightColor.YELLOW)))) shouldContainExactly
            listOf(LevelViolation.FilterNotPrimary(HexCoord(0, 0), LightColor.YELLOW))
        violationsOf(level(boardOf(2, HexCoord(0, 1) to Element.Filter(LightColor.WHITE)))) shouldContainExactly
            listOf(LevelViolation.FilterNotPrimary(HexCoord(0, 1), LightColor.WHITE))
    }

    @Test
    fun `Primaerfarben-Filter Rot Gruen Blau sind gueltig`() {
        val board =
            boardOf(
                2,
                HexCoord(0, 0) to Element.Filter(LightColor.RED),
                HexCoord(0, 1) to Element.Filter(LightColor.GREEN),
                HexCoord(0, -1) to Element.Filter(LightColor.BLUE),
            )

        DefaultLevelValidator.validate(level(board)).shouldBeInstanceOf<LevelValidationResult.Valid>()
    }

    @Test
    fun `ParOutOfRange - 0 und 15 werden gemeldet, Grenzwerte 1 und 14 sind gueltig`() {
        violationsOf(level(boardOf(), par = 0)) shouldContainExactly listOf(LevelViolation.ParOutOfRange(0))
        violationsOf(level(boardOf(), par = 15)) shouldContainExactly listOf(LevelViolation.ParOutOfRange(15))
        DefaultLevelValidator.validate(level(boardOf(), par = 1))
            .shouldBeInstanceOf<LevelValidationResult.Valid>()
        DefaultLevelValidator.validate(level(boardOf(), par = 14))
            .shouldBeInstanceOf<LevelValidationResult.Valid>()
    }

    // --- Vollstaendige Sammlung statt First-Fail ------------------------------

    /** Ein Level, das ALLE neun Verstoßtypen aus Paragraf 16_2 gleichzeitig verletzt. */
    private fun corruptOnAllNineRules(): LevelDefinition {
        val mirrors = allCells(2).take(9).map { it to Element.Mirror(Orientation.ZERO) }
        val rest =
            mapOf(
                HexCoord(7, 0) to Element.Wall,
                HexCoord(1, 1) to Element.Portal(pairId = 5),
                HexCoord(2, 2) to Element.Filter(LightColor.CYAN),
            )
        return LevelDefinition(
            board = Board(radius = 6, elements = mirrors.toMap() + rest),
            par = 0,
            tier = Difficulty.D7,
            seed = 0L,
        )
    }

    @Test
    fun `alle neun Verstosstypen werden in EINEM Durchlauf gesammelt (kein First-Fail)`() {
        val violations = shouldNotThrowAny { violationsOf(corruptOnAllNineRules()) }

        violations shouldContainExactlyInAnyOrder
            listOf(
                LevelViolation.RadiusOutOfRange(6),
                LevelViolation.CoordOutsideBoard(HexCoord(7, 0)),
                LevelViolation.SourceCountOutOfRange(0),
                LevelViolation.CrystalCountOutOfRange(0),
                LevelViolation.TooManyRotatables(9),
                LevelViolation.PortalPairIdOutOfRange(HexCoord(1, 1), 5),
                LevelViolation.PortalNotPaired(pairId = 5, count = 1),
                LevelViolation.FilterNotPrimary(HexCoord(2, 2), LightColor.CYAN),
                LevelViolation.ParOutOfRange(0),
            )
    }

    @Test
    fun `extreme Zahlenwerte werfen nie, sondern liefern Invalid (R43, S4)`() {
        val extreme =
            LevelDefinition(
                board =
                    Board(
                        radius = Int.MIN_VALUE,
                        elements =
                            mapOf(
                                HexCoord(Int.MAX_VALUE, Int.MAX_VALUE) to Element.Wall,
                                HexCoord(Int.MIN_VALUE, Int.MIN_VALUE) to Element.Portal(Int.MIN_VALUE),
                            ),
                    ),
                par = Int.MAX_VALUE,
                tier = Difficulty.D1,
                seed = Long.MIN_VALUE,
            )

        val violations = shouldNotThrowAny { violationsOf(extreme) }

        violations shouldContainExactlyInAnyOrder
            listOf(
                LevelViolation.RadiusOutOfRange(Int.MIN_VALUE),
                LevelViolation.CoordOutsideBoard(HexCoord(Int.MAX_VALUE, Int.MAX_VALUE)),
                LevelViolation.CoordOutsideBoard(HexCoord(Int.MIN_VALUE, Int.MIN_VALUE)),
                LevelViolation.SourceCountOutOfRange(0),
                LevelViolation.CrystalCountOutOfRange(0),
                LevelViolation.PortalPairIdOutOfRange(HexCoord(Int.MIN_VALUE, Int.MIN_VALUE), Int.MIN_VALUE),
                LevelViolation.PortalNotPaired(pairId = Int.MIN_VALUE, count = 1),
                LevelViolation.ParOutOfRange(Int.MAX_VALUE),
            )
    }
}
