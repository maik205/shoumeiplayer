package com.maik205.shoumeiplayer.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.di.AppContainer
import com.maik205.shoumeiplayer.ui.components.EmptyView
import com.maik205.shoumeiplayer.ui.components.ErrorView
import com.maik205.shoumeiplayer.ui.components.MediaCardUi
import com.maik205.shoumeiplayer.ui.components.MediaRow
import com.maik205.shoumeiplayer.ui.components.NavRailDestination
import com.maik205.shoumeiplayer.ui.components.NavRailScaffold
import com.maik205.shoumeiplayer.ui.components.SkeletonGrid
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.screens.library.GridTileUi
import com.maik205.shoumeiplayer.ui.screens.library.PosterGrid
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Ink100
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Tungsten
import com.maik205.shoumeiplayer.ui.theme.focusTween

// §5.6 — left column is the keyboard, 360dp wide; the live query sits above it.
private val KeyboardColumnWidth = 360.dp
private val QueryLineTop = 40.dp
private val KeyboardTop = 24.dp
private val ResultsTop = Dimens.OverscanVertical
private val StatesTop = 96.dp

// The results pane's left inset. Must match the other grids' start inset: a focused tile scales
// 1.04 and paints a 2dp rim outside its own bounds, so a 0dp start clips the first column's rim.
private val ResultsStart = Dimens.OverscanHorizontal

// §3.3 key metrics: 48dp square unit, 8dp gap in both axes.
private val KeyUnit = 48.dp
private val KeyGap = 8.dp

/**
 * §5.6 — Search.
 *
 * There is no text-input field and no IME anywhere on this screen (brief risk 4): the query is a
 * read-only [Text] with a drawn caret, and every character comes from [KeyboardGrid.Default]
 * rendered as real, individually-focusable keys so the platform focus rim is truthful rather than
 * app-painted. D-pad navigation across the grid is driven by the pure [KeyboardGrid.move] reducer
 * so row-to-row travel (the ragged bottom row especially) clamps predictably; an unchanged move
 * means "leave the keyboard" and is left unconsumed for the rail/results focus escape hatches.
 *
 * There is no result count and no searching label: during the 350ms debounce the grid renders
 * skeleton tiles, which *is* the loading state.
 */
@Composable
fun SearchScreen(
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit,
    onNavigate: (NavRailDestination) -> Unit = {},
) {
    val viewModel = containerViewModel { container: AppContainer ->
        SearchViewModel(container.libraryRepository, container.imageUrlBuilder)
    }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    val trimmed = query.trim()
    val isBlank = trimmed.isEmpty()
    val hasResults = !isBlank && uiState.results.isNotEmpty()
    val hasSuggestions = isBlank && suggestions.isNotEmpty()
    // §5.6 / §6 — the debounce window and the request itself share one state: skeleton tiles.
    val pending = !isBlank &&
        trimmed.length >= MIN_QUERY_LENGTH &&
        uiState.error == null &&
        (uiState.searching || uiState.loading)

    // The one hop both the keyboard's rightmost column and the rail's RIGHT land on.
    val resultsFirstFocus = remember { FocusRequester() }
    val hasRightTarget = hasResults || hasSuggestions

    NavRailScaffold(
        selected = NavRailDestination.Search,
        onSelect = onNavigate,
        modifier = Modifier.fillMaxSize(),
    ) { contentFocusRequester, railFocusRequester ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink000),
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                KeyboardColumn(
                    query = query,
                    onKey = viewModel::onKey,
                    contentFocusRequester = contentFocusRequester,
                    railFocusRequester = railFocusRequester,
                    resultsFirstFocus = resultsFirstFocus,
                    hasRightTarget = hasRightTarget,
                    modifier = Modifier
                        .width(KeyboardColumnWidth)
                        .fillMaxHeight()
                        .padding(start = Dimens.OverscanHorizontal, top = Dimens.OverscanVertical),
                )

                Spacer(modifier = Modifier.width(Dimens.Gutter))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    when {
                        hasSuggestions -> MediaRow(
                            title = stringResource(R.string.suggested),
                            items = suggestions.map(GridTileUi::toMediaCardUi),
                            onItemClick = onNavigateToDetail,
                            firstItemFocusRequester = resultsFirstFocus,
                            startPadding = ResultsStart,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .padding(top = ResultsTop),
                        )

                        isBlank -> Unit // No hint fell back to anything: leave the pane empty.

                        hasResults -> PosterGrid(
                            tiles = uiState.results,
                            onTileClick = onNavigateToDetail,
                            contentPadding = PaddingValues(
                                start = ResultsStart,
                                end = Dimens.OverscanHorizontal,
                                top = ResultsTop,
                                bottom = 54.dp,
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRestorer(),
                            firstTileFocus = resultsFirstFocus,
                        )

                        pending -> SkeletonGrid(
                            startPadding = ResultsStart,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = ResultsTop),
                        )

                        uiState.error != null -> ErrorView(
                            message = uiState.error.orEmpty(),
                            onRetry = { viewModel.retry() },
                            modifier = Modifier.align(Alignment.TopStart),
                            startPadding = ResultsStart,
                            topPadding = StatesTop,
                        )

                        trimmed.length < MIN_QUERY_LENGTH -> Text(
                            text = stringResource(R.string.search_keep_typing),
                            style = MaterialTheme.typography.displaySmall,
                            color = Paper.copy(alpha = Alpha.TextDisabled),
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = ResultsStart, top = StatesTop)
                                .width(Dimens.BodyMaxWidth * 2),
                        )

                        else -> EmptyView(
                            message = stringResource(R.string.search_no_results_format, trimmed),
                            actionLabel = stringResource(R.string.action_clear_search),
                            onAction = { viewModel.onQueryChange("") },
                            modifier = Modifier.align(Alignment.TopStart),
                            startPadding = ResultsStart,
                            topPadding = StatesTop,
                        )
                    }
                }
            }
        }
    }
}

/** The keyboard's left column: the read-only query line, then the non-lazy key grid. */
@Composable
private fun KeyboardColumn(
    query: String,
    onKey: (KeyAction) -> Unit,
    contentFocusRequester: FocusRequester,
    railFocusRequester: FocusRequester,
    resultsFirstFocus: FocusRequester,
    hasRightTarget: Boolean,
    modifier: Modifier = Modifier,
) {
    val grid = KeyboardGrid.Default
    // The (0,0) key *is* the scaffold's content target: one requester per node, aliased — chaining a
    // second Modifier.focusRequester() on the same node is unspecified behaviour.
    val keyFocusRequesters = remember(grid, contentFocusRequester) {
        grid.rows.mapIndexed { rowIndex, row ->
            row.mapIndexed { colIndex, _ ->
                if (rowIndex == 0 && colIndex == 0) contentFocusRequester else FocusRequester()
            }
        }
    }
    var cursor by rememberSaveable(stateSaver = KeyboardCursorSaver) {
        mutableStateOf(KeyboardCursor(0, 0))
    }

    LaunchedEffect(Unit) { runCatching { contentFocusRequester.requestFocus() } }

    Column(modifier = modifier) {
        Spacer(modifier = Modifier.height(QueryLineTop))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchGlyph(
                modifier = Modifier.padding(end = 16.dp),
                color = Paper.copy(alpha = Alpha.TextTertiary),
            )
            Text(
                text = query,
                style = MaterialTheme.typography.displayMedium,
                color = Paper,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(4.dp))
            // §5.6 — a caret block, not a blinking input-field cursor: display text only.
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(Tungsten),
            )
        }
        Spacer(modifier = Modifier.height(KeyboardTop))

        Column(
            modifier = Modifier
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val direction = when (event.key) {
                        Key.DirectionUp -> KeyboardDirection.Up
                        Key.DirectionDown -> KeyboardDirection.Down
                        Key.DirectionLeft -> KeyboardDirection.Left
                        Key.DirectionRight -> KeyboardDirection.Right
                        else -> return@onPreviewKeyEvent false
                    }
                    val next = grid.move(cursor, direction)
                    if (next == cursor) {
                        // Unchanged: let default focus search take over (rail on LEFT at col 0,
                        // the results pane on RIGHT at the last col of a row).
                        false
                    } else {
                        cursor = next
                        runCatching { keyFocusRequesters[next.row][next.col].requestFocus() }
                        true
                    }
                },
            verticalArrangement = Arrangement.spacedBy(KeyGap),
        ) {
            grid.rows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(KeyGap)) {
                    row.forEachIndexed { colIndex, key ->
                        var keyModifier = Modifier
                            .focusRequester(keyFocusRequesters[rowIndex][colIndex])
                            .onFocusChanged { if (it.isFocused) cursor = KeyboardCursor(rowIndex, colIndex) }
                        if (colIndex == 0) {
                            // LEFT from the leading key column falls through to the rail.
                            keyModifier = keyModifier.focusProperties { left = railFocusRequester }
                        }
                        if (colIndex == row.lastIndex && hasRightTarget) {
                            keyModifier = keyModifier.focusProperties { right = resultsFirstFocus }
                        }
                        KeyboardKeyView(
                            keyboardKey = key,
                            onSelect = { onKey(key.action) },
                            modifier = keyModifier,
                        )
                    }
                }
            }
        }
    }
}

private val KeyboardCursorSaver: Saver<KeyboardCursor, List<Int>> = Saver(
    save = { listOf(it.row, it.col) },
    restore = { KeyboardCursor(it[0], it[1]) },
)

/** A single key: 48dp tall, `span` units wide, individually focusable so the focus rim is real. */
@Composable
private fun KeyboardKeyView(
    keyboardKey: KeyboardKey,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val width: Dp = KeyUnit * keyboardKey.span + KeyGap * (keyboardKey.span - 1)
    val shape = RoundedCornerShape(4.dp)

    val rim by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = focusTween(focused),
        label = "keyRim",
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else Alpha.TextTertiary,
        animationSpec = focusTween(focused),
        label = "keyLabel",
    )

    // F7 — `KeyboardGrid` is deliberately pure Kotlin with no Compose/Android imports (its own
    // KDoc), so its key labels stay plain literals. A single letter/digit is not a translatable
    // sentence; Space/Delete/Clear are, so they route through strings.xml at this Composable call
    // site instead.
    val displayLabel = when (keyboardKey.action) {
        is KeyAction.Space -> stringResource(R.string.key_space)
        is KeyAction.Delete -> stringResource(R.string.key_delete)
        is KeyAction.Clear -> stringResource(R.string.key_clear)
        is KeyAction.Char -> keyboardKey.label
    }

    Box(
        modifier = modifier
            .width(width)
            .height(KeyUnit)
            .clip(shape)
            .background(Ink100)
            .border(2.dp, Lit.copy(alpha = rim), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // §1 — no ripples
                onClick = onSelect,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.titleSmall,
            color = Paper.copy(alpha = labelAlpha),
            maxLines = 1,
        )
    }
}

private fun GridTileUi.toMediaCardUi(): MediaCardUi = MediaCardUi(
    id = id,
    title = title,
    subtitle = subtitle,
    imageUrl = imageUrl,
    progressFraction = progressFraction,
    watched = watched,
    blurHash = blurHash,
    aspect = aspect,
)

/** §5.6 — the `⌕` mark, hand-drawn so it does not depend on a glyph in the TV build's font. */
@Composable
private fun SearchGlyph(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(28.dp)) {
        val r = size.minDimension * 0.32f
        val cx = size.width * 0.40f
        val cy = size.height * 0.40f
        val stroke = size.minDimension * 0.09f
        drawCircle(color = color, radius = r, center = Offset(cx, cy), style = Stroke(width = stroke))
        drawLine(
            color = color,
            start = Offset(cx + r * 0.72f, cy + r * 0.72f),
            end = Offset(size.width * 0.92f, size.height * 0.92f),
            strokeWidth = stroke,
        )
    }
}
