package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.maik205.shoumeiplayer.di.player.JellyfinPlaybackMetadataLoader
import com.maik205.shoumeiplayer.feature.player.PlayerViewModel
import com.maik205.shoumeiplayer.feature.player.TrackController
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.VideoQuality
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel

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
            playbackResolver = container.playbackRepository,
            trackController = TrackController(
                preferenceProvider = container.authRepository,
                initialAudioStreamIndex = initialAudioStreamIndex,
                initialSubtitleStreamIndex = initialSubtitleStreamIndex,
                preferAudioDescription = container.audioDescriptionRequested,
            ),
            metadataLoader = JellyfinPlaybackMetadataLoader(
                libraryRepository = container.libraryRepository,
                authRepository = container.authRepository,
                imageUrlBuilder = container.imageUrlBuilder,
                settingsStore = container.settingsStore,
                playbackRepository = container.playbackRepository,
            ),
            reporter = container.progressReporter,
            itemId = itemId,
            startPositionTicks = startPositionTicks,
            teardownScope = container.applicationScope,
            settingsStore = container.settingsStore,
            initialQualityLabel = initialQualityLabel,
            audioRouteLabel = container.audioRouteLabel,
            effectiveHdrMode = container.effectiveHdrModeLabel,
            networkAvailable = container.networkMonitor.snapshot.map { it.validated }
                .stateIn(
                    container.applicationScope,
                    SharingStarted.Eagerly,
                    container.networkMonitor.snapshot.value.validated,
                ),
        )
    }
    val controller = remember(viewModel) { PlayerViewModelController(viewModel) }
    PlayerMediaSession(viewModel, controller)
    TelevisionPlayerContent(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        timelineState = viewModel.timelineState,
        controller = controller,
        audioOnly = audioOnly,
        onExit = onExit,
        onNavigateToItem = onNavigateToItem,
        onNavigateToPerson = onNavigateToPerson,
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerMediaSession(
    viewModel: PlayerViewModel,
    controller: TelevisionPlayerController,
) {
    val context = LocalContext.current
    val player = remember(viewModel, controller) {
        ShoumeiMedia3Player(
            uiState = viewModel.uiState,
            timelineState = viewModel.timelineState,
            activeItemId = { viewModel.activeItemId },
            onPlay = controller::play,
            onPause = controller::pause,
            onStop = controller::stopAndReport,
            onSeek = controller::seekTo,
            onPrevious = {
                if (viewModel.uiState.value.isAudio) {
                    controller.playPreviousAudio()
                } else {
                    controller.playPreviousEpisode()
                }
            },
            onNext = {
                if (viewModel.uiState.value.isAudio) {
                    controller.playNextAudio()
                } else {
                    controller.playNextEpisode()
                }
            },
            onSetSpeed = controller::setSpeed,
        )
    }

    DisposableEffect(context, player) {
        val session = createPlayerMediaSession(context, player)
        onDispose {
            session.release()
            player.release()
        }
    }
}

internal interface TelevisionPlayerController {
    fun stopAndReport()
    fun loadShelves()
    fun play()
    fun pause()
    fun togglePlayPause()
    fun playUpNext()
    fun playPreviousEpisode()
    fun playNextEpisode()
    fun playPreviousAudio()
    fun playNextAudio()
    fun switchTo(itemId: String)
    fun seekBy(deltaMs: Long)
    fun seekTo(positionMs: Long)
    fun setSurface(surface: Surface?)
    fun setSurfaceSize(width: Int, height: Int)
    fun selectTrack(track: PlayerTrack)
    fun setQuality(quality: VideoQuality)
    fun setSpeed(speed: Float)
    fun setFrameMode(mode: String)
    fun setHdrMode(mode: String)
    fun setDeinterlaceMode(mode: String)
    fun setAudioDelayMs(delayMs: Long)
    fun setSubtitleDelayMs(delayMs: Long)
    fun resetPlaybackDelays()
    fun retryPlayback()
}

private class PlayerViewModelController(
    private val viewModel: PlayerViewModel,
) : TelevisionPlayerController {
    override fun stopAndReport() = viewModel.stopAndReport()
    override fun loadShelves() = viewModel.loadShelves()
    override fun play() = viewModel.play()
    override fun pause() = viewModel.pause()
    override fun togglePlayPause() = viewModel.togglePlayPause()
    override fun playUpNext() = viewModel.playUpNext()
    override fun playPreviousEpisode() = viewModel.playPreviousEpisode()
    override fun playNextEpisode() = viewModel.playNextEpisode()
    override fun playPreviousAudio() = viewModel.playPreviousAudio()
    override fun playNextAudio() = viewModel.playNextAudio()
    override fun switchTo(itemId: String) = viewModel.switchTo(itemId)
    override fun seekBy(deltaMs: Long) = viewModel.seekBy(deltaMs)
    override fun seekTo(positionMs: Long) = viewModel.seekTo(positionMs)
    override fun setSurface(surface: Surface?) = viewModel.setSurface(surface)
    override fun setSurfaceSize(width: Int, height: Int) = viewModel.setSurfaceSize(width, height)
    override fun selectTrack(track: PlayerTrack) = viewModel.selectTrack(track)
    override fun setQuality(quality: VideoQuality) = viewModel.setQuality(quality)
    override fun setSpeed(speed: Float) = viewModel.setSpeed(speed)
    override fun setFrameMode(mode: String) = viewModel.setFrameMode(mode)
    override fun setHdrMode(mode: String) = viewModel.setHdrMode(mode)
    override fun setDeinterlaceMode(mode: String) = viewModel.setDeinterlaceMode(mode)
    override fun setAudioDelayMs(delayMs: Long) = viewModel.setAudioDelayMs(delayMs)
    override fun setSubtitleDelayMs(delayMs: Long) = viewModel.setSubtitleDelayMs(delayMs)
    override fun resetPlaybackDelays() = viewModel.resetPlaybackDelays()
    override fun retryPlayback() = viewModel.retryPlayback()
}
