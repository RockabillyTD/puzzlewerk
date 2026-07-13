package de.puzzlewerk.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import de.puzzlewerk.app.ui.home.HomeRoute
import de.puzzlewerk.app.ui.levelselect.LevelSelectRoute
import de.puzzlewerk.app.ui.navigation.NavigationState
import de.puzzlewerk.app.ui.navigation.Screen
import de.puzzlewerk.app.ui.navigation.rememberNavigationState
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme

/**
 * Wurzel-Composable: hält den Backstack (ADR-008) und ordnet jedem [Screen]
 * seinen Inhalt zu. Navigiert wird ausschließlich hier; Edge-to-Edge-Insets
 * behandelt `safeDrawingPadding` (targetSdk 36 erzwingt Edge-to-Edge).
 */
@Composable
fun PuzzlewerkApp(
    viewModelFactory: ViewModelProvider.Factory,
    navigationState: NavigationState = rememberNavigationState(),
) {
    PuzzlewerkTheme {
        BackHandler(enabled = navigationState.canNavigateBack) {
            navigationState.navigateBack()
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
            ) {
                ScreenContent(
                    screen = navigationState.currentScreen,
                    viewModelFactory = viewModelFactory,
                    onNavigate = navigationState::navigateTo,
                )
            }
        }
    }
}

/** Exhaustives Screen-Mapping (ADR-008): ein neuer Screen erzwingt hier Behandlung. */
@Composable
private fun ScreenContent(
    screen: Screen,
    viewModelFactory: ViewModelProvider.Factory,
    onNavigate: (Screen) -> Unit,
) {
    when (screen) {
        Screen.Home -> HomeRoute(viewModelFactory = viewModelFactory, onNavigate = onNavigate)
        Screen.LevelSelect -> LevelSelectRoute(viewModelFactory = viewModelFactory, onNavigate = onNavigate)
        is Screen.Game -> PlaceholderScreen(title = stringResource(R.string.screen_title_game))
        Screen.Daily -> PlaceholderScreen(title = stringResource(R.string.screen_title_daily))
        Screen.Settings -> PlaceholderScreen(title = stringResource(R.string.screen_title_settings))
    }
}

/**
 * Übergangsinhalt, bis die Screen-Tickets ihn ersetzen (Home: PW-3.3b,
 * Spielfeld: PW-3.4/3.5, Levelauswahl: PW-3.6, Daily/Settings: Phase 4).
 */
@Composable
private fun PlaceholderScreen(title: String) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.placeholder_screen_hint),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaceholderScreenPreview() {
    PuzzlewerkTheme {
        PlaceholderScreen(title = stringResource(R.string.screen_title_level_select))
    }
}
