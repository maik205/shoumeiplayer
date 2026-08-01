package com.maik205.shoumeiplayer.platform.media

import android.content.Context
import android.media.metrics.MediaMetricsManager
import android.media.metrics.PlaybackMetrics
import android.media.metrics.NetworkEvent
import android.media.metrics.PlaybackErrorEvent
import android.media.metrics.PlaybackSession
import android.media.metrics.PlaybackStateEvent
import android.media.metrics.TrackChangeEvent
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.maik205.shoumeiplayer.BuildConfig
import com.maik205.shoumeiplayer.player.PlaybackMetricsEvent
import com.maik205.shoumeiplayer.player.PlaybackMetricsSink
import com.maik205.shoumeiplayer.player.PlaybackMetricsState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.TrackType

@RequiresApi(Build.VERSION_CODES.S)
internal class AndroidPlaybackMetricsSink(context: Context) : PlaybackMetricsSink {
    private val createdAtMs = SystemClock.elapsedRealtime()
    private val session: PlaybackSession? = context
        .getSystemService(MediaMetricsManager::class.java)
        ?.createPlaybackSession()

    init {
        session?.reportPlaybackMetrics(
            PlaybackMetrics.Builder()
                .setPlayerName("Shoumei Player")
                .setPlayerVersion(BuildConfig.VERSION_NAME)
                .setStreamType(PlaybackMetrics.STREAM_TYPE_OTHER)
                .setPlaybackType(PlaybackMetrics.PLAYBACK_TYPE_VOD)
                .build(),
        )
    }

    override fun record(event: PlaybackMetricsEvent) {
        val state = when (event.state) {
            PlaybackMetricsState.PREPARING -> PlaybackStateEvent.STATE_JOINING_FOREGROUND
            PlaybackMetricsState.PLAYING -> PlaybackStateEvent.STATE_PLAYING
            PlaybackMetricsState.PAUSED -> PlaybackStateEvent.STATE_PAUSED
            PlaybackMetricsState.BUFFERING -> PlaybackStateEvent.STATE_BUFFERING
            PlaybackMetricsState.ENDED -> PlaybackStateEvent.STATE_ENDED
            PlaybackMetricsState.FAILED -> PlaybackStateEvent.STATE_FAILED
            PlaybackMetricsState.STOPPED -> PlaybackStateEvent.STATE_STOPPED
        }
        session?.reportPlaybackStateEvent(
            PlaybackStateEvent.Builder()
                .setState(state)
                .setTimeSinceCreatedMillis(SystemClock.elapsedRealtime() - createdAtMs)
                .build(),
        )
        if (event.state == PlaybackMetricsState.FAILED) {
            session?.reportPlaybackErrorEvent(
                PlaybackErrorEvent.Builder()
                    .setTimeSinceCreatedMillis(SystemClock.elapsedRealtime() - createdAtMs)
                    .setErrorCode(PlaybackErrorEvent.ERROR_PLAYER_OTHER)
                    .setException(IllegalStateException(event.errorCode ?: "Playback failed"))
                    .build(),
            )
        }
    }

    override fun recordNetwork(transport: String, validated: Boolean) {
        val type = if (!validated) {
            NetworkEvent.NETWORK_TYPE_OFFLINE
        } else when (transport) {
            "wifi" -> NetworkEvent.NETWORK_TYPE_WIFI
            "ethernet" -> NetworkEvent.NETWORK_TYPE_ETHERNET
            "cellular" -> NetworkEvent.NETWORK_TYPE_OTHER
            else -> NetworkEvent.NETWORK_TYPE_UNKNOWN
        }
        session?.reportNetworkEvent(
            NetworkEvent.Builder()
                .setNetworkType(type)
                .setTimeSinceCreatedMillis(SystemClock.elapsedRealtime() - createdAtMs)
                .build(),
        )
    }

    override fun recordTracks(tracks: List<PlayerTrack>) {
        TrackType.entries.forEach { type ->
            val selected = tracks.firstOrNull { it.type == type && it.selected }
            val platformType = when (type) {
                TrackType.VIDEO -> TrackChangeEvent.TRACK_TYPE_VIDEO
                TrackType.AUDIO -> TrackChangeEvent.TRACK_TYPE_AUDIO
                TrackType.SUBTITLE -> TrackChangeEvent.TRACK_TYPE_TEXT
            }
            val builder = TrackChangeEvent.Builder(platformType)
                .setTimeSinceCreatedMillis(SystemClock.elapsedRealtime() - createdAtMs)
                .setTrackState(
                    if (selected == null) TrackChangeEvent.TRACK_STATE_OFF else TrackChangeEvent.TRACK_STATE_ON,
                )
            selected?.language?.takeIf(String::isNotBlank)?.let(builder::setLanguage)
            session?.reportTrackChangeEvent(builder.build())
        }
    }

    override fun close() {
        session?.close()
    }
}
