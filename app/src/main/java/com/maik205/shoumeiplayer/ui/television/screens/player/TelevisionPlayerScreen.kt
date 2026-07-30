package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.TrackType
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.screens.player.PlayerUiState
import com.maik205.shoumeiplayer.ui.screens.player.PlayerViewModel
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import kotlinx.coroutines.delay

private const val PLAYER_OSD_TIMEOUT_MS = 5_000L
private const val MINI_SEEK_TIMEOUT_MS = 1_500L
private const val EXIT_ARM_TIMEOUT_MS = 2_000L
private const val STILL_WATCHING_TIMEOUT_MS = 2L * 60L * 60L * 1_000L
private const val POST_PLAY_SECONDS = 10

/**
 * Google TV playback entry point. The video surface is deliberately lifecycle-neutral: surface
 * recreation only detaches mpv's render target; final Jellyfin stop reporting happens on an
 * explicit exit or ViewModel teardown, never because Compose happened to recompose.
 */
@Composable
fun TelevisionPlayerScreen(
    itemId: String,
    startPositionTicks: Long,
    audioOnly: Boolean,
    initialAudioStreamIndex: Int? = null,
    initialSubtitleStreamIndex: Int? = null,
    initialQualityLabel: String? = null,
    onExit: () -> Unit,
    onNavigateToItem: (String) -> Unit,
    onNavigateToPerson: (String, String) -> Unit,
) {
    val viewModel = containerViewModel { container ->
        PlayerViewModel(
            engine = container.playerEngine,
            playbackRepository = container.playbackRepository,
            libraryRepository = container.libraryRepository,
            authRepository = container.authRepository,
            imageUrlBuilder = container.imageUrlBuilder,
            reporter = container.progressReporter,
            itemId = itemId,
            startPositionTicks = startPositionTicks,
            teardownScope = container.applicationScope,
            settingsStore = container.settingsStore,
            initialAudioStreamIndex = initialAudioStreamIndex,
            initialSubtitleStreamIndex = initialSubtitleStreamIndex,
            initialQualityLabel = initialQualityLabel,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val audio = audioOnly || state.isAudio
    val playbackError = state.error ?: (state.state as? PlayerState.Error)?.message
    val seekIntervalMs = state.seekIntervalSeconds.toLong() * 1_000L

    val rootFocus = remember { FocusRequester() }
    val timelineFocus = remember { FocusRequester() }
    val playPauseFocus = remember { FocusRequester() }
    var panel by remember { mutableStateOf<TelevisionPlayerPanel?>(null) }
    var panelBackStack by remember { mutableStateOf<List<TelevisionPlayerPanel>>(emptyList()) }
    var osdVisible by remember { mutableStateOf(audioOnly) }
    var miniSeekVisible by remember { mutableStateOf(false) }
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
    var postPlaySeconds by remember { mutableStateOf<Int?>(null) }
    var frameMode by remember { mutableStateOf("Fit") }
    var hdrMode by remember { mutableStateOf("Auto") }
    var videoTrack by remember { mutableStateOf("HEVC Main 10") }
    var deinterlaceMode by remember { mutableStateOf("Auto") }
    var sleepTimer by remember { mutableStateOf("Off") }

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
        noteInteraction()
        if (focusTimeline) requestTimeline() else requestPlayPause()
    }

    fun hideOsd() {
        osdVisible = false
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
        viewModel.stopAndReport()
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
        val current = panel
        if (current == null) {
            panelBackStack = emptyList()
        } else if (current != target) {
            panelBackStack = panelBackStack + current
        }
        panel = target
        noteInteraction()
        if (target == TelevisionPlayerPanel.Extras) viewModel.loadShelves()
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
            viewModel.pause()
            stillWatching = true
        }
    }

    LaunchedEffect(state.state, state.upNext?.itemId, sleepTimer) {
        if (state.state == PlayerState.Ended && sleepTimer == "End of episode") {
            exitPlayer()
            return@LaunchedEffect
        }
        if (!audio && state.state == PlayerState.Ended && state.upNext?.autoPlay == true) {
            for (remaining in POST_PLAY_SECONDS downTo 1) {
                postPlaySeconds = remaining
                delay(1_000L)
            }
            postPlaySeconds = null
            viewModel.playUpNext()
        } else {
            postPlaySeconds = null
        }
    }

    LaunchedEffect(sleepTimer) {
        val timeoutMs = when (sleepTimer) {
            "15 min" -> 15L * 60L * 1_000L
            "30 min" -> 30L * 60L * 1_000L
            "45 min" -> 45L * 60L * 1_000L
            "1 hr" -> 60L * 60L * 1_000L
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
                    viewModel.seekTo(0)
                    viewModel.play()
                }
                shuffleEnabled -> state.queue
                    .filterNot { it.playing }
                    .randomOrNull()
                    ?.itemId
                    ?.let(viewModel::switchTo)
                hasQueuedAudioNext -> viewModel.playNextAudio()
            }
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            // Surface loss is not playback completion. Explicit navigation above owns stop reports.
            viewModel.setSurface(null)
        }
    }

    BackHandler {
        when {
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

            !audio && osdVisible -> hideOsd()
            !audio && miniSeekVisible -> miniSeekVisible = false
            else -> exitPlayer()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TelevisionColors.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false

                fun seek(deltaMs: Long): Boolean {
                    viewModel.seekBy(deltaMs)
                    noteInteraction()
                    if (!audio && !osdVisible) miniSeekTick++
                    return true
                }

                when (native.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        viewModel.togglePlayPause()
                        noteInteraction()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        viewModel.play()
                        noteInteraction()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        viewModel.pause()
                        noteInteraction()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-seekIntervalMs)
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(seekIntervalMs)
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                        if (audio) viewModel.playPreviousAudio() else viewModel.playPreviousEpisode()
                        noteInteraction()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        if (audio) viewModel.playNextAudio() else viewModel.playNextEpisode()
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
                        viewModel.togglePlayPause()
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
            LaunchedEffect(Unit) { viewModel.setSurface(null) }
            TelevisionAudioPlayer(
                state = state,
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
                onSeekBy = viewModel::seekBy,
                onTogglePlayPause = viewModel::togglePlayPause,
                onPrevious = viewModel::playPreviousAudio,
                onNext = {
                    if (shuffleEnabled) {
                        state.queue
                            .filterNot { it.playing }
                            .randomOrNull()
                            ?.itemId
                            ?.let(viewModel::switchTo)
                    } else {
                        viewModel.playNextAudio()
                    }
                },
                onToggleShuffle = { shuffleEnabled = !shuffleEnabled },
                onToggleRepeat = { repeatEnabled = !repeatEnabled },
                onToggleLyrics = {
                    val showingLyrics = !lyricsVisible
                    lyricsVisible = showingLyrics
                    if (showingLyrics) {
                        audioQueueVisible = false
                    }
                },
                onToggleQueue = {
                    val openingQueue = !audioQueueVisible
                    audioQueueVisible = openingQueue
                    if (openingQueue) {
                        lyricsVisible = false
                    } else {
                        requestPlayPause()
                    }
                },
                onToggleUpNextCoverMode = { upNextCoverMode = !upNextCoverMode },
                onToggleSuggestedCoverMode = { suggestedCoverMode = !suggestedCoverMode },
                onPlayItem = viewModel::switchTo,
                onInteraction = ::noteInteraction,
            )
        } else {
            VideoSurface(
                onSurface = viewModel::setSurface,
                onSurfaceSize = viewModel::setSurfaceSize,
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
                    dimmed = panel != null,
                    timelineFocus = timelineFocus,
                    playPauseFocus = playPauseFocus,
                    exitArmed = exitArmed,
                    onExitButton = ::handleExitButton,
                    onSeekBy = viewModel::seekBy,
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onHideOsd = ::hideOsd,
                    onPrevious = viewModel::playPreviousEpisode,
                    onNext = viewModel::playNextEpisode,
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
                MiniPlayerTimeline(
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (state.loading || state.state == PlayerState.Loading || state.state == PlayerState.Buffering) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(TelevisionColors.PaperSoft),
            )
        }

        state.notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TelevisionColors.Paper,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp)
                    .background(TelevisionColors.Black.copy(alpha = 0.86f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        when (panel) {
            TelevisionPlayerPanel.Audio -> PlayerSelectionPanel(
                title = "Audio",
                rows = audioRows(state.audioTracks) {
                    viewModel.selectTrack(it)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Subtitles -> {
                val selectableTracks = state.subtitleTracks.filterNot {
                    it.id == -1 || it.label.equals("Off", ignoreCase = true)
                }
                val off = PlayerTrack(
                    id = -1,
                    type = TrackType.SUBTITLE,
                    label = "Off",
                    selected = state.subtitleTracks.none(PlayerTrack::selected) ||
                        state.subtitleTracks.any {
                            it.selected && (it.id == -1 || it.label.equals("Off", ignoreCase = true))
                        },
                )
                PlayerSelectionPanel(
                    title = "Subtitles",
                    rows = subtitleRows(listOf(off) + selectableTracks) {
                        viewModel.selectTrack(it)
                        closePanel()
                    },
                    onDismiss = ::closePanel,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            TelevisionPlayerPanel.Chapters -> PlayerSelectionPanel(
                title = "Chapters",
                rows = chapterRows(state.chapters) {
                    viewModel.seekTo(it.positionMs)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Quality -> PlayerSelectionPanel(
                title = "Quality",
                rows = qualityRows(state.quality) {
                    viewModel.setQuality(it)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Speed -> PlayerSelectionPanel(
                title = "Playback speed",
                rows = speedRows(state.speed) {
                    viewModel.setSpeed(it)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Frame -> PlayerSelectionPanel(
                title = "Frame",
                rows = selectionRows(
                    prefix = "frame",
                    selected = frameMode,
                    values = listOf("Fit", "Fill", "Original", "16:9", "4:3"),
                ) {
                    frameMode = it
                    viewModel.setFrameMode(it)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Hdr -> PlayerSelectionPanel(
                title = "HDR handling",
                rows = selectionRows(
                    prefix = "hdr",
                    selected = hdrMode,
                    values = listOf("Auto", "Passthrough", "Tone map", "Convert to SDR"),
                ) {
                    hdrMode = it
                    viewModel.setHdrMode(it)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.VideoTrack -> PlayerSelectionPanel(
                title = "Video",
                rows = state.videoTracks.map { track ->
                    PlayerSelectionRow(
                        key = "video:${track.id}",
                        label = track.label,
                        detail = track.language,
                        selected = track.selected,
                        onClick = {
                            videoTrack = track.label
                            viewModel.selectTrack(track)
                            closePanel()
                        },
                    )
                }.ifEmpty {
                    listOf(PlayerSelectionRow("video:none", "No alternate video tracks", onClick = {}))
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.AudioDelay -> PlaybackDelayPanel(
                title = "Audio delay",
                valueMs = state.audioDelayMs,
                onChange = viewModel::setAudioDelayMs,
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.SubtitleDelay -> PlaybackDelayPanel(
                title = "Subtitle delay",
                valueMs = state.subtitleDelayMs,
                onChange = viewModel::setSubtitleDelayMs,
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Deinterlace -> PlayerSelectionPanel(
                title = "Deinterlace",
                rows = selectionRows(
                    prefix = "deinterlace",
                    selected = deinterlaceMode,
                    values = listOf("Auto", "On", "Off"),
                ) {
                    deinterlaceMode = it
                    viewModel.setDeinterlaceMode(it)
                    closePanel()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Sleep -> PlayerSelectionPanel(
                title = "Sleep timer",
                rows = selectionRows(
                    prefix = "sleep",
                    selected = sleepTimer,
                    values = listOf("Off", "15 min", "30 min", "45 min", "1 hr", "End of episode"),
                ) { sleepTimer = it; closePanel() },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Information -> PlayerSelectionPanel(
                title = "Playback information",
                rows = listOf(
                    PlayerSelectionRow("info:source", "Source", state.playMethod ?: state.quality.label, onClick = {}),
                    PlayerSelectionRow("info:container", "Container", state.container ?: "Unknown", onClick = {}),
                    PlayerSelectionRow("info:video", "Video", state.videoDescription ?: videoTrack, onClick = {}),
                    PlayerSelectionRow("info:color", "Color", hdrMode, onClick = {}),
                    PlayerSelectionRow("info:audio", "Audio", state.audioDescription ?: state.audioTracks.firstOrNull { it.selected }?.label ?: "Unknown", onClick = {}),
                    PlayerSelectionRow("info:display", "Display", state.displayDescription ?: "TV", onClick = {}),
                    PlayerSelectionRow("info:decoder", "Decoder", "mpv / hardware", onClick = {}),
                    PlayerSelectionRow("info:dropped", "Dropped frames", "0", onClick = {}),
                ),
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.Options -> PlaybackOptionsPanel(
                audioDelayMs = state.audioDelayMs,
                subtitleDelayMs = state.subtitleDelayMs,
                onAudioDelayChange = {
                    noteInteraction()
                    viewModel.setAudioDelayMs(it)
                },
                onSubtitleDelayChange = {
                    noteInteraction()
                    viewModel.setSubtitleDelayMs(it)
                },
                onReset = {
                    noteInteraction()
                    viewModel.resetPlaybackDelays()
                },
                onDismiss = ::closePanel,
                modifier = Modifier.align(Alignment.CenterEnd),
            )

            TelevisionPlayerPanel.More -> PlayerSelectionPanel(
                title = "More",
                rows = listOf(
                    PlayerSelectionRow(
                        key = "more:speed",
                        label = "Playback speed",
                        detail = "${state.speed}x",
                        onClick = { openPanel(TelevisionPlayerPanel.Speed) },
                    ),
                    PlayerSelectionRow(
                        key = "more:quality",
                        label = "Frame",
                        detail = frameMode,
                        onClick = { openPanel(TelevisionPlayerPanel.Frame) },
                    ),
                    PlayerSelectionRow(
                        key = "more:hdr",
                        label = "HDR handling",
                        detail = hdrMode,
                        onClick = { openPanel(TelevisionPlayerPanel.Hdr) },
                    ),
                    PlayerSelectionRow("more:video", "Video", videoTrack, onClick = { openPanel(TelevisionPlayerPanel.VideoTrack) }),
                    PlayerSelectionRow("more:audio-delay", "Audio delay", signedPlaybackDelay(state.audioDelayMs), onClick = { openPanel(TelevisionPlayerPanel.AudioDelay) }),
                    PlayerSelectionRow("more:subtitle-delay", "Subtitle delay", signedPlaybackDelay(state.subtitleDelayMs), onClick = { openPanel(TelevisionPlayerPanel.SubtitleDelay) }),
                    PlayerSelectionRow("more:deinterlace", "Deinterlace", deinterlaceMode, onClick = { openPanel(TelevisionPlayerPanel.Deinterlace) }),
                    PlayerSelectionRow("more:sleep", "Sleep timer", sleepTimer, onClick = { openPanel(TelevisionPlayerPanel.Sleep) }),
                    PlayerSelectionRow("more:information", "Playback information", "Stream details", onClick = { openPanel(TelevisionPlayerPanel.Information) }),
                    PlayerSelectionRow(
                        key = "more:options",
                        label = "Legacy timing controls",
                        detail = "Audio and subtitle timing",
                        onClick = { openPanel(TelevisionPlayerPanel.Options) },
                    ),
                    PlayerSelectionRow(
                        key = "more:extras",
                        label = "While you watch",
                        detail = "Similar titles and cast",
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
            )

            null -> Unit
        }

        if (playbackError != null) {
            PlayerErrorOverlay(
                message = playbackError,
                onRetry = {
                    panel = null
                    panelBackStack = emptyList()
                    viewModel.retryPlayback()
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
                onPlayNext = viewModel::playUpNext,
                onPlayEpisode = viewModel::switchTo,
                onReplay = viewModel::togglePlayPause,
                onBack = ::exitPlayer,
            )
        }

        if (stillWatching) {
            StillWatchingOverlay(
                onContinue = {
                    stillWatching = false
                    noteInteraction()
                    viewModel.play()
                },
                onStop = ::exitPlayer,
            )
        }
    }
}

@Composable
private fun VideoSurface(
    onSurface: (android.view.Surface?) -> Unit,
    onSurfaceSize: (Int, Int) -> Unit,
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            onSurface(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            onSurfaceSize(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            onSurface(null)
                        }
                    },
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun VideoPlayerChrome(
    state: PlayerUiState,
    dimmed: Boolean,
    timelineFocus: FocusRequester,
    playPauseFocus: FocusRequester,
    exitArmed: Boolean,
    onExitButton: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onHideOsd: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPanel: (TelevisionPlayerPanel) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = state.state == PlayerState.Playing || state.state == PlayerState.Buffering
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (dimmed) 0.26f else 1f }
            .background(
                Brush.verticalGradient(
                    0f to TelevisionColors.Black.copy(alpha = 0f),
                    0.28f to TelevisionColors.Black.copy(alpha = 0f),
                    0.62f to TelevisionColors.Black.copy(alpha = 0.56f),
                    1f to TelevisionColors.Black.copy(alpha = 0.97f),
                ),
            )
            .padding(
                start = 67.dp,
                end = 67.dp,
                top = 150.dp,
                bottom = 32.dp,
            ),
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = 24.sp,
                lineHeight = 27.sp,
                letterSpacing = (-0.8).sp,
            ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        videoMetadata(state)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TelevisionColors.PaperMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(12.dp))
        PlayerTimeline(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            bufferedMs = state.bufferedMs,
            chapters = state.chapters,
            seekIntervalMs = state.seekIntervalSeconds.toLong() * 1_000L,
            focusRequester = timelineFocus,
            onSeekBy = {
                onInteraction()
                onSeekBy(it)
            },
            onClick = {
                onInteraction()
                onTogglePlayPause()
            },
            onNavigateUp = onHideOsd,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCompactActionButton(
                label = if (exitArmed) "Exit?" else "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                expandedWidth = 92.dp,
                onFocused = onInteraction,
                onClick = {
                    onInteraction()
                    onExitButton()
                },
            )
            PlayerCompactActionButton(
                label = "Rewind ${state.seekIntervalSeconds}s",
                icon = Icons.Default.Replay10,
                expandedWidth = 122.dp,
                onFocused = onInteraction,
                onClick = {
                    onInteraction()
                    onSeekBy(-state.seekIntervalSeconds.toLong() * 1_000L)
                },
            )
            PlayerCompactActionButton(
                label = if (playing) "Pause" else "Play",
                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                selected = playing,
                focusRequester = playPauseFocus,
                expandedWidth = 104.dp,
                onFocused = onInteraction,
                onClick = {
                    onInteraction()
                    onTogglePlayPause()
                },
            )
            PlayerCompactActionButton(
                label = "Forward ${state.seekIntervalSeconds}s",
                icon = Icons.Default.Forward10,
                expandedWidth = 126.dp,
                onFocused = onInteraction,
                onClick = {
                    onInteraction()
                    onSeekBy(state.seekIntervalSeconds.toLong() * 1_000L)
                },
            )
            if (state.previousEpisodeId != null) {
                PlayerCompactActionButton(
                    label = "Previous",
                    icon = Icons.Default.SkipPrevious,
                    expandedWidth = 112.dp,
                    onFocused = onInteraction,
                    onClick = {
                        onInteraction()
                        onPrevious()
                    },
                )
            }
            if (state.nextEpisodeId != null) {
                PlayerCompactActionButton(
                    label = "Next",
                    icon = Icons.Default.SkipNext,
                    expandedWidth = 92.dp,
                    onFocused = onInteraction,
                    onClick = {
                        onInteraction()
                        onNext()
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.chapters.isNotEmpty()) {
                PlayerCompactActionButton(
                    label = "Chapters",
                    icon = Icons.Default.VideoLibrary,
                    expandedWidth = 112.dp,
                    onFocused = onInteraction,
                    onClick = { onOpenPanel(TelevisionPlayerPanel.Chapters) },
                )
            }
            PlayerCompactActionButton(
                label = "Subtitles",
                icon = Icons.Default.ClosedCaption,
                selected = state.subtitleTracks.any(PlayerTrack::selected),
                expandedWidth = 122.dp,
                onFocused = onInteraction,
                onClick = { onOpenPanel(TelevisionPlayerPanel.Subtitles) },
            )
            if (state.audioTracks.isNotEmpty()) {
                PlayerCompactActionButton(
                    label = "Audio",
                    icon = Icons.Default.GraphicEq,
                    expandedWidth = 96.dp,
                    onFocused = onInteraction,
                    onClick = { onOpenPanel(TelevisionPlayerPanel.Audio) },
                )
            }
            PlayerCompactActionButton(
                label = "Options",
                icon = Icons.Default.Tune,
                expandedWidth = 92.dp,
                onFocused = onInteraction,
                onClick = { onOpenPanel(TelevisionPlayerPanel.More) },
            )
        }
    }
}

private fun videoMetadata(state: PlayerUiState): String? {
    val episode = if (state.isEpisode) {
        listOfNotNull(
            state.seasonNumber?.let { "S$it" },
            state.episodeNumber?.let { "E$it" },
        ).joinToString(" ")
    } else {
        null
    }
    return listOfNotNull(
        state.seriesName?.takeIf { it != state.title },
        episode?.takeIf(String::isNotBlank),
        state.year?.toString(),
    ).takeIf(List<String>::isNotEmpty)?.joinToString("  ·  ")
}

private fun selectionRows(
    prefix: String,
    selected: String,
    values: List<String>,
    onSelect: (String) -> Unit,
): List<PlayerSelectionRow> = values.map { value ->
    PlayerSelectionRow(
        key = "$prefix:$value",
        label = value,
        selected = value == selected,
        onClick = { onSelect(value) },
    )
}

private fun signedPlaybackDelay(valueMs: Long): String = when {
    valueMs > 0L -> "+${valueMs} ms"
    valueMs < 0L -> "${valueMs} ms"
    else -> "0 ms"
}
