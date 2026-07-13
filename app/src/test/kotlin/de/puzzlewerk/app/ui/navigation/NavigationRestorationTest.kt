package de.puzzlewerk.app.ui.navigation

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restaurations-Tests für [rememberNavigationState] (ADR-008): der
 * StateRestorationTester fährt exakt den SavedState-Pfad, den sowohl
 * Konfigurationswechsel als auch Prozess-Tod nehmen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NavigationRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun backstackUeberlebtSavedStateRestauration() {
        val tester = StateRestorationTester(composeRule)
        var state: NavigationState? = null
        tester.setContent { state = rememberNavigationState() }

        composeRule.runOnIdle {
            checkNotNull(state).navigateTo(Screen.LevelSelect)
            checkNotNull(state).navigateTo(Screen.Game(LevelRequest.Campaign(7)))
        }
        val before = checkNotNull(state).backstack
        state = null

        tester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertEquals(before, checkNotNull(state).backstack)
            assertEquals(Screen.Game(LevelRequest.Campaign(7)), checkNotNull(state).currentScreen)
        }
    }

    @Test
    fun wurzelZustandBleibtNachRestaurationDieWurzel() {
        val tester = StateRestorationTester(composeRule)
        var state: NavigationState? = null
        tester.setContent { state = rememberNavigationState() }
        state = null

        tester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertEquals(listOf<Screen>(Screen.Home), checkNotNull(state).backstack)
        }
    }
}
