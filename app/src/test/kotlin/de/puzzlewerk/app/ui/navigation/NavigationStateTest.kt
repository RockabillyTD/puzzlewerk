package de.puzzlewerk.app.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Tests für den Backstack-Holder und den Saver (ADR-008). */
class NavigationStateTest {
    private val saverScope = SaverScope { true }

    private fun save(state: NavigationState): Any = with(NavigationState.saver) { saverScope.save(state) }!!

    @Test
    fun `Startzustand ist Home ohne Back-Moeglichkeit`() {
        val state = NavigationState.initial()

        assertEquals(Screen.Home, state.currentScreen)
        assertFalse(state.canNavigateBack)
    }

    @Test
    fun `navigateTo legt Screens auf den Stack`() {
        val state = NavigationState.initial()

        state.navigateTo(Screen.LevelSelect)
        state.navigateTo(Screen.Game(LevelRequest.Campaign(3)))

        assertEquals(Screen.Game(LevelRequest.Campaign(3)), state.currentScreen)
        assertEquals(3, state.backstack.size)
        assertTrue(state.canNavigateBack)
    }

    @Test
    fun `navigateBack poppt den obersten Screen`() {
        val state = NavigationState.initial()
        state.navigateTo(Screen.LevelSelect)

        state.navigateBack()

        assertEquals(Screen.Home, state.currentScreen)
        assertFalse(state.canNavigateBack)
    }

    @Test
    fun `navigateBack auf der Wurzel ist ein No-op`() {
        val state = NavigationState.initial()

        state.navigateBack()

        assertEquals(listOf<Screen>(Screen.Home), state.backstack)
    }

    @Test
    fun `encode decode ist fuer jeden Screen ein Roundtrip`() {
        val screens =
            listOf(
                Screen.Home,
                Screen.LevelSelect,
                Screen.Game(LevelRequest.Campaign(1)),
                Screen.Game(LevelRequest.Campaign(50)),
                Screen.Game(LevelRequest.Daily(20_000L)),
                Screen.Daily,
                Screen.Settings,
            )

        for (screen in screens) {
            assertEquals(screen, decodeScreen(encodeScreen(screen)))
        }
    }

    @Test
    fun `decode verwirft alles Unbekannte strikt`() {
        val invalid =
            listOf(
                "",
                "quatsch",
                "game/campaign/",
                "game/campaign/0",
                "game/campaign/51",
                "game/campaign/abc",
                "game/daily/-1",
                "game/daily/xyz",
                "HOME",
            )

        for (encoded in invalid) {
            assertNull("»$encoded« darf nicht dekodierbar sein", decodeScreen(encoded))
        }
    }

    @Test
    fun `Saver stellt den Backstack wieder her`() {
        val state = NavigationState.initial()
        state.navigateTo(Screen.LevelSelect)
        state.navigateTo(Screen.Game(LevelRequest.Daily(123L)))

        val restored = NavigationState.saver.restore(save(state))

        assertEquals(state.backstack, restored?.backstack)
    }

    @Test
    fun `Saver verwirft manipulierte oder wurzellose Staende komplett`() {
        assertNull(NavigationState.saver.restore(listOf("home", "quatsch")))
        assertNull(NavigationState.saver.restore(listOf("levelselect")))
        assertNull(NavigationState.saver.restore(emptyList<String>()))
    }

    @Test
    fun `LevelRequest validiert seine Wertebereiche`() {
        assertThrows(IllegalArgumentException::class.java) { LevelRequest.Campaign(0) }
        assertThrows(IllegalArgumentException::class.java) { LevelRequest.Campaign(51) }
        assertThrows(IllegalArgumentException::class.java) { LevelRequest.Daily(-1L) }
    }
}
