package de.puzzlewerk.app.ui.levelselect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.puzzlewerk.app.R
import de.puzzlewerk.app.ui.navigation.Screen
import de.puzzlewerk.app.ui.theme.PuzzlewerkTheme
import de.puzzlewerk.game.level.CAMPAIGN_LEVEL_COUNT
import de.puzzlewerk.game.level.Difficulty
import de.puzzlewerk.game.level.campaignTier

private val TILE_MIN_SIZE = 80.dp
private const val LOCKED_ALPHA = 0.45f
private const val MAX_STARS = 3

// Vorhängeschloss-Geometrie als Bruchteile der Canvas-Kante (§13-Sperr-Symbol).
private const val LOCK_BODY_LEFT = 0.2f
private const val LOCK_BODY_TOP = 0.45f
private const val LOCK_BODY_WIDTH = 0.6f
private const val LOCK_BODY_HEIGHT = 0.45f
private const val LOCK_CORNER = 0.1f
private const val LOCK_SHACKLE_LEFT = 0.3f
private const val LOCK_SHACKLE_TOP = 0.2f
private const val LOCK_SHACKLE_SIZE = 0.4f
private const val LOCK_STROKE = 0.1f
private const val HALF_TURN_DEGREES = 180f

// Preview-Kennzahlen (nur Fake-State, keine Spiellogik).
private const val PREVIEW_UNLOCK_BUFFER = 3
private const val PREVIEW_BASE_POINTS = 1000
private const val PREVIEW_POINTS_STEP = 5

/**
 * Verdrahtung der Levelauswahl: baut das ViewModel über die gemeinsame Factory
 * (ADR-006), sammelt Navigations-Effects und übersetzt sie in Navigation.
 * Tiefere Composables sehen nur State + Intents (docs/ui-architektur.md §2).
 */
@Composable
fun LevelSelectRoute(
    viewModelFactory: ViewModelProvider.Factory,
    onNavigate: (Screen) -> Unit,
) {
    val viewModel: LevelSelectViewModel = viewModel(factory = viewModelFactory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNavigate by rememberUpdatedState(onNavigate)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LevelSelectEffect.NavigateToGame -> currentOnNavigate(Screen.Game(effect.request))
            }
        }
    }
    LevelSelectScreen(state = state, onIntent = viewModel::onIntent)
}

/** Levelauswahl §12.4 — reine Funktion des [LevelSelectUiState], preview-fähig. */
@Composable
fun LevelSelectScreen(
    state: LevelSelectUiState,
    onIntent: (LevelSelectIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingContent(modifier)
        state.hasLoadError -> ErrorContent(onIntent = onIntent, modifier = modifier)
        else -> LevelGrid(state = state, onIntent = onIntent, modifier = modifier)
    }
}

@Composable
private fun LoadingContent(modifier: Modifier) {
    val description = stringResource(R.string.level_select_loading)
    Box(
        modifier = modifier.fillMaxSize().semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = description, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ErrorContent(
    onIntent: (LevelSelectIntent) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.level_select_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = { onIntent(LevelSelectIntent.ResetProgress) },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
        ) {
            Text(text = stringResource(R.string.level_select_reset))
        }
    }
}

@Composable
private fun LevelGrid(
    state: LevelSelectUiState,
    onIntent: (LevelSelectIntent) -> Unit,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = TILE_MIN_SIZE),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            LevelSelectHeader(totalStars = state.totalStars, totalScore = state.totalScore)
        }
        items(items = state.tiles, key = { it.levelNumber }) { tile ->
            LevelTileItem(tile = tile, onIntent = onIntent)
        }
    }
}

@Composable
private fun LevelSelectHeader(
    totalStars: Int,
    totalScore: Int,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.screen_title_level_select),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = pluralStringResource(R.plurals.level_select_total_stars, totalStars, totalStars),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = pluralStringResource(R.plurals.level_select_total_score, totalScore, totalScore),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LevelTileItem(
    tile: LevelTile,
    onIntent: (LevelSelectIntent) -> Unit,
) {
    val tierLabel = stringResource(R.string.level_select_tier, tile.tier.ordinal + 1)
    val description = tileContentDescription(tile, tierLabel)
    val tappable = tile.state !is TileState.Locked
    val playLabel = stringResource(R.string.level_select_play)

    val base =
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(tileBackground(tile.state))
    val interactive =
        if (tappable) {
            base.clickable(onClickLabel = playLabel) {
                onIntent(LevelSelectIntent.TileClicked(tile.levelNumber))
            }
        } else {
            base
        }

    Box(
        modifier = interactive.semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Visuelle Ebene ohne eigene Semantik — die Kachel spricht als Ganzes.
        TileVisuals(tile = tile, tierLabel = tierLabel, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun TileVisuals(
    tile: LevelTile,
    tierLabel: String,
    modifier: Modifier,
) {
    val dimmed = if (tile.state is TileState.Locked) Modifier.alpha(LOCKED_ALPHA) else Modifier
    Column(
        modifier = modifier.padding(6.dp).then(dimmed),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text = tierLabel, style = MaterialTheme.typography.labelSmall)
        Text(
            text = tile.levelNumber.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        TileStateIndicator(state = tile.state)
    }
}

@Composable
private fun TileStateIndicator(state: TileState) {
    when (state) {
        TileState.Locked -> LockGlyph()
        TileState.Open -> Unit
        is TileState.Solved -> StarRow(stars = state.stars)
    }
}

/** Sterne als Formkanal (§13): Anzahl gefüllter Sterne kodiert die Wertung, nicht Farbe. */
@Composable
private fun StarRow(stars: Int) {
    val filled = stringResource(R.string.level_select_stars_filled)
    val empty = stringResource(R.string.level_select_stars_empty)
    val text =
        buildString {
            repeat(stars) { append(filled) }
            repeat(MAX_STARS - stars) { append(empty) }
        }
    Text(text = text, style = MaterialTheme.typography.labelMedium)
}

/** Gezeichnetes Vorhängeschloss als sichtbares (nicht nur farbliches) Sperr-Symbol (§13). */
@Composable
private fun LockGlyph() {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * LOCK_BODY_LEFT, h * LOCK_BODY_TOP),
            size = Size(w * LOCK_BODY_WIDTH, h * LOCK_BODY_HEIGHT),
            cornerRadius = CornerRadius(w * LOCK_CORNER, w * LOCK_CORNER),
        )
        drawArc(
            color = color,
            startAngle = HALF_TURN_DEGREES,
            sweepAngle = HALF_TURN_DEGREES,
            useCenter = false,
            topLeft = Offset(w * LOCK_SHACKLE_LEFT, h * LOCK_SHACKLE_TOP),
            size = Size(w * LOCK_SHACKLE_SIZE, h * LOCK_SHACKLE_SIZE),
            style = Stroke(width = w * LOCK_STROKE),
        )
    }
}

@Composable
private fun tileBackground(state: TileState): Color =
    when (state) {
        is TileState.Solved -> MaterialTheme.colorScheme.secondaryContainer
        TileState.Open -> MaterialTheme.colorScheme.surfaceVariant
        TileState.Locked -> MaterialTheme.colorScheme.surface
    }

@Composable
private fun tileContentDescription(
    tile: LevelTile,
    tierLabel: String,
): String =
    when (val s = tile.state) {
        TileState.Locked -> stringResource(R.string.level_select_cd_locked, tile.levelNumber, tierLabel)
        TileState.Open -> stringResource(R.string.level_select_cd_open, tile.levelNumber, tierLabel)
        is TileState.Solved ->
            stringResource(R.string.level_select_cd_solved, tile.levelNumber, tierLabel, s.stars, s.points)
    }

// ---- Previews (Fake-State, ohne ViewModel/Container) ----

private fun previewTiles(highestSolved: Int): List<LevelTile> =
    (1..CAMPAIGN_LEVEL_COUNT).map { n ->
        val state =
            when {
                n <= highestSolved ->
                    TileState.Solved(
                        stars = (n % MAX_STARS) + 1,
                        points = PREVIEW_BASE_POINTS + n * PREVIEW_POINTS_STEP,
                    )
                n <= highestSolved + PREVIEW_UNLOCK_BUFFER -> TileState.Open
                else -> TileState.Locked
            }
        LevelTile(levelNumber = n, tier = campaignTier(n), state = state)
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun LevelSelectScreenPreview() {
    PuzzlewerkTheme {
        LevelSelectScreen(
            state =
                LevelSelectUiState(
                    isLoading = false,
                    tiles = previewTiles(highestSolved = 5),
                    totalStars = 11,
                    totalScore = 6075,
                ),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LevelSelectFirstStartPreview() {
    PuzzlewerkTheme {
        LevelSelectScreen(
            state = LevelSelectUiState(isLoading = false, tiles = previewTiles(highestSolved = 0)),
            onIntent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LevelSelectErrorPreview() {
    PuzzlewerkTheme {
        LevelSelectScreen(state = LevelSelectUiState(isLoading = false, hasLoadError = true), onIntent = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun LevelSelectDifficultyPreview() {
    // Referenziert Difficulty, damit die Tier-Anzeige-Annahme (D1..D7) sichtbar bleibt.
    val allTiers: List<Difficulty> = Difficulty.entries
    PuzzlewerkTheme {
        LevelSelectScreen(
            state = LevelSelectUiState(isLoading = false, tiles = previewTiles(highestSolved = allTiers.size)),
            onIntent = {},
        )
    }
}
