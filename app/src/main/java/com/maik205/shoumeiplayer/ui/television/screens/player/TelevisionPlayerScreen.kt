package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.TrackType
import com.maik205.shoumeiplayer.feature.player.PlayerUiState
import com.maik205.shoumeiplayer.feature.player.PlayerTimelineState
import com.maik205.shoumeiplayer.feature.player.PlayerMessage
import com.maik205.shoumeiplayer.feature.player.PlayerMessageKind
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

private const val PLAYER_OSD_TIMEOUT_MS = 5_000L
private const val MINI_SEEK_TIMEOUT_MS = 1_500L
private const val EXIT_ARM_TIMEOUT_MS = 2_000L
private const val STILL_WATCHING_TIMEOUT_MS = 2L * 60L * 60L * 1_000L
private const val POST_PLAY_SECONDS = 10

private enum class FrameMode(val engineValue: String) {
    Fit("Fit"),
    Fill("Fill"),
    Original("Original"),
    Aspect16By9("16:9"),
    Aspect4By3("4:3"),
}

private enum class HdrMode(val engineValue: String) {
    Automatic("Auto"),
    Passthrough("Passthrough"),
    ToneMap("Tone map"),
    ConvertToSdr("Convert to SDR"),
}

private enum class DeinterlaceMode(val engineValue: String) {
    Automatic("Auto"),
    On("On"),
    Off("Off"),
}

private enum class SleepTimer {
    Off,
    Minutes15,
    Minutes30,
    Minutes45,
    Hour,
    EndOfEpisode,
}

@Composable
private fun FrameMode.label(): String = when (this) {
    FrameMode.Fit -> stringResource(R.string.tv_player_fit)
    FrameMode.Fill -> stringResource(R.string.tv_player_fill)
    FrameMode.Original -> stringResource(R.string.tv_player_original)
    FrameMode.Aspect16By9 -> stringResource(R.string.tv_player_aspect_16_9)
    FrameMode.Aspect4By3 -> stringResource(R.string.tv_player_aspect_4_3)
}

@Composable
private fun HdrMode.label(): String = when (this) {
    HdrMode.Automatic -> stringResource(R.string.tv_auto)
    HdrMode.Passthrough -> stringResource(R.string.tv_settings_passthrough)
    HdrMode.ToneMap -> stringResource(R.string.tv_settings_tone_map)
    HdrMode.ConvertToSdr -> stringResource(R.string.tv_player_convert_sdr)
}

@Composable
private fun DeinterlaceMode.label(): String = when (this) {
    DeinterlaceMode.Automatic -> stringResource(R.string.tv_auto)
    DeinterlaceMode.On -> stringResource(R.string.on)
    DeinterlaceMode.Off -> stringResource(R.string.off)
}

@Composable
private fun SleepTimer.label(): String = when (this) {
    SleepTimer.Off -> stringResource(R.string.off)
    SleepTimer.Minutes15 -> stringResource(R.string.tv_player_15_min)
    SleepTimer.Minutes30 -> stringResource(R.string.tv_player_30_min)
    SleepTimer.Minutes45 -> stringResource(R.string.tv_player_45_min)
    SleepTimer.Hour -> stringResource(R.string.tv_player_1_hr)
    SleepTimer.EndOfEpisode -> stringResource(R.string.tv_player_end_episode)
}

@Composable
internal fun PlayerMessage.resolveMessage(): String = when (kind) {
    PlayerMessageKind.MetadataLoadFailed -> stringResource(R.string.tv_player_metadata_failed)
    PlayerMessageKind.PlaybackLoadFailed -> stringResource(R.string.tv_player_playback_failed)
    PlayerMessageKind.StreamSwapFailed -> stringResource(R.string.tv_player_swap_failed)
    PlayerMessageKind.ShelvesLoadFailed -> stringResource(R.string.tv_player_shelves_failed)
    PlayerMessageKind.MusicContextLoadFailed -> stringResource(R.string.tv_player_music_context_failed)
    PlayerMessageKind.AdjacencyLoadFailed -> stringResource(R.string.tv_player_adjacency_failed)
    PlayerMessageKind.NetworkPaused -> stringResource(R.string.tv_player_network_paused)
}

/**
 * Google TV playback entry point. The video surface is deliberately lifecycle-neutral: surface
 * recreation only detaches mpv's render target; final Jellyfin stop reporting happens on an
 * explicit exit or ViewModel teardown, never because Compose happened to recompose.
 */
@Composable
internal fun TelevisionPlayerContent(
    state: PlayerUiState,
    timelineState: StateFlow<PlayerTimelineState>,
    controller: TelevisionPlayerController,
    audioOnly: Boolean,
    onExit: () -> Unit,
    onNavigateToItem: (String) -> Unit,
    onNavigateToPerson: (String, String) -> Unit,
) {
    val audio = audioOnly || state.isAudio
    val activity = LocalContext.current as? Activity
    val engineFailed = state.state is PlayerState.Error
    val playbackError = state.error?.resolveMessage()
        ?: if (engineFailed) stringResource(R.string.tv_player_playback_failed) else null
    val seekIntervalMs = state.seekIntervalSeconds.toLong() * 1_000L

    DisposableEffect(activity, audio, state.state) {
        val window = activity?.window
        val shouldKeepScreenOn = shouldKeepScreenOn(audio, state.state)
        val previouslyOwned = window?.let {
            it.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        } ?: false
        if (shouldKeepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (shouldKeepScreenOn && !previouslyOwned) {
                window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    val rootFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    var panel by remember { mutableStateOf<TelevisionPlayerPanel?>(null) }
    var panelBackStack by remember { mutableStateOf<List<TelevisionPlayerPanel>>(emptyList()) }
    var osdVisible by remember { mutableStateOf(audioOnly) }
    var miniSeekVisible by remember { mutableStateOf(false) }
    var whileWatchingVisible by remember { mutableStateOf(false) }
    var miniSeekTick by remember { mutableIntStateOf(0) }
    var interactionTick by remember { mutableIntStateOf(0) }
    var timelineFocusTick by remember { mutableIntStateOf(0) }
    var playPauseFocusTick by remember { mutableIntStateOf(0) }
    var exitArmed by remember { mutableStateOf(false) }
    var stillWatching by remember { mutableStateOf(false) }
    var lyricsVisible by remember { mutableStateOf(false) }
    var audioQueueVisible by remember { mutableStateOf(false) }
    var upNextCoverMode by remember { mutableStateOf(false) }
    var suggestedCoverMode by remember { mutableStateOf(true) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatEnabled by remember { mutableStateOf(false) }
    var playRequestPending by remember { mutableStateOf(false) }
    var postPlaySeconds by remember { mutableStateOf<Int?>(null) }
    var postPlayBrowsing by remember(state.upNext?.itemId) { mutableStateOf(false) }
    var frameMode by remember { mutableStateOf(FrameMode.Fit) }
    var hdrMode by remember { mutableStateOf(HdrMode.Automatic) }
    var videoTrack by remember { mutableStateOf("") }
    var deinterlaceMode by remember { mutableStateOf(DeinterlaceMode.Automatic) }
    var sleepTimer by remember { mutableStateOf(SleepTimer.Off) }

    val playControlLoading = playRequestPending ||
        state.loading ||
        state.state == PlayerState.Loading ||
        state.state == PlayerState.Buffering

    LaunchedEffect(state.state, state.error) {
        if (state.state != PlayerState.Paused && state.state != PlayerState.Ended) {
            playRequestPending = false
        }
        if (playbackError != null || state.state == PlayerState.Ended) {
            panel = null
            panelBackStack = emptyList()
            whileWatchingVisible = false
            lyricsVisible = false
            audioQueueVisible = false
            stillWatching = false
        }
    }

    fun togglePlayPauseWithFeedback() {
        if (playControlLoading) return
        if (state.state == PlayerState.Paused || state.state == PlayerState.Ended) {
            playRequestPending = true
        }
        controller.togglePlayPause()
    }

    fun playWithFeedback() {
        if (playControlLoading || state.state == PlayerState.Playing) return
        playRequestPending = true
        controller.play()
    }

    fun noteInteraction() {
        interactionTick++
    }

    fun requestTimeline() {
        timelineFocusTick++
    }

    fun requestPlayPause() {
        playPauseFocusTick++
    }

    fun revealOsd(focusTimeline: Boolean = false) {
        osdVisible = true
        whileWatchingVisible = false
        noteInteraction()
        if (focusTimeline) requestTimeline() else requestPlayPause()
    }

    fun hideOsd() {
        osdVisible = false
        whileWatchingVisible = false
        panel = null
        panelBackStack = emptyList()
        runCatching { rootFocus.requestFocus() }
    }

    fun closePanel() {
        panel = panelBackStack.lastOrNull()
        panelBackStack = panelBackStack.dropLast(1)
        noteInteraction()
        if (panel == null) requestTimeline()
    }

    fun leavePlayer(destination: () -> Unit) {
        controller.stopAndReport()
        destination()
    }

    fun exitPlayer() = leavePlayer(onExit)

    fun handleExitButton() {
        if (exitArmed) {
            exitPlayer()
        } else {
            exitArmed = true
            noteInteraction()
        }
    }

    fun openPanel(target: TelevisionPlayerPanel) {
        whileWatchingVisible = false
        lyricsVisible = false
        audioQueueVisible = false
        val current = panel
        if (current == null) {
            panelBackStack = emptyList()
        } else if (current != target) {
            panelBackStack = panelBackStack + current
        }
        panel = target
        noteInteraction()
        if (target == TelevisionPlayerPanel.Extras) controller.loadShelves()
    }

    LaunchedEffect(Unit) {
        rootFocus.requestFocus()
    }

    LaunchedEffect(timelineFocusTick) {
        if (timelineFocusTick > 0) {
            runCatching { timelineFocus.requestFocus() }
        }
    }

    LaunchedEffect(playPauseFocusTick) {
        if (playPauseFocusTick > 0) {
            runCatching { playPauseFocus.requestFocus() }
        }
    }

    LaunchedEffect(audio, state.loading, state.error, state.title) {
        if (!audio && !state.loading && state.error == null) revealOsd()
        if (audio) {
            osdVisible = true
            requestPlayPause()
        }
    }

    LaunchedEffect(state.videoTracks) {
        state.videoTracks.firstOrNull { it.selected }?.let { videoTrack = it.label }
    }

    LaunchedEffect(osdVisible, interactionTick, panel, audio) {
        if (!audio && osdVisible && panel == null) {
            delay(PLAYER_OSD_TIMEOUT_MS)
            hideOsd()
        }
    }

    LaunchedEffect(miniSeekTick) {
        if (miniSeekTick > 0) {
            miniSeekVisible = true
            delay(MINI_SEEK_TIMEOUT_MS)
            miniSeekVisible = false
        }
    }

    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(EXIT_ARM_TIMEOUT_MS)
            exitArmed = false
        }
    }

    LaunchedEffect(interactionTick, state.state) {
        if (state.state == PlayerState.Playing || state.state == PlayerState.Buffering) {
            delay(STILL_WATCHING_TIMEOUT_MS)
            controller.pause()
            panel = null
            panelBackStack = emptyList()
            whileWatchingVisible = false
            lyricsVisible = false
            audioQueueVisible = false
            stillWatching = true
        }
    }

    LaunchedEffect(state.state, state.upNext?.itemId, sleepTimer, postPlayBrowsing) {
        if (state.state == PlayerState.Ended && sleepTimer == SleepTimer.EndOfEpisode) {
            exitPlayer()
            return@LaunchedEffect
        }
        if (!audio && state.state == PlayerState.Ended && state.upNext?.autoPlay == true) {
            if (postPlayBrowsing) return@LaunchedEffect
            val startingSeconds = postPlaySeconds ?: POST_PLAY_SECONDS
            for (remaining in startingSeconds downTo 1) {
                postPlaySeconds = remaining
                delay(1_000L)
            }
            postPlaySeconds = null
            controller.playUpNext()
        } else {
            postPlaySeconds = null
        }
    }

    LaunchedEffect(sleepTimer) {
        val timeoutMs = when (sleepTimer) {
            SleepTimer.Minutes15 -> 15L * 60L * 1_000L
            SleepTimer.Minutes30 -> 30L * 60L * 1_000L
            SleepTimer.Minutes45 -> 45L * 60L * 1_000L
            SleepTimer.Hour -> 60L * 60L * 1_000L
            else -> null
        }
        timeoutMs?.let {
            delay(it)
            exitPlayer()
        }
    }

    val currentAudioQueueIndex = state.queue.indexOfFirst { it.playing }
    val hasQueuedAudioNext =
        audio && currentAudioQueueIndex >= 0 && currentAudioQueueIndex < state.queue.lastIndex

    LaunchedEffect(audio, state.state, state.queue, repeatEnabled, shuffleEnabled) {
        if (audio && state.state == PlayerState.Ended) {
            when {
                repeatEnabled -> {
                    controller.seekTo(0)
                    controller.play()
                }
                shuffleEnabled -> state.queue
                    .filterNot { it.playing }
                    .randomOrNull()
                    ?.itemId
                    ?.let(controller::switchTo)
                hasQueuedAudioNext -> controller.playNextAudio()
            }
        }
    }

    DisposableEffect(controller) {
        onDispose {
            // Surface loss is not playback completion. Explicit navigation above owns stop reports.
            controller.setSurface(null)
        }
    }

    BackHandler {
        when {
            postPlayBrowsing -> postPlayBrowsing = false
            stillWatching -> exitPlayer()
            playbackError != null -> exitPlayer()
            state.state == PlayerState.Ended -> exitPlayer()
            panel != null -> {
                closePanel()
            }

            audioQueueVisible -> {
                audioQueueVisible = false
                noteInteraction()
                requestPlayPause()
            }

            lyricsVisible -> {
                lyricsVisible = false
                noteInteraction()
            }

            !audio && whileWatchingVisible -> {
                whileWatchingVisible = false
                noteInteraction()
                requestPlayPause()
            }
            !audio && osdVisible -> hideOsd()
            !audio && miniSeekVisible -> miniSeekVisible = false
            else -> exitPlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TelevisionTheme.colors.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                fun seek(deltaMs: Long): Boolean {
                    controller.seekBy(deltaMs)
                    noteInteraction()
                    if (!audio && !osdVisible) miniSeekTick++
                    return true
                }

                when (native.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        togglePlayPauseWithFeedback()
                        noteInteraction()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        playWithFeedback()
                        noteInteraction()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        controller.pause()
                        noteInteraction()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-seekIntervalMs)
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(seekIntervalMs)
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (audio) controller.playPreviousAudio() else controller.playPreviousEpisode()
                        noteInteraction()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (audio) controller.playNextAudio() else controller.playNextEpisode()
                        noteInteraction()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        exitPlayer()
                        true
                    }

                    KeyEvent.KEYCODE_MENU -> {
                        if (!audio) revealOsd()
                        true
                    }

                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN,
                    -> if (!audio && !osdVisible) {
                        revealOsd(focusTimeline = true)
                        true
                    } else {
                        noteInteraction()
                        false
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    -> if (!audio && !osdVisible) {
                        togglePlayPauseWithFeedback()
                        revealOsd()
                        true
                    } else {
                        noteInteraction()
                        false
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> if (!audio && !osdVisible) {
                        seek(-seekIntervalMs)
                    } else {
                        noteInteraction()
                        false
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (!audio && !osdVisible) {
                        seek(seekIntervalMs)
                    } else {
                        noteInteraction()
                        false
                    }
                    else -> false
                }
            },
    ) {
        if (audio) {
            LaunchedEffect(Unit) { controller.setSurface(null) }
            TelevisionAudioPlayer(
                state = state,
                timelineState = timelineState,
                playLoading = playControlLoading,
                timelineFocus = timelineFocus,
                playPauseFocus = playPauseFocus,
                exitArmed = exitArmed,
                lyricsVisible = lyricsVisible,
                queueVisible = audioQueueVisible,
                upNextCoverMode = upNextCoverMode,
                suggestedCoverMode = suggestedCoverMode,
                shuffleEnabled = shuffleEnabled,
                repeatEnabled = repeatEnabled,
                onExitButton = ::handleExitButton,
                onSeekBy = controller::seekBy,
                onTogglePlayPause = ::togglePlayPauseWithFeedback,
                onPrevious = controller::playPreviousAudio,
                onNext = {
                    if (shuffleEnabled) {
                        state.queue
                            .filterNot { it.playing }
                            .randomOrNull()
                            ?.itemId
                            ?.let(controller::switchTo)
                    } else {
                        controller.playNextAudio()
                    }
                },
                onToggleShuffle = { shuffleEnabled = !shuffleEnabled },
                onToggleRepeat = { repeatEnabled = !repeatEnabled },
                onToggleLyrics = {
                    val showingLyrics = !lyricsVisible
                    lyricsVisible = showingLyrics
                    if (showingLyrics) {
                        audioQueueVisible = false
                        panel = null
                        panelBackStack = emptyList()
                    }
                },
                onToggleQueue = {
                    val openingQueue = !audioQueueVisible
                    audioQueueVisible = openingQueue
                    if (openingQueue) {
                        lyricsVisible = false
                        panel = null
                        panelBackStack = emptyList()
                    } else {
                        requestPlayPause()
                    }
                },
                onToggleUpNextCoverMode = { upNextCoverMode = !upNextCoverMode },
                onToggleSuggestedCoverMode = { suggestedCoverMode = !suggestedCoverMode },
                onPlayItem = controller::switchTo,
                onInteraction = ::noteInteraction,
                onRetryContext = controller::retryMusicContext,
            )
        } else {
            VideoSurface(
                onSurface = controller::setSurface,
                onSurfaceSize = controller::setSurfaceSize,
            )
            state.logoUrl?.takeIf { osdVisible }?.let { logoUrl ->
                AsyncImage(
                    model = logoUrl,
                    contentDescription = state.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = TelevisionDimensions.SafeTop,
                            end = TelevisionDimensions.SafeHorizontal,
                        )
                        .width(220.dp)
                        .height(74.dp),
                )
            }
            if (osdVisible) {
                VideoPlayerChrome(
                    state = state,
                    timelineState = timelineState,
                    playLoading = playControlLoading,
                    dimmed = panel != null,
                    timelineFocus = timelineFocus,
                    playPauseFocus = playPauseFocus,
                    whileWatchingVisible = whileWatchingVisible,
                    onOpenWhileWatching = {
                        whileWatchingVisible = true
                        panel = null
                        panelBackStack = emptyList()
                        noteInteraction()
                        controller.loadShelves()
                    },
                    onOpenItem = { target -> leavePlayer { onNavigateToItem(target) } },
                    onRetryWhileWatching = controller::loadShelves,
                    exitArmed = exitArmed,
                    onExitButton = ::handleExitButton,
                    onSeekBy = controller::seekBy,
                    onTogglePlayPause = ::togglePlayPauseWithFeedback,
                    onHideOsd = ::hideOsd,
                    onPrevious = controller::playPreviousEpisode,
                    onNext = controller::playNextEpisode,
                    onOpenPanel = ::openPanel,
                    onInteraction = ::noteInteraction,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
            AnimatedVisibility(
                visible = !osdVisible && miniSeekVisible,
                enter = fadeIn(tween(160)) + slideInVertically(
                    animationSpec = tween(180),
                    initialOffsetY = { it / 2 },
                ),
                exit = fadeOut(tween(140)) + slideOutVertically(
                    animationSpec = tween(140),
                    targetOffsetY = { it / 2 },
                ),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                MiniPlayerTimelineHost(
                    timelineState = timelineState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        state.notice?.let {
            Text(
                text = it.resolveMessage(),
                style = MaterialTheme.typography.bodyMedium,
                color = TelevisionTheme.colors.Paper,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp)
                    .background(TelevisionTheme.colors.Black.copy(alpha = 0.86f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        val playbackWaiting = state.loading ||
            state.state == PlayerState.Loading ||
            state.state == PlayerState.Buffering
        if (playbackWaiting && playbackError == null && !osdVisible && !miniSeekVisible) {
            PlayerLoadingOverlay(
                timelineState = timelineState,
                dimBackground = state.loading,
            )
        }
        if (state.swapping) {
            Text(
                text = stringResource(R.string.tv_player_swapping),
                style = MaterialTheme.typography.bodyMedium,
                color = TelevisionTheme.colors.Paper,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp)
                    .background(TelevisionTheme.colors.Black.copy(alpha = 0.86f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        when (panel) {
            TelevisionPlayerPanel.Audio -> {
                val trackRows = audioRows(state.audioTracks) {
                    controller.selectTrack(it)
                    closePanel()
                }
                val rows = if (trackRows.isEmpty()) {
                    listOf(
                        PlayerSelectionRow(
                            key = "audio:none",
                            label = stringResource(R.string.tv_player_no_audio_tracks),
                            onClick = {},
                        ),
                    )
                } else {
                    trackRows
                }
                PlayerSelectionPanel(
                    title = stringResource(R.string.tv_audio),
                    rows = rows,
                    onDismiss = ::closePanel,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            TelevisionPlayerPanel.Subtitles -> {
                val selectableTracks = state.subtitleTracks.filterNot {
                    it.id == -1
                }
                val off = PlayerTrack(
                    id = -1,
                    type = TrackType.SUBTITLE,
                    label = stringResource(R.string.off),
                    selected = state.subtitleTracks.none(PlayerTrack::selected) ||
                        state.subtitleTracks.any {
                            it.selected && it.id == -1
                        },
                )
                PlayerSelectionPanel(
                    title = stringResource(R.string.tv_subtitles),
                    rows = subtitleRows(listOf(off) + selectableTracks) {
                        controller.selectTrack(it)
                        closePanel()
                    },
                    onDismiss = ::closePanel,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            TelevisionPlayerPanel.Chapters -> {
                val availableChapterRows = chapterRows(state.chapters) {
                    controller.seekTo(it.positionMs)
                    closePanel()
                }
                val rows = if (availableChapterRows.isEmpty()) {
                    listOf(
                        PlayerSelectionRow(
                            key = "chapters:none",
                            label = stringResource(R.string.tv_player_no_chapters),
                            onClick = {},
                        ),
                    )
                } else {
                    availableChapterRows
                }
                PlayerSelectionPanel(
                    title = stringResource(R.string.tv_detail_chapters),
                    rows = rows,
                    onDismiss = ::closePanel,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            TelevisionPlayerPanel.Quality -> PlayerSelectionPanel(
                title = stringResource(R.string.tv_quality),
                rows = qualityRows(state.quality) {
                    controller.setQuality(it)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Speed -> PlayerSelectionPanel(
                title = stringResource(R.string.tv_player_playback_speed),
                rows = speedRows(state.speed) {
                    controller.setSpeed(it)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Frame -> PlayerSelectionPanel(
                title = stringResource(R.string.tv_player_frame),
                rows = selectionRows(
                    prefix = "frame",
                    selected = frameMode,
                    values = listOf(
                        PlayerSelectionOption(FrameMode.Fit, FrameMode.Fit.label()),
                        PlayerSelectionOption(FrameMode.Fill, FrameMode.Fill.label()),
                        PlayerSelectionOption(FrameMode.Original, FrameMode.Original.label()),
                        PlayerSelectionOption(FrameMode.Aspect16By9, FrameMode.Aspect16By9.label()),
                        PlayerSelectionOption(FrameMode.Aspect4By3, FrameMode.Aspect4By3.label()),
                    ),
                ) {
                    frameMode = it
                    controller.setFrameMode(it.engineValue)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Hdr -> PlayerSelectionPanel(
                title = stringResource(R.string.tv_player_hdr_handling),
                rows = selectionRows(
                    prefix = "hdr",
                    selected = hdrMode,
                    values = listOf(
                        PlayerSelectionOption(HdrMode.Automatic, HdrMode.Automatic.label()),
                        PlayerSelectionOption(HdrMode.Passthrough, HdrMode.Passthrough.label()),
                        PlayerSelectionOption(HdrMode.ToneMap, HdrMode.ToneMap.label()),
                        PlayerSelectionOption(HdrMode.ConvertToSdr, HdrMode.ConvertToSdr.label()),
                    ),
                ) {
                    hdrMode = it
                    controller.setHdrMode(it.engineValue)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.VideoTrack -> PlayerSelectionPanel(
                title = stringResource(R.string.tv_player_video),
                rows = state.videoTracks.map { track ->
                    PlayerSelectionRow(
                        key = "video:${track.id}",
                        label = track.label,
                        detail = track.language,
                        selected = track.selected,
                        onClick = {
                            videoTrack = track.label
                            controller.selectTrack(track)
                            closePanel()
                        },
                    )
                }.ifEmpty {
                    listOf(
                        PlayerSelectionRow(
                            "video:none",
                            stringResource(R.string.tv_player_no_alternate_video_tracks),
                            onClick = {},
                        ),
                    )
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.AudioDelay -> PlaybackDelayPanel(
                title = stringResource(R.string.tv_player_audio_delay),
                valueMs = state.audioDelayMs,
                onChange = controller::setAudioDelayMs,
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.SubtitleDelay -> PlaybackDelayPanel(
                title = stringResource(R.string.tv_player_subtitle_delay),
                valueMs = state.subtitleDelayMs,
                onChange = controller::setSubtitleDelayMs,
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Deinterlace -> PlayerSelectionPanel(
                title = stringResource(R.string.tv_player_deinterlace),
                rows = selectionRows(
                    prefix = "deinterlace",
                    selected = deinterlaceMode,
                    values = listOf(
                        PlayerSelectionOption(DeinterlaceMode.Automatic, DeinterlaceMode.Automatic.label()),
                        PlayerSelectionOption(DeinterlaceMode.On, DeinterlaceMode.On.label()),
                        PlayerSelectionOption(DeinterlaceMode.Off, DeinterlaceMode.Off.label()),
                    ),
                ) {
                    deinterlaceMode = it
                    controller.setDeinterlaceMode(it.engineValue)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Sleep -> PlayerSelectionPanel(
                title = stringResource(R.string.tv_player_sleep_timer),
                rows = selectionRows(
                    prefix = "sleep",
                    selected = sleepTimer,
                    values = listOf(
                        PlayerSelectionOption(SleepTimer.Off, SleepTimer.Off.label()),
                        PlayerSelectionOption(SleepTimer.Minutes15, SleepTimer.Minutes15.label()),
                        PlayerSelectionOption(SleepTimer.Minutes30, SleepTimer.Minutes30.label()),
                        PlayerSelectionOption(SleepTimer.Minutes45, SleepTimer.Minutes45.label()),
                        PlayerSelectionOption(SleepTimer.Hour, SleepTimer.Hour.label()),
                        PlayerSelectionOption(SleepTimer.EndOfEpisode, SleepTimer.EndOfEpisode.label()),
                    ),
                ) { sleepTimer = it; closePanel() },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Information -> PlayerSelectionPanel(
                title = stringResource(R.string.tv_player_playback_information),
                rows = listOf(
                    PlayerSelectionRow("info:source", stringResource(R.string.tv_player_source), state.playMethod.playMethodLabel() ?: state.quality.descriptionLabel(), onClick = {}),
                    PlayerSelectionRow("info:container", stringResource(R.string.tv_player_container), state.container ?: stringResource(R.string.tv_unknown), onClick = {}),
                PlayerSelectionRow(
                    "info:video",
                    stringResource(R.string.tv_player_video),
                    state.videoInfo.descriptionLabel() ?: if (videoTrack.isBlank()) {
                        stringResource(R.string.tv_unknown)
                    } else {
                        videoTrack
                    },
                    onClick = {},
                ),
                PlayerSelectionRow(
                    "info:color",
                    stringResource(R.string.tv_player_color),
                    state.effectiveHdrMode.descriptionLabel() ?: stringResource(R.string.tv_unknown),
                    onClick = {},
                ),
                    PlayerSelectionRow("info:audio", stringResource(R.string.tv_audio), state.audioInfo.descriptionLabel() ?: state.audioTracks.firstOrNull { it.selected }?.label ?: stringResource(R.string.tv_unknown), onClick = {}),
                PlayerSelectionRow(
                    "info:output",
                    stringResource(R.string.tv_player_output),
                    if (state.activeAudioRoute.isBlank()) {
                        stringResource(R.string.tv_player_unknown_output)
                    } else {
                        state.activeAudioRoute
                    },
                    onClick = {},
                ),
                    PlayerSelectionRow("info:display", stringResource(R.string.tv_player_display), streamResolutionLabel(state.displayWidth, state.displayHeight) ?: stringResource(R.string.tv_android_tv), onClick = {}),
                    PlayerSelectionRow("info:decoder", stringResource(R.string.tv_player_decoder), stringResource(R.string.tv_player_mpv_hardware), onClick = {}),
                    PlayerSelectionRow("info:dropped", stringResource(R.string.tv_player_dropped_frames), "0", onClick = {}),
                ),
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Options -> PlaybackOptionsPanel(
                audioDelayMs = state.audioDelayMs,
                subtitleDelayMs = state.subtitleDelayMs,
                onAudioDelayChange = {
                    noteInteraction()
                    controller.setAudioDelayMs(it)
                },
                onSubtitleDelayChange = {
                    noteInteraction()
                    controller.setSubtitleDelayMs(it)
                },
                onReset = {
                    noteInteraction()
                    controller.resetPlaybackDelays()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.More -> PlayerSelectionPanel(
                title = stringResource(R.string.tv_player_more),
                rows = listOf(
                    PlayerSelectionRow(
                        key = "more:speed",
                        label = stringResource(R.string.tv_player_playback_speed),
                        detail = stringResource(R.string.tv_player_playback_speed_value, state.speed),
                        onClick = { openPanel(TelevisionPlayerPanel.Speed) },
                    ),
                    PlayerSelectionRow(
                        key = "more:quality",
                        label = stringResource(R.string.tv_player_frame),
                        detail = frameMode.label(),
                        onClick = { openPanel(TelevisionPlayerPanel.Frame) },
                    ),
                    PlayerSelectionRow(
                        key = "more:hdr",
                        label = stringResource(R.string.tv_player_hdr_handling),
                        detail = hdrMode.label(),
                        onClick = { openPanel(TelevisionPlayerPanel.Hdr) },
                    ),
                  PlayerSelectionRow(
                      "more:video",
                      stringResource(R.string.tv_player_video),
                      if (videoTrack.isBlank()) stringResource(R.string.tv_unknown) else videoTrack,
                      onClick = { openPanel(TelevisionPlayerPanel.VideoTrack) },
                  ),
                    PlayerSelectionRow("more:audio-delay", stringResource(R.string.tv_player_audio_delay), signedPlaybackDelay(state.audioDelayMs), onClick = { openPanel(TelevisionPlayerPanel.AudioDelay) }),
                    PlayerSelectionRow("more:subtitle-delay", stringResource(R.string.tv_player_subtitle_delay), signedPlaybackDelay(state.subtitleDelayMs), onClick = { openPanel(TelevisionPlayerPanel.SubtitleDelay) }),
                    PlayerSelectionRow("more:deinterlace", stringResource(R.string.tv_player_deinterlace), deinterlaceMode.label(), onClick = { openPanel(TelevisionPlayerPanel.Deinterlace) }),
                    PlayerSelectionRow("more:sleep", stringResource(R.string.tv_player_sleep_timer), sleepTimer.label(), onClick = { openPanel(TelevisionPlayerPanel.Sleep) }),
                    PlayerSelectionRow("more:information", stringResource(R.string.tv_player_playback_information), stringResource(R.string.tv_player_stream_details), onClick = { openPanel(TelevisionPlayerPanel.Information) }),
                    PlayerSelectionRow(
                        key = "more:options",
                        label = stringResource(R.string.tv_player_legacy_timing),
                        detail = stringResource(R.string.tv_player_timing_detail),
                        onClick = { openPanel(TelevisionPlayerPanel.Options) },
                    ),
                    PlayerSelectionRow(
                        key = "more:extras",
                        label = stringResource(R.string.tv_player_while_watching),
                        detail = stringResource(R.string.tv_player_similar_cast),
                        onClick = { openPanel(TelevisionPlayerPanel.Extras) },
                    ),
                ),
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Extras -> PlayerExtrasOverlay(
                state = state,
                onDismiss = {
                    closePanel()
                },
                onOpenItem = { target -> leavePlayer { onNavigateToItem(target) } },
                onOpenPerson = { person ->
                    leavePlayer { onNavigateToPerson(person.id, person.name) }
                },
                onRetry = controller::loadShelves,
            )

            null -> Unit
        }

        if (playbackError != null) {
            PlayerErrorOverlay(
                message = playbackError,
                onRetry = {
                    panel = null
                    panelBackStack = emptyList()
                    controller.retryPlayback()
                },
                onBack = ::exitPlayer,
            )
        } else if (
            state.state == PlayerState.Ended &&
            (!audio || (!state.musicContextLoading && !hasQueuedAudioNext))
        ) {
            PostPlayOverlay(
                upNext = state.upNext,
                episodes = state.postPlayEpisodes,
                countdownSeconds = postPlaySeconds,
                browsingEpisodes = postPlayBrowsing,
                onBrowsingEpisodesChange = { postPlayBrowsing = it },
                onPlayNext = controller::playUpNext,
                onPlayEpisode = controller::switchTo,
                onReplay = controller::togglePlayPause,
                onBack = ::exitPlayer,
            )
        }

        if (
            stillWatching &&
            playbackError == null &&
            state.state != PlayerState.Ended
        ) {
            StillWatchingOverlay(
                onContinue = {
                    stillWatching = false
                    noteInteraction()
                    controller.play()
                },
                onStop = ::exitPlayer,
            )
        }
    }
}

@Composable
private fun PlayerLoadingOverlay(
    timelineState: StateFlow<PlayerTimelineState>,
    dimBackground: Boolean,
) {
    val timeline by timelineState.collectAsStateWithLifecycle()
    val loadingLabel = stringResource(
        if (dimBackground) R.string.tv_player_loading else R.string.tv_player_buffering,
    )
    val streamBitrate = listOfNotNull(
        timeline.videoBitrateBitsPerSecond,
        timeline.audioBitrateBitsPerSecond,
    ).takeIf { it.isNotEmpty() }?.sum()
    val bufferStatus = when {
        timeline.seeking -> stringResource(R.string.tv_player_buffer_seeking)
        timeline.pausedForCache -> stringResource(R.string.tv_player_buffer_catching_up)
        timeline.cacheIdle == false -> stringResource(R.string.tv_player_buffer_fetching)
        else -> null
    }
    val bufferAhead = timeline.bufferedMs?.let { bufferedMs ->
        val bufferedAheadMs = (bufferedMs - timeline.positionMs).coerceAtLeast(0L)
        if (bufferedAheadMs >= 1_000L) formatBufferedAhead(bufferedAheadMs) else null
    }
    val bufferSummary = listOfNotNull(bufferStatus, bufferAhead)
        .joinToString(separator = stringResource(R.string.tv_player_buffer_detail_separator))
        .ifEmpty { stringResource(R.string.tv_player_buffer_waiting) }
    val bufferMetrics = buildList {
        timeline.readRateBytesPerSecond
            ?.takeIf { it > 0L }
            ?.let { add(formatIncomingRate(it)) }
        streamBitrate
            ?.takeIf { it > 0L }
            ?.let { add(formatStreamBitrate(it)) }
    }.joinToString(separator = stringResource(R.string.tv_player_buffer_detail_separator))
    val bufferDetails = listOfNotNull(bufferSummary, bufferMetrics.takeIf(String::isNotEmpty))
        .joinToString(separator = stringResource(R.string.tv_player_buffer_detail_separator))
    val loadingDescription = stringResource(
        R.string.tv_player_loading_description,
        loadingLabel,
        bufferDetails,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                TelevisionTheme.colors.Black.copy(alpha = if (dimBackground) 0.92f else 0f),
            )
            .semantics(mergeDescendants = true) {
                contentDescription = loadingDescription
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 6.dp)
                .width(300.dp)
                .height(78.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            TelevisionTheme.colors.BlackRaised.copy(alpha = 0.78f),
                            TelevisionTheme.colors.Black.copy(alpha = 0f),
                        ),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    bottom = TelevisionDimensions.SafeBottom,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(TelevisionDimensions.ActionIcon),
                trackColor = TelevisionTheme.colors.ProgressTrack,
                strokeWidth = 1.5.dp,
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.width(234.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = loadingLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = TelevisionTheme.colors.PaperMuted,
                )
                Text(
                    text = bufferDetails,
                    style = MaterialTheme.typography.bodySmall,
                    color = TelevisionTheme.colors.PaperSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun formatBufferedAhead(bufferedAheadMs: Long): String {
    val seconds = bufferedAheadMs.coerceAtLeast(0L) / 1_000L
    return if (seconds < 60L) {
        stringResource(R.string.tv_player_buffer_ahead_seconds, seconds)
    } else {
        stringResource(R.string.tv_player_buffer_ahead, formatPlayerTime(bufferedAheadMs))
    }
}

@Composable
private fun formatIncomingRate(bytesPerSecond: Long): String {
    val rate = bytesPerSecond.toDouble()
    return when {
        rate >= 1_000_000.0 -> stringResource(
            R.string.tv_player_buffer_rate_megabytes,
            rate / 1_000_000.0,
        )
        rate >= 1_000.0 -> stringResource(
            R.string.tv_player_buffer_rate_kilobytes,
            rate / 1_000.0,
        )
        else -> stringResource(R.string.tv_player_buffer_rate_bytes, rate)
    }
}

@Composable
private fun formatStreamBitrate(bitsPerSecond: Long): String {
    val bitrate = bitsPerSecond.toDouble()
    return if (bitrate >= 1_000_000.0) {
        stringResource(
            R.string.tv_player_buffer_bitrate_megabits,
            bitrate / 1_000_000.0,
        )
    } else {
        stringResource(
            R.string.tv_player_buffer_bitrate_kilobits,
            bitrate / 1_000.0,
        )
    }
}

internal fun shouldKeepScreenOn(audio: Boolean, state: PlayerState): Boolean =
    !audio && (state == PlayerState.Playing || state == PlayerState.Buffering)
