package de.puzzlewerk.app

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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

/** Wurzel-Composable: Screen-Zuordnung und System-Back (ADR-008). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PuzzlewerkAppTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    @Test
    fun zeigtHomeAlsWurzel() {
        composeRule.setContent { PuzzlewerkApp(navigationState = NavigationState.initial()) }

        composeRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
    }

    @Test
    fun zeigtDenOberstenScreenDesBackstacks() {
        val navigationState = NavigationState.initial()
        composeRule.setContent { PuzzlewerkApp(navigationState = navigationState) }

        composeRule.runOnIdle { navigationState.navigateTo(Screen.LevelSelect) }
        composeRule.onNodeWithText(string(R.string.screen_title_level_select)).assertIsDisplayed()

        composeRule.runOnIdle { navigationState.navigateTo(Screen.Game(LevelRequest.Campaign(1))) }
        composeRule.onNodeWithText(string(R.string.screen_title_game)).assertIsDisplayed()
    }

    @Test
    fun systemBackPopptBisZurWurzelUndDeaktiviertSichDort() {
        val navigationState = NavigationState.initial()
        composeRule.setContent { PuzzlewerkApp(navigationState = navigationState) }
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
}
