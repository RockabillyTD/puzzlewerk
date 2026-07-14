package de.puzzlewerk.app.ui.game

import app.cash.turbine.test
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.progress.FakeProgressRepository
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.engine.GameEngine
import de.puzzlewerk.game.engine.defaultGameEngine
import de.puzzlewerk.game.generator.DefaultLevelGenerator
import de.puzzlewerk.game.generator.LevelGenerator
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.score.DefaultScoreCalculator
import de.puzzlewerk.game.trace.DefaultTracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unabhängiger QS-Pass (PW-3.7-QS): Randfälle des [GameViewModel], die die
 * Entwickler-Tests (GameViewModelTest) nicht abdecken — Undo auf leerem
 * Verlauf (R28), Intents im Ladezustand (§9.4/§12.3), die exakte
 * Reset-Bestätigungsschwelle (§12.3: ab ≥ 5 Zügen), ConfirmReset ohne offenen
 * Dialog, die volle Undo-Kette (§6.2, I10) und die Phase-3-Eingrenzung
 * „nur Kampagne speichert" für Daily-Partien (§10, ui-architektur §3).
 *
 * Reine JVM-Tests (ADR-009), deterministisch: StandardTestDispatcher, feste
 * Kampagnen-Seeds bzw. ein injizierter Fake-Generator; keine Wallclock.
 */
@OptIn(ExperimentalCoroutinesApi::class) // setMain/runCurrent: Standardweg laut ADR-009-Test-Stack
class GameViewModelQsTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine: GameEngine = defaultGameEngine(DefaultTracer)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        request: LevelRequest,
        repository: FakeProgressRepository = FakeProgressRepository(),
        generator: LevelGenerator = DefaultLevelGenerator,
    ): GameViewModel =
        GameViewModel(
            request = request,
            engine = engine,
            generator = generator,
            scoreCalculator = DefaultScoreCalculator,
            progressRepository = repository,
            dispatcher = dispatcher,
        )

    /** R28/§6.2: Undo bei leerem Verlauf ⇒ `Invalid(VerlaufLeer)` — sanftes Feedback, State unverändert. */
    @Test
    fun `Undo bei leerem Verlauf meldet InvalidMove und laesst den State unveraendert`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            val before = vm.state.value
            assertEquals(0, before.moves)

            vm.effects.test {
                vm.onIntent(GameIntent.Undo)

                assertEquals(GameEffect.InvalidMove, awaitItem())
                assertEquals(before, vm.state.value)
            }
        }

    /** §9.4/§12.3: Im Ladezustand (Level wird generiert) sind alle Eingaben wirkungslos — kein Crash, kein Effect. */
    @Test
    fun `Intents im Ladezustand sind wirkungslos und stoeren das Laden nicht`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            // Bewusst KEIN runCurrent: das Level ist noch nicht generiert.
            assertTrue(vm.state.value.isLoading)

            vm.effects.test {
                vm.onIntent(GameIntent.TapCell(HexCoord(0, 0)))
                vm.onIntent(GameIntent.Undo)
                vm.onIntent(GameIntent.Reset)
                vm.onIntent(GameIntent.ConfirmReset)
                vm.onIntent(GameIntent.Replay)

                expectNoEvents()
                assertTrue(vm.state.value.isLoading)
                assertFalse(vm.state.value.pendingResetConfirm)
            }

            runCurrent() // Laden abschließen: frische Partie, nichts aus der Ladephase wirkt nach.
            val loaded = vm.state.value
            assertFalse(loaded.isLoading)
            assertEquals(0, loaded.moves)
            assertNotNull(loaded.board)
        }

    /** §12.3: Die Bestätigungsschwelle ist GENAU ≥ 5 — mit 4 Zügen setzt Reset direkt zurück, ohne Dialog. */
    @Test
    fun `Reset mit genau 4 Zuegen setzt ohne Bestaetigung direkt zurueck`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            repeat(4) { vm.onIntent(GameIntent.TapCell(ROT_CELL_L1)) }
            assertEquals(4, vm.state.value.moves)

            vm.onIntent(GameIntent.Reset)

            assertFalse(vm.state.value.pendingResetConfirm)
            assertEquals(0, vm.state.value.moves)
        }

    /** §12.3 (Race Doppel-Tap/verspäteter Intent): ConfirmReset OHNE offenen Dialog darf nicht zurücksetzen. */
    @Test
    fun `ConfirmReset ohne offenen Dialog ist wirkungslos`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            repeat(2) { vm.onIntent(GameIntent.TapCell(ROT_CELL_L1)) }
            assertEquals(2, vm.state.value.moves)

            vm.onIntent(GameIntent.ConfirmReset)

            assertEquals(2, vm.state.value.moves) // kein heimlicher Reset
            assertFalse(vm.state.value.pendingResetConfirm)
        }

    /** §6.2/I10: Undo ∘ Rotate = Identität — volle Undo-Kette stellt Brett und Zähler des Starts wieder her. */
    @Test
    fun `volle Undo-Kette stellt das Startbrett wieder her`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            val startBoard = vm.state.value.board

            repeat(3) { vm.onIntent(GameIntent.TapCell(ROT_CELL_L1)) }
            assertEquals(3, vm.state.value.moves)
            repeat(3) { vm.onIntent(GameIntent.Undo) }

            assertEquals(0, vm.state.value.moves)
            assertFalse(vm.state.value.canUndo)
            assertEquals(startBoard, vm.state.value.board)
        }

    /** §10/§12.3 (Phase-3-Scope): Eine gelöste DAILY-Partie schreibt KEINEN Kampagnenfortschritt. */
    @Test
    fun `geloeste Daily-Partie speichert keinen Kampagnenfortschritt`() =
        runTest(dispatcher) {
            val repository = FakeProgressRepository()
            val vm =
                viewModel(
                    request = LevelRequest.Daily(FIXED_EPOCH_DAY),
                    repository = repository,
                    generator = LevelGenerator { _, _ -> tinySolvableLevel() },
                )
            runCurrent()

            vm.effects.test {
                vm.onIntent(GameIntent.TapCell(MIRROR_CELL)) // eine Drehung löst (par 1)
                runCurrent()

                expectNoEvents() // insbesondere kein SaveFailed
            }
            val result = vm.state.value.result
            assertNotNull(result)
            assertEquals(3, result!!.stars)

            val stored = repository.progress.first()
            assertTrue(stored is DataResult.Success)
            assertTrue((stored as DataResult.Success).value.bestByLevel.isEmpty())
        }

    private companion object {
        /** Drehbarer Spiegel in Kampagnenlevel 1 (fester Seed); Drehen allein löst NIE (siehe GameViewModelTest). */
        val ROT_CELL_L1 = HexCoord(q = 1, r = 0)

        /** Spiegel des Fake-Levels [tinySolvableLevel]. */
        val MIRROR_CELL = HexCoord(0, 0)

        /** Festes Daily-Datum (deterministisch, keine Wallclock). */
        const val FIXED_EPOCH_DAY = 20_000L

        /**
         * „Spiegelweg"-Miniatur (§9.5/7) wie im E2eSmokeTest: Quelle Weiß (−2,0)→O,
         * drehbarer Spiegel (0,0) mit Start m=5, Kristall Weiß (2,0); genau EINE
         * Drehung (5→0, Parallelfall) löst ⇒ par 1.
         */
        fun tinySolvableLevel(): LevelDefinition =
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
                par = 1,
                tier = Difficulty.D1,
                seed = 0L,
            )
    }
}
