package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.ui.screens.player.AudioQueueItemUi
import com.maik205.shoumeiplayer.ui.screens.player.LyricLineUi
import com.maik205.shoumeiplayer.ui.screens.player.PlayerUiState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import kotlinx.coroutines.launch

private val AudioLeft = 58.dp
private val AudioTop = 59.dp
private val AudioMainWidth = 854.dp
private val AudioMainHeight = 292.dp
private val AudioCoverSize = 230.dp
private val AudioContentLeft = 403.dp
private val AudioContentWidth = 451.dp
private val AudioRailTop = 372.dp
private val AudioColumnWidth = 394.dp
private val AudioMaterialEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val AudioCssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

@Composable
internal fun TelevisionAudioPlayer(
    state: PlayerUiState,
    timelineFocus: FocusRequester,
    playPauseFocus: FocusRequester,
    exitArmed: Boolean,
    lyricsVisible: Boolean,
    queueVisible: Boolean,
    upNextCoverMode: Boolean,
    suggestedCoverMode: Boolean,
    shuffleEnabled: Boolean,
    repeatEnabled: Boolean,
    onExitButton: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleUpNextCoverMode: () -> Unit,
    onToggleSuggestedCoverMode: () -> Unit,
    onPlayItem: (String) -> Unit,
    onInteraction: () -> Unit,
) {
    val lyricsFocus = remember { FocusRequester() }
    val lyricsTimelineFocus = remember { FocusRequester() }
    val lyricsPlayPauseFocus = remember { FocusRequester() }
    val normalLyricsFocus = remember { FocusRequester() }
    val firstQueueItemFocus = remember { FocusRequester() }
    var hasShownLyrics by remember { mutableStateOf(false) }
    val currentQueueIndex = state.queue.indexOfFirst { it.playing }
    val upNext = if (currentQueueIndex >= 0) state.queue.drop(currentQueueIndex + 1) else state.queue
    val queueHasItems = upNext.isNotEmpty() || state.suggestedAudio.isNotEmpty()
    val playerAlpha by animateFloatAsState(
        targetValue = if (queueVisible) 0.52f else 1f,
        animationSpec = tween(160, easing = AudioMaterialEase),
        label = "audioPlayerOpacity",
    )
    val railAlpha by animateFloatAsState(
        targetValue = when {
            queueVisible -> 1f
            lyricsVisible -> 0.26f
            else -> 0.42f
        },
        animationSpec = tween(150, easing = AudioCssEase),
        label = "audioContextOpacity",
    )
    val lyricsStageBlur by animateDpAsState(
        targetValue = if (lyricsVisible) 0.dp else 1.65.dp,
        animationSpec = tween(180, easing = AudioCssEase),
        label = "audioLyricsStageBlur",
    )

    LaunchedEffect(queueVisible, upNextCoverMode, state.queue, state.suggestedAudio) {
        if (queueVisible && queueHasItems) {
            withFrameNanos { }
            runCatching { firstQueueItemFocus.requestFocus() }
        }
    }
    LaunchedEffect(lyricsVisible, state.lyrics.size) {
        if (lyricsVisible) {
            hasShownLyrics = true
            withFrameNanos { }
            runCatching { lyricsFocus.requestFocus() }
        } else if (hasShownLyrics) {
            withFrameNanos { }
            runCatching { normalLyricsFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(TelevisionColors.Black),
    ) {
        state.albumArtworkUrl?.let { artwork ->
            AsyncImage(
                model = artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.70f,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(25.dp),
            )
        }
        Box(Modifier.fillMaxSize().background(TelevisionColors.Black.copy(alpha = 0.74f)))

        TelevisionFocusRevealButton(
            label = if (exitArmed) "Exit?" else "Back",
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            onClick = {
                onInteraction()
                onExitButton()
            },
            expandedWidth = 58.dp,
            focusedScale = 1f,
            modifier = Modifier.offset(x = 47.dp, y = 20.dp),
        )

        Box(
            modifier = Modifier
                .offset(x = AudioLeft, y = AudioTop)
                .width(AudioMainWidth)
                .height(AudioMainHeight)
                .alpha(playerAlpha),
        ) {
            AudioNormalPlayback(
                state = state,
                lyricsVisible = lyricsVisible,
                timelineFocus = timelineFocus,
                playPauseFocus = playPauseFocus,
                shuffleEnabled = shuffleEnabled,
                repeatEnabled = repeatEnabled,
                lyricsControlFocus = normalLyricsFocus,
                onSeekBy = onSeekBy,
                onTogglePlayPause = onTogglePlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onToggleQueue = onToggleQueue,
                onToggleLyrics = onToggleLyrics,
                onInteraction = onInteraction,
            )
            AudioLyricsPlayback(
                state = state,
                visible = lyricsVisible,
                timelineFocus = lyricsTimelineFocus,
                playPauseFocus = lyricsPlayPauseFocus,
                shuffleEnabled = shuffleEnabled,
                repeatEnabled = repeatEnabled,
                onSeekBy = onSeekBy,
                onTogglePlayPause = onTogglePlayPause,
                onPrevious = onPrevious,
                onNext = onNext,
                onToggleShuffle = onToggleShuffle,
                onToggleRepeat = onToggleRepeat,
                onToggleQueue = onToggleQueue,
                onToggleLyrics = onToggleLyrics,
                onInteraction = onInteraction,
            )
        }

        AnimatedVisibility(
            visible = lyricsVisible,
            enter = fadeIn(tween(180, easing = AudioCssEase)),
            exit = fadeOut(tween(180, easing = AudioCssEase)),
            modifier = Modifier
                .offset(x = 528.dp, y = 49.dp)
                .width(384.dp)
                .height(324.dp)
                .blur(lyricsStageBlur),
        ) {
            LyricsPane(
                lyrics = state.lyrics,
                positionMs = state.positionMs,
                synced = state.lyricsSynced,
                focusRequester = lyricsFocus,
            )
        }

        AudioContextColumns(
            upNext = upNext,
            suggested = state.suggestedAudio,
            loading = state.musicContextLoading,
            upNextCoverMode = upNextCoverMode,
            suggestedCoverMode = suggestedCoverMode,
            firstItemFocus = firstQueueItemFocus,
            onToggleUpNextCoverMode = onToggleUpNextCoverMode,
            onToggleSuggestedCoverMode = onToggleSuggestedCoverMode,
            onPlayItem = onPlayItem,
            onInteraction = onInteraction,
            modifier = Modifier
                .offset(x = AudioLeft, y = AudioRailTop)
                .width(AudioMainWidth)
                .height(146.dp)
                .alpha(railAlpha),
        )
    }
}

@Composable
private fun AudioNormalPlayback(
    state: PlayerUiState,
    lyricsVisible: Boolean,
    timelineFocus: FocusRequester,
    playPauseFocus: FocusRequester,
    shuffleEnabled: Boolean,
    repeatEnabled: Boolean,
    lyricsControlFocus: FocusRequester,
    onSeekBy: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    onInteraction: () -> Unit,
) {
    val density = LocalDensity.current
    val coverScale by animateFloatAsState(
        targetValue = if (lyricsVisible) 0.56f else 1f,
        animationSpec = tween(280, easing = AudioMaterialEase),
        label = "audioCoverScale",
    )
    val coverTranslation by animateDpAsState(
        targetValue = if (lyricsVisible) (-54).dp else 0.dp,
        animationSpec = tween(280, easing = AudioMaterialEase),
        label = "audioCoverTranslation",
    )
    val coverAlpha by animateFloatAsState(
        targetValue = if (lyricsVisible) 0.88f else 1f,
        animationSpec = tween(160, easing = AudioCssEase),
        label = "audioCoverOpacity",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (lyricsVisible) 0f else 1f,
        animationSpec = tween(150, easing = AudioMaterialEase),
        label = "audioContentOpacity",
    )
    val contentBlur by animateDpAsState(
        targetValue = if (lyricsVisible) 1.65.dp else 0.dp,
        animationSpec = tween(160, easing = AudioMaterialEase),
        label = "audioContentBlur",
    )
    AudioCover(
        state = state,
        alpha = coverAlpha,
        modifier = Modifier
            .offset(y = 31.dp)
            .size(AudioCoverSize)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = coverScale
                scaleY = coverScale
                translationY = with(density) { coverTranslation.toPx() }
            },
    )
    Column(
        modifier = Modifier
            .offset(x = AudioContentLeft, y = 35.dp)
            .width(AudioContentWidth)
            .blur(contentBlur)
            .alpha(contentAlpha),
    ) {
        AudioMetadata(state = state, titleSize = 35.sp, titleLineHeight = 34.sp)
        Spacer(Modifier.height(15.dp))
        AudioTimeline(
            state = state,
            focusRequester = timelineFocus,
            enabled = !lyricsVisible,
            onSeekBy = onSeekBy,
            onTogglePlayPause = onTogglePlayPause,
            onInteraction = onInteraction,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(5.dp))
        AudioMainTransport(
            state = state,
            playPauseFocus = playPauseFocus,
            enabled = !lyricsVisible,
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onInteraction = onInteraction,
        )
        Spacer(Modifier.height(10.dp))
        AudioTools(
            shuffleEnabled = shuffleEnabled,
            repeatEnabled = repeatEnabled,
            enabled = !lyricsVisible,
            lyricsControlFocus = lyricsControlFocus,
            lyricsVisible = false,
            queueVisible = false,
            onToggleShuffle = onToggleShuffle,
            onToggleRepeat = onToggleRepeat,
            onToggleQueue = onToggleQueue,
            onToggleLyrics = onToggleLyrics,
            onInteraction = onInteraction,
        )
    }
}

@Composable
private fun AudioLyricsPlayback(
    state: PlayerUiState,
    visible: Boolean,
    timelineFocus: FocusRequester,
    playPauseFocus: FocusRequester,
    shuffleEnabled: Boolean,
    repeatEnabled: Boolean,
    onSeekBy: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    onInteraction: () -> Unit,
) {
    val metadataAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(180, easing = AudioCssEase),
        label = "audioLyricsMetadataOpacity",
    )
    val metadataBlur by animateDpAsState(
        targetValue = if (visible) 0.dp else 1.65.dp,
        animationSpec = tween(180, easing = AudioCssEase),
        label = "audioLyricsMetadataBlur",
    )
    Column(
        modifier = Modifier
            .offset(x = 163.dp, y = 22.dp)
            .width(240.dp)
            .blur(metadataBlur)
            .alpha(metadataAlpha),
    ) {
        AudioMetadata(state = state, titleSize = 30.sp, titleLineHeight = 29.sp)
    }
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(
            animationSpec = tween(240, easing = AudioMaterialEase),
            expandFrom = Alignment.Start,
            clip = true,
        ) + fadeIn(tween(120, easing = AudioCssEase)),
        exit = shrinkHorizontally(
            animationSpec = tween(240, easing = AudioMaterialEase),
            shrinkTowards = Alignment.Start,
            clip = true,
        ) + fadeOut(tween(120, easing = AudioCssEase)),
        modifier = Modifier
            .offset(y = 232.dp)
            .width(AudioContentLeft),
    ) {
        Column {
            AudioTimeline(
                state = state,
                focusRequester = timelineFocus,
                onSeekBy = onSeekBy,
                onTogglePlayPause = onTogglePlayPause,
                onInteraction = onInteraction,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AudioLyricsActionButton(
                    label = "Shuffle",
                    icon = Icons.Default.Shuffle,
                    selected = shuffleEnabled,
                    onClick = onToggleShuffle,
                    onInteraction = onInteraction,
                )
                AudioLyricsActionButton(
                    label = "Previous",
                    icon = Icons.Default.SkipPrevious,
                    onClick = onPrevious,
                    onInteraction = onInteraction,
                )
                AudioLyricsActionButton(
                    label = if (isAudioPlaying(state)) "Pause" else "Play",
                    icon = if (isAudioPlaying(state)) Icons.Default.Pause else Icons.Default.PlayArrow,
                    selected = true,
                    focusRequester = playPauseFocus,
                    onClick = onTogglePlayPause,
                    onInteraction = onInteraction,
                )
                AudioLyricsActionButton(
                    label = "Next",
                    icon = Icons.Default.SkipNext,
                    onClick = onNext,
                    onInteraction = onInteraction,
                )
                AudioLyricsActionButton(
                    label = "Repeat",
                    icon = Icons.Default.Repeat,
                    selected = repeatEnabled,
                    onClick = onToggleRepeat,
                    onInteraction = onInteraction,
                )
                AudioLyricsActionButton(
                    label = "Queue",
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    onClick = onToggleQueue,
                    onInteraction = onInteraction,
                )
                AudioLyricsActionButton(
                    label = "Lyrics",
                    icon = Icons.Default.Lyrics,
                    selected = true,
                    onClick = onToggleLyrics,
                    onInteraction = onInteraction,
                )
            }
        }
    }
}

@Composable
private fun AudioCover(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    AsyncImage(
        model = state.albumArtworkUrl,
        contentDescription = state.title,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = modifier
            .shadow(28.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(TelevisionColors.ImagePlaceholder),
    )
}

@Composable
private fun AudioMetadata(
    state: PlayerUiState,
    titleSize: androidx.compose.ui.unit.TextUnit,
    titleLineHeight: androidx.compose.ui.unit.TextUnit,
) {
    state.album?.takeIf(String::isNotBlank)?.let { album ->
        Text(
            text = album,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp),
            fontWeight = FontWeight.SemiBold,
            color = TelevisionColors.PaperMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
    }
    Text(
        text = state.title,
        style = MaterialTheme.typography.displayMedium.copy(
            fontSize = titleSize,
            lineHeight = titleLineHeight,
            letterSpacing = (-1.9).sp,
        ),
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    state.artist?.takeIf(String::isNotBlank)?.let { artist ->
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = state.artistArtworkUrl,
                contentDescription = artist,
                contentScale = ContentScale.Crop,
                alpha = 0.82f,
                modifier = Modifier
                    .size(23.dp)
                    .clip(CircleShape)
                    .background(TelevisionColors.ImagePlaceholder, CircleShape),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
                color = TelevisionColors.PaperMuted,
            )
        }
    }
    audioFormatLabels(state).takeIf { it.isNotEmpty() }?.let { format ->
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            format.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                    color = TelevisionColors.PaperMuted.copy(alpha = 0.72f),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun audioFormatLabels(state: PlayerUiState): List<String> = buildList {
    state.container?.uppercase()?.takeIf(String::isNotBlank)?.let(::add)
    state.audioDescription
        ?.split('·')
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.forEach { label ->
            if (none { it.equals(label, ignoreCase = true) }) add(label)
        }
}

@Composable
private fun AudioTimeline(
    state: PlayerUiState,
    focusRequester: FocusRequester,
    enabled: Boolean = true,
    onSeekBy: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val duration = state.durationMs?.takeIf { it > 0L }
    val fraction = duration?.let { (state.positionMs.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    TelevisionFocusSurface(
        onClick = {
            onInteraction()
            onTogglePlayPause()
        },
        enabled = enabled,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = 0.68f,
        focusedAlpha = 1f,
        onFocusChanged = {
            focused = it
            if (it) onInteraction()
        },
        modifier = modifier
            .height(29.dp)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val step = state.seekIntervalSeconds.toLong() * 1_000L
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onInteraction()
                        onSeekBy(-step)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onInteraction()
                        onSeekBy(step)
                        true
                    }
                    else -> false
                }
            },
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.fillMaxWidth().height(if (focused) 3.dp else 1.5.dp)) {
                drawRect(TelevisionColors.Paper.copy(alpha = 0.20f))
                drawRect(TelevisionColors.Paper, size = size.copy(width = size.width * fraction))
                if (focused) {
                    drawCircle(
                        color = TelevisionColors.Paper,
                        radius = 4.5.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(size.width * fraction, size.height / 2f),
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatAudioTime(state.positionMs), fontSize = 7.sp, color = TelevisionColors.PaperMuted)
                Text(
                    duration?.let(::formatAudioTime) ?: "--:--",
                    fontSize = 7.sp,
                    color = TelevisionColors.PaperMuted,
                )
            }
        }
    }
}

@Composable
private fun AudioMainTransport(
    state: PlayerUiState,
    playPauseFocus: FocusRequester,
    enabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onInteraction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AudioCircleButton(
            label = "Previous",
            icon = Icons.Default.SkipPrevious,
            enabled = enabled,
            onClick = onPrevious,
            onInteraction = onInteraction,
        )
        AudioCircleButton(
            label = if (isAudioPlaying(state)) "Pause" else "Play",
            icon = if (isAudioPlaying(state)) Icons.Default.Pause else Icons.Default.PlayArrow,
            primary = true,
            enabled = enabled,
            focusRequester = playPauseFocus,
            onClick = onTogglePlayPause,
            onInteraction = onInteraction,
        )
        AudioCircleButton(
            label = "Next",
            icon = Icons.Default.SkipNext,
            enabled = enabled,
            onClick = onNext,
            onInteraction = onInteraction,
        )
    }
}

@Composable
private fun AudioCircleButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    primary: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val buttonSize = if (primary) 37.dp else 30.dp
    TelevisionFocusSurface(
        onClick = {
            onInteraction()
            onClick()
        },
        enabled = enabled,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = if (primary || selected) 1f else 0.52f,
        focusedAlpha = 1f,
        onFocusChanged = { if (it) onInteraction() },
        modifier = Modifier.size(buttonSize),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TelevisionColors.Paper,
                modifier = Modifier.size(if (primary) 27.dp else 20.dp),
            )
        }
    }
}

@Composable
private fun AudioLyricsActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    TelevisionFocusRevealButton(
        label = label,
        icon = icon,
        selected = selected,
        expandWhenSelected = false,
        focusRequester = focusRequester,
        collapsedWidth = 23.dp,
        expandedWidth = 64.dp,
        buttonHeight = 23.dp,
        iconSize = 14.dp,
        focusedScale = 1f,
        onClick = {
            onInteraction()
            onClick()
        },
        onFocusChanged = { if (it) onInteraction() },
    )
}

@Composable
private fun AudioTools(
    shuffleEnabled: Boolean,
    repeatEnabled: Boolean,
    enabled: Boolean,
    lyricsControlFocus: FocusRequester,
    lyricsVisible: Boolean,
    queueVisible: Boolean,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    onInteraction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(24.dp).focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AudioToolButton("Shuffle", Icons.Default.Shuffle, shuffleEnabled, onToggleShuffle, onInteraction, enabled)
        AudioToolButton("Repeat", Icons.Default.Repeat, repeatEnabled, onToggleRepeat, onInteraction, enabled)
        AudioToolButton("Queue", Icons.AutoMirrored.Filled.QueueMusic, queueVisible, onToggleQueue, onInteraction, enabled)
        AudioToolButton(
            label = "Lyrics",
            icon = Icons.Default.Lyrics,
            selected = lyricsVisible,
            enabled = enabled,
            focusRequester = lyricsControlFocus,
            collapseOnClick = true,
            onClick = onToggleLyrics,
            onInteraction = onInteraction,
        )
    }
}

@Composable
private fun AudioToolButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onInteraction: () -> Unit,
    enabled: Boolean = true,
    collapseOnClick: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val focusManager = LocalFocusManager.current
    TelevisionFocusRevealButton(
        label = label,
        icon = icon,
        enabled = enabled,
        selected = selected,
        expandWhenSelected = false,
        focusRequester = focusRequester,
        collapsedWidth = 19.dp,
        expandedWidth = 62.dp,
        buttonHeight = 24.dp,
        iconSize = 14.dp,
        focusedScale = 1f,
        onClick = {
            if (collapseOnClick) focusManager.clearFocus(force = true)
            onInteraction()
            onClick()
        },
        onFocusChanged = { if (it) onInteraction() },
    )
}

@Composable
private fun LyricsPane(
    lyrics: List<LyricLineUi>,
    positionMs: Long,
    synced: Boolean,
    focusRequester: FocusRequester,
) {
    val activeIndex = if (synced) {
        lyrics.indexOfLast { it.startMs != null && it.startMs <= positionMs }.takeIf { it >= 0 }
    } else {
        null
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(activeIndex) {
        if (activeIndex != null) listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Lyrics",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp),
            fontWeight = FontWeight.SemiBold,
            color = TelevisionColors.PaperMuted,
        )
        Spacer(Modifier.height(7.dp))
        if (lyrics.isEmpty()) {
            Text(
                "Lyrics are not available for this track.",
                color = TelevisionColors.PaperMuted,
                modifier = Modifier.focusRequester(focusRequester).focusable(),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                            return@onPreviewKeyEvent false
                        }
                        val target = when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_UP -> (listState.firstVisibleItemIndex - 1).coerceAtLeast(0)
                            KeyEvent.KEYCODE_DPAD_DOWN ->
                                (listState.firstVisibleItemIndex + 1).coerceAtMost(lyrics.lastIndex)
                            else -> return@onPreviewKeyEvent false
                        }
                        scope.launch { listState.animateScrollToItem(target) }
                        true
                    }
                    .focusable(),
            ) {
                itemsIndexed(
                    items = lyrics,
                    key = { index, line -> "${line.startMs}:$index:${line.text.hashCode()}" },
                ) { index, line ->
                    val current = index == activeIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(39.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            line.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 23.sp,
                                lineHeight = 25.sp,
                            ),
                            fontWeight = FontWeight.Bold,
                            color = TelevisionColors.Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.alpha(
                                when {
                                    current -> 1f
                                    activeIndex != null && index == activeIndex + 1 -> 0.82f
                                    else -> 0.17f
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioContextColumns(
    upNext: List<AudioQueueItemUi>,
    suggested: List<AudioQueueItemUi>,
    loading: Boolean,
    upNextCoverMode: Boolean,
    suggestedCoverMode: Boolean,
    firstItemFocus: FocusRequester,
    onToggleUpNextCoverMode: () -> Unit,
    onToggleSuggestedCoverMode: () -> Unit,
    onPlayItem: (String) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(57.dp),
    ) {
        AudioQueueColumn(
            title = "Up next",
            items = upNext,
            coverMode = upNextCoverMode,
            firstItemFocus = if (upNext.isNotEmpty()) firstItemFocus else null,
            emptyMessage = if (loading) "Loading queue…" else "End of queue",
            onToggleCoverMode = onToggleUpNextCoverMode,
            onPlayItem = onPlayItem,
            onInteraction = onInteraction,
            modifier = Modifier.width(AudioColumnWidth),
        )
        AudioQueueColumn(
            title = "Suggested",
            items = suggested,
            coverMode = suggestedCoverMode,
            firstItemFocus = if (upNext.isEmpty() && suggested.isNotEmpty()) firstItemFocus else null,
            emptyMessage = if (loading) "Finding tracks…" else "No suggestions",
            onToggleCoverMode = onToggleSuggestedCoverMode,
            onPlayItem = onPlayItem,
            onInteraction = onInteraction,
            modifier = Modifier.width(AudioColumnWidth),
        )
    }
}

@Composable
private fun AudioQueueColumn(
    title: String,
    items: List<AudioQueueItemUi>,
    coverMode: Boolean,
    firstItemFocus: FocusRequester?,
    emptyMessage: String,
    onToggleCoverMode: () -> Unit,
    onPlayItem: (String) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coverFocusRequesters = remember(items.map(AudioQueueItemUi::itemId), firstItemFocus) {
        List(items.size) { index ->
            if (index == 0 && firstItemFocus != null) firstItemFocus else FocusRequester()
        }
    }
    val coverRailState = rememberLazyListState()
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(19.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.SemiBold,
                color = TelevisionColors.PaperMuted,
            )
            Spacer(Modifier.weight(1f))
            TelevisionFocusRevealButton(
                label = if (coverMode) "Tracks" else "Covers",
                icon = if (coverMode) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                onClick = {
                    onInteraction()
                    onToggleCoverMode()
                },
                expandedWidth = 52.dp,
                collapsedWidth = 15.dp,
                buttonHeight = 19.dp,
                iconSize = 12.dp,
                focusedScale = 1f,
                onFocusChanged = { if (it) onInteraction() },
            )
        }
        Spacer(Modifier.height(5.dp))
        when {
            items.isEmpty() -> Text(emptyMessage, fontSize = 8.sp, color = TelevisionColors.PaperSoft)
            coverMode -> LazyRow(
                state = coverRailState,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().focusGroup(),
            ) {
                itemsIndexed(items, key = { _, item -> item.itemId }) { index, item ->
                    AudioCoverItem(
                        item = item,
                        focusRequester = coverFocusRequesters.getOrNull(index),
                        modifier = Modifier.televisionHorizontalWrap(
                            index,
                            coverFocusRequesters,
                            coverRailState,
                        ),
                        onClick = {
                            onInteraction()
                            onPlayItem(item.itemId)
                        },
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().focusGroup(),
            ) {
                itemsIndexed(items, key = { _, item -> item.itemId }) { index, item ->
                    AudioTrackRow(
                        index = index,
                        item = item,
                        focusRequester = if (index == 0) firstItemFocus else null,
                        onClick = {
                            onInteraction()
                            onPlayItem(item.itemId)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioTrackRow(
    index: Int,
    item: AudioQueueItemUi,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = 0.38f,
        modifier = Modifier.fillMaxWidth().height(21.dp),
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(
                (index + 1).toString().padStart(2, '0'),
                fontSize = 6.sp,
                color = TelevisionColors.PaperMuted,
                modifier = Modifier.width(17.dp),
            )
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall.televisionItemTitle(),
                fontWeight = FontWeight.SemiBold,
                color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            item.durationMs?.let {
                Text(
                    formatAudioTime(it),
                    fontSize = 6.sp,
                    color = TelevisionColors.PaperMuted,
                )
            }
        }
    }
}

@Composable
private fun AudioCoverItem(
    item: AudioQueueItemUi,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = 1f,
        restingAlpha = 0.42f,
        modifier = modifier.width(89.dp).height(116.dp),
    ) { focused ->
        Column {
            AsyncImage(
                model = item.artworkUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(89.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TelevisionColors.ImagePlaceholder),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleSmall.televisionItemTitle(),
                fontWeight = FontWeight.SemiBold,
                color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.artist?.let {
                Text(
                    it,
                    fontSize = 6.sp,
                    lineHeight = 7.sp,
                    color = TelevisionColors.PaperMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun isAudioPlaying(state: PlayerUiState): Boolean =
    state.state == PlayerState.Playing || state.state == PlayerState.Buffering

private fun formatAudioTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
