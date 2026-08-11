package com.maik205.shoumeiplayer.ui.television.screens.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.i18n.resolve
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBackground
import com.maik205.shoumeiplayer.ui.television.components.TelevisionAppTopNavigation
import com.maik205.shoumeiplayer.ui.television.components.TelevisionEmptyState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.TelevisionMediaTile
import com.maik205.shoumeiplayer.ui.television.components.TelevisionRowHeader
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import com.maik205.shoumeiplayer.ui.television.model.HeroUi
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.domain.model.MediaShelf as MediaShelfUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.abs

@Composable
fun TelevisionSearchScreen(
    query: String,
    searching: Boolean,
    results: List<MediaItemUi>,
    error: UiText?,
    resultLimitReached: Boolean,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenItem: (MediaItemUi) -> Unit,
    onBack: () -> Unit,
    libraries: List<LibraryDestinationUi>,
    userName: String,
    avatarUrl: String?,
    onNavigateHome: () -> Unit,
    onNavigateLibrary: (LibraryDestinationUi) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateProfile: () -> Unit,
    navigationState: LazyListState,
) {
    val fieldFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val topNavigationFocus = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    var retryFocused by remember { mutableStateOf(false) }
    var focusedResultId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusedResultIndex by rememberSaveable { mutableIntStateOf(0) }
    var restoreResultId by rememberSaveable { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val resultIds = results.map(MediaItemUi::id)
    val resultFocusRequesters = remember(resultIds) { List(results.size) { FocusRequester() } }
    val scope = rememberCoroutineScope()
    var resultFocusJob by remember { mutableStateOf<Job?>(null) }
    val currentRestoreResultId by rememberUpdatedState(restoreResultId)
    val visibleResults = error == null && results.isNotEmpty() && query.trim().length >= 2
    val focusedResult = focusedResultId
        ?.let { id -> results.firstOrNull { it.id == id } }
        ?: results.firstOrNull()
    val resultStatus = when {
        searching -> stringResource(R.string.tv_search_searching)
        resultLimitReached -> stringResource(R.string.tv_search_limit)
        results.isNotEmpty() -> pluralStringResource(
            R.plurals.tv_search_result_count,
            results.size,
            results.size,
        )
        else -> null
    }

    fun requestResultFocus(index: Int) {
        if (index !in results.indices) return
        resultFocusJob?.cancel()
        if (gridState.layoutInfo.visibleItemsInfo.any { it.index == index }) {
            runCatching { resultFocusRequesters[index].requestFocus() }
            return
        }
        resultFocusJob = scope.launch {
            gridState.animateScrollToItem(index)
            androidx.compose.runtime.withFrameNanos { }
            runCatching { resultFocusRequesters[index].requestFocus() }
        }
    }

    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            val itemId = currentRestoreResultId ?: return@launch
            repeat(12) {
                val target = results.indexOfFirst { it.id == itemId }
                    .takeIf { it >= 0 }
                    ?: focusedResultIndex.coerceIn(0, results.lastIndex.coerceAtLeast(0))
                if (visibleResults && target in results.indices) {
                    gridState.scrollToItem(target)
                    androidx.compose.runtime.withFrameNanos { }
                    if (runCatching { resultFocusRequesters[target].requestFocus() }.getOrDefault(false)) {
                        restoreResultId = null
                        return@launch
                    }
                }
                androidx.compose.runtime.withFrameNanos { }
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    LaunchedEffect(error) {
        if (error == null && retryFocused) runCatching { fieldFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TelevisionTheme.colors.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    end = TelevisionDimensions.SafeHorizontal,
                    top = 122.dp,
                    bottom = TelevisionDimensions.SafeBottom,
                ),
            horizontalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(SearchGridColumns),
                state = gridState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                when {
                    searching && results.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        TelevisionLoadingState(
                            label = stringResource(R.string.search),
                            shape = TelevisionLoadingShape.Search,
                        )
                    }
                    error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                        TelevisionErrorState(
                            title = stringResource(R.string.tv_search_unavailable),
                            message = error.resolve(),
                            onRetry = {
                                onRetry()
                                runCatching { fieldFocus.requestFocus() }
                            },
                            retryLabel = stringResource(R.string.retry),
                            focusRequester = retryFocus,
                            requestInitialFocus = false,
                            modifier = Modifier
                                .onFocusChanged { retryFocused = it.hasFocus }
                                .focusProperties { up = fieldFocus },
                        )
                    }
                    query.isBlank() -> Unit
                    query.trim().length < 2 -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = stringResource(R.string.search_keep_typing),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                            ),
                            color = TelevisionTheme.colors.PaperSoft,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    results.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        TelevisionEmptyState(
                            title = stringResource(R.string.search_no_results_format, query),
                        )
                    }
                    else -> itemsIndexed(
                        results,
                        key = { index, item -> "${item.id}:$index" },
                    ) { index, item ->
                        TelevisionSearchResultCard(
                            item = item,
                            anyResultFocused = focusedResultId != null,
                            focusRequester = resultFocusRequesters[index],
                            onClick = {
                                restoreResultId = item.id
                                onOpenItem(item)
                            },
                            onFocused = {
                                focusedResultId = item.id
                                focusedResultIndex = index
                            },
                            modifier = Modifier.onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (val move = resolveSearchGridMove(index, results.size, event.key)) {
                                    SearchGridMove.Field -> {
                                        fieldFocus.requestFocus()
                                        true
                                    }
                                    is SearchGridMove.Result -> {
                                        requestResultFocus(move.index)
                                        true
                                    }
                                    SearchGridMove.Blocked -> true
                                    SearchGridMove.Unhandled -> false
                                }
                            },
                        )
                    }
                }
            }

            if (visibleResults && focusedResult != null) {
                TelevisionSearchInspector(
                    item = focusedResult,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    end = TelevisionDimensions.SafeHorizontal,
                    top = 46.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TelevisionFocusRevealButton(
                label = stringResource(R.string.tv_back),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                expandedWidth = 92.dp,
                focusRequester = backFocus,
                modifier = Modifier.focusProperties {
                    up = topNavigationFocus
                    left = FocusRequester.Cancel
                    right = fieldFocus
                    down = FocusRequester.Cancel
                },
            )
            Spacer(Modifier.width(16.dp))
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (focused) TelevisionTheme.colors.Paper else TelevisionTheme.colors.PaperMuted,
            )
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { value ->
                    restoreResultId = null
                    focusedResultId = null
                    focusedResultIndex = 0
                    onQueryChange(value)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .focusRequester(fieldFocus)
                    .focusProperties { up = topNavigationFocus }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                keyboardController?.show()
                                true
                            }
                            Key.DirectionLeft -> {
                                backFocus.requestFocus()
                                true
                            }
                            Key.DirectionRight -> if (query.isNotBlank()) {
                                clearFocus.requestFocus()
                                true
                            } else {
                                false
                            }
                            Key.DirectionDown -> when {
                                visibleResults -> {
                                    val target = focusedResultId
                                        ?.let { id -> results.indexOfFirst { it.id == id } }
                                        ?.takeIf { it >= 0 }
                                        ?: focusedResultIndex.coerceIn(0, results.lastIndex)
                                    requestResultFocus(target)
                                    true
                                }
                                error != null -> {
                                    retryFocus.requestFocus()
                                    true
                                }
                                else -> true
                            }
                            else -> false
                        }
                    }
                    .onFocusChanged { focused = it.isFocused },
                singleLine = true,
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    color = TelevisionTheme.colors.Paper,
                ),
                cursorBrush = SolidColor(TelevisionTheme.colors.Paper),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    showKeyboardOnFocus = false,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        when {
                            visibleResults -> requestResultFocus(
                                focusedResultIndex.coerceIn(0, results.lastIndex),
                            )
                            error != null -> retryFocus.requestFocus()
                        }
                    },
                ),
                decorationBox = { input ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.displaySmall,
                                color = TelevisionTheme.colors.PaperSoft,
                            )
                        }
                        input()
                    }
                },
            )
            if (query.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                TelevisionFocusRevealButton(
                    label = stringResource(R.string.tv_search_clear),
                    icon = Icons.Default.Close,
                    onClick = {
                        restoreResultId = null
                        focusedResultId = null
                        focusedResultIndex = 0
                        onQueryChange("")
                        runCatching { fieldFocus.requestFocus() }
                    },
                    focusRequester = clearFocus,
                    expandedWidth = 88.dp,
                    modifier = Modifier.focusProperties {
                        left = fieldFocus
                        right = FocusRequester.Cancel
                        up = topNavigationFocus
                        down = if (visibleResults) resultFocusRequesters.first() else FocusRequester.Cancel
                    },
                )
            }
            resultStatus?.let { status ->
                Spacer(Modifier.width(18.dp))
                if (searching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.3.dp,
                        color = TelevisionTheme.colors.PaperMuted,
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 8.5.sp,
                        lineHeight = 10.sp,
                    ),
                    color = TelevisionTheme.colors.PaperMuted,
                    maxLines = 1,
                )
            }
        }

        AppTopNavigation(
            libraries = libraries,
            selectedKey = "search",
            userName = userName,
            avatarUrl = avatarUrl,
            onNavigateHome = onNavigateHome,
            onNavigateSearch = {},
            onNavigateLibrary = onNavigateLibrary,
            onNavigateSettings = onNavigateSettings,
            onNavigateProfile = onNavigateProfile,
            contentFocusRequester = fieldFocus,
            selectedFocusRequester = topNavigationFocus,
            navigationState = navigationState,
        )
    }
}

private const val SearchGridColumns = 4

internal sealed interface SearchGridMove {
    data object Field : SearchGridMove
    data class Result(val index: Int) : SearchGridMove
    data object Blocked : SearchGridMove
    data object Unhandled : SearchGridMove
}

internal fun resolveSearchGridMove(
    index: Int,
    itemCount: Int,
    key: Key,
): SearchGridMove {
    if (index !in 0 until itemCount) return SearchGridMove.Unhandled
    val column = index % SearchGridColumns
    return when (key) {
        Key.DirectionUp -> if (index < SearchGridColumns) {
            SearchGridMove.Field
        } else {
            SearchGridMove.Result(index - SearchGridColumns)
        }
        Key.DirectionDown -> (index + SearchGridColumns)
            .takeIf { it < itemCount }
            ?.let(SearchGridMove::Result)
            ?: SearchGridMove.Blocked
        Key.DirectionLeft -> if (column > 0) {
            SearchGridMove.Result(index - 1)
        } else {
            SearchGridMove.Blocked
        }
        Key.DirectionRight -> if (column < SearchGridColumns - 1 && index + 1 < itemCount) {
            SearchGridMove.Result(index + 1)
        } else {
            SearchGridMove.Blocked
        }
        else -> SearchGridMove.Unhandled
    }
}
