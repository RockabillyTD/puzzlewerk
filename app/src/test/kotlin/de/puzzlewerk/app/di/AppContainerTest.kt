package de.puzzlewerk.app.di

import androidx.lifecycle.ViewModel
import de.puzzlewerk.app.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Composition Root (ADR-006): der Compiler prüft den Graphen, die Factory den Rest. */
@OptIn(ExperimentalCoroutinesApi::class) // setMain: Standardweg laut ADR-009-Test-Stack
class AppContainerTest {
    @Before
    fun setUp() {
        // HomeViewModel startet in init eine Collection auf viewModelScope (Main).
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Factory baut das HomeViewModel aus dem Container`() {
        val container = AppContainer()

        assertNotNull(container.viewModelFactory.create(HomeViewModel::class.java))
    }

    @Test
    fun `Factory lehnt unregistrierte ViewModels ab`() {
        val factory = AppContainer().viewModelFactory

        assertThrows(IllegalStateException::class.java) {
            factory.create(UnregisteredViewModel::class.java)
        }
    }

    private class UnregisteredViewModel : ViewModel()
}
