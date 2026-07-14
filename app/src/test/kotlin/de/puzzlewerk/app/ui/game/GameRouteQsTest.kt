package de.puzzlewerk.app.ui.game

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.app.ui.navigation.Screen
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme
import de.puzzlewerk.data.PersistenceFailure
import de.puzzlewerk.data.progress.FakeProgressRepository
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.engine.defaultGameEngine
import de.puzzlewerk.game.generator.LevelGenerator
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.score.DefaultScoreCalculator
import de.puzzlewerk.game.trace.DefaultTracer
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unabhängiger QS-Pass (PW-3.7-QS) gegen die GameRoute-Verdrahtung — Lücken
 * neben E2eSmokeTest (der nur Level 1 → „Weiter" → Level 2 fährt):
 *
 * - Level-50-Kante (§11.1: 50 Kampagnenlevel; §12.3): das Overlay des LETZTEN
 *   Levels darf kein „Weiter" anbieten und nichts navigieren.
 * - Daily-Partien (§12.3/§10): kein „Weiter" (Kampagnen-Kette existiert nicht).
 * - Speicherfehler (R43-Geist, §12.3): definierte Meldung statt Crash.
 * - §13.3/§13.5: Sterne-Glyphen sind von TalkBack ausgenommen, die Wertung
 *   liegt als Text „x von 3 Sternen" vor (Gate-Checkliste Punkt 3).
 *
 * Gelöst wird über die Semantics-`onClick`-Aktion der Spiegel-Zelle — exakt
 * der TalkBack-Doppeltipp-Pfad aus §13.5 („Doppeltipp führt den Zug aus").
 * Deterministisch: Fake-Generator (Spiegelweg-Miniatur, par 1), Effekte auf
 * dem vom Compose-Test getakteten Main-Looper (wie E2eSmokeTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class GameRouteQsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val engine = defaultGameEngine(DefaultTracer)
    private val fakeGenerator = LevelGenerator { _, _ -> tinySolvableLevel() }
    private val navigated = mutableListOf<Screen>()
    private var backCount = 0

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun gameViewModelFactory(
        request: LevelRequest,
        repository: FakeProgressRepository,
    ): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val viewModel: ViewModel =
                    GameViewModel(
                        request = request,
                        engine = engine,
                        generator = fakeGenerator,
                        scoreCalculator = DefaultScoreCalculator,
                        progressRepository = repository,
                        // Wie E2eSmokeTest: Main-Looper wird vom Compose-Test getaktet.
                        dispatcher = Dispatchers.Main,
                    )
                return requireNotNull(modelClass.cast(viewModel))
            }
        }

    private fun setRoute(
        request: LevelRequest,
        repository: FakeProgressRepository = FakeProgressRepository(),
    ) {
        composeRule.setContent {
            PuzzlewerkTheme {
                GameRoute(
                    request = request,
                    gameViewModelFactory = { req -> gameViewModelFactory(req, repository) },
                    onNavigate = navigated::add,
                    onNavigateBack = { backCount++ },
                )
            }
        }
    }

    /** §13.5-Beschreibung des Fake-Spiegels im Startzustand (m = 5 ⇒ „6 von 6", Zelle (0,0)). */
    private val mirrorStartDescription: String
        get() =
            string(
                R.string.board_cell_rotatable,
                string(R.string.board_element_mirror),
                "6",
                "6",
                string(R.string.board_cell_position, 0, 0),
            )

    /** Löst die Partie über die Semantics-onClick-Aktion der Spiegel-Zelle (TalkBack-Doppeltipp, §13.5). */
    private fun solveViaCellAction() {
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(string(R.string.game_moves_par, 0, 1)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(mirrorStartDescription).performClick()
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(string(R.string.game_result_title)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** §11.1/§12.3: Level 50 ist das letzte — Overlay ohne „Weiter", nur Nochmal/Zurück; keine Navigation. */
    @Test
    fun level50GeloestZeigtKeinWeiterImOverlay() {
        setRoute(LevelRequest.Campaign(50))

        solveViaCellAction()

        composeRule.onNodeWithText(string(R.string.game_result_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_result_next)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.game_result_replay)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_result_back)).assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(navigated.isEmpty()) }
    }

    /** §12.3/§10: Eine Daily-Partie hat keine Kampagnen-Kette — kein „Weiter" im Overlay. */
    @Test
    fun dailyPartieZeigtKeinWeiterImOverlay() {
        setRoute(LevelRequest.Daily(FIXED_EPOCH_DAY))

        solveViaCellAction()

        composeRule.onNodeWithText(string(R.string.game_result_next)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.game_result_replay)).assertIsDisplayed()
    }

    /** R43-Geist/§12.3: Speicherfehler beim Lösen ⇒ definierte Snackbar-Meldung, Overlay bleibt, kein Crash. */
    @Test
    fun speicherfehlerZeigtDefinierteMeldung() {
        val repository = FakeProgressRepository()
        repository.failWith(PersistenceFailure.Io("kein Platz"))
        setRoute(LevelRequest.Campaign(1), repository)

        solveViaCellAction()

        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(string(R.string.game_save_failed)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(string(R.string.game_save_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_result_title)).assertIsDisplayed()
    }

    /** §13.3/§13.5 (Gate-Checkliste 3): Sterne mehrkanalig — Glyphenzeile ohne Semantik, Wertung als Text. */
    @Test
    fun sterneGlyphenSindVonTalkBackAusgenommenWertungAlsText() {
        setRoute(LevelRequest.Campaign(1))

        solveViaCellAction()

        val glyphs = string(R.string.game_stars_filled).repeat(3)
        composeRule.onNodeWithText(glyphs).assertDoesNotExist() // clearAndSetSemantics auf der Glyphenzeile
        composeRule
            .onNodeWithText(quantityString(R.plurals.game_result_stars, MAX_STARS, MAX_STARS))
            .assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(0, backCount) }
    }

    private fun quantityString(
        pluralId: Int,
        quantity: Int,
        vararg args: Any,
    ): String = composeRule.activity.resources.getQuantityString(pluralId, quantity, *args)

    private companion object {
        private const val LOAD_TIMEOUT_MILLIS = 5_000L
        private const val MAX_STARS = 3

        /** Festes Daily-Datum — deterministisch, keine Wallclock (ADR-009 Flaky-Verbot). */
        private const val FIXED_EPOCH_DAY = 20_000L

        /**
         * „Spiegelweg"-Miniatur (§9.5/7) wie im E2eSmokeTest: Quelle Weiß (−2,0)→O,
         * drehbarer Spiegel (0,0) mit Start m=5, Kristall Weiß (2,0); genau EINE
         * Drehung (5→0, Parallelfall) löst ⇒ par 1, ★★★, 1500 Punkte.
         */
        private fun tinySolvableLevel(): LevelDefinition =
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
