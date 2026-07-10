package de.puzzlewerk.game.level

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.trace.DefaultTracer
import de.puzzlewerk.game.trace.allCells
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.random.Random

private const val COLOR_MASKS = 7
private const val ROTATABLE_OVERFLOW = 9
private const val INVALID_RADIUS = 6
private const val INVALID_PAIR_ID = 2

/**
 * Property-Tests fuer den DefaultLevelValidator (Ticket PW-2.4): zufaellige
 * SCHEMA-VALIDE Level (fester Seed) muessen Valid liefern UND Tracer-sicher
 * sein; gezielt einzeln verletzte Level muessen GENAU die erwartete Violation
 * liefern, mehrfach verletzte ALLE erwarteten (Paragraf 16_2, S4, R43).
 */
class DefaultLevelValidatorPropertyTest {
    private val primaries = listOf(LightColor.RED, LightColor.GREEN, LightColor.BLUE)

    private fun randomColor(random: Random): LightColor = LightColor(1 + random.nextInt(COLOR_MASKS))

    private fun randomRotatable(random: Random): Element {
        val orientation = Orientation(random.nextInt(Orientation.COUNT))
        return if (random.nextBoolean()) Element.Mirror(orientation) else Element.Splitter(orientation)
    }

    private fun randomPassive(random: Random): Element =
        when (random.nextInt(3)) {
            0 -> Element.Prism
            1 -> Element.Filter(primaries.random(random))
            else -> Element.Wall
        }

    /**
     * Schema-valides Zufallslevel nach Paragraf 16_2. [minRadius] = 3 fuer
     * Mutations-Properties, die freie Zellen als Spielraum brauchen
     * (Radius 3 hat 37 Zellen, belegt sind hoechstens 24).
     */
    private fun arbValidLevel(minRadius: Int = Board.MIN_RADIUS): Arb<LevelDefinition> =
        arbitrary { rs ->
            val random = rs.random
            val radius = minRadius + random.nextInt(Board.MAX_RADIUS - minRadius + 1)
            val free = allCells(radius).shuffled(random).toMutableList()
            val elements = mutableMapOf<HexCoord, Element>()

            fun place(
                count: Int,
                factory: () -> Element,
            ) = repeat(count) { elements[free.removeLast()] = factory() }

            place(1 + random.nextInt(LevelDefinition.MAX_SOURCES)) {
                Element.Source(randomColor(random), Direction.entries.random(random))
            }
            place(1 + random.nextInt(LevelDefinition.MAX_CRYSTALS)) { Element.Crystal(randomColor(random)) }
            for (pairId in 0 until LevelDefinition.MAX_PORTAL_PAIRS) {
                if (random.nextBoolean()) place(2) { Element.Portal(pairId) }
            }
            place(random.nextInt(minOf(LevelDefinition.MAX_ROTATABLES, free.size) + 1)) {
                randomRotatable(random)
            }
            place(random.nextInt(minOf(3, free.size) + 1)) { randomPassive(random) }
            LevelDefinition(
                board = Board(radius, elements),
                par = LevelDefinition.MIN_PAR + random.nextInt(LevelDefinition.MAX_PAR),
                tier = Difficulty.entries.random(random),
                seed = random.nextLong(),
            )
        }

    /** Freie Brettzellen aufsteigend nach (q, r) — deterministische Mutationsziele. */
    private fun freeCells(board: Board): List<HexCoord> =
        allCells(board.radius).filterNot { board.elements.containsKey(it) }

    private fun LevelDefinition.withElements(elements: Map<HexCoord, Element>): LevelDefinition =
        copy(board = board.copy(elements = elements))

    private fun violationsOf(level: LevelDefinition): List<LevelViolation> =
        DefaultLevelValidator
            .validate(level)
            .shouldBeInstanceOf<LevelValidationResult.Invalid>()
            .violations

    // --- Valide Level ---------------------------------------------------------

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `jedes schema-valide Zufallslevel ist Valid und Tracer-sicher (Paragraf 16_2, S4)`() {
        // Block-Body statt Expression-Body: eine @Test-Methode mit Rueckgabewert
        // (PropertyContext aus checkAll) wuerde JUnit Jupiter STILL ignorieren.
        runBlocking {
            checkAll(PropTestConfig(seed = 0x50573234), arbValidLevel()) { level ->
                DefaultLevelValidator.validate(level) shouldBe LevelValidationResult.Valid(level)
                // Valid impliziert Tracer-sicher: gepaarte Portale (keine
                // NoSuchElementException in der Zwillingssuche), Kristalle vorhanden.
                shouldNotThrowAny { DefaultTracer.trace(level.board) }
            }
        }
    }

    // --- Gezielte Einzelverletzungen: genau EINE erwartete Violation -----------

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Mutation Par 0 - genau ParOutOfRange`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 1L), arbValidLevel()) { level ->
                violationsOf(level.copy(par = 0)) shouldContainExactly
                    listOf(LevelViolation.ParOutOfRange(0))
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Mutation Radius 6 - genau RadiusOutOfRange`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 2L), arbValidLevel()) { level ->
                val mutated = level.copy(board = level.board.copy(radius = INVALID_RADIUS))

                violationsOf(mutated) shouldContainExactly
                    listOf(LevelViolation.RadiusOutOfRange(INVALID_RADIUS))
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Mutation alle Quellen entfernt - genau SourceCountOutOfRange 0`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 3L), arbValidLevel()) { level ->
                val mutated = level.withElements(level.board.elements.filterValues { it !is Element.Source })

                violationsOf(mutated) shouldContainExactly
                    listOf(LevelViolation.SourceCountOutOfRange(0))
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Mutation alle Kristalle entfernt - genau CrystalCountOutOfRange 0, Brett waere vakuum-solved`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 4L), arbValidLevel()) { level ->
                val mutated = level.withElements(level.board.elements.filterValues { it !is Element.Crystal })

                violationsOf(mutated) shouldContainExactly
                    listOf(LevelViolation.CrystalCountOutOfRange(0))
                // Dokumentation des Restrisikos: ohne Validator gaelte das Brett als geloest.
                DefaultTracer.trace(mutated.board).solved.shouldBeTrue()
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Mutation Wand ausserhalb des Bretts - genau CoordOutsideBoard`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 5L), arbValidLevel()) { level ->
                val outside = HexCoord(level.board.radius + 1, 0)
                val mutated = level.withElements(level.board.elements + (outside to Element.Wall))

                violationsOf(mutated) shouldContainExactly
                    listOf(LevelViolation.CoordOutsideBoard(outside))
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Mutation Aufstockung auf 9 Drehbare - genau TooManyRotatables`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 6L), arbValidLevel(minRadius = 3)) { level ->
                val missing = ROTATABLE_OVERFLOW - level.board.elements.values.count { it.isRotatable }
                val added = freeCells(level.board).take(missing).associateWith { Element.Mirror(Orientation.ZERO) }
                val mutated = level.withElements(level.board.elements + added)

                violationsOf(mutated) shouldContainExactly
                    listOf(LevelViolation.TooManyRotatables(ROTATABLE_OVERFLOW))
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Mutation Portal-Zwilling entfernt oder Einzelportal ergaenzt - genau PortalNotPaired`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 7L), arbValidLevel(minRadius = 3)) { level ->
                val portals =
                    level.board.elements.entries
                        .filter { it.value is Element.Portal }
                        .sortedWith(compareBy({ it.key.q }, { it.key.r }))
                val (mutated, pairId) =
                    if (portals.isEmpty()) {
                        val cell = freeCells(level.board).first()
                        level.withElements(level.board.elements + (cell to Element.Portal(pairId = 0))) to 0
                    } else {
                        val victim = portals.first()
                        level.withElements(level.board.elements - victim.key) to
                            (victim.value as Element.Portal).pairId
                    }

                violationsOf(mutated) shouldContainExactly
                    listOf(LevelViolation.PortalNotPaired(pairId = pairId, count = 1))
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Mutation Portalpaar mit ID 2 - genau zwei PortalPairIdOutOfRange`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 8L), arbValidLevel(minRadius = 3)) { level ->
                val (first, second) = freeCells(level.board).take(2)
                val mutated =
                    level.withElements(
                        level.board.elements +
                            mapOf(
                                first to Element.Portal(INVALID_PAIR_ID),
                                second to Element.Portal(INVALID_PAIR_ID),
                            ),
                    )

                violationsOf(mutated) shouldContainExactly
                    listOf(
                        LevelViolation.PortalPairIdOutOfRange(first, INVALID_PAIR_ID),
                        LevelViolation.PortalPairIdOutOfRange(second, INVALID_PAIR_ID),
                    )
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Mutation Gelb-Filter - genau FilterNotPrimary`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 9L), arbValidLevel(minRadius = 3)) { level ->
                val cell = freeCells(level.board).first()
                val mutated = level.withElements(level.board.elements + (cell to Element.Filter(LightColor.YELLOW)))

                violationsOf(mutated) shouldContainExactly
                    listOf(LevelViolation.FilterNotPrimary(cell, LightColor.YELLOW))
            }
        }
    }

    // --- Mehrfachverletzung: ALLE Verstoesse werden gesammelt -------------------

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `drei gleichzeitige Mutationen - ALLE erwarteten Violations, kein First-Fail`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 10L), arbValidLevel(minRadius = 3)) { level ->
                val cell = freeCells(level.board).first()
                val mutated =
                    level
                        .copy(par = 0)
                        .withElements(
                            level.board.elements.filterValues { it !is Element.Source } +
                                (cell to Element.Filter(LightColor.WHITE)),
                        )

                violationsOf(mutated) shouldContainExactlyInAnyOrder
                    listOf(
                        LevelViolation.SourceCountOutOfRange(0),
                        LevelViolation.FilterNotPrimary(cell, LightColor.WHITE),
                        LevelViolation.ParOutOfRange(0),
                    )
            }
        }
    }
}
