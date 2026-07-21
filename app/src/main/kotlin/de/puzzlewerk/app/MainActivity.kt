package de.puzzlewerk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Einziger Einstiegspunkt der App. Die gesamte UI ist Compose;
 * es gibt keine weitere Activity (Regel S5). Edge-to-Edge ist ab
 * targetSdk 35+ Pflicht — die Insets behandelt [PuzzlewerkApp].
 */
class MainActivity : ComponentActivity() {
    private val container get() = (application as PuzzlewerkApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PuzzlewerkApp(
                viewModelFactory = container.viewModelFactory,
                gameViewModelFactory = container::gameViewModelFactory,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // Lebenszyklus-Glue der AudioEngine (PW-4.8): sichtbar ⇒ Wiedereinstieg.
        container.audioEngine(this).setHostVisible(true)
    }

    override fun onStop() {
        // Nicht mehr sichtbar ⇒ Mixer pausiert wie bei Fokus-Verlust (R47-Pfad).
        container.audioEngine(this).setHostVisible(false)
        super.onStop()
    }
}
