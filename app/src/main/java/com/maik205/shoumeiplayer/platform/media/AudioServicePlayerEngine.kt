package com.maik205.shoumeiplayer.platform.media

import android.content.ComponentName
import android.content.Context
import android.view.Surface
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.player.AudioPlaybackHandoff
import com.maik205.shoumeiplayer.player.PlayRequest
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class AudioServicePlayerEngine(context: Context) : PlayerEngine {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture = MediaController.Builder(
        appContext,
        SessionToken(appContext, ComponentName(appContext, ShoumeiAudioPlaybackService::class.java)),
    ).buildAsync()
    private var controller: MediaController? = null
    private var pendingRequest: PlayRequest? = null
    private var pendingQueue: Pair<List<String>, String>? = null

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
        Futures.addCallback(
            controllerFuture,
            object : FutureCallback<MediaController> {
                override fun onSuccess(result: MediaController) {
                    controller = result
                    result.addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) = refresh(player)
                    })
                    pendingRequest?.also {
                        pendingRequest = null
                        load(it)
                    }
                    pendingQueue?.also { (ids, currentId) ->
                        pendingQueue = null
                        setQueue(ids, currentId)
                    }
                    refresh(result)
                }

                override fun onFailure(t: Throwable) {
                    _state.value = PlayerState.Error(t.message ?: "Audio service unavailable")
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
        scope.launch {
            while (true) {
                controller?.let(::refresh)
                delay(250)
            }
        }
    }

    override fun setSurface(surface: Surface?) = Unit
    override fun setSurfaceSize(width: Int, height: Int) = Unit
    override fun configure(settings: ClientSettings) = Unit

    override fun load(item: PlayRequest) {
        val mediaId = item.itemId
        if (mediaId.isNullOrBlank()) {
            _state.value = PlayerState.Error("Audio item has no stable media ID")
            return
        }
        val current = controller
        if (current == null) {
            pendingRequest = item
            return
        }
        AudioPlaybackHandoff.offer(null, item.copy(requiresVideoSurface = false))
        current.setMediaItem(
            MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).build())
                .build(),
            item.startPositionMs,
        )
        current.prepare()
        current.play()
    }

    override fun setQueue(itemIds: List<String>, currentItemId: String) {
        val ids = itemIds.filter(String::isNotBlank).distinct()
        val index = ids.indexOf(currentItemId)
        if (index < 0) return
        val current = controller
        if (current == null) {
            pendingQueue = ids to currentItemId
            return
        }
        current.setMediaItems(
            ids.map { MediaItem.Builder().setMediaId(it).build() },
            index,
            current.currentPosition.coerceAtLeast(0L),
        )
    }

    override fun play() { controller?.play() }
    override fun pause() { controller?.pause() }
    override fun seekTo(ms: Long) { controller?.seekTo(ms) }
    override fun setSpeed(speed: Float) { controller?.setPlaybackSpeed(speed) }
    override fun selectTrack(track: PlayerTrack) = Unit
    override fun stop() { controller?.stop() }

    override fun release() {
        controller?.release()
        controller = null
        scope.cancel()
    }

    private fun refresh(player: Player) {
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = player.duration.takeUnless { it == androidx.media3.common.C.TIME_UNSET }
        _bufferedMs.value = player.bufferedPosition.coerceAtLeast(0L)
        _speed.value = player.playbackParameters.speed
        _state.value = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlayerState.Buffering
            Player.STATE_READY -> if (player.playWhenReady) PlayerState.Playing else PlayerState.Paused
            Player.STATE_ENDED -> PlayerState.Ended
            else -> PlayerState.Idle
        }
    }
}
