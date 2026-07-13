package de.puzzlewerk.app.ui.home

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Zustands- und Interaktionstests des Home-Screens (§12.2, ADR-009). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val intents = mutableListOf<HomeIntent>()

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    private fun setScreen(state: HomeUiState) {
        composeRule.setContent {
            PuzzlewerkTheme {
                HomeScreen(state = state, onIntent = intents::add)
            }
        }
    }

    @Test
    fun normalzustandZeigtWeiterMitZielLevel() {
        setScreen(HomeUiState(isLoading = false, continueTarget = ContinueTarget.Level(3)))

        composeRule.onNodeWithText(string(R.string.home_continue)).assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText(string(R.string.home_continue_level, 3)).assertIsDisplayed()
    }

    @Test
    fun weiterKlickSendetContinueIntent() {
        setScreen(HomeUiState(isLoading = false, continueTarget = ContinueTarget.Level(1)))

        composeRule.onNodeWithText(string(R.string.home_continue)).performClick()

        composeRule.runOnIdle { assertEquals(listOf<HomeIntent>(HomeIntent.ContinueClicked), intents) }
    }

    @Test
    fun levelauswahlKlickSendetLevelSelectIntent() {
        setScreen(HomeUiState(isLoading = false))

        composeRule.onNodeWithText(string(R.string.screen_title_level_select)).performClick()

        composeRule.runOnIdle { assertEquals(listOf<HomeIntent>(HomeIntent.LevelSelectClicked), intents) }
    }

    @Test
    fun dailyUndEinstellungenSindDeaktiviertMitBaldBadge() {
        setScreen(HomeUiState(isLoading = false))

        composeRule.onNodeWithText(string(R.string.screen_title_daily)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.home_badge_soon)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.screen_title_settings)).assertIsNotEnabled()
    }

    @Test
    fun allesGeloestZeigtLevelauswahlZiel() {
        setScreen(HomeUiState(isLoading = false, continueTarget = ContinueTarget.AllSolved))

        composeRule.onNodeWithText(string(R.string.home_continue_all_solved)).assertIsDisplayed()
    }

    @Test
    fun ladezustandZeigtIndikatorStattAktionen() {
        setScreen(HomeUiState())

        composeRule.onNodeWithContentDescription(string(R.string.home_loading)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_continue)).assertDoesNotExist()
    }

    @Test
    fun fehlerzustandZeigtMeldungUndDeaktiviertWeiter() {
        setScreen(HomeUiState(isLoading = false, hasLoadError = true))

        composeRule.onNodeWithText(string(R.string.home_load_error)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_continue)).assertIsNotEnabled()
        // Fortschritt unbekannt — der Ziel-Untertitel („Level 1") darf nicht erscheinen.
        composeRule.onNodeWithText(string(R.string.home_continue_level, 1)).assertDoesNotExist()
    }

    @Test
    fun interaktiveElementeHabenMindestensTouchTargetHoehe() {
        setScreen(HomeUiState(isLoading = false))

        // §13.6: Touch-Targets ≥ 48 dp.
        composeRule.onNodeWithText(string(R.string.home_continue)).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithText(string(R.string.screen_title_level_select)).assertHeightIsAtLeast(48.dp)
    }
}
