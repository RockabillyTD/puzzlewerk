package de.puzzlewerk.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT

/**
 * Backstack-Holder nach ADR-008: Navigation ist eine Listenoperation über
 * [Screen]-Wertobjekten. Es navigiert ausschließlich der Wurzel-Composable;
 * ViewModels kennen diesen Zustand nicht (sie emittieren Effects).
 */
@Stable
class NavigationState internal constructor(
    initialBackstack: List<Screen>,
) {
    init {
        require(initialBackstack.isNotEmpty()) { "Der Backstack darf nie leer sein" }
    }

    /** Aktueller Stack; das erste Element ist die Wurzel. */
    var backstack: List<Screen> by mutableStateOf(initialBackstack)
        private set

    /** Oberster, sichtbarer Screen. */
    val currentScreen: Screen get() = backstack.last()

    /** System-Back poppt nur oberhalb der Wurzel (ADR-008). */
    val canNavigateBack: Boolean get() = backstack.size > 1

    /** Legt [screen] oben auf den Stack. */
    fun navigateTo(screen: Screen) {
        backstack = backstack + screen
    }

    /** Entfernt den obersten Screen; auf der Wurzel ein No-op. */
    fun navigateBack() {
        if (canNavigateBack) {
            backstack = backstack.dropLast(1)
        }
    }

    companion object {
        /** Startzustand: [Screen.Home] ist die Wurzel (ADR-008). */
        fun initial(): NavigationState = NavigationState(listOf(Screen.Home))

        /**
         * Saver für `rememberSaveable`: Screens als kompakte Strings.
         * SavedState kommt von außerhalb des Prozesses (S4) — jeder nicht
         * strikt dekodierbare Eintrag verwirft den GESAMTEN Stack, die App
         * startet dann definiert auf Home statt auf einem geratenen Zustand.
         */
        val saver: Saver<NavigationState, Any> =
            listSaver(
                save = { state -> state.backstack.map(::encodeScreen) },
                restore = { saved ->
                    val screens = saved.mapNotNull(::decodeScreen)
                    if (screens.size == saved.size && screens.firstOrNull() == Screen.Home) {
                        NavigationState(screens)
                    } else {
                        null
                    }
                },
            )
    }
}

/**
 * Persistenter Backstack über Konfigurationswechsel UND Prozess-Tod
 * (ADR-008; Tests: NavigationRestorationTest).
 */
@Composable
fun rememberNavigationState(): NavigationState =
    rememberSaveable(saver = NavigationState.saver) { NavigationState.initial() }

private const val TOKEN_HOME = "home"
private const val TOKEN_LEVEL_SELECT = "levelselect"
private const val TOKEN_DAILY = "daily"
private const val TOKEN_SETTINGS = "settings"
private const val TOKEN_GAME_CAMPAIGN = "game/campaign/"
private const val TOKEN_GAME_DAILY = "game/daily/"

/** Bijektive Kurzform eines [Screen] für den [NavigationState.saver]. */
internal fun encodeScreen(screen: Screen): String =
    when (screen) {
        Screen.Home -> TOKEN_HOME
        Screen.LevelSelect -> TOKEN_LEVEL_SELECT
        Screen.Daily -> TOKEN_DAILY
        Screen.Settings -> TOKEN_SETTINGS
        is Screen.Game ->
            when (val request = screen.request) {
                is LevelRequest.Campaign -> "$TOKEN_GAME_CAMPAIGN${request.levelNumber}"
                is LevelRequest.Daily -> "$TOKEN_GAME_DAILY${request.epochDay}"
            }
    }

/** Strikte Umkehrung von [encodeScreen]; `null` für alles Unbekannte (S4). */
internal fun decodeScreen(encoded: String): Screen? =
    when (encoded) {
        TOKEN_HOME -> Screen.Home
        TOKEN_LEVEL_SELECT -> Screen.LevelSelect
        TOKEN_DAILY -> Screen.Daily
        TOKEN_SETTINGS -> Screen.Settings
        else -> decodeGameScreen(encoded)
    }

private fun decodeGameScreen(encoded: String): Screen.Game? =
    when {
        encoded.startsWith(TOKEN_GAME_CAMPAIGN) ->
            encoded
                .removePrefix(TOKEN_GAME_CAMPAIGN)
                .toIntOrNull()
                ?.takeIf { it in 1..CAMPAIGN_LEVEL_COUNT }
                ?.let { Screen.Game(LevelRequest.Campaign(it)) }
        encoded.startsWith(TOKEN_GAME_DAILY) ->
            encoded
                .removePrefix(TOKEN_GAME_DAILY)
                .toLongOrNull()
                ?.takeIf { it >= 0 }
                ?.let { Screen.Game(LevelRequest.Daily(it)) }
        else -> null
    }
