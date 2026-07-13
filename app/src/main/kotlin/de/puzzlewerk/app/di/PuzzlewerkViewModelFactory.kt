package de.puzzlewerk.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.puzzlewerk.app.ui.home.HomeViewModel

/**
 * Gemeinsame ViewModel-Factory (ADR-006): konstruiert jedes ViewModel per
 * Konstruktor-Injektion aus dem [AppContainer]. Neue ViewModels werden hier
 * registriert — ein fehlender Zweig ist ein gewöhnlicher Testfehler.
 */
internal class PuzzlewerkViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel =
            when {
                modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                    HomeViewModel(progressRepository = container.progressRepository)
                else ->
                    error("Unbekanntes ViewModel ${modelClass.name} — hier registrieren (ADR-006)")
            }
        return requireNotNull(modelClass.cast(viewModel))
    }
}
