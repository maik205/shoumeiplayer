package com.maik205.shoumeiplayer.player

enum class PlaybackMetricsState {
    PREPARING,
    PLAYING,
    PAUSED,
    BUFFERING,
    ENDED,
    FAILED,
    STOPPED,
}

data class PlaybackMetricsEvent(
    val itemId: String,
    val state: PlaybackMetricsState,
    val positionMs: Long,
    val durationMs: Long?,
    val playMethod: String? = null,
    val errorCode: String? = null,
)

interface PlaybackMetricsSink : AutoCloseable {
    fun record(event: PlaybackMetricsEvent)
    fun recordNetwork(transport: String, validated: Boolean) = Unit
    fun recordTracks(tracks: List<PlayerTrack>) = Unit
    override fun close() = Unit
}

fun PlayerState.toPlaybackMetricsState(): PlaybackMetricsState = when (this) {
    PlayerState.Loading, PlayerState.Idle -> PlaybackMetricsState.PREPARING
    PlayerState.Playing -> PlaybackMetricsState.PLAYING
    PlayerState.Paused -> PlaybackMetricsState.PAUSED
    PlayerState.Buffering -> PlaybackMetricsState.BUFFERING
    PlayerState.Ended -> PlaybackMetricsState.ENDED
    is PlayerState.Error -> PlaybackMetricsState.FAILED
}
