package de.puzzlewerk.app.ui.levelselect

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
@OptIn(ExperimentalCoroutinesApi::class)
class LevelSelectViewModelTest {
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
        var resetCalls = 0
            private set

        override val progress: Flow<DataResult<CampaignProgress>> = results

        override suspend fun recordSolved(
            levelNumber: Int,
            result: Score,
        ): WriteResult = WriteResult.Success

        override suspend fun reset(): WriteResult {
            resetCalls++
            results.value = DataResult.Success(CampaignProgress.EMPTY)
            return WriteResult.Success
        }
    }

    private fun progressOf(vararg solved: Pair<Int, Score>): DataResult<CampaignProgress> =
        DataResult.Success(CampaignProgress(solved.toMap()))

    private fun LevelSelectUiState.tile(levelNumber: Int): LevelTile = tiles.first { it.levelNumber == levelNumber }

    @Test
    fun `beginnt im Ladezustand`() {
        val viewModel = LevelSelectViewModel(FakeProgressRepository())

        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `Erststart schaltet nur Level 1 bis 3 frei, Rest gesperrt`() =
        runTest(dispatcher) {
            val viewModel = LevelSelectViewModel(FakeProgressRepository())

            runCurrent()

            val state = viewModel.state.value
            assertFalse(state.isLoading)
            assertEquals(CAMPAIGN_LEVEL_COUNT, state.tiles.size)
            assertEquals(TileState.Open, state.tile(1).state)
            assertEquals(TileState.Open, state.tile(3).state)
            assertEquals(TileState.Locked, state.tile(4).state)
            assertEquals(TileState.Locked, state.tile(CAMPAIGN_LEVEL_COUNT).state)
        }

    @Test
    fun `nach Fortschritt bis Level 5 sind 1 bis 8 offen`() =
        runTest(dispatcher) {
            // highestSolvedLevel = 5 → freigeschaltet bis 5 + 3 = 8 (§11.2).
            val solved = (1..5).map { it to Score(points = 1200, stars = 2) }.toTypedArray()
            val viewModel = LevelSelectViewModel(FakeProgressRepository(progressOf(*solved)))

            runCurrent()

            val state = viewModel.state.value
            (6..8).forEach { assertEquals(TileState.Open, state.tile(it).state) }
            assertEquals(TileState.Locked, state.tile(9).state)
        }

    @Test
    fun `geloeste Kachel traegt Sterne und Punkte`() =
        runTest(dispatcher) {
            val viewModel =
                LevelSelectViewModel(FakeProgressRepository(progressOf(2 to Score(points = 1450, stars = 3))))

            runCurrent()

            assertEquals(TileState.Solved(stars = 3, points = 1450), viewModel.state.value.tile(2).state)
        }

    @Test
    fun `Kopf-Summen addieren Sterne und Punkte aller geloesten Level`() =
        runTest(dispatcher) {
            val viewModel =
                LevelSelectViewModel(
                    FakeProgressRepository(
                        progressOf(
                            1 to Score(points = 1000, stars = 1),
                            2 to Score(points = 1300, stars = 2),
                            3 to Score(points = 1500, stars = 3),
                        ),
                    ),
                )

            runCurrent()

            assertEquals(6, viewModel.state.value.totalStars)
            assertEquals(3800, viewModel.state.value.totalScore)
        }

    @Test
    fun `Persistenzfehler wird zum definierten Fehlerzustand`() =
        runTest(dispatcher) {
            val failure = DataResult.Failure(PersistenceFailure.Corrupted("kaputt"))
            val viewModel = LevelSelectViewModel(FakeProgressRepository(failure))

            runCurrent()

            assertTrue(viewModel.state.value.hasLoadError)
            assertFalse(viewModel.state.value.isLoading)
            assertTrue(viewModel.state.value.tiles.isEmpty())
        }

    @Test
    fun `Tap auf offene Kachel navigiert ins Spiel`() =
        runTest(dispatcher) {
            val viewModel = LevelSelectViewModel(FakeProgressRepository())
            runCurrent()

            viewModel.effects.test {
                viewModel.onIntent(LevelSelectIntent.TileClicked(2))

                assertEquals(LevelSelectEffect.NavigateToGame(LevelRequest.Campaign(2)), awaitItem())
            }
        }

    @Test
    fun `Tap auf gesperrte Kachel navigiert nicht`() =
        runTest(dispatcher) {
            val viewModel = LevelSelectViewModel(FakeProgressRepository())
            runCurrent()

            viewModel.effects.test {
                viewModel.onIntent(LevelSelectIntent.TileClicked(CAMPAIGN_LEVEL_COUNT))

                expectNoEvents()
            }
        }

    @Test
    fun `ResetProgress setzt den Fortschritt zurueck`() =
        runTest(dispatcher) {
            val repository = FakeProgressRepository(progressOf(1 to Score(points = 1200, stars = 2)))
            val viewModel = LevelSelectViewModel(repository)
            runCurrent()

            viewModel.onIntent(LevelSelectIntent.ResetProgress)
            runCurrent()

            assertEquals(1, repository.resetCalls)
            assertEquals(TileState.Open, viewModel.state.value.tile(1).state)
        }
}
