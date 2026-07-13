package de.puzzlewerk.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.navigation.Screen
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme

/**
 * Verdrahtung des Home-Screens: baut das ViewModel über die gemeinsame
 * Factory (ADR-006), sammelt Effects und übersetzt sie in Navigation.
 * Tiefere Composables sehen nur State + Intents (docs/ui-architektur.md §2).
 */
@Composable
fun HomeRoute(
    viewModelFactory: ViewModelProvider.Factory,
    onNavigate: (Screen) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToGame -> currentOnNavigate(Screen.Game(effect.request))
                HomeEffect.NavigateToLevelSelect -> currentOnNavigate(Screen.LevelSelect)
            }
        }
    }
    HomeScreen(state = state, onIntent = viewModel::onIntent)
}

/** Home §12.2 — reine Funktion des [HomeUiState], vollständig preview-fähig. */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        if (state.isLoading) {
            LoadingIndicator()
        } else {
            HomeActions(state = state, onIntent = onIntent)
        }
    }
}

@Composable
private fun LoadingIndicator() {
    val description = stringResource(R.string.home_loading)
    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description })
}

@Composable
private fun HomeActions(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
) {
    if (state.hasLoadError) {
        Text(
            text = stringResource(R.string.home_load_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
    ContinueButton(state = state, onIntent = onIntent)
    Spacer(modifier = Modifier.height(16.dp))
    SecondaryActions(onIntent = onIntent)
}

@Composable
private fun SecondaryActions(onIntent: (HomeIntent) -> Unit) {
    DailyButton()
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { onIntent(HomeIntent.LevelSelectClicked) },
        modifier = Modifier.secondaryButton(),
    ) {
        Text(text = stringResource(R.string.screen_title_level_select))
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = Modifier.secondaryButton(),
    ) {
        Text(text = stringResource(R.string.screen_title_settings))
    }
}

@Composable
private fun ContinueButton(
    state: HomeUiState,
    onIntent: (HomeIntent) -> Unit,
) {
    Button(
        onClick = { onIntent(HomeIntent.ContinueClicked) },
        enabled = !state.hasLoadError,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.home_continue),
                style = MaterialTheme.typography.titleMedium,
            )
            // Im Fehlerzustand ist der Fortschritt unbekannt — kein Ziel vorgaukeln.
            if (!state.hasLoadError) {
                Text(
                    text =
                        when (val target = state.continueTarget) {
                            is ContinueTarget.Level -> stringResource(R.string.home_continue_level, target.levelNumber)
                            ContinueTarget.AllSolved -> stringResource(R.string.home_continue_all_solved)
                        },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Tägliches Prisma: in Phase 3 deaktiviert, mit „bald"-Badge (§12.2/Ticket). */
@Composable
private fun DailyButton() {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = Modifier.secondaryButton(),
    ) {
        Text(text = stringResource(R.string.screen_title_daily))
        Spacer(modifier = Modifier.width(8.dp))
        Badge { Text(text = stringResource(R.string.home_badge_soon)) }
    }
}

/** Touch-Target ≥ 48 dp (§13.6) für alle sekundären Buttons. */
private fun Modifier.secondaryButton(): Modifier =
    fillMaxWidth()
        .heightIn(min = 48.dp)

private const val PREVIEW_LEVEL_NUMBER = 3

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    PuzzlewerkTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false, continueTarget = ContinueTarget.Level(PREVIEW_LEVEL_NUMBER)),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenAllSolvedPreview() {
    PuzzlewerkTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false, continueTarget = ContinueTarget.AllSolved),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenErrorPreview() {
    PuzzlewerkTheme {
        HomeScreen(
            state = HomeUiState(isLoading = false, hasLoadError = true),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenLoadingPreview() {
    PuzzlewerkTheme {
        HomeScreen(state = HomeUiState(), onIntent = {})
    }
}
