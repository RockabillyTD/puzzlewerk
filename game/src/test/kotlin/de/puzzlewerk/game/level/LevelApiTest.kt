package de.puzzlewerk.game.level

import de.puzzlewerk.core.mix64
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class LevelApiTest {
    private val level =
        LevelDefinition(
            board =
                Board(
                    radius = 2,
                    elements =
                        mapOf(
                            HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                            HexCoord(0, 0) to Element.Mirror(Orientation(5)),
                            HexCoord(2, 0) to Element.Crystal(LightColor.WHITE),
                        ),
                ),
            par = 2,
            tier = Difficulty.D1,
            seed = 42L,
        )

    @Test
    fun `LevelDefinition ist ein Wert mit struktureller Gleichheit`() {
        level shouldBe level.copy()
        level.par shouldBe 2
        level.tier shouldBe Difficulty.D1
        level.seed shouldBe 42L
    }

    @Test
    fun `bindende Wertebereiche aus Paragraf 16_2 sind als Konstanten verfuegbar`() {
        LevelDefinition.MIN_PAR shouldBe 1
        LevelDefinition.MAX_PAR shouldBe 14
        LevelDefinition.MIN_SOURCES shouldBe 1
        LevelDefinition.MAX_SOURCES shouldBe 3
        LevelDefinition.MIN_CRYSTALS shouldBe 1
        LevelDefinition.MAX_CRYSTALS shouldBe 6
        LevelDefinition.MAX_ROTATABLES shouldBe 8
        LevelDefinition.MAX_PORTAL_PAIRS shouldBe 2
    }

    @Test
    fun `Wochenstaffel Mo bis So entspricht D1 bis D7 (Paragraf 10_2)`() {
        (1..7).map { Difficulty.forIsoDayOfWeek(it) } shouldBe Difficulty.entries.toList()
    }

    @Test
    fun `Wochentage ausserhalb 1 bis 7 sind Programmierfehler`() {
        shouldThrow<IllegalArgumentException> { Difficulty.forIsoDayOfWeek(0) }
        shouldThrow<IllegalArgumentException> { Difficulty.forIsoDayOfWeek(8) }
    }

    @Test
    fun `campaignSeed folgt der Formel aus Paragraf 11_1`() {
        // Golden-Werte aus unabhaengiger Referenzimplementierung (PW-2.1)
        campaignSeed(1) shouldBe -3224116255038447501L
        campaignSeed(2) shouldBe 5344741445836217946L
        campaignSeed(50) shouldBe 3861887887411408224L
        // Formel-Gegenprobe: mix64(n xor "LEVEL")
        campaignSeed(7) shouldBe mix64(7L xor 0x4C4556454CL)
    }

    @Test
    fun `campaignSeed ausserhalb 1 bis 50 ist ein Programmierfehler`() {
        CAMPAIGN_LEVEL_COUNT shouldBe 50
        shouldThrow<IllegalArgumentException> { campaignSeed(0) }
        shouldThrow<IllegalArgumentException> { campaignSeed(51) }
    }

    @Test
    fun `Validierungsergebnisse sind sealed Werte (R43)`() {
        val valid: LevelValidationResult = LevelValidationResult.Valid(level)
        val invalid: LevelValidationResult =
            LevelValidationResult.Invalid(
                listOf(
                    LevelViolation.RadiusOutOfRange(9),
                    LevelViolation.CoordOutsideBoard(HexCoord(9, 9)),
                    LevelViolation.SourceCountOutOfRange(0),
                    LevelViolation.CrystalCountOutOfRange(7),
                    LevelViolation.TooManyRotatables(9),
                    LevelViolation.PortalPairIdOutOfRange(HexCoord(0, 0), 2),
                    LevelViolation.PortalNotPaired(pairId = 0, count = 1),
                    LevelViolation.FilterNotPrimary(HexCoord(1, 0), LightColor.YELLOW),
                    LevelViolation.ParOutOfRange(0),
                ),
            )

        (valid as LevelValidationResult.Valid).level shouldBe level
        (invalid as LevelValidationResult.Invalid).violations.shouldNotBeEmpty()
        invalid.violations.size shouldBe 9
    }

    @Test
    fun `LevelValidator ist als fun interface implementierbar`() {
        val alwaysValid = LevelValidator { candidate -> LevelValidationResult.Valid(candidate) }

        alwaysValid.validate(level) shouldBe LevelValidationResult.Valid(level)
    }
}
