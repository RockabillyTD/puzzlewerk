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
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PuzzlewerkApplication).container
        setContent {
            PuzzlewerkApp(viewModelFactory = container.viewModelFactory)
        }
    }
}
