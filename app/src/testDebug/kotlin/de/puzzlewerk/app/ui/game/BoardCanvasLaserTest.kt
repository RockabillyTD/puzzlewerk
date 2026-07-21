package de.puzzlewerk.app.ui.game

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import de.puzzlewerk.app.ui.juice.JuiceState
import de.puzzlewerk.app.ui.juice.ParticleSnapshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Render-Smoke des Laser-Pfads (PW-4.5, ADR-011: „zeichnet ohne Crash, auch
 * bei 0 Partikeln"): weißer Kern + additiver Halo + Musterkanal über echten
 * Beispiel-Strahlen (Primärfarben mit Muster UND Mischfarben-Segmente), dazu
 * Partikel-Layer und Vollbild-Flash aus einem synthetischen [JuiceState].
 * Pixel-Vergleiche sind bewusst außen vor (ADR-011: eigenes Tooling = eigenes
 * ADR); die Optik nimmt Branko am Gate ab.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class BoardCanvasLaserTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setBoard(juice: JuiceState?) {
        compose.setContent {
            BoardCanvas(
                state = BoardSampleStates.exampleLevelSolved,
                modifier = Modifier.size(320.dp),
                juice = juice?.let { mutableStateOf(it) },
            )
        }
    }

    /** Partikel auch AUSSERHALB des Canvas und mit Rest-Alpha — nichts davon darf crashen. */
    private fun particles(): ParticleSnapshot =
        ParticleSnapshot(
            count = 3,
            xDp = floatArrayOf(0f, 80f, 500f),
            yDp = floatArrayOf(-20f, 120f, 40f),
            sizeDp = floatArrayOf(2f, 3f, 3f),
            alpha = floatArrayOf(1f, 0.5f, 0.01f),
            colorArgb = intArrayOf(0xFFE5484D.toInt(), 0xFF30A46C.toInt(), 0xFF3E63DD.toInt()),
            vxDp = FloatArray(3),
            vyDp = FloatArray(3),
            gravityDpPerSec2 = FloatArray(3),
            alphaFadePerMillis = FloatArray(3),
        )

    private fun juiceState(
        haloPulseFactor: Float,
        flashAlpha: Float,
        particles: ParticleSnapshot,
        reduceMotion: Boolean,
    ): JuiceState =
        JuiceState(
            elapsedMillis = 125L,
            reduceMotion = reduceMotion,
            levelSeed = 1L,
            haloPulseFactor = haloPulseFactor,
            flashAlpha = flashAlpha,
            emitters = emptyList(),
            pendingBursts = emptyList(),
            particles = particles,
            flashRemainingMillis = 0L,
        )

    @Test
    fun `Laser-Pfad mit Puls Partikeln und Flash rendert ohne Crash`() {
        setBoard(
            juiceState(haloPulseFactor = 1.2f, flashAlpha = 0.35f, particles = particles(), reduceMotion = false),
        )
        compose.onNodeWithContentDescription("Spielbrett").assertExists()
    }

    /** R44/§13.7: statischer Halo (Faktor exakt 1,0), keine Partikel, kein Flash. */
    @Test
    fun `Reduce-Motion-Pfad rendert statischen Halo ohne Crash`() {
        setBoard(
            juiceState(
                haloPulseFactor = 1f,
                flashAlpha = 0f,
                particles = ParticleSnapshot.EMPTY,
                reduceMotion = true,
            ),
        )
        compose.onNodeWithContentDescription("Spielbrett").assertExists()
    }

    @Test
    fun `ohne Juice-State rendert der Laser mit Ruhe-Halo`() {
        setBoard(juice = null)
        compose.onNodeWithContentDescription("Spielbrett").assertExists()
    }
}
