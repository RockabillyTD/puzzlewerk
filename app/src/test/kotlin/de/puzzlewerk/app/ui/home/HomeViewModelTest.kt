package de.puzzlewerk.app.ui.home

import app.cash.turbine.test
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.WriteResult
import de.puzzlewerk.data.progress.CampaignProgress
import de.puzzlewerk.data.progress.ProgressRepository
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT
import de.puzzlewerk.game.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Reine JVM-Tests (ADR-009: kein Robolectric für ViewModels). */
@OptIn(ExperimentalCoroutinesApi::class) // setMain/runCurrent: Standardweg laut ADR-009-Test-Stack
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeProgressRepository(
        initial: DataResult<CampaignProgress> = DataResult.Success(CampaignProgress.EMPTY),
    ) : ProgressRepository {
        val results = MutableStateFlow(initial)
        override val progress: Flow<DataResult<CampaignProgress>> = results

        override suspend fun recordSolved(
            levelNumber: Int,
            result: Score,
        ): WriteResult = WriteResult.Success

        override suspend fun reset(): WriteResult = WriteResult.Success
    }

    private fun progressOf(vararg solvedLevels: Int): DataResult<CampaignProgress> =
        DataResult.Success(CampaignProgress(solvedLevels.toList().associateWith { Score(points = 1200, stars = 2) }))

    @Test
    fun `beginnt im Ladezustand`() {
        val viewModel = HomeViewModel(FakeProgressRepository())

        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `Erststart fuehrt Weiter zu Level 1`() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(FakeProgressRepository())

            runCurrent()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(ContinueTarget.Level(1), viewModel.state.value.continueTarget)
        }

    @Test
    fun `Weiter zielt auf das niedrigste ungeloeste Level`() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(FakeProgressRepository(progressOf(1, 2, 5)))

            runCurrent()

            assertEquals(ContinueTarget.Level(3), viewModel.state.value.continueTarget)
        }

    @Test
    fun `alles geloest fuehrt Weiter zur Levelauswahl`() =
        runTest(dispatcher) {
            val all = (1..CAMPAIGN_LEVEL_COUNT).toList().toIntArray()
            val viewModel = HomeViewModel(FakeProgressRepository(progressOf(*all)))

            runCurrent()

            assertEquals(ContinueTarget.AllSolved, viewModel.state.value.continueTarget)
        }

    @Test
    fun `Persistenzfehler wird zum definierten Fehlerzustand`() =
        runTest(dispatcher) {
            val failure = DataResult.Failure(PersistenceFailure.Corrupted("kaputt"))
            val viewModel = HomeViewModel(FakeProgressRepository(failure))

            runCurrent()

            assertTrue(viewModel.state.value.hasLoadError)
            assertFalse(viewModel.state.value.isLoading)
        }

    @Test
    fun `ContinueClicked emittiert NavigateToGame mit dem Ziel-Level`() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(FakeProgressRepository(progressOf(1)))
            runCurrent()

            viewModel.effects.test {
                viewModel.onIntent(HomeIntent.ContinueClicked)

                assertEquals(HomeEffect.NavigateToGame(LevelRequest.Campaign(2)), awaitItem())
            }
        }

    @Test
    fun `ContinueClicked bei alles geloest emittiert NavigateToLevelSelect`() =
        runTest(dispatcher) {
            val all = (1..CAMPAIGN_LEVEL_COUNT).toList().toIntArray()
            val viewModel = HomeViewModel(FakeProgressRepository(progressOf(*all)))
            runCurrent()

            viewModel.effects.test {
                viewModel.onIntent(HomeIntent.ContinueClicked)

                assertEquals(HomeEffect.NavigateToLevelSelect, awaitItem())
            }
        }

    @Test
    fun `LevelSelectClicked emittiert NavigateToLevelSelect`() =
        runTest(dispatcher) {
            val viewModel = HomeViewModel(FakeProgressRepository())
            runCurrent()

            viewModel.effects.test {
                viewModel.onIntent(HomeIntent.LevelSelectClicked)

                assertEquals(HomeEffect.NavigateToLevelSelect, awaitItem())
            }
        }

    @Test
    fun `ContinueClicked im Fehler- oder Ladezustand ist wirkungslos`() =
        runTest(dispatcher) {
            val failure = DataResult.Failure(PersistenceFailure.Io("voll"))
            val viewModel = HomeViewModel(FakeProgressRepository(failure))

            viewModel.effects.test {
                viewModel.onIntent(HomeIntent.ContinueClicked) // noch im Ladezustand
                runCurrent()
                viewModel.onIntent(HomeIntent.ContinueClicked) // im Fehlerzustand

                expectNoEvents()
            }
        }

    @Test
    fun `Repository-Updates aktualisieren den State`() =
        runTest(dispatcher) {
            val repository = FakeProgressRepository()
            val viewModel = HomeViewModel(repository)
            runCurrent()
            assertEquals(ContinueTarget.Level(1), viewModel.state.value.continueTarget)

            repository.results.value = progressOf(1)
            runCurrent()

            assertEquals(ContinueTarget.Level(2), viewModel.state.value.continueTarget)
        }
}
