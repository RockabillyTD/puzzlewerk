package de.puzzlewerk.app.ui.levelselect

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT
import de.puzzlewerk.game.level.campaignTier
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Zustands- und Interaktionstests der Levelauswahl (§12.4, ADR-009). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LevelSelectScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val intents = mutableListOf<LevelSelectIntent>()

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun tierLabel(levelNumber: Int): String =
        string(R.string.level_select_tier, campaignTier(levelNumber).ordinal + 1)

    private fun setScreen(state: LevelSelectUiState) {
        composeRule.setContent {
            PuzzlewerkTheme {
                LevelSelectScreen(state = state, onIntent = intents::add)
            }
        }
    }

    private fun tiles(highestSolved: Int): List<LevelTile> =
        (1..CAMPAIGN_LEVEL_COUNT).map { n ->
            val tileState =
                when {
                    n <= highestSolved -> TileState.Solved(stars = 2, points = 1200)
                    n <= highestSolved + 3 -> TileState.Open
                    else -> TileState.Locked
                }
            LevelTile(levelNumber = n, tier = campaignTier(n), state = tileState)
        }

    @Test
    fun ladezustandZeigtIndikator() {
        setScreen(LevelSelectUiState())

        composeRule.onNodeWithContentDescription(string(R.string.level_select_loading)).assertIsDisplayed()
    }

    @Test
    fun offeneKachelHatKlickAktionUndNavigiert() {
        setScreen(LevelSelectUiState(isLoading = false, tiles = tiles(highestSolved = 0)))

        val open = string(R.string.level_select_cd_open, 2, tierLabel(2))
        composeRule.onNodeWithContentDescription(open).assertHasClickAction().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf<LevelSelectIntent>(LevelSelectIntent.TileClicked(2)), intents)
        }
    }

    @Test
    fun gesperrteKachelIstNichtTappbar() {
        setScreen(LevelSelectUiState(isLoading = false, tiles = tiles(highestSolved = 0)))

        // Level 4 ist bei highestSolved=0 gesperrt und liegt oben im Raster (§11.2).
        val locked = string(R.string.level_select_cd_locked, 4, tierLabel(4))
        composeRule
            .onNodeWithContentDescription(locked)
            .assertIsDisplayed()
            .assert(hasClickAction().not())
    }

    @Test
    fun geloesteKachelHatSemantischeBeschreibungMitSternen() {
        setScreen(
            LevelSelectUiState(
                isLoading = false,
                tiles = listOf(LevelTile(1, campaignTier(1), TileState.Solved(stars = 3, points = 1450))),
            ),
        )

        val solved = string(R.string.level_select_cd_solved, 1, tierLabel(1), 3, 1450)
        composeRule.onNodeWithContentDescription(solved).assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun offeneKachelErfuelltTouchTarget() {
        setScreen(LevelSelectUiState(isLoading = false, tiles = tiles(highestSolved = 0)))

        val open = string(R.string.level_select_cd_open, 1, tierLabel(1))
        composeRule.onNodeWithContentDescription(open).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun fehlerzustandZeigtMeldungUndResetKnopf() {
        setScreen(LevelSelectUiState(isLoading = false, hasLoadError = true))

        composeRule.onNodeWithText(string(R.string.level_select_error)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.level_select_reset)).performClick()

        composeRule.runOnIdle {
            assertEquals(listOf<LevelSelectIntent>(LevelSelectIntent.ResetProgress), intents)
        }
    }
}
