package de.puzzlewerk.app

import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Smoke-Test des einzigen Einstiegspunkts (S5): Start und Recreate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun string(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    @Test
    fun startZeigtDieHomeWurzel() {
        composeRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
    }

    @Test
    fun konfigurationswechselBehaeltDenNavigationszustand() {
        // Über die echte UI navigieren: Home → Levelauswahl (Platzhalter).
        composeRule.onNodeWithText(string(R.string.screen_title_level_select)).performClick()
        composeRule.onNodeWithText(string(R.string.placeholder_screen_hint)).assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        // rememberSaveable stellt den Backstack wieder her (ADR-008).
        composeRule.onNodeWithText(string(R.string.screen_title_level_select)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.placeholder_screen_hint)).assertIsDisplayed()
    }
}
