package de.puzzlewerk.app.di

import androidx.lifecycle.ViewModelProvider
import de.puzzlewerk.data.progress.ProgressRepository

/**
 * Composition Root (ADR-006): die EINZIGE Stelle, die Produktions-
 * Implementierungen konstruiert. Exponiert ausschließlich Interfaces;
 * Tests und Previews bauen ihre Objekte direkt mit Fakes.
 */
class AppContainer {
    /**
     * Kampagnenfortschritt (§7.2, §11). Bis die DataStore-Implementierung
     * aus PW-3.2 verdrahtet ist (PW-3.5/PW-3.6), hält eine Übergangs-
     * Implementierung den Fortschritt nur im Speicher — Verhalten laut
     * Interface-Vertrag, aber ohne Persistenz über den Prozess hinaus.
     * Issue: docs/backlog.md („DataStore-Repositories verdrahten", PW-3.3).
     */
    val progressRepository: ProgressRepository = InMemoryProgressRepository()

    /** Gemeinsame Factory aller ViewModels (ADR-006). */
    val viewModelFactory: ViewModelProvider.Factory = PuzzlewerkViewModelFactory(this)
}
