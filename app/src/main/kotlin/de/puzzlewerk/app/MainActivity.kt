package de.puzzlewerk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * Einziger Einstiegspunkt der App. Die gesamte UI ist Compose;
 * es gibt keine weitere Activity (Regel S5).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PuzzlewerkApp()
        }
    }
}
