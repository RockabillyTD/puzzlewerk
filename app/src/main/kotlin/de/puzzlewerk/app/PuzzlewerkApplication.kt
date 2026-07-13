package de.puzzlewerk.app

import android.app.Application
import de.puzzlewerk.app.di.AppContainer

/**
 * Hält genau eine [AppContainer]-Instanz (App-Singleton-Scope, ADR-006).
 * Die einzige Activity erreicht den Container über diese Application.
 */
class PuzzlewerkApplication : Application() {
    /** Composition Root; lebt so lange wie der Prozess. */
    val container: AppContainer by lazy { AppContainer() }
}
