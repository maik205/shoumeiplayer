package com.maik205.shoumeiplayer.ui.television.screens.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.maik205.shoumeiplayer.MainActivity
import com.maik205.shoumeiplayer.feature.player.AudioQueueItemUi
import com.maik205.shoumeiplayer.feature.player.PlayerTimelineState
import com.maik205.shoumeiplayer.feature.player.PlayerUiState
import com.maik205.shoumeiplayer.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@UnstableApi
internal class ShoumeiMedia3Player(
    private val uiState: StateFlow<PlayerUiState>,
    private val timelineState: StateFlow<PlayerTimelineState>,
    private val activeItemId: () -> String,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onSeek: (Long) -> Unit,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
    private val onSetSpeed: (Float) -> Unit,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        scope.launch {
            combine(uiState, timelineState) { _, _ -> Unit }.collect {
                invalidateState()
            }
        }
    }

    override fun getState(): State {
        val ui = uiState.value
        val timeline = timelineState.value
        val playlist = sessionPlaylist(ui, timeline, activeItemId())
        val playbackState = ui.state.toMedia3PlaybackState()
        val playing = ui.state == PlayerState.Playing || ui.state == PlayerState.Buffering
        val commands = Player.Commands.Builder()
            .add(Player.COMMAND_PLAY_PAUSE)
            .add(Player.COMMAND_STOP)
            .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_BACK)
            .add(Player.COMMAND_SEEK_FORWARD)
            .add(Player.COMMAND_SET_SPEED_AND_PITCH)
            .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            .add(Player.COMMAND_GET_TIMELINE)
            .add(Player.COMMAND_GET_METADATA)
            .apply {
                if (playlist.currentIndex > 0) {
                    add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
                if (playlist.currentIndex < playlist.items.lastIndex) {
                    add(Player.COMMAND_SEEK_TO_NEXT)
                    add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
            }
            .build()

        return State.Builder()
            .setAvailableCommands(commands)
            .setPlaylist(playlist.items)
            .setCurrentMediaItemIndex(playlist.currentIndex)
            .setPlaybackState(playbackState)
            .setPlayWhenReady(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setIsLoading(playbackState == Player.STATE_BUFFERING)
            .setContentPositionMs(timeline.positionMs)
            .setContentBufferedPositionMs { timeline.bufferedMs ?: timeline.positionMs }
            .setTotalBufferedDurationMs {
                ((timeline.bufferedMs ?: timeline.positionMs) - timeline.positionMs).coerceAtLeast(0L)
            }
            .setSeekBackIncrementMs(ui.seekIntervalSeconds * 1_000L)
            .setSeekForwardIncrementMs(ui.seekIntervalSeconds * 1_000L)
            .setPlaybackParameters(PlaybackParameters(ui.speed))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(if (ui.isAudio) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) onPlay() else onPause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        onStop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: @Player.Command Int,
    ): ListenableFuture<*> {
        when (seekCommand.toMediaSessionSeekAction()) {
            MediaSessionSeekAction.Previous -> onPrevious()
            MediaSessionSeekAction.Next -> onNext()
            MediaSessionSeekAction.Position -> onSeek(positionMs.coerceAtLeast(0L))
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        onSetSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }
}

@UnstableApi
internal fun createPlayerMediaSession(
    context: Context,
    player: Player,
): MediaSession {
    val sessionActivity = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return MediaSession.Builder(context, player)
        .setSessionActivity(sessionActivity)
        .build()
}

internal fun PlayerState.toMedia3PlaybackState(): @Player.State Int = when (this) {
    PlayerState.Loading, PlayerState.Buffering -> Player.STATE_BUFFERING
    PlayerState.Playing, PlayerState.Paused -> Player.STATE_READY
    PlayerState.Ended -> Player.STATE_ENDED
    PlayerState.Idle, is PlayerState.Error -> Player.STATE_IDLE
}

internal fun Int.toMediaSessionSeekAction(): MediaSessionSeekAction = when (this) {
    Player.COMMAND_SEEK_TO_PREVIOUS,
    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
    -> MediaSessionSeekAction.Previous

    Player.COMMAND_SEEK_TO_NEXT,
    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    -> MediaSessionSeekAction.Next

    else -> MediaSessionSeekAction.Position
}

internal enum class MediaSessionSeekAction {
    Previous,
    Next,
    Position,
}

@UnstableApi
private fun sessionPlaylist(
    ui: PlayerUiState,
    timeline: PlayerTimelineState,
    activeItemId: String,
): SessionPlaylist {
    if (ui.isAudio && ui.queue.isNotEmpty()) {
        val currentIndex = ui.queue.indexOfFirst { it.itemId == activeItemId || it.playing }
            .takeIf { it >= 0 }
            ?: 0
        return SessionPlaylist(
            items = ui.queue.map { item -> item.toMediaItemData(item.itemId == activeItemId, timeline) },
            currentIndex = currentIndex,
        )
    }

    val itemIds = buildList {
        ui.previousEpisodeId?.let(::add)
        add(activeItemId)
        ui.nextEpisodeId?.let(::add)
    }
    val currentIndex = itemIds.indexOf(activeItemId)
    return SessionPlaylist(
        items = itemIds.map { itemId ->
            val current = itemId == activeItemId
            val metadata = if (current) ui.currentMediaMetadata() else MediaMetadata.EMPTY
            SimpleBasePlayer.MediaItemData.Builder(itemId)
                .setMediaItem(MediaItem.Builder().setMediaId(itemId).setMediaMetadata(metadata).build())
                .setDurationUs(if (current) timeline.durationMs.toDurationUs() else C.TIME_UNSET)
                .setIsSeekable(current)
                .build()
        },
        currentIndex = currentIndex,
    )
}

@UnstableApi
private fun AudioQueueItemUi.toMediaItemData(
    current: Boolean,
    timeline: PlayerTimelineState,
): SimpleBasePlayer.MediaItemData {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
        .build()
    return SimpleBasePlayer.MediaItemData.Builder(itemId)
        .setMediaItem(MediaItem.Builder().setMediaId(itemId).setMediaMetadata(metadata).build())
        .setDurationUs((if (current) timeline.durationMs else durationMs).toDurationUs())
        .setIsSeekable(true)
        .build()
}

private fun PlayerUiState.currentMediaMetadata(): MediaMetadata = MediaMetadata.Builder()
    .setTitle(title)
    .setArtist(artist ?: seriesName)
    .setAlbumTitle(album)
    .apply { (albumArtworkUrl ?: logoUrl)?.let { setArtworkUri(Uri.parse(it)) } }
    .build()

private fun Long?.toDurationUs(): Long = this?.takeIf { it > 0L }?.times(1_000L) ?: C.TIME_UNSET

@UnstableApi
private data class SessionPlaylist(
    val items: List<SimpleBasePlayer.MediaItemData>,
    val currentIndex: Int,
)
