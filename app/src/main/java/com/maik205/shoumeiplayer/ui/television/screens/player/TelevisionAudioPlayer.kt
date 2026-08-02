package com.maik205.shoumeiplayer.ui.television.screens.player

import android.os.Build
import android.os.PowerManager
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
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.feature.player.AudioQueueItemUi
import com.maik205.shoumeiplayer.feature.player.LyricLineUi
import com.maik205.shoumeiplayer.feature.player.PlayerTimelineState
import com.maik205.shoumeiplayer.feature.player.PlayerUiState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

internal val AudioLeft = 58.dp
internal val AudioTop = 59.dp
internal val AudioMainWidth = 854.dp
internal val AudioMainHeight = 292.dp
internal val AudioCoverSize = 230.dp
internal val AudioContentLeft = 403.dp
internal val AudioContentWidth = 451.dp
internal val AudioRailTop = 372.dp
internal val AudioColumnWidth = 394.dp
internal val AudioMaterialEase = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
internal val AudioCssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

@Composable
internal fun TelevisionAudioPlayer(
    state: PlayerUiState,
    timelineState: StateFlow<PlayerTimelineState>,
    playLoading: Boolean,
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
    onRetryContext: () -> Unit,
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
        targetValue = if (lyricsVisible || !thermalAllowsOptionalWork()) 0.dp else 1.65.dp,
        animationSpec = tween(180, easing = AudioCssEase),
        label = "audioLyricsStageBlur",
    )

    // Opening the queue claims focus once. Keying this on the queue contents and the cover-mode
    // toggle meant playing a track, a suggestion refresh, or switching between covers and a list
    // all dragged focus back to the first item from wherever the viewer actually was (PLAYER-017).
    //
    // The claim is only recorded once a requester accepts it, so an empty queue that later fills
    // -- or one showing a Retry -- still gets an entry target rather than leaving focus on the
    // dimmed player underneath (PLAYER-018).
    var queueEntryClaimed by remember { mutableStateOf(false) }
    LaunchedEffect(queueVisible) {
        if (!queueVisible) queueEntryClaimed = false
    }
    LaunchedEffect(queueVisible, queueHasItems, state.musicContextError) {
        if (!queueVisible || queueEntryClaimed) return@LaunchedEffect
        withFrameNanos { }
        if (runCatching { firstQueueItemFocus.requestFocus() }.isSuccess) {
            queueEntryClaimed = true
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

    val colors = TelevisionTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.Black),
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
        Box(Modifier.fillMaxSize().background(colors.Black.copy(alpha = 0.74f)))

        TelevisionFocusRevealButton(
            label = if (exitArmed) {
                stringResource(R.string.tv_player_exit)
            } else {
                stringResource(R.string.tv_back)
            },
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
                timelineState = timelineState,
                lyricsVisible = lyricsVisible,
                playLoading = playLoading,
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
                contextEntryFocus = firstQueueItemFocus,
            )
            AudioLyricsPlayback(
                state = state,
                timelineState = timelineState,
                visible = lyricsVisible,
                playLoading = playLoading,
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
            LyricsPaneHost(
                lyrics = state.lyrics,
                timelineState = timelineState,
                synced = state.lyricsSynced,
                focusRequester = lyricsFocus,
                loading = state.musicContextLoading,
                errorMessage = state.musicContextError?.resolveMessage(),
            )
        }

        AudioContextColumns(
            upNext = upNext,
            suggested = state.suggestedAudio,
            loading = state.musicContextLoading,
            errorMessage = state.musicContextError?.resolveMessage(),
            upNextCoverMode = upNextCoverMode,
            suggestedCoverMode = suggestedCoverMode,
            firstItemFocus = firstQueueItemFocus,
            onToggleUpNextCoverMode = onToggleUpNextCoverMode,
            onToggleSuggestedCoverMode = onToggleSuggestedCoverMode,
            onPlayItem = onPlayItem,
            onInteraction = onInteraction,
            onRetry = onRetryContext,
            upFocusRequester = normalLyricsFocus,
            modifier = Modifier
                .offset(x = AudioLeft, y = AudioRailTop)
                .width(AudioMainWidth)
                .height(146.dp)
                .alpha(railAlpha),
        )
    }
}

@Composable
private fun thermalAllowsOptionalWork(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val powerManager = context.getSystemService(PowerManager::class.java)
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        (powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) <
        PowerManager.THERMAL_STATUS_MODERATE
}
