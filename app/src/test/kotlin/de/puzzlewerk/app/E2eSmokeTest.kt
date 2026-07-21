package de.puzzlewerk.app

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.puzzlewerk.app.audio.FakeAudioEngine
import de.puzzlewerk.app.di.InMemoryProgressRepository
import de.puzzlewerk.app.ui.game.GameAudioChoreographer
import de.puzzlewerk.app.ui.game.GameViewModel
import de.puzzlewerk.app.ui.home.HomeViewModel
import de.puzzlewerk.app.ui.levelselect.LevelSelectViewModel
import de.puzzlewerk.app.ui.navigation.LevelRequest
import de.puzzlewerk.app.ui.navigation.NavigationState
import de.puzzlewerk.app.ui.navigation.Screen
import de.puzzlewerk.data.DataResult
import de.puzzlewerk.data.settings.FakeSettingsRepository
import de.puzzlewerk.game.board.Board
import de.puzzlewerk.game.board.Direction
import de.puzzlewerk.game.board.HexCoord
import de.puzzlewerk.game.board.Orientation
import de.puzzlewerk.game.color.LightColor
import de.puzzlewerk.game.element.Element
import de.puzzlewerk.game.engine.defaultGameEngine
import de.puzzlewerk.game.generator.LevelGenerator
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.LevelDefinition
import de.puzzlewerk.game.level.campaignTier
import de.puzzlewerk.game.score.DefaultScoreCalculator
import de.puzzlewerk.game.trace.DefaultTracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-End-Smoke (PW-3.7, ADR-009): EIN glücklicher Pfad durch die verdrahtete
 * App — Home → Levelauswahl → Level 1 → Spiel → LÖSEN per Pixel-Tap → Overlay →
 * „Weiter" → frisches Folgelevel. Treibt das echte Wurzel-Composable
 * [PuzzlewerkApp] direkt (nicht über MainActivity) mit Test-Doubles nach dem
 * Container-Muster.
 *
 * Deterministik: Ein FAKE-[LevelGenerator] liefert das Fallback-„Spiegelweg"
 * (§9.5/7) als winziges, in GENAU EINER Drehung lösbares Level (par 1, ein
 * drehbarer Spiegel mittig auf `(0,0)`). Die übrigen Abhängigkeiten
 * (`defaultGameEngine`, `DefaultScoreCalculator`, In-Memory-Repository) sind
 * echt. Der Brett-Tap ist ein ECHTER Pixel-Tap auf das Zellzentrum (0,0) — das
 * fällt mit dem Brett-Ursprung/Canvas-Zentrum zusammen (BoardGeometry.fit) — und
 * deckt so den inversen Pixel→Axial-Pfad in BoardCanvas Ende-zu-Ende ab.
 *
 * Schritt 7 ist zugleich der Regressionstest zum PW-3.7-Integrationsbefund:
 * `GameRoute` muss das [GameViewModel] JE Request schlüsseln, sonst bleibt beim
 * Wechsel Game(1) → Game(2) das gelöste Level-1-ViewModel am Leben (Overlay
 * bleibt stehen, kein frisches Level).
 *
 * `qualifiers = "de"` pinnt die deutsche Basissprache der Text-Assertions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de")
class E2eSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // --- Test-Doubles nach Container-Muster (ADR-006) ---
    private val progressRepository = InMemoryProgressRepository()
    private val engine = defaultGameEngine(DefaultTracer)
    private val fakeGenerator = LevelGenerator { _, _ -> tinySolvableLevel() }

    private val viewModelFactory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val viewModel: ViewModel =
                    when {
                        modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                            HomeViewModel(progressRepository = progressRepository)
                        modelClass.isAssignableFrom(LevelSelectViewModel::class.java) ->
                            LevelSelectViewModel(progressRepository = progressRepository)
                        else -> error("Unerwartetes ViewModel im Smoke-Test: ${modelClass.name}")
                    }
                return requireNotNull(modelClass.cast(viewModel))
            }
        }

    private fun gameViewModelFactory(request: LevelRequest): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val viewModel: ViewModel =
                    GameViewModel(
                        request = request,
                        engine = engine,
                        generator = fakeGenerator,
                        scoreCalculator = DefaultScoreCalculator,
                        progressRepository = progressRepository,
                        audio = GameAudioChoreographer(FakeAudioEngine(), FakeSettingsRepository()),
                        // Determinismus-Seam (ViewModel-Doc: „Tests nutzen einen injizierten
                        // Dispatcher"): die winzige Fake-Generierung läuft auf dem Main-Looper,
                        // den der Compose-Test taktet — sonst bliebe die off-main-Fortsetzung
                        // (Dispatchers.Default) unter Robolectric ungepumpt.
                        dispatcher = Dispatchers.Main,
                    )
                return requireNotNull(modelClass.cast(viewModel))
            }
        }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = composeRule.activity.getString(id, *args)

    /** Kopfzeile einer frischen Partie des Fake-Levels: „Züge 0 · Par 1". */
    private val movesParStart: String
        get() = string(R.string.game_moves_par, 0, 1)

    private fun setApp(navigationState: NavigationState) {
        composeRule.setContent {
            PuzzlewerkApp(
                viewModelFactory = viewModelFactory,
                gameViewModelFactory = ::gameViewModelFactory,
                navigationState = navigationState,
            )
        }
    }

    /** Wartet, bis [text] sichtbar ist (Level wird off-main generiert, §9.4). */
    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun happyPath_home_levelauswahl_level1_loesen_weiter() {
        val navigationState = NavigationState.initial()
        setApp(navigationState)

        // 1) Home ist die Wurzel.
        composeRule.onNodeWithText(string(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.home_continue)).assertIsDisplayed()

        // 2+3) Home → Levelauswahl → Kachel Level 1 → Spiel-Screen.
        navigateToLevelOne(navigationState)

        // 4) Brett rendert, Kopfzeile „Züge 0 · Par 1".
        awaitFreshGame()

        // 5) ECHTER Pixel-Tap auf das Zellzentrum (0,0) = Brettmitte → dreht und löst.
        composeRule
            .onNodeWithContentDescription(string(R.string.board_canvas))
            .performTouchInput { click() }

        // 6) Ergebnis-Overlay mit Sternen (3) und Punkten (1500) — 1 Zug bei Par 1.
        assertResultOverlayVisible()

        // 7) „Weiter" → Folgelevel Campaign(2) als FRISCHE Partie, Overlay weg.
        continueToFreshLevelTwo(navigationState)

        // 8) Der Lösungserfolg von Level 1 ist als Bestwert gespeichert (§7.2).
        assertLevelOneBestScoreSaved()
    }

    /** Schritte 2+3: Home → Levelauswahl (Backstack prüfen) → Kachel Level 1 → Spiel-Screen. */
    private fun navigateToLevelOne(navigationState: NavigationState) {
        composeRule.onNodeWithText(string(R.string.screen_title_level_select)).performClick()
        composeRule.runOnIdle { assertEquals(Screen.LevelSelect, navigationState.currentScreen) }

        val tier1 = string(R.string.level_select_tier, campaignTier(1).ordinal + 1)
        composeRule
            .onNodeWithContentDescription(string(R.string.level_select_cd_open, 1, tier1))
            .performClick()
        composeRule.runOnIdle {
            assertEquals(Screen.Game(LevelRequest.Campaign(1)), navigationState.currentScreen)
        }
    }

    /** Schritt 4: Brett und Kopfzeile „Züge 0 · Par 1" einer frischen Partie sind da. */
    private fun awaitFreshGame() {
        awaitText(movesParStart)
        composeRule.onNodeWithContentDescription(string(R.string.board_canvas)).assertIsDisplayed()
        composeRule.onNodeWithText(movesParStart).assertIsDisplayed()
    }

    /**
     * Schritt 6: Overlay-Inhalte prüfen. („Züge 1 · Par 1" prüfen wir NICHT per
     * `onNodeWithText`: die Kopfzeile trägt es hinter dem Overlay ebenfalls, der
     * Matcher wäre mehrdeutig — Sterne/Punkte/Titel sind dagegen overlay-exklusiv.)
     */
    private fun assertResultOverlayVisible() {
        awaitText(string(R.string.game_result_title))
        composeRule.onNodeWithText(string(R.string.game_result_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(quantityString(R.plurals.game_result_stars, MAX_STARS, MAX_STARS))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(quantityString(R.plurals.game_result_points, STAR_POINTS, STAR_POINTS))
            .assertIsDisplayed()
    }

    /**
     * Schritt 7: „Weiter" navigiert zu Campaign(2) und lädt eine FRISCHE Partie —
     * Kopfzeile wieder „Züge 0 · Par 1" (genau einmal), kein Ergebnis-Overlay und
     * keine gelöste Kopfzeile „Züge 1 · Par 1" mehr im Baum (Regression:
     * ViewModel-Schlüsselung je Request in `GameRoute`).
     */
    private fun continueToFreshLevelTwo(navigationState: NavigationState) {
        composeRule.onNodeWithText(string(R.string.game_result_next)).performClick()
        composeRule.runOnIdle {
            assertEquals(Screen.Game(LevelRequest.Campaign(2)), navigationState.currentScreen)
        }
        awaitText(movesParStart)
        composeRule.onAllNodesWithText(movesParStart).assertCountEquals(1)
        composeRule.onNodeWithText(movesParStart).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.board_canvas)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.game_result_title)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.game_moves_par, 1, 1)).assertDoesNotExist()
    }

    /** Schritt 8: `recordSolved` hat Level 1 mit voller Wertung (§7.2) im Repository abgelegt. */
    private fun assertLevelOneBestScoreSaved() {
        val loaded = runBlocking { progressRepository.progress.first() }
        val best =
            when (loaded) {
                is DataResult.Success -> loaded.value.bestByLevel.getValue(1)
                is DataResult.Failure -> error("Fortschritt nicht lesbar: $loaded")
            }
        assertEquals(MAX_STARS, best.stars)
        assertEquals(STAR_POINTS, best.points)
    }

    private fun quantityString(
        pluralId: Int,
        quantity: Int,
        vararg args: Any,
    ): String = composeRule.activity.resources.getQuantityString(pluralId, quantity, *args)

    private companion object {
        private const val LOAD_TIMEOUT_MILLIS = 5_000L

        /** Volle Sternwertung (§7.2). */
        private const val MAX_STARS = 3

        /** Volle Punktzahl bei Zügen ≤ Par (§7.2, I5) — 1 Zug bei Par 1. */
        private const val STAR_POINTS = 1500

        /**
         * Winziges, deterministisch in GENAU EINER Drehung lösbares Level nach
         * dem Fallback „Spiegelweg" (§9.5/7): Quelle Weiß auf `(−2,0)` Richtung
         * Ost, ein drehbarer Spiegel mittig auf `(0,0)`, Kristall Weiß auf
         * `(2,0)`. Startorientierung 5 ⇒ ein Rotate `(5→0)` stellt den
         * Parallelfall her (Strahl läuft gerade durch) ⇒ gelöst, par 1.
         */
        private fun tinySolvableLevel(): LevelDefinition =
            LevelDefinition(
                board =
                    Board(
                        radius = 2,
                        elements =
                            mapOf(
                                HexCoord(-2, 0) to Element.Source(LightColor.WHITE, Direction.EAST),
                                HexCoord(0, 0) to Element.Mirror(Orientation(5)),
                                HexCoord(2, 0) to Element.Crystal(LightColor.WHITE),
                            ),
                    ),
                par = 1,
                tier = Difficulty.D1,
                seed = 0L,
            )
    }
}
