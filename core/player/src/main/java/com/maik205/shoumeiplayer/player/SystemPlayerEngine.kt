package com.maik205.shoumeiplayer.player

import android.content.Context
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Media3-backed engine used when platform playback is selected. */
internal class SystemPlayerEngine(context: Context) : PlayerEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player: ExoPlayer
    private val httpFactory = DefaultHttpDataSource.Factory()
    private var currentSurface: Surface? = null

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state
    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs
    private val _durationMs = MutableStateFlow<Long?>(null)
    override val durationMs: StateFlow<Long?> = _durationMs
    private val _bufferedMs = MutableStateFlow<Long?>(null)
    override val bufferedMs: StateFlow<Long?> = _bufferedMs
    private val _tracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val tracks: StateFlow<List<PlayerTrack>> = _tracks
    private val _speed = MutableStateFlow(1f)
    override val speed: StateFlow<Float> = _speed
    private val _videoFps = MutableStateFlow<Double?>(null)
    override val videoFps: StateFlow<Double?> = _videoFps

    init {
        val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext, httpFactory)
        player = buildPlayer(context, dataSourceFactory)
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                refresh()
            }

            override fun onPlayerError(error: PlaybackException) {
                _state.value = PlayerState.Error(error.errorCodeName)
            }
        })
        scope.launch {
            while (true) {
                refresh()
                delay(250)
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(context: Context, dataSourceFactory: DefaultDataSource.Factory): ExoPlayer =
        ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

    override fun setSurface(surface: Surface?) {
        currentSurface = surface
        player.setVideoSurface(surface)
    }

    override fun configure(settings: ClientSettings) = Unit

    @OptIn(UnstableApi::class)
    override fun load(item: PlayRequest) {
        httpFactory.setDefaultRequestProperties(item.headers)
        val mediaItem = MediaItem.Builder()
            .setMediaId(item.itemId ?: item.url)
            .setUri(item.url)
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(item.title).build())
            .build()
        player.setMediaItem(mediaItem, item.startPositionMs.coerceAtLeast(0L))
        _state.value = PlayerState.Loading
        player.prepare()
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun seekTo(ms: Long) = player.seekTo(ms.coerceAtLeast(0L))

    override fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 4f)
        player.setPlaybackSpeed(clamped)
        _speed.value = clamped
    }

    override fun selectTrack(track: PlayerTrack) = Unit

    override fun stop() {
        player.stop()
        _state.value = PlayerState.Idle
    }

    override fun release() {
        player.setVideoSurface(null)
        currentSurface = null
        player.release()
        scope.cancel()
    }

    private fun refresh() {
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = player.duration.takeUnless { it == C.TIME_UNSET }
        _bufferedMs.value = player.bufferedPosition.takeUnless { it == C.TIME_UNSET }
        _speed.value = player.playbackParameters.speed
        _state.value = when {
            _state.value is PlayerState.Error -> _state.value
            player.playbackState == Player.STATE_BUFFERING -> PlayerState.Buffering
            player.playbackState == Player.STATE_READY && player.playWhenReady -> PlayerState.Playing
            player.playbackState == Player.STATE_READY -> PlayerState.Paused
            player.playbackState == Player.STATE_ENDED -> PlayerState.Ended
            else -> _state.value
        }
    }
}
