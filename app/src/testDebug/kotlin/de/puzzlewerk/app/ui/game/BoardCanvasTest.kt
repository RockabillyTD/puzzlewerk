package de.puzzlewerk.app.ui.game

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Semantics-Tests für [BoardCanvas] (ADR-009/2): je Zelle eine
 * §13.5-Beschreibung; Kristallzustände über die Beschreibung prüfbar
 * (Zustand nie nur über Farbe, §13.3). SDK-Pinnung nach ADR-009;
 * `qualifiers = "de"` pinnt die deutsche Basissprache der Assertions.
 *
 * Liegt in `src/testDebug`, weil die Test-Activity aus `ui-test-manifest`
 * nur im Debug-Manifest existiert (ADR-009) — im Release-Unit-Test-Lauf
 * gäbe es keine startbare Activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class BoardCanvasTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setBoard(state: BoardUiState) {
        compose.setContent {
            BoardCanvas(state = state, modifier = Modifier.size(320.dp))
        }
    }

    @Test
    fun `Startzustand beschreibt Quelle Spiegel Prisma und dunkle Kristalle`() {
        setBoard(BoardSampleStates.exampleLevelStart)
        compose
            .onNodeWithContentDescription("Lichtquelle, Weiß (Rot, Grün und Blau), Richtung Ost, Reihe 0, Spalte -2")
            .assertExists()
        compose
            .onNodeWithContentDescription("Spiegel, drehbar, Orientierung 6 von 6, Reihe 0, Spalte 0")
            .assertExists()
        compose.onNodeWithContentDescription("Prisma, Reihe -1, Spalte 1").assertExists()
        compose
            .onNodeWithContentDescription("Kristall, benötigt Rot, empfängt nichts, dunkel, Reihe -2, Spalte 1")
            .assertExists()
    }

    @Test
    fun `jede Zelle des Bretts hat einen Semantics-Knoten`() {
        setBoard(BoardSampleStates.exampleLevelStart)
        compose.onNodeWithContentDescription("Spielbrett").assertExists()
        compose.onAllNodes(hasContentDescription("Reihe", substring = true)).assertCountEquals(19)
        compose.onAllNodes(hasContentDescription("Leere Zelle", substring = true)).assertCountEquals(13)
    }

    @Test
    fun `geloester Zustand meldet erfuellte Kristalle und neue Spiegelstellung`() {
        setBoard(BoardSampleStates.exampleLevelSolved)
        compose
            .onNodeWithContentDescription("Spiegel, drehbar, Orientierung 2 von 6, Reihe 0, Spalte 0")
            .assertExists()
        compose
            .onNodeWithContentDescription("Kristall, benötigt Rot, empfängt Rot, erfüllt, Reihe -2, Spalte 1")
            .assertExists()
        compose
            .onNodeWithContentDescription("Kristall, benötigt Grün, empfängt Grün, erfüllt, Reihe -2, Spalte 2")
            .assertExists()
        compose
            .onNodeWithContentDescription("Kristall, benötigt Blau, empfängt Blau, erfüllt, Reihe -1, Spalte 2")
            .assertExists()
    }

    @Test
    fun `alle Elementtypen sind ueber ihre Beschreibung auffindbar`() {
        setBoard(BoardSampleStates.elementZoo)
        compose
            .onNodeWithContentDescription("Lichtquelle, Gelb (Rot und Grün), Richtung Ost, Reihe 0, Spalte -2")
            .assertExists()
        compose
            .onNodeWithContentDescription("Splitter, drehbar, Orientierung 3 von 6, Reihe 0, Spalte 0")
            .assertExists()
        compose
            .onNodeWithContentDescription("Spiegel, drehbar, Orientierung 1 von 6, Reihe 1, Spalte -2")
            .assertExists()
        compose.onNodeWithContentDescription("Prisma, Reihe -2, Spalte 0").assertExists()
        compose.onNodeWithContentDescription("Filter Rot, Reihe -1, Spalte -1").assertExists()
        compose.onNodeWithContentDescription("Wand, Reihe 1, Spalte -1").assertExists()
        compose.onAllNodesWithContentDescription("Portal A", substring = true).assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Portal B", substring = true).assertCountEquals(2)
    }

    @Test
    fun `alle vier Kristallzustaende sind unterscheidbar beschrieben`() {
        setBoard(BoardSampleStates.elementZoo)
        compose
            .onNodeWithContentDescription(
                "Kristall, benötigt Weiß (Rot, Grün und Blau), empfängt nichts, dunkel, Reihe -2, Spalte 2",
            ).assertExists()
        compose
            .onNodeWithContentDescription(
                "Kristall, benötigt Gelb (Rot und Grün), empfängt Rot, teilerfüllt, Reihe -1, Spalte 2",
            ).assertExists()
        compose
            .onNodeWithContentDescription("Kristall, benötigt Rot, empfängt Rot, erfüllt, Reihe 1, Spalte 1")
            .assertExists()
        compose
            .onNodeWithContentDescription(
                "Kristall, benötigt Grün, empfängt Gelb (Rot und Grün), übersättigt, Reihe 2, Spalte 0",
            ).assertExists()
    }
}
