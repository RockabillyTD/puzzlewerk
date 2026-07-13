package de.puzzlewerk.app

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.puzzlewerk.app.di.AppContainer
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.app.ui.navigation.NavigationState
import de.puzzlewerk.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wurzel-Composable: Screen-Zuordnung, System-Back und Home-Verdrahtung (ADR-006/ADR-008). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PuzzlewerkAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    private fun setApp(navigationState: NavigationState) {
        // Der echte Container ist hier bewusst im Spiel: die Übergangs-
        // Implementierung hält alles im Speicher (kein I/O, deterministisch).
        val factory = AppContainer().viewModelFactory
        composeRule.setContent {
            PuzzlewerkApp(viewModelFactory = factory, navigationState = navigationState)
        }
    }

    @Test
    fun zeigtHomeAlsWurzel() {
        setApp(NavigationState.initial())

        composeRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_continue)).assertIsDisplayed()
    }

    @Test
    fun zeigtDenOberstenScreenDesBackstacks() {
        val navigationState = NavigationState.initial()
        setApp(navigationState)

        composeRule.runOnIdle { navigationState.navigateTo(Screen.LevelSelect) }
        composeRule.onNodeWithText(string(R.string.screen_title_level_select)).assertIsDisplayed()

        composeRule.runOnIdle { navigationState.navigateTo(Screen.Game(LevelRequest.Campaign(1))) }
        composeRule.onNodeWithText(string(R.string.screen_title_game)).assertIsDisplayed()
    }

    @Test
    fun systemBackPopptBisZurWurzelUndDeaktiviertSichDort() {
        val navigationState = NavigationState.initial()
        setApp(navigationState)
        composeRule.runOnIdle { navigationState.navigateTo(Screen.LevelSelect) }
        // Erst nach der Recomposition ist der BackHandler scharf (enabled folgt dem State).
        composeRule.onNodeWithText(string(R.string.screen_title_level_select)).assertIsDisplayed()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(Screen.Home, navigationState.currentScreen)
            // Auf der Wurzel ist der BackHandler aus — System-Back verlässt die App (ADR-008).
            assertFalse(navigationState.canNavigateBack)
        }
    }

    @Test
    fun weiterNavigiertBeimErststartZuLevelEins() {
        val navigationState = NavigationState.initial()
        setApp(navigationState)

        composeRule.onNodeWithText(string(R.string.home_continue)).performClick()

        composeRule.onNodeWithText(string(R.string.screen_title_game)).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(Screen.Game(LevelRequest.Campaign(1)), navigationState.currentScreen)
        }
    }

    @Test
    fun levelauswahlButtonNavigiertZurLevelauswahl() {
        val navigationState = NavigationState.initial()
        setApp(navigationState)

        composeRule.onNodeWithText(string(R.string.screen_title_level_select)).performClick()

        composeRule.runOnIdle {
            assertEquals(Screen.LevelSelect, navigationState.currentScreen)
        }
    }
}
