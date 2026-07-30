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
import com.maik205.shoumeiplayer.feature.player.AudioQueueItemUi
import com.maik205.shoumeiplayer.feature.player.LyricLineUi
import com.maik205.shoumeiplayer.feature.player.PlayerUiState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import kotlinx.coroutines.launch

@Composable
internal fun AudioNormalPlayback(
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
internal fun AudioLyricsPlayback(
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
