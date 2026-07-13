package de.puzzlewerk.app.ui.game

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme
import de.puzzlewerk.game.board.HexCoord
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Zustands- und Interaktionstests des Spiel-Screens (§12.3, ADR-009).
 * `qualifiers = "de"` pinnt die deutsche Basissprache; Animationen sind aus
 * (kein Tween ⇒ deterministisch, §13.6 „Animationen entfernen").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class GameScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val intents = mutableListOf<GameIntent>()
    private var navigatedNext = 0
    private var navigatedBack = 0

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun points(count: Int): String =
        composeRule.activity.resources.getQuantityString(R.plurals.game_result_points, count, count)

    private fun starsText(count: Int): String =
        composeRule.activity.resources.getQuantityString(R.plurals.game_result_stars, count, count)

    private fun setScreen(
        state: GameUiState,
        withNext: Boolean = true,
    ) {
        composeRule.setContent {
            PuzzlewerkTheme {
                GameScreen(
                    state = state,
                    onIntent = intents::add,
                    onNavigateBack = { navigatedBack++ },
                    onNavigateNext = if (withNext) ({ navigatedNext++ }) else null,
                )
            }
        }
    }

    private val playing =
        GameUiState(isLoading = false, board = BoardSampleStates.exampleLevelStart, moves = 3, par = 5, canUndo = true)

    private val solved =
        GameUiState(
            isLoading = false,
            board = BoardSampleStates.exampleLevelSolved,
            moves = 2,
            par = 3,
            result = GameResult(points = 1450, stars = 3, moves = 2, par = 3),
        )

    @Test
    fun kopfzeileZeigtZuegeUndPar() {
        setScreen(playing)

        composeRule.onNodeWithText(string(R.string.game_moves_par, 3, 5)).assertIsDisplayed()
    }

    @Test
    fun ladezustandZeigtIndikator() {
        setScreen(GameUiState())

        composeRule.onNodeWithContentDescription(string(R.string.game_loading)).assertIsDisplayed()
    }

    @Test
    fun tapAufDrehbaresElementSendetTapCell() {
        setScreen(playing)

        // §13.5-Beschreibung des Spiegels (Reihe 0 = r, Spalte 0 = q) ⇒ HexCoord(0, 0).
        composeRule
            .onNodeWithContentDescription("Spiegel, drehbar, Orientierung 6 von 6, Reihe 0, Spalte 0")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(listOf<GameIntent>(GameIntent.TapCell(HexCoord(0, 0))), intents)
        }
    }

    @Test
    fun ergebnisOverlayZeigtSterneUndPunkteUndAktionen() {
        setScreen(solved)

        composeRule.onNodeWithText(string(R.string.game_result_title)).assertIsDisplayed()
        // Sterne mehrkanalig: Textzeile „3 von 3 Sternen" neben den ★-Glyphen (§13).
        composeRule.onNodeWithText(starsText(3)).assertIsDisplayed()
        composeRule.onNodeWithText(points(1450)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_result_next)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_result_replay)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_result_back)).assertIsDisplayed()
    }

    @Test
    fun weiterNavigiertZumNaechstenLevel() {
        setScreen(solved, withNext = true)

        composeRule.onNodeWithText(string(R.string.game_result_next)).performClick()

        composeRule.runOnIdle { assertEquals(1, navigatedNext) }
    }

    @Test
    fun ohneNaechstesLevelKeinWeiterKnopf() {
        setScreen(solved, withNext = false)

        composeRule.onNodeWithText(string(R.string.game_result_replay)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_result_next)).assertDoesNotExist()
    }

    @Test
    fun nochmalSendetReplayIntent() {
        setScreen(solved)

        composeRule.onNodeWithText(string(R.string.game_result_replay)).performClick()

        composeRule.runOnIdle { assertEquals(listOf<GameIntent>(GameIntent.Replay), intents) }
    }

    @Test
    fun zurueckImOverlayNavigiertZurueck() {
        setScreen(solved)

        composeRule.onNodeWithText(string(R.string.game_result_back)).performClick()

        composeRule.runOnIdle { assertEquals(1, navigatedBack) }
    }

    @Test
    fun undoUndResetUnterDemOverlaySindInaktiv() {
        setScreen(solved)

        composeRule.onNodeWithText(string(R.string.game_undo)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.game_reset)).assertIsNotEnabled()
    }

    @Test
    fun resetLaufenderPartieSendetResetIntent() {
        setScreen(playing)

        composeRule.onNodeWithText(string(R.string.game_reset)).performClick()

        composeRule.runOnIdle { assertEquals(listOf<GameIntent>(GameIntent.Reset), intents) }
    }
}
