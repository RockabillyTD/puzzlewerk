package de.puzzlewerk.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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

    @Test
    fun startZeigtDieHomeWurzel() {
        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.app_name))
            .assertIsDisplayed()
    }

    @Test
    fun konfigurationswechselRendertWieder() {
        composeRule.activityRule.scenario.recreate()

        composeRule
            .onNodeWithText(composeRule.activity.getString(R.string.app_name))
            .assertIsDisplayed()
    }
}
