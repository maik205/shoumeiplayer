package com.maik205.shoumeiplayer.platform.media

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.maik205.shoumeiplayer.ShoumeiApp
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.player.DEFAULT_MAX_STREAMING_BITRATE
import com.maik205.shoumeiplayer.player.AudioPlaybackHandoff
import com.maik205.shoumeiplayer.player.PlayRequest
import com.maik205.shoumeiplayer.player.PlaybackResolutionRequest
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlaybackMetricsEvent
import com.maik205.shoumeiplayer.player.PlaybackMetricsSink
import com.maik205.shoumeiplayer.player.PlaybackOwner
import com.maik205.shoumeiplayer.player.PlaybackOwnershipCoordinator
import com.maik205.shoumeiplayer.player.toPlaybackMetricsState
import com.maik205.shoumeiplayer.util.Ticks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.json.JSONArray

@UnstableApi
class ShoumeiAudioPlaybackService : MediaSessionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: AudioServicePlayer
    private lateinit var session: MediaSession
    private lateinit var resumptionStore: AudioResumptionStore
    private var metricsSink: PlaybackMetricsSink? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as ShoumeiApp).container
        val engine = container.playerEngine
        resumptionStore = AudioResumptionStore(this)
        metricsSink = container.newPlaybackMetricsSink()
        player = AudioServicePlayer(
            engine = engine,
            resolver = container.playbackRepository,
            reporter = container.progressReporter,
            metricsSink = metricsSink,
            scope = scope,
            onPersist = { items, index, positionMs ->
                scope.launch(Dispatchers.IO) {
                    val accountId = container.sessionStore.current()?.userId ?: return@launch
                    resumptionStore.save(accountId, items, index, positionMs)
                }
            },
            onEnded = {
                resumptionStore.clear()
                stopSelf()
            },
            ownershipCoordinator = container.playbackOwnershipCoordinator,
        )
        container.playbackOwnershipCoordinator.acquire(PlaybackOwner.AUDIO_SERVICE) {
            player.stopForOwnership()
        }
        session = MediaSession.Builder(this, player)
            .setId("shoumei-audio")
            .setCallback(object : MediaSession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: List<MediaItem>,
                ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(mediaItems)

                override fun onPlaybackResumption(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    isForPlayback: Boolean,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                    scope.launch {
                        val saved = resumptionStore.read()
                        val activeAccountId = container.sessionStore.current()?.userId
                        if (saved == null || saved.accountId != activeAccountId) {
                            if (saved != null) resumptionStore.clear()
                            future.set(
                                MediaSession.MediaItemsWithStartPosition(
                                    emptyList(),
                                    C.INDEX_UNSET,
                                    C.TIME_UNSET,
                                ),
                            )
                        } else {
                            future.set(
                                MediaSession.MediaItemsWithStartPosition(
                                    saved.mediaIds.map { MediaItem.Builder().setMediaId(it).build() },
                                    saved.currentIndex,
                                    saved.positionMs,
                                ),
                            )
                        }
                    }
                    return future
                }
            })
            .build()
        scope.launch {
            container.networkMonitor.snapshot.collect {
                metricsSink?.recordNetwork(it.transport, it.validated)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        if (!player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        val container = (application as ShoumeiApp).container
        container.playbackOwnershipCoordinator.release(PlaybackOwner.AUDIO_SERVICE)
        player.stop()
        player.release()
        session.release()
        metricsSink?.close()
        metricsSink = null
        scope.cancel()
        super.onDestroy()
    }
}

@UnstableApi
private class AudioServicePlayer(
    private val engine: PlayerEngine,
    private val resolver: com.maik205.shoumeiplayer.player.PlaybackResolver,
    private val reporter: com.maik205.shoumeiplayer.player.PlaybackProgressReporter,
    private val metricsSink: PlaybackMetricsSink?,
    private val scope: CoroutineScope,
    private val onPersist: (List<MediaItem>, Int, Long) -> Unit,
    private val onEnded: () -> Unit,
    private val ownershipCoordinator: PlaybackOwnershipCoordinator? = null,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private var items: List<MediaItem> = emptyList()
    private var currentIndex = 0
    private var reportingJob: Job? = null
    private var resolutionJob: Job? = null
    private var errorMessage: String? = null
    private var resolved: com.maik205.shoumeiplayer.player.ResolvedPlayback? = null

    init {
        scope.launch {
            combine(engine.state, engine.positionMs, engine.durationMs) { _, _, _ -> Unit }
                .collect {
                    invalidateState()
                    val state = engine.state.value
                    metricsSink?.record(
                        PlaybackMetricsEvent(
                            itemId = items.getOrNull(currentIndex)?.mediaId.orEmpty(),
                            state = state.toPlaybackMetricsState(),
                            positionMs = engine.positionMs.value,
                            durationMs = engine.durationMs.value,
                            errorCode = (state as? PlayerState.Error)?.message,
                        ),
                    )
                    metricsSink?.recordTracks(engine.tracks.value)
                    if (items.isNotEmpty()) onPersist(items, currentIndex, engine.positionMs.value)
                    if (engine.state.value == PlayerState.Ended) {
                        stopReporting(failed = false)
                        onEnded()
                    }
                }
        }
    }

    override fun getState(): State {
        val commands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_PREPARE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .add(Player.COMMAND_SET_MEDIA_ITEM)
            .add(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)
            .apply {
                if (currentIndex > 0) add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                if (currentIndex < items.lastIndex) add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            .build()
        val mediaItems = items.map { item ->
            SimpleBasePlayer.MediaItemData.Builder(item.mediaId)
                .setMediaItem(item)
                .setDurationUs(
                    if (item == items.getOrNull(currentIndex)) {
                        engine.durationMs.value?.times(1_000) ?: C.TIME_UNSET
                    } else {
                        C.TIME_UNSET
                    },
                )
                .setIsSeekable(true)
                .build()
        }
        val playbackState = when {
            errorMessage != null -> Player.STATE_IDLE
            else -> when (engine.state.value) {
            PlayerState.Loading, PlayerState.Buffering -> Player.STATE_BUFFERING
            PlayerState.Playing, PlayerState.Paused -> Player.STATE_READY
            PlayerState.Ended -> Player.STATE_ENDED
            PlayerState.Idle, is PlayerState.Error -> Player.STATE_IDLE
            }
        }
        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(mediaItems)
            .setCurrentMediaItemIndex(currentIndex.coerceAtMost(items.lastIndex.coerceAtLeast(0)))
            .setPlaybackState(playbackState)
            .setPlayWhenReady(engine.state.value == PlayerState.Playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setContentPositionMs(engine.positionMs.value)
            .setContentBufferedPositionMs { engine.bufferedMs.value ?: engine.positionMs.value }
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
    }

    override fun handleSetMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<*> {
        ownershipCoordinator?.acquire(PlaybackOwner.AUDIO_SERVICE) { stopForOwnership() }
        val previousItemId = items.getOrNull(currentIndex)?.mediaId
        resolutionJob?.cancel()
        resolutionJob = null
        errorMessage = null
        items = mediaItems.filter { it.mediaId.isNotBlank() }
        currentIndex = startIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        if (previousItemId != items.getOrNull(currentIndex)?.mediaId || engine.state.value == PlayerState.Idle) {
            loadCurrent(startPositionMs.coerceAtLeast(0L), playWhenReady = true)
        }
        invalidateState()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) engine.play() else engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()

    override fun handleStop(): ListenableFuture<*> {
        resolutionJob?.cancel()
        resolutionJob = null
        errorMessage = null
        AudioPlaybackHandoff.clear()
        stopReporting(failed = false)
        engine.stop()
        onPersist(items, currentIndex, engine.positionMs.value)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        val targetIndex = when (seekCommand) {
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            -> currentIndex - 1
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            -> currentIndex + 1
            else -> mediaItemIndex
        }
        if (targetIndex != currentIndex && targetIndex in items.indices) {
            currentIndex = targetIndex
            loadCurrent(positionMs.coerceAtLeast(0L), playWhenReady = true)
        } else {
            engine.seekTo(positionMs.coerceAtLeast(0L))
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        resolutionJob?.cancel()
        resolutionJob = null
        AudioPlaybackHandoff.clear()
        stopReporting(failed = false)
        // AppContainer owns the process-global libmpv instance. Releasing it here would invalidate
        // later video playback and any other wrapper sharing the same native handle.
        engine.stop()
        return Futures.immediateVoidFuture()
    }

    private fun loadCurrent(positionMs: Long, playWhenReady: Boolean) {
        val item = items.getOrNull(currentIndex) ?: return
        resolutionJob?.cancel()
        resolutionJob = null
        errorMessage = null
        val handoff = AudioPlaybackHandoff.consume(item.mediaId, null)
        val request = handoff?.request
        if (request != null) {
            handoff.resolved?.let(::startReporting)
            engine.load(request.copy(startPositionMs = positionMs, requiresVideoSurface = false))
            if (playWhenReady) engine.play()
            return
        }
        resolutionJob = scope.launch {
            when (val result = resolver.resolve(
                PlaybackResolutionRequest(
                    itemId = item.mediaId,
                    startPositionTicks = Ticks.fromMs(positionMs),
                    mediaSourceId = null,
                    audioStreamIndex = null,
                    subtitleStreamIndex = -1,
                    maxStreamingBitrate = DEFAULT_MAX_STREAMING_BITRATE,
                    forceTranscode = false,
                ),
            )) {
                is ApiResult.Failure -> {
                    errorMessage = result.error.displayMessage
                    Log.e(TAG, "Audio resolution failed for item ${item.mediaId}: ${result.error}")
                    invalidateState()
                }
                is ApiResult.Success -> {
                    errorMessage = null
                    val resolved = result.data
                    startReporting(resolved)
                    engine.load(
                        PlayRequest(
                            url = resolved.streamUrl,
                            title = item.mediaMetadata.title?.toString().orEmpty(),
                            headers = resolved.headers,
                            startPositionMs = positionMs,
                            durationMs = resolved.runTimeTicks?.let(Ticks::toMs),
                            requiresVideoSurface = false,
                            preferredAudioTrackId = resolved.defaultAudioIndex,
                            preferredSubtitleTrackId = -1,
                        ),
                    )
                    if (playWhenReady) engine.play()
                }
            }
        }
    }

    fun stopForOwnership() {
        resolutionJob?.cancel()
        resolutionJob = null
        errorMessage = null
        AudioPlaybackHandoff.clear()
        stopReporting(failed = false)
        engine.stop()
        invalidateState()
    }

    private fun startReporting(item: com.maik205.shoumeiplayer.player.ResolvedPlayback) {
        stopReporting(failed = false)
        resolved = item
        reportingJob = scope.launch {
            reporter.run(
                engine = engine,
                resolved = item,
                selectedAudioIndex = { item.defaultAudioIndex },
                selectedSubtitleIndex = { -1 },
            )
        }
    }

    private fun stopReporting(failed: Boolean) {
        reportingJob?.cancel()
        reportingJob = null
        val outgoing = resolved ?: return
        resolved = null
        scope.launch(Dispatchers.IO) {
            reporter.reportStopped(outgoing, engine.positionMs.value, failed)
        }
    }
}

private const val TAG = "ShoumeiAudioPlaybackService"

internal data class SavedAudioPlayback(
    val accountId: String,
    val mediaIds: List<String>,
    val currentIndex: Int,
    val positionMs: Long,
)

internal class AudioResumptionStore(context: Context) {
    private val preferences = context.getSharedPreferences("audio_resumption", Context.MODE_PRIVATE)

    fun save(accountId: String, items: List<MediaItem>, currentIndex: Int, positionMs: Long) {
        val ids = items.map(MediaItem::mediaId).filter(String::isNotBlank)
        if (ids.isEmpty()) return
        preferences.edit()
            .putString("account_id", accountId)
            .putString("media_ids", JSONArray(ids).toString())
            .putInt("current_index", currentIndex.coerceIn(ids.indices))
            .putLong("position_ms", positionMs.coerceAtLeast(0L))
            .apply()
    }

    fun read(): SavedAudioPlayback? {
        val accountId = preferences.getString("account_id", null)?.takeIf(String::isNotBlank) ?: return null
        val encoded = preferences.getString("media_ids", null) ?: return null
        val ids = runCatching {
            val array = JSONArray(encoded)
            List(array.length()) { array.getString(it) }.filter(String::isNotBlank)
        }.getOrNull().orEmpty()
        if (ids.isEmpty()) return null
        return SavedAudioPlayback(
            accountId = accountId,
            mediaIds = ids,
            currentIndex = preferences.getInt("current_index", 0).coerceIn(ids.indices),
            positionMs = preferences.getLong("position_ms", 0L).coerceAtLeast(0L),
        )
    }

    fun clear() {
        preferences.edit().clear().apply()
    }
}
