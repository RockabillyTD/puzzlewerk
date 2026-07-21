package de.puzzlewerk.app.ui.game

import app.cash.turbine.test
import de.puzzlewerk.app.audio.FakeAudioEngine
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.progress.FakeProgressRepository
import de.puzzlewerk.data.settings.FakeSettingsRepository
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.engine.GameEngine
import de.puzzlewerk.game.engine.defaultGameEngine
import de.puzzlewerk.game.generator.DefaultLevelGenerator
import de.puzzlewerk.game.generator.LevelGenerator
import de.puzzlewerk.game.score.DefaultScoreCalculator
import de.puzzlewerk.game.score.ScoreCalculator
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Reine JVM-Tests (ADR-009: kein Robolectric für ViewModels) mit
 * StandardTestDispatcher, Turbine und den echten `:game`-Defaults auf festen
 * Kampagnen-Seeds — keine Zeit-/Zufallsabhängigkeit außerhalb der injizierten
 * Abstraktionen. Feste Fakten der genutzten Level (deterministisch generiert):
 *
 * - Level 1: par 3, zwei Spiegel; das Rotieren allein von [ROT_CELL_L1]
 *   (mit dem zweiten Spiegel im Startzustand) löst das Brett NIE — ideal für
 *   Undo-/Reset-Fälle ohne versehentliches Lösen.
 * - Level 4: par 1, ein Spiegel [SOLVE_CELL_L4]; ein einziger Tap löst (★★★).
 */
@OptIn(ExperimentalCoroutinesApi::class) // setMain/runCurrent: Standardweg laut ADR-009-Test-Stack
class GameViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val engine: GameEngine = defaultGameEngine(DefaultTracer)
    private val generator: LevelGenerator = DefaultLevelGenerator
    private val scoreCalculator: ScoreCalculator = DefaultScoreCalculator

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
    ): GameViewModel =
        GameViewModel(
            request = request,
            engine = engine,
            generator = generator,
            scoreCalculator = scoreCalculator,
            progressRepository = repository,
            audio = GameAudioChoreographer(FakeAudioEngine(), FakeSettingsRepository()),
            dispatcher = dispatcher,
        )

    @Test
    fun `startet im Ladezustand`() {
        val vm = viewModel(LevelRequest.Campaign(1))

        assertTrue(vm.state.value.isLoading)
        assertNull(vm.state.value.board)
    }

    @Test
    fun `Level 1 laden liefert Brett, moves 0 und par`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))

            runCurrent()

            val state = vm.state.value
            assertFalse(state.isLoading)
            assertEquals(0, state.moves)
            assertEquals(PAR_L1, state.par)
            assertNotNull(state.board)
            assertTrue(state.board!!.cells.isNotEmpty())
            assertNull(state.result)
        }

    @Test
    fun `Tap auf drehbares Element erhoeht moves und aendert das Brett`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            val before = vm.state.value.board

            vm.onIntent(GameIntent.TapCell(ROT_CELL_L1))

            val state = vm.state.value
            assertEquals(1, state.moves)
            assertNotEquals(before, state.board)
            assertNull(state.result)
            assertTrue(state.canUndo)
        }

    @Test
    fun `Tap auf leere Zelle laesst State unveraendert und meldet InvalidMove`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            val emptyCell = vm.state.value.board!!.cells.first { it.element == null }.coord
            val before = vm.state.value

            vm.effects.test {
                vm.onIntent(GameIntent.TapCell(emptyCell))

                assertEquals(GameEffect.InvalidMove, awaitItem())
                assertEquals(before, vm.state.value)
            }
        }

    @Test
    fun `Level 4 mit einem Tap loesen setzt Score und speichert Kampagnenergebnis`() =
        runTest(dispatcher) {
            val repository = FakeProgressRepository()
            val vm = viewModel(LevelRequest.Campaign(4), repository)
            runCurrent()

            vm.onIntent(GameIntent.TapCell(SOLVE_CELL_L4))
            runCurrent() // recordSolved-Coroutine ausführen

            val result = vm.state.value.result
            assertNotNull(result)
            assertEquals(3, result!!.stars)
            assertEquals(THREE_STAR_POINTS, result.points)
            assertEquals(1, result.moves)
            assertEquals(PAR_L4, result.par)
            assertFalse(vm.state.value.canUndo) // Gelöst sperrt Undo (§6.2/R32)

            val stored = repository.progress.first()
            assertTrue(stored is DataResult.Success)
            val best = (stored as DataResult.Success).value.bestByLevel
            assertEquals(3, best[4]?.stars)
        }

    @Test
    fun `Undo senkt moveCount wieder auf 0`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            vm.onIntent(GameIntent.TapCell(ROT_CELL_L1))
            assertEquals(1, vm.state.value.moves)

            vm.onIntent(GameIntent.Undo)

            assertEquals(0, vm.state.value.moves)
            assertFalse(vm.state.value.canUndo)
        }

    @Test
    fun `Reset unter 5 Zuegen setzt direkt zurueck`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            repeat(2) { vm.onIntent(GameIntent.TapCell(ROT_CELL_L1)) }
            assertEquals(2, vm.state.value.moves)

            vm.onIntent(GameIntent.Reset)

            assertFalse(vm.state.value.pendingResetConfirm)
            assertEquals(0, vm.state.value.moves)
        }

    @Test
    fun `Reset ab 5 Zuegen verlangt erst Bestaetigung, dann ConfirmReset`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            repeat(5) { vm.onIntent(GameIntent.TapCell(ROT_CELL_L1)) }
            assertEquals(5, vm.state.value.moves)

            vm.onIntent(GameIntent.Reset)
            assertTrue(vm.state.value.pendingResetConfirm)
            assertEquals(5, vm.state.value.moves) // noch nicht zurückgesetzt

            vm.onIntent(GameIntent.ConfirmReset)
            assertFalse(vm.state.value.pendingResetConfirm)
            assertEquals(0, vm.state.value.moves)
        }

    @Test
    fun `DismissReset bricht die Bestaetigung ab und behaelt die Zuege`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(1))
            runCurrent()
            repeat(5) { vm.onIntent(GameIntent.TapCell(ROT_CELL_L1)) }
            vm.onIntent(GameIntent.Reset)

            vm.onIntent(GameIntent.DismissReset)

            assertFalse(vm.state.value.pendingResetConfirm)
            assertEquals(5, vm.state.value.moves)
        }

    @Test
    fun `Speicherfehler beim Loesen meldet SaveFailed`() =
        runTest(dispatcher) {
            val repository = FakeProgressRepository()
            repository.failWith(PersistenceFailure.Io("voll"))
            val vm = viewModel(LevelRequest.Campaign(4), repository)
            runCurrent()

            vm.effects.test {
                vm.onIntent(GameIntent.TapCell(SOLVE_CELL_L4))
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is GameEffect.SaveFailed)
            }
        }

    @Test
    fun `Reset auf geloestem Brett haengt nicht`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(4))
            runCurrent()
            vm.onIntent(GameIntent.TapCell(SOLVE_CELL_L4))
            assertNotNull(vm.state.value.result) // gelöst (§6.3/R32)
            val solvedState = vm.state.value

            vm.onIntent(GameIntent.Reset)

            // Kein hängender Bestätigungsdialog, State unverändert (PW-3.5a-Fix).
            assertFalse(vm.state.value.pendingResetConfirm)
            assertEquals(solvedState, vm.state.value)
        }

    @Test
    fun `Tap auf geloestes Brett meldet keinen InvalidMove und laesst State unveraendert`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(4))
            runCurrent()
            vm.onIntent(GameIntent.TapCell(SOLVE_CELL_L4))
            val solvedState = vm.state.value

            vm.effects.test {
                vm.onIntent(GameIntent.TapCell(SOLVE_CELL_L4)) // R32: gelöst lehnt jeden Zug ab
                expectNoEvents()
                assertEquals(solvedState, vm.state.value)
            }
        }

    @Test
    fun `Replay startet dieselbe Partie frisch`() =
        runTest(dispatcher) {
            val vm = viewModel(LevelRequest.Campaign(4))
            runCurrent()
            vm.onIntent(GameIntent.TapCell(SOLVE_CELL_L4))
            assertNotNull(vm.state.value.result)

            vm.onIntent(GameIntent.Replay)

            assertNull(vm.state.value.result) // Overlay weg
            assertEquals(0, vm.state.value.moves)
            assertFalse(vm.state.value.isLoading)
            assertNotNull(vm.state.value.board)
        }

    private companion object {
        val ROT_CELL_L1 = HexCoord(q = 1, r = 0)
        val SOLVE_CELL_L4 = HexCoord(q = 1, r = -1)
        const val PAR_L1 = 3
        const val PAR_L4 = 1
        const val THREE_STAR_POINTS = 1500
    }
}
