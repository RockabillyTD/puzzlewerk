package de.puzzlewerk.game.level

import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.engine.Move
import de.puzzlewerk.game.engine.MoveResult
import de.puzzlewerk.game.engine.defaultGameEngine
import de.puzzlewerk.game.trace.DefaultTracer
import de.puzzlewerk.game.trace.allCells
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

private const val COLOR_MASKS = 7
private const val PORTAL_TWINS = 2
private const val MOVES_PER_GAME = 40
private const val ENGINE_ITERATIONS = 300
private const val EXTREME_PROBABILITY_ONE_IN = 4

/** Zulaessige Filterfarben nach Paragraf 16_2/4: ausschliesslich Primaerfarben. */
private val primaries = listOf(LightColor.RED, LightColor.GREEN, LightColor.BLUE)

private val extremeInts = listOf(Int.MIN_VALUE, Int.MIN_VALUE + 1, -1, Int.MAX_VALUE - 1, Int.MAX_VALUE)
private val garbagePairIds = listOf(Int.MIN_VALUE, -1, 0, 1, 2, Int.MAX_VALUE)

/** Hex-Norm `max(abs(q), abs(r), abs(q+r))` in Long — unabhaengig von HexCoord.ringIndex (Paragraf 2_1). */
private fun hexNormOf(coord: HexCoord): Long =
    maxOf(abs(coord.q.toLong()), abs(coord.r.toLong()), abs(coord.q.toLong() + coord.r.toLong()))

/**
 * Unabhaengiges Orakel: erwartete Verstoss-MULTIMENGE einer beliebigen
 * LevelDefinition, Regel fuer Regel direkt aus Design Paragraf 16_2 und der
 * LevelViolation-KDoc uebersetzt (Zellen gelten gegen den DEKLARIERTEN Radius).
 */
private fun expectedViolationsOf(level: LevelDefinition): List<LevelViolation> =
    buildList {
        if (level.board.radius !in Board.MIN_RADIUS..Board.MAX_RADIUS) {
            add(LevelViolation.RadiusOutOfRange(level.board.radius))
        }
        level.board.elements.keys
            .filter { hexNormOf(it) > level.board.radius }
            .forEach { add(LevelViolation.CoordOutsideBoard(it)) }
        addAll(expectedCountViolationsOf(level.board.elements.values))
        addAll(expectedPortalViolationsOf(level.board.elements))
        level.board.elements.forEach { (coord, element) ->
            if (element is Element.Filter && element.color !in primaries) {
                add(LevelViolation.FilterNotPrimary(coord, element.color))
            }
        }
        if (level.par !in LevelDefinition.MIN_PAR..LevelDefinition.MAX_PAR) {
            add(LevelViolation.ParOutOfRange(level.par))
        }
    }

private fun expectedCountViolationsOf(elements: Collection<Element>): List<LevelViolation> =
    buildList {
        val sources = elements.count { it is Element.Source }
        if (sources !in LevelDefinition.MIN_SOURCES..LevelDefinition.MAX_SOURCES) {
            add(LevelViolation.SourceCountOutOfRange(sources))
        }
        val crystals = elements.count { it is Element.Crystal }
        if (crystals !in LevelDefinition.MIN_CRYSTALS..LevelDefinition.MAX_CRYSTALS) {
            add(LevelViolation.CrystalCountOutOfRange(crystals))
        }
        // Unabhaengig hergeleitet: drehbar sind in V1 genau Spiegel und Splitter (Paragraf 4).
        val rotatables = elements.count { it is Element.Mirror || it is Element.Splitter }
        if (rotatables > LevelDefinition.MAX_ROTATABLES) {
            add(LevelViolation.TooManyRotatables(rotatables))
        }
    }

private fun expectedPortalViolationsOf(elements: Map<HexCoord, Element>): List<LevelViolation> {
    val portals = elements.entries.mapNotNull { (coord, e) -> (e as? Element.Portal)?.let { coord to it.pairId } }
    val outOfRange =
        portals
            .filter { (_, pairId) -> pairId !in 0 until LevelDefinition.MAX_PORTAL_PAIRS }
            .map { (coord, pairId) -> LevelViolation.PortalPairIdOutOfRange(coord, pairId) }
    val unpaired =
        portals
            .groupBy { (_, pairId) -> pairId }
            .filterValues { it.size != PORTAL_TWINS }
            .map { (pairId, twins) -> LevelViolation.PortalNotPaired(pairId, twins.size) }
    return outOfRange + unpaired
}

// --- Generatoren (feste Seeds ueber PropTestConfig) ----------------------------

private fun garbageScalar(
    random: Random,
    range: IntRange,
): Int = if (random.nextInt(EXTREME_PROBABILITY_ONE_IN) == 0) extremeInts.random(random) else range.random(random)

private fun garbageCoordinate(random: Random): Int = garbageScalar(random, -8..8)

private fun garbageElement(random: Random): Element =
    when (random.nextInt(8)) {
        0 -> Element.Source(LightColor(1 + random.nextInt(COLOR_MASKS)), Direction.entries.random(random))
        1 -> Element.Mirror(Orientation(random.nextInt(Orientation.COUNT)))
        2 -> Element.Splitter(Orientation(random.nextInt(Orientation.COUNT)))
        3 -> Element.Prism
        4 -> Element.Filter(LightColor(1 + random.nextInt(COLOR_MASKS)))
        5 -> Element.Portal(garbagePairIds.random(random))
        6 -> Element.Crystal(LightColor(1 + random.nextInt(COLOR_MASKS)))
        else -> Element.Wall
    }

/** Absurde LevelDefinitions weit jenseits des Schemas — der Validator darf NIE werfen (S4, R43). */
private fun arbGarbageLevel(): Arb<LevelDefinition> =
    arbitrary { rs ->
        val random = rs.random
        val elements =
            buildMap {
                repeat(random.nextInt(41)) {
                    put(HexCoord(garbageCoordinate(random), garbageCoordinate(random)), garbageElement(random))
                }
            }
        LevelDefinition(
            board = Board(radius = garbageScalar(random, -2..8), elements = elements),
            par = garbageScalar(random, -2..17),
            tier = Difficulty.entries.random(random),
            seed = random.nextLong(),
        )
    }

private fun validElementsFor(
    random: Random,
    radius: Int,
): Map<HexCoord, Element> {
    val cells = allCells(radius).shuffled(random).iterator()
    return buildMap {
        repeat(1 + random.nextInt(LevelDefinition.MAX_SOURCES)) {
            val color = LightColor(1 + random.nextInt(COLOR_MASKS))
            put(cells.next(), Element.Source(color, Direction.entries.random(random)))
        }
        repeat(1 + random.nextInt(LevelDefinition.MAX_CRYSTALS)) {
            put(cells.next(), Element.Crystal(LightColor(1 + random.nextInt(COLOR_MASKS))))
        }
        repeat(random.nextInt(LevelDefinition.MAX_ROTATABLES + 1)) {
            val orientation = Orientation(random.nextInt(Orientation.COUNT))
            put(cells.next(), if (random.nextBoolean()) Element.Mirror(orientation) else Element.Splitter(orientation))
        }
        repeat(if (random.nextBoolean()) PORTAL_TWINS else 0) { put(cells.next(), Element.Portal(pairId = 0)) }
        repeat(if (random.nextBoolean()) PORTAL_TWINS else 0) { put(cells.next(), Element.Portal(pairId = 1)) }
        repeat(random.nextInt(4)) {
            val passives = listOf(Element.Prism, Element.Wall, Element.Filter(primaries.random(random)))
            put(cells.next(), passives.random(random))
        }
    }
}

/**
 * Eigenes schema-valides Zufallslevel nach Paragraf 16_2 (unabhaengig vom
 * Generator der Bestandssuite). Radius 3..5: garantiert freie Zellen fuer
 * Mutationen (hoechstens 24 von 37 Zellen belegt).
 */
private fun arbQsValidLevel(): Arb<LevelDefinition> =
    arbitrary { rs ->
        val random = rs.random
        val radius = random.nextInt(3, Board.MAX_RADIUS + 1)
        LevelDefinition(
            board = Board(radius, validElementsFor(random, radius)),
            par = (LevelDefinition.MIN_PAR..LevelDefinition.MAX_PAR).random(random),
            tier = Difficulty.entries.random(random),
            seed = random.nextLong(),
        )
    }

private fun violationsOf(level: LevelDefinition): List<LevelViolation> =
    DefaultLevelValidator
        .validate(level)
        .shouldBeInstanceOf<LevelValidationResult.Invalid>()
        .violations

private fun LevelDefinition.withBoardElements(elements: Map<HexCoord, Element>): LevelDefinition =
    copy(board = board.copy(elements = elements))

/** Gezielte Einzelmutationen eines validen Levels mit unabhaengig erwarteter Verstoss-Multimenge. */
private fun mutationsOf(
    level: LevelDefinition,
    freeCell: HexCoord,
): List<Pair<LevelDefinition, List<LevelViolation>>> {
    val outside = HexCoord(level.board.radius + 1, 0)
    return listOf(
        level.copy(par = 0) to listOf(LevelViolation.ParOutOfRange(0)),
        level.copy(par = 15) to listOf(LevelViolation.ParOutOfRange(15)),
        level.copy(board = level.board.copy(radius = 6)) to listOf(LevelViolation.RadiusOutOfRange(6)),
        level.withBoardElements(level.board.elements + (outside to Element.Wall)) to
            listOf(LevelViolation.CoordOutsideBoard(outside)),
        level.withBoardElements(level.board.elements + (freeCell to Element.Filter(LightColor.CYAN))) to
            listOf(LevelViolation.FilterNotPrimary(freeCell, LightColor.CYAN)),
        level.withBoardElements(level.board.elements + (freeCell to Element.Portal(Int.MIN_VALUE))) to
            listOf(
                LevelViolation.PortalPairIdOutOfRange(freeCell, Int.MIN_VALUE),
                LevelViolation.PortalNotPaired(pairId = Int.MIN_VALUE, count = 1),
            ),
    )
}

private fun randomMove(random: Random): Move =
    when (random.nextInt(8)) {
        0 -> Move.Undo
        1 -> Move.Reset
        else -> Move.Rotate(HexCoord(random.nextInt(-7, 8), random.nextInt(-7, 8)))
    }

/**
 * Unabhaengiger QS-Pass (PW-2.4-QS), Vertragsseite: Fuzzing gegen ein
 * eigenstaendig aus Paragraf 16_2 uebersetztes Orakel (exakte Multimenge, nie
 * werfen), Determinismus unter permutierter Map-Einfuegereihenfolge,
 * Rundreise valide -> Einzelmutation -> exakte Diagnose -> Rueckbau -> Valid,
 * sowie das Kernversprechen "Valid impliziert Tracer- UND Engine-sicher".
 */
class DefaultLevelValidatorContractQsTest {
    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Fuzzing - absurde LevelDefinitions werfen nie und liefern exakt die Orakel-Multimenge (S4, R43)`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 0x5057_3234_0001L), arbGarbageLevel()) { level ->
                val result = shouldNotThrowAny { DefaultLevelValidator.validate(level) }
                val expected = expectedViolationsOf(level)
                when (result) {
                    is LevelValidationResult.Valid -> {
                        expected shouldBe emptyList()
                        result.level shouldBe level
                    }
                    is LevelValidationResult.Invalid -> result.violations shouldContainExactlyInAnyOrder expected
                }
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Determinismus - permutierte Map-Einfuegereihenfolge liefert byte-gleiche Ergebnisse`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 0x5057_3234_0002L), arbGarbageLevel()) { level ->
                val random = Random(level.seed)
                val reordered =
                    level.withBoardElements(
                        level.board.elements.entries
                            .shuffled(random)
                            .associate { (coord, element) -> coord to element },
                    )

                reordered shouldBe level
                DefaultLevelValidator.validate(reordered) shouldBe DefaultLevelValidator.validate(level)
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Rundreise - Einzelmutation meldet exakt das Erwartete, Rueckbau ist wieder Valid (fester Seed)`() {
        runBlocking {
            checkAll(PropTestConfig(seed = 0x5057_3234_0003L), arbQsValidLevel()) { level ->
                val freeCell = allCells(level.board.radius).first { it !in level.board.elements.keys }
                for ((mutated, expected) in mutationsOf(level, freeCell)) {
                    violationsOf(mutated) shouldContainExactlyInAnyOrder expected
                    // Rueckbau: die unveraenderte Ursprungsdefinition ist (weiterhin) Valid.
                    DefaultLevelValidator.validate(level) shouldBe LevelValidationResult.Valid(level)
                }
            }
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `Vertrag - Valid impliziert Engine-Sicherheit - newGame und zufaellige Zuege werfen nie`() {
        val engine = defaultGameEngine(DefaultTracer)
        runBlocking {
            checkAll(
                PropTestConfig(seed = 0x5057_3234_0004L, iterations = ENGINE_ITERATIONS),
                arbQsValidLevel(),
            ) { level ->
                DefaultLevelValidator.validate(level).shouldBeInstanceOf<LevelValidationResult.Valid>()
                val random = Random(level.seed)
                shouldNotThrowAny {
                    var state = engine.newGame(level).state
                    repeat(MOVES_PER_GAME) {
                        when (val result = engine.applyMove(state, randomMove(random))) {
                            is MoveResult.Applied -> state = result.state
                            is MoveResult.Invalid -> Unit
                        }
                    }
                }
            }
        }
    }
}
