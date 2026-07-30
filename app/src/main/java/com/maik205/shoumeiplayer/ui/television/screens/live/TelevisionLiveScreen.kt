package com.maik205.shoumeiplayer.ui.television.screens.live

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.ui.television.components.TelevisionEmptyState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionAppTopNavigation
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusScale
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.ui.television.components.televisionLibraryNavigationKey
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.yield

private val ChannelWidth = 188.dp
private val TimelineWidth = 660.dp
private const val GuideWindowHours = 6

@Composable
fun TelevisionLiveScreen(
    state: TelevisionLiveState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onFocusProgram: (LiveProgramUi?) -> Unit,
    onOpenProgram: (MediaItemUi) -> Unit,
    onPlay: (MediaItemUi) -> Unit,
    libraries: List<LibraryDestinationUi>,
    userName: String,
    avatarUrl: String?,
    onNavigateHome: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: (LibraryDestinationUi) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateProfile: () -> Unit,
    navigationState: LazyListState,
) {
    var guideFocused by remember { mutableStateOf(false) }
    val topNavigationFocus = remember { FocusRequester() }
    val heroFocus = remember { FocusRequester() }
    val selectedNavigationKey = libraries
        .firstOrNull { it.collectionType.equals("livetv", ignoreCase = true) }
        ?.let { televisionLibraryNavigationKey(it.id) }
    val heroHeight by animateDpAsState(
        targetValue = if (guideFocused) 118.dp else 250.dp,
        animationSpec = tween(170),
        label = "liveHeroHeight",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TelevisionColors.Black),
    ) {
        Column(Modifier.fillMaxSize()) {
            LiveHero(
                program = state.focused,
                compact = guideFocused,
                height = heroHeight,
                onBack = onBack,
                onRefresh = onRefresh,
                onPlay = {
                    state.focused?.let { program ->
                        state.channels
                            .firstOrNull { it.item.id == program.channelId }
                            ?.item
                            ?.let(onPlay)
                    }
                },
                onHeroFocused = { guideFocused = false },
                focusRequester = heroFocus,
            )
            when {
                state.loading -> TelevisionLoadingState(
                    label = "Loading guide",
                    shape = TelevisionLoadingShape.Guide,
                    modifier = Modifier.padding(horizontal = TelevisionDimensions.SafeHorizontal),
                )
                state.error != null && state.channels.isEmpty() -> TelevisionErrorState(
                    title = "The guide is unavailable",
                    message = state.error,
                    onRetry = onRefresh,
                    modifier = Modifier.padding(horizontal = TelevisionDimensions.SafeHorizontal),
                )
                state.channels.isEmpty() -> TelevisionEmptyState(
                    title = "No live channels",
                    modifier = Modifier.padding(horizontal = TelevisionDimensions.SafeHorizontal),
                )
                else -> GuideTimeline(
                    state = state,
                    onFocusChanged = { guideFocused = it },
                    onFocusProgram = onFocusProgram,
                    onOpenProgram = onOpenProgram,
                    onPlayChannel = onPlay,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        TelevisionAppTopNavigation(
            libraries = libraries,
            selectedKey = selectedNavigationKey,
            userName = userName,
            avatarUrl = avatarUrl,
            onNavigateHome = onNavigateHome,
            onNavigateSearch = onNavigateSearch,
            onNavigateLibrary = onNavigateLibrary,
            onNavigateSettings = onNavigateSettings,
            onNavigateProfile = onNavigateProfile,
            contentFocusRequester = heroFocus,
            selectedFocusRequester = topNavigationFocus,
            navigationState = navigationState,
        )
    }
}

@Composable
private fun LiveHero(
    program: LiveProgramUi?,
    compact: Boolean,
    height: Dp,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: () -> Unit,
    onHeroFocused: () -> Unit,
    focusRequester: FocusRequester,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        program?.item?.backdropUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(520.dp)
                    .alpha(if (compact) 0.18f else 0.42f),
                contentScale = ContentScale.Crop,
            )
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
                label = "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                focusRequester = focusRequester.takeIf { program == null },
                expandedWidth = 92.dp,
                onFocusChanged = { if (it) onHeroFocused() },
            )
            Spacer(Modifier.weight(1f))
            TelevisionFocusRevealButton(
                label = "Refresh",
                icon = Icons.Default.Refresh,
                onClick = onRefresh,
                expandedWidth = 108.dp,
                onFocusChanged = { if (it) onHeroFocused() },
            )
        }

        if (program != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = TelevisionDimensions.SafeHorizontal,
                        bottom = if (compact) 10.dp else 22.dp,
                    ),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.width(if (compact) 630.dp else 720.dp)) {
                    Text(
                        text = if (compact) {
                            "${program.item.title}   ${program.timeLabel}"
                        } else {
                            program.timeLabel
                        },
                        style = if (compact) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                        color = if (compact) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                        maxLines = 1,
                    )
                    if (!compact) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = program.item.title,
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        program.item.overview?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TelevisionColors.PaperSoft,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                TelevisionFocusRevealButton(
                    label = "Watch",
                    icon = Icons.Default.PlayArrow,
                    onClick = onPlay,
                    focusRequester = focusRequester,
                    selected = true,
                    expandedWidth = 98.dp,
                    onFocusChanged = { if (it) onHeroFocused() },
                )
            }
        }
    }
}

@Composable
private fun GuideTimeline(
    state: TelevisionLiveState,
    onFocusChanged: (Boolean) -> Unit,
    onFocusProgram: (LiveProgramUi?) -> Unit,
    onOpenProgram: (MediaItemUi) -> Unit,
    onPlayChannel: (MediaItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val initialFocus = remember { FocusRequester() }
    val initialTarget = remember(state.windowStart, state.channels) {
        val program = state.focused
        (program?.channelId ?: state.channels.firstOrNull()?.item?.id) to program?.item?.id
    }
    val initialChannelId = initialTarget.first
    val initialProgramId = initialTarget.second

    LaunchedEffect(state.windowStart, state.channels) {
        val channelIndex = state.channels.indexOfFirst { it.item.id == initialChannelId }
            .coerceAtLeast(0)
        if (state.channels.isNotEmpty()) {
            listState.scrollToItem(channelIndex)
            yield()
            initialFocus.requestFocus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = TelevisionDimensions.SafeHorizontal),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Live",
                style = MaterialTheme.typography.labelLarge,
                color = TelevisionColors.PaperMuted,
                modifier = Modifier.width(ChannelWidth),
            )
            repeat(GuideWindowHours) { index ->
                val time = state.windowStart.plusSeconds(index * 60L * 60L)
                Text(
                    text = guideClock(time),
                    style = MaterialTheme.typography.labelMedium,
                    color = TelevisionColors.PaperMuted,
                    modifier = Modifier.width(TimelineWidth / GuideWindowHours),
                )
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .focusGroup(),
            contentPadding = PaddingValues(bottom = 52.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(state.channels, key = { it.item.id }) { channel ->
                GuideChannelRow(
                    channel = channel,
                    windowStart = state.windowStart,
                    windowEnd = state.windowEnd,
                    onFocusChanged = onFocusChanged,
                    onFocusProgram = onFocusProgram,
                    onOpenProgram = onOpenProgram,
                    onPlayChannel = onPlayChannel,
                    initialFocus = if (channel.item.id == initialChannelId) initialFocus else null,
                    initialProgramId = initialProgramId,
                )
            }
            item(key = "end") {
                Text(
                    text = "That is the full guide",
                    style = MaterialTheme.typography.titleSmall,
                    color = TelevisionColors.PaperMuted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 34.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideChannelRow(
    channel: LiveChannelUi,
    windowStart: Instant,
    windowEnd: Instant,
    onFocusChanged: (Boolean) -> Unit,
    onFocusProgram: (LiveProgramUi?) -> Unit,
    onOpenProgram: (MediaItemUi) -> Unit,
    onPlayChannel: (MediaItemUi) -> Unit,
    initialFocus: FocusRequester?,
    initialProgramId: String?,
) {
    val initialProgramExists = channel.programs.any { it.item.id == initialProgramId }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .televisionBringIntoViewOnFocus(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TelevisionFocusSurface(
            onClick = { onPlayChannel(channel.item) },
            focusRequester = if (!initialProgramExists) initialFocus else null,
            restingAlpha = 0.5f,
            scaleTo = 1f,
            onFocusChanged = { focused ->
                if (focused) {
                    onFocusChanged(true)
                    onFocusProgram(channel.programs.firstOrNull())
                }
            },
            modifier = Modifier.width(ChannelWidth),
        ) { focused ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = channel.number.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                    modifier = Modifier.width(34.dp),
                )
                Text(
                    text = channel.item.title,
                    style = MaterialTheme.typography.titleSmall.televisionItemTitle(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(ChannelWidth - 42.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .width(TimelineWidth)
                .height(54.dp),
        ) {
            channel.programs.forEach { program ->
                val start = positionFraction(program.start, windowStart, windowEnd)
                val end = positionFraction(program.end, windowStart, windowEnd)
                if (end > 0f && start < 1f) {
                    val clippedStart = start.coerceIn(0f, 1f)
                    val clippedEnd = end.coerceIn(0f, 1f)
                    val width = (TimelineWidth.value * (clippedEnd - clippedStart))
                        .coerceAtLeast(72f)
                        .dp
                    TelevisionFocusSurface(
                        onClick = { onOpenProgram(program.item) },
                        focusRequester = if (program.item.id == initialProgramId) initialFocus else null,
                        restingAlpha = 0.52f,
                        scaleTo = TelevisionFocusScale.Navigation,
                        onFocusChanged = { focused ->
                            if (focused) {
                                onFocusChanged(true)
                                onFocusProgram(program)
                            }
                        },
                        modifier = Modifier
                            .offset(x = (TimelineWidth.value * clippedStart).roundToInt().dp)
                            .width(width)
                            .height(52.dp),
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = program.item.title,
                                style = MaterialTheme.typography.titleSmall.televisionItemTitle(),
                                color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun positionFraction(
    value: Instant,
    windowStart: Instant,
    windowEnd: Instant,
): Float {
    val total = (windowEnd.toEpochMilli() - windowStart.toEpochMilli()).coerceAtLeast(1L)
    return (value.toEpochMilli() - windowStart.toEpochMilli()).toFloat() / total.toFloat()
}

private fun guideClock(value: Instant): String = DateTimeFormatter
    .ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())
    .format(value)
