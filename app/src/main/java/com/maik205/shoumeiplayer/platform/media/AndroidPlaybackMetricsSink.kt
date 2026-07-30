package com.maik205.shoumeiplayer.platform.media

import android.content.Context
import android.media.metrics.MediaMetricsManager
import android.media.metrics.PlaybackMetrics
import android.media.metrics.PlaybackSession
import android.media.metrics.PlaybackStateEvent
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import com.maik205.shoumeiplayer.BuildConfig
import com.maik205.shoumeiplayer.player.PlaybackMetricsEvent
import com.maik205.shoumeiplayer.player.PlaybackMetricsSink
import com.maik205.shoumeiplayer.player.PlaybackMetricsState

@RequiresApi(Build.VERSION_CODES.S)
internal class AndroidPlaybackMetricsSink(context: Context) : PlaybackMetricsSink {
    private val createdAtMs = SystemClock.elapsedRealtime()
    private val session: PlaybackSession? = context
        .getSystemService(MediaMetricsManager::class.java)
        ?.getPlaybackSession()

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
            PlaybackMetricsState.FAILED -> PlaybackStateEvent.STATE_STOPPED
            PlaybackMetricsState.STOPPED -> PlaybackStateEvent.STATE_STOPPED
        }
        session?.reportPlaybackStateEvent(
            PlaybackStateEvent.Builder()
                .setState(state)
                .setTimeSinceCreatedMillis(SystemClock.elapsedRealtime() - createdAtMs)
                .build(),
        )
    }

    override fun close() {
        session?.close()
    }
}
