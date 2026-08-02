package com.maik205.shoumeiplayer.player

import com.maik205.shoumeiplayer.domain.result.ApiResult

const val PLAY_METHOD_DIRECT = "DirectPlay"
const val PLAY_METHOD_DIRECT_STREAM = "DirectStream"
const val PLAY_METHOD_TRANSCODE = "Transcode"
const val DEFAULT_MAX_STREAMING_BITRATE = 400_000_000L

/** Provider-neutral stream metadata used by player policy and presentation. */
data class PlaybackMediaStream(
    val index: Int,
    val type: String? = null,
    val codec: String? = null,
    val language: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val channels: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val isAudioDescription: Boolean = false,
)

data class SystemCaptionStyle(
    val enabled: Boolean,
    val localeTag: String?,
    val fontScale: Float,
    val foregroundColor: Int,
    val backgroundColor: Int,
    val edgeType: Int,
    val edgeColor: Int,
    val typefaceName: String?,
)

/** User-level language and subtitle policy needed to select initial tracks. */
data class PlaybackTrackPreferences(
    val playDefaultAudioTrack: Boolean = true,
    val audioLanguagePreference: String? = null,
    val subtitleLanguagePreference: String? = null,
    val subtitleMode: String? = null,
)

fun interface PlaybackTrackPreferenceProvider {
    suspend fun preferences(): PlaybackTrackPreferences?
}

/**
 * The account preference that decided the current playback's track indices, published so the
 * engine can keep mpv's own language fallback consistent with it.
 *
 * The app never lets mpv pick a track by language: [TrackSelection] resolves an explicit Jellyfin
 * stream index from these very preferences and the engine sets `aid`/`sid` to it. mpv's
 * `alang`/`slang` are therefore only the fallback used when that index is absent from the actual
 * stream (a transcode can drop streams). Driving them from a second, client-side language setting
 * is what made the two disagree in the first place, so they are driven from this instead.
 *
 * It is a process-scoped holder rather than a constructor argument because the engine chain is
 * built at process start, long before a session (and therefore any account preference) exists, and
 * `PlayerEngine.configure` only carries device-owned `ClientSettings`. The publisher is the same
 * [PlaybackTrackPreferenceProvider] call that feeds [TrackSelection], which runs immediately
 * before every `load`, so what the engine reads is what the index selection just used.
 */
object ServerTrackPreferences {
    @Volatile
    private var published: PlaybackTrackPreferences? = null

    fun publish(preferences: PlaybackTrackPreferences?) {
        published = preferences
    }

    fun latestOrNull(): PlaybackTrackPreferences? = published

    /** Signed-out/test hook: a stale account's languages must not survive into the next session. */
    fun clear() {
        published = null
    }
}

/** Everything the engine and server-session coordinator need for one resolved stream. */
data class ResolvedPlayback(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val streamUrl: String,
    val playMethod: String,
    val runTimeTicks: Long?,
    val audioTracks: List<PlayerTrack>,
    val subtitleTracks: List<PlayerTrack>,
    val defaultAudioIndex: Int?,
    val defaultSubtitleIndex: Int?,
    val mediaStreams: List<PlaybackMediaStream> = emptyList(),
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
    val headers: Map<String, String>,
    val liveStreamId: String? = null,
    val requiresLiveStreamClose: Boolean = false,
    val maxStreamingBitrate: Long = DEFAULT_MAX_STREAMING_BITRATE,
)

interface PlaybackReporting {
    suspend fun reportStart(
        resolved: ResolvedPlayback,
        positionTicks: Long,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ): ApiResult<Unit>

    suspend fun reportProgress(
        resolved: ResolvedPlayback,
        positionTicks: Long,
        isPaused: Boolean,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ): ApiResult<Unit>

    suspend fun reportStopped(
        resolved: ResolvedPlayback,
        positionTicks: Long,
        failed: Boolean,
    ): ApiResult<Unit>

    suspend fun ping(playSessionId: String): ApiResult<Unit>
    suspend fun stopTranscode(resolved: ResolvedPlayback): ApiResult<Unit>
}
