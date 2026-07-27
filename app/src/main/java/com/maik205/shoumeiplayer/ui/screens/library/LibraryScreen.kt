package com.maik205.shoumeiplayer.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.components.ChipRow
import com.maik205.shoumeiplayer.ui.components.ChipUi
import com.maik205.shoumeiplayer.ui.components.EmptyView
import com.maik205.shoumeiplayer.ui.components.ErrorView
import com.maik205.shoumeiplayer.ui.components.GenreUi
import com.maik205.shoumeiplayer.ui.components.NavRailDestination
import com.maik205.shoumeiplayer.ui.components.NavRailScaffold
import com.maik205.shoumeiplayer.ui.components.SkeletonGrid
import com.maik205.shoumeiplayer.ui.components.SpecSeparator
import com.maik205.shoumeiplayer.ui.navigation.LibraryRoute
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Ash600
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Ink100
import com.maik205.shoumeiplayer.ui.theme.Ink150
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Scrims
import com.maik205.shoumeiplayer.ui.theme.Tungsten
import kotlinx.coroutines.flow.distinctUntilChanged

/** How close (in item count) to the end of the loaded list before the next page is requested. */
private const val LOAD_MORE_THRESHOLD = 20

// §5.4 — pinned header y=27→124, chips at 140, metadata strip above the grid, grid content from 188.
private val HeaderTop = Dimens.OverscanVertical      // 27
private val HeaderNameBottom = 124.dp
private val ChipsY = 140.dp
private val MetaStripBottom = 188.dp
private val GridContentTop = 188.dp
private val GridContentBottom = 54.dp

/** §5.4 — the vignette band under the pinned header, so tiles dissolve as they scroll beneath it. */
private val VignetteHeight = 72.dp

/** §6 — where a state view parks, clear of the pinned header. */
private val StatesTop = 220.dp

private val DialogWidth = 360.dp
private val DialogRowHeight = 40.dp
private val DialogRowRadius = 4.dp

/**
 * §5.4 — Library.
 *
 * The grid fills the screen and scrolls *under* a pinned, solid-black header; `contentPadding.top`
 * (188dp) does the parking, and a 72dp [Scrims.TopVignette] band dissolves tiles at the seam.
 */
@Composable
fun LibraryScreen(
    route: LibraryRoute,
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit,
    onNavigate: (NavRailDestination) -> Unit = {},
) {
    val viewModel = containerViewModel { container ->
        LibraryViewModel(container.libraryRepository, container.imageUrlBuilder, route)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    val gridState = rememberLazyGridState()
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisibleIndex ->
                val items = uiState.items
                if (lastVisibleIndex != null && items.isNotEmpty() &&
                    lastVisibleIndex >= items.size - LOAD_MORE_THRESHOLD
                ) {
                    viewModel.loadMore()
                }
            }
    }

    val firstTileFocus = remember { FocusRequester() }

    NavRailScaffold(
        selected = NavRailDestination.Libraries,
        onSelect = onNavigate,
    ) { contentFocusRequester, railFocusRequester ->
        // M6.3 — the first actionable element on the screen takes focus on arrival. The chip row's
        // leading chip *is* the scaffold's content target: one requester per node, aliased —
        // chaining a second Modifier.focusRequester() on the same node is unspecified behaviour.
        LaunchedEffect(Unit) { runCatching { contentFocusRequester.requestFocus() } }

        var genreDialogOpen by remember { mutableStateOf(false) }
        var sortDialogOpen by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink000),
        ) {
            when {
                // §6 — loading is a skeleton grid at the real tile dimensions, never a spinner.
                uiState.loading && uiState.items.isEmpty() -> SkeletonGrid(
                    modifier = Modifier.padding(top = GridContentTop),
                )

                uiState.items.isEmpty() && uiState.error != null -> ErrorView(
                    message = uiState.error.orEmpty(),
                    onRetry = viewModel::retry,
                    startPadding = Dimens.OverscanHorizontal,
                    topPadding = StatesTop,
                )

                // §6 — a filtered-to-nothing grid offers the way back out; an unfiltered empty
                // library has nothing to act on, so it stays a plain sentence.
                uiState.items.isEmpty() -> {
                    val filtered = uiState.filter != ListingFilter()
                    EmptyView(
                        message = stringResource(
                            if (filtered) R.string.library_empty_filtered else R.string.library_empty_unfiltered,
                        ),
                        actionLabel = stringResource(R.string.action_clear_filter).takeIf { filtered },
                        onAction = if (filtered) {
                            { viewModel.setFilter(ListingFilter()) }
                        } else {
                            null
                        },
                        startPadding = Dimens.OverscanHorizontal,
                        topPadding = StatesTop,
                    )
                }

                else -> PosterGrid(
                    tiles = uiState.items,
                    onTileClick = onNavigateToDetail,
                    state = gridState,
                    contentPadding = PaddingValues(
                        start = Dimens.OverscanHorizontal,
                        end = Dimens.OverscanHorizontal,
                        top = GridContentTop,
                        bottom = GridContentBottom,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer()
                        // Anti-trap: LEFT out of the grid opens the rail. A tile with a tile to
                        // its left resolves that neighbour first, so this only fires from the
                        // leading column.
                        .focusProperties { left = railFocusRequester },
                    firstTileFocus = firstTileFocus,
                    rowZeroUp = contentFocusRequester,
                    onTileFocused = viewModel::onTileFocused,
                )
            }

            // The dissolve band sits between the header and the scrolling grid.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = MetaStripBottom)
                    .height(VignetteHeight)
                    .background(Scrims.TopVignette),
            )

            LibraryHeader(
                title = uiState.title,
                filter = uiState.filter,
                focused = uiState.focused,
                onWatched = viewModel::setWatched,
                onOpenGenreDialog = { genreDialogOpen = true },
                onOpenSortDialog = { sortDialogOpen = true },
                contentFocusRequester = contentFocusRequester,
                railFocusRequester = railFocusRequester,
                gridFirstFocus = firstTileFocus.takeIf { uiState.items.isNotEmpty() },
                modifier = Modifier.align(Alignment.TopStart),
            )

            // §5.4 — "loading more" is a 2dp tungsten indeterminate bar pinned to the bottom edge.
            if (uiState.loadingMore) {
                LinearProgressIndicator(
                    color = Tungsten,
                    trackColor = Color.Transparent,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(2.dp),
                )
            }
        }

        if (genreDialogOpen) {
            OptionDialog(
                title = stringResource(R.string.filter_genre),
                options = buildList {
                    add(DialogOption(label = stringResource(R.string.filter_all), selected = uiState.filter.genre == null))
                    uiState.genres.forEach { genre ->
                        add(DialogOption(label = genre.name, selected = uiState.filter.genre?.id == genre.id, value = genre))
                    }
                },
                onSelect = { option ->
                    @Suppress("UNCHECKED_CAST")
                    viewModel.setGenre(option.value as GenreUi?)
                    genreDialogOpen = false
                },
                onDismiss = { genreDialogOpen = false },
            )
        }

        if (sortDialogOpen) {
            OptionDialog(
                title = stringResource(R.string.filter_sort),
                options = LibrarySort.entries.map { entry ->
                    DialogOption(
                        label = stringResource(entry.labelRes),
                        selected = uiState.filter.sort == entry,
                        value = entry,
                    )
                },
                onSelect = { option ->
                    viewModel.setSort(option.value as LibrarySort)
                    sortDialogOpen = false
                },
                onDismiss = { sortDialogOpen = false },
            )
        }
    }
}

/**
 * §5.4 — the library's own name at `displayMedium`, the chip row at y=140, then the focus metadata
 * strip (focused tile title + spec line) sitting just above the grid.
 *
 * No item-count readout, no rule under the name: the grid below is the evidence of how much is in
 * here.
 */
@Composable
private fun LibraryHeader(
    title: String,
    filter: ListingFilter,
    focused: GridTileUi?,
    onWatched: (WatchedFilter) -> Unit,
    onOpenGenreDialog: () -> Unit,
    onOpenSortDialog: () -> Unit,
    contentFocusRequester: FocusRequester,
    railFocusRequester: FocusRequester,
    gridFirstFocus: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val genreLabel = filter.genre?.name ?: stringResource(R.string.filter_genre)
    val sortLabel = stringResource(filter.sort.labelRes)

    val chips = listOf(
        ChipUi(
            id = "watched:${WatchedFilter.All.name}",
            label = stringResource(WatchedFilter.All.labelRes),
            selected = filter.watched == WatchedFilter.All,
        ),
        ChipUi(
            id = "watched:${WatchedFilter.Unwatched.name}",
            label = stringResource(WatchedFilter.Unwatched.labelRes),
            selected = filter.watched == WatchedFilter.Unwatched,
        ),
        ChipUi(
            id = "watched:${WatchedFilter.Watched.name}",
            label = stringResource(WatchedFilter.Watched.labelRes),
            selected = filter.watched == WatchedFilter.Watched,
        ),
        ChipUi(id = "genre", label = genreLabel, selected = filter.genre != null, hasMenu = true),
        ChipUi(id = "sort", label = sortLabel, selected = false, hasMenu = true),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Ink000)
            .padding(horizontal = Dimens.OverscanHorizontal),
    ) {
        Spacer(modifier = Modifier.height(HeaderTop))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HeaderNameBottom - HeaderTop),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(ChipsY - HeaderNameBottom))

        ChipRow(
            chips = chips,
            onChipClick = { chip ->
                when (chip.id) {
                    "genre" -> onOpenGenreDialog()
                    "sort" -> onOpenSortDialog()
                    else -> when (chip.id) {
                        "watched:${WatchedFilter.All.name}" -> onWatched(WatchedFilter.All)
                        "watched:${WatchedFilter.Unwatched.name}" -> onWatched(WatchedFilter.Unwatched)
                        "watched:${WatchedFilter.Watched.name}" -> onWatched(WatchedFilter.Watched)
                    }
                }
            },
            startPadding = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(contentFocusRequester)
                .focusProperties {
                    // Anti-trap: LEFT out of the chip row opens the rail. A chip with a chip to
                    // its left resolves that neighbour first, so this only fires from the first
                    // chip.
                    left = railFocusRequester
                    if (gridFirstFocus != null) down = gridFirstFocus
                },
        )

        Spacer(modifier = Modifier.height(MetaStripBottom - ChipsY - 36.dp))

        // §5.4 — one-line metadata strip: the focused tile's title + spec line. Per-tile labels in
        // the grid itself are unchanged.
        Text(
            text = focused?.let { tile -> listOfNotNull(tile.title, tile.subtitle).joinToString(SpecSeparator) }.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = Ash600,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class DialogOption(val label: String, val selected: Boolean, val value: Any? = null)

/**
 * §3.5 — the Genre / Sort chip idiom: an [Ink150] plate, 4dp radius, `1.dp Lit @8%` hairline,
 * listing options as pill rows.
 */
@Composable
private fun OptionDialog(
    title: String,
    options: List<DialogOption>,
    onSelect: (DialogOption) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(DialogWidth)
                .background(Ink150, RoundedCornerShape(4.dp))
                .border(1.dp, Lit.copy(alpha = Alpha.Hairline), RoundedCornerShape(4.dp))
                .padding(20.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyColumn(
                modifier = Modifier.focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(options, key = { it.label }) { option ->
                    OptionRow(option = option, onClick = { onSelect(option) })
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    option: DialogOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DialogRowHeight)
            .clip(RoundedCornerShape(DialogRowRadius))
            .background(if (focused || option.selected) Paper else Ink100)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.titleSmall,
            color = if (focused || option.selected) Ink000 else Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
