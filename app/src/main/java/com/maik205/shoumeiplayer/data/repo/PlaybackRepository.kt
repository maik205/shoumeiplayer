package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.data.ApiError
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.TrickplayMath
import com.maik205.shoumeiplayer.data.TrickplaySource
import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.api.ShoumeiDeviceProfile
import com.maik205.shoumeiplayer.data.api.dto.ChapterInfoDto
import com.maik205.shoumeiplayer.data.api.dto.MediaSourceInfoDto
import com.maik205.shoumeiplayer.data.api.dto.MediaStreamDto
import com.maik205.shoumeiplayer.data.api.dto.PlaybackInfoDto
import com.maik205.shoumeiplayer.data.api.dto.PlaybackInfoResponse
import com.maik205.shoumeiplayer.data.api.dto.PlaybackProgressBody
import com.maik205.shoumeiplayer.data.api.dto.PlaybackStopBody
import com.maik205.shoumeiplayer.data.api.dto.TrickplayInfoDto
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.player.ExternalSubtitle
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.TrackType

/** `PlayMethod` values Jellyfin reporting understands. */
const val PLAY_METHOD_DIRECT = "DirectPlay"
const val PLAY_METHOD_TRANSCODE = "Transcode"

/**
 * The "no cap" ceiling used when the user has quality on Auto: high enough that the server never
 * transcodes for bandwidth alone, which is what makes direct play the normal case on a LAN.
 */
const val DEFAULT_MAX_STREAMING_BITRATE = 400_000_000L

/** Playback resolution result: everything a player needs to start a stream. */
data class ResolvedPlayback(
    val itemId: String,
    val mediaSourceId: String,
    val playSessionId: String,
    val streamUrl: String,
    val playMethod: String, // "DirectPlay" | "DirectStream" | "Transcode"
    val runTimeTicks: Long?,
    val audioTracks: List<PlayerTrack>,
    val subtitleTracks: List<PlayerTrack>,
    val defaultAudioIndex: Int?,
    val defaultSubtitleIndex: Int?,
    /**
     * Every `MediaStream` of the chosen source, verbatim. Track selection needs fields
     * ([MediaStreamDto.isForced], [MediaStreamDto.language]) that [PlayerTrack] does not carry.
     */
    val mediaStreams: List<MediaStreamDto> = emptyList(),
    /**
     * Subtitles the server will hand over as separate files rather than muxing them in. The player
     * has to sideload these explicitly or they simply never appear.
     */
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
    val headers: Map<String, String>,
    /**
     * The `MaxStreamingBitrate` this resolve was made with. Carried so the OSD can show which
     * quality rung the running stream belongs to without keeping a second copy of that state.
     */
    val maxStreamingBitrate: Long = DEFAULT_MAX_STREAMING_BITRATE,
    /**
     * Chapter marks and the trickplay manifest for the item. `/PlaybackInfo` returns neither, so
     * these stay empty here — `PlayerViewModel` fills them from its `LibraryRepository.item()`
     * call, which requests `fields=Chapters,Trickplay`.
     */
    val chapters: List<ChapterInfoDto> = emptyList(),
    val trickplay: Map<String, TrickplayInfoDto> = emptyMap(),
)

interface PlaybackReporting {
    suspend fun reportStart(r: ResolvedPlayback, positionTicks: Long, audioIndex: Int?, subtitleIndex: Int?): ApiResult<Unit>
    suspend fun reportProgress(r: ResolvedPlayback, positionTicks: Long, isPaused: Boolean, audioIndex: Int?, subtitleIndex: Int?): ApiResult<Unit>
    suspend fun reportStopped(r: ResolvedPlayback, positionTicks: Long, failed: Boolean): ApiResult<Unit>

    /** Keeps the server's "now playing" session from being reaped between progress reports. */
    suspend fun ping(playSessionId: String): ApiResult<Unit>

    /** Tears down a server-side transcode. No-op unless the session actually was transcoding. */
    suspend fun stopTranscode(r: ResolvedPlayback): ApiResult<Unit>
}

/**
 * Resolves a playable stream URL for an item via `/PlaybackInfo`, and reports
 * playback start/progress/stop to the server so resume points and "now
 * playing" state stay in sync.
 */
class PlaybackRepository(private val client: JellyfinClient) : PlaybackReporting {

    /**
     * Resolves a playable source. [audioStreamIndex]/[subtitleStreamIndex] are forwarded to the
     * server as `AudioStreamIndex`/`SubtitleStreamIndex` so a transcoded stream is built around
     * the tracks the user actually wants; for direct play they are informational and the engine
     * does the switching locally.
     *
     * [maxStreamingBitrate] caps what the server may hand back. [forceTranscode] is what makes that
     * cap bite: with direct play and stream copy on the table the server answers with the original
     * file and ignores the ceiling entirely, so an explicit quality choice (docs/osd-v3.md §5) has
     * to take both off the table. Audio stream copy stays allowed — the cap is about the video.
     */
    suspend fun resolve(
        itemId: String,
        startPositionTicks: Long = 0,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        maxStreamingBitrate: Long = DEFAULT_MAX_STREAMING_BITRATE,
        forceTranscode: Boolean = false,
    ): ApiResult<ResolvedPlayback> {
        val session = client.currentSession() ?: return ApiResult.Failure(ApiError.Unauthorized)

        val body = PlaybackInfoDto(
            deviceProfile = ShoumeiDeviceProfile.build(),
            startTimeTicks = startPositionTicks,
            maxStreamingBitrate = maxStreamingBitrate,
            mediaSourceId = mediaSourceId,
            enableDirectPlay = !forceTranscode,
            enableDirectStream = !forceTranscode,
            enableTranscoding = true,
            allowVideoStreamCopy = !forceTranscode,
            allowAudioStreamCopy = true,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
        )

        val infoResult = client.post<PlaybackInfoResponse>(
            "/Items/$itemId/PlaybackInfo",
            body,
            mapOf("userId" to session.userId),
        )
        val info = when (infoResult) {
            is ApiResult.Failure -> return infoResult
            is ApiResult.Success -> infoResult.data
        }

        val source = if (forceTranscode) {
            info.mediaSources.firstOrNull { !it.transcodingUrl.isNullOrBlank() }
        } else {
            info.mediaSources.firstOrNull { it.supportsDirectPlay || it.supportsDirectStream }
        }
            ?: info.mediaSources.firstOrNull()
            ?: return ApiResult.Failure(ApiError.Unknown("No playable media source returned"))
        val sourceId = source.id ?: return ApiResult.Failure(ApiError.Unknown("Media source missing id"))
        val playSessionId = info.playSessionId ?: ""

        // A server that answers a forced-transcode request with direct-play flags anyway would
        // otherwise get a `static=true` URL and quietly ignore the bitrate cap.
        val transcoding = forceTranscode || !(source.supportsDirectPlay || source.supportsDirectStream)
        if (transcoding && source.transcodingUrl.isNullOrBlank()) {
            return ApiResult.Failure(ApiError.Unknown("Server offered no transcode for this quality"))
        }
        val streamUrl = buildStreamUrl(itemId, source, sourceId, playSessionId, session, transcoding)
            ?: return ApiResult.Failure(ApiError.Network("No server configured"))
        val externalSubtitles = externalSubtitles(source.mediaStreams, session)
        val playMethod = if (transcoding) PLAY_METHOD_TRANSCODE else PLAY_METHOD_DIRECT

        return ApiResult.Success(
            ResolvedPlayback(
                itemId = itemId,
                mediaSourceId = sourceId,
                playSessionId = playSessionId,
                streamUrl = streamUrl,
                playMethod = playMethod,
                runTimeTicks = source.runTimeTicks,
                audioTracks = source.mediaStreams.filter { it.type == "Audio" }.map { toPlayerTrack(it, TrackType.AUDIO) },
                subtitleTracks = source.mediaStreams.filter { it.type == "Subtitle" }.map { toPlayerTrack(it, TrackType.SUBTITLE) },
                defaultAudioIndex = source.defaultAudioStreamIndex,
                defaultSubtitleIndex = source.defaultSubtitleStreamIndex,
                mediaStreams = source.mediaStreams,
                externalSubtitles = externalSubtitles,
                headers = mapOf("Authorization" to client.authHeader()),
                maxStreamingBitrate = maxStreamingBitrate,
            ),
        )
    }

    /**
     * Builds the Coil-loadable URL for one trickplay tile sheet of [itemId]. Returns null when no
     * server is configured or the manifest has no usable band for [targetWidth].
     *
     * [bands] is one media source's slice of `BaseItemDto.Trickplay` — `{ width -> info }`.
     */
    suspend fun trickplaySource(
        itemId: String,
        mediaSourceId: String?,
        bands: Map<String, TrickplayInfoDto>,
        targetWidth: Int,
    ): TrickplaySource? {
        val info = TrickplayMath.selectBand(bands, targetWidth) ?: return null
        val session = client.currentSession() ?: return null
        return TrickplaySource(
            info = info,
            serverUrl = session.serverUrl,
            itemId = itemId,
            apiKey = session.accessToken,
            mediaSourceId = mediaSourceId,
        )
    }

    private suspend fun buildStreamUrl(
        itemId: String,
        source: MediaSourceInfoDto,
        sourceId: String,
        playSessionId: String,
        session: Session,
        transcoding: Boolean,
    ): String? = if (!transcoding) {
        client.resolveUrl(
            "/Videos/$itemId/stream",
            mapOf(
                "static" to "true",
                "mediaSourceId" to sourceId,
                "playSessionId" to playSessionId.takeIf { it.isNotBlank() },
                "api_key" to session.accessToken,
                "deviceId" to session.deviceId,
            ),
        )
    } else {
        val transcodingUrl = source.transcodingUrl ?: return null
        val extraParams = if (transcodingUrl.contains("api_key")) emptyMap() else mapOf("api_key" to session.accessToken)
        client.resolveUrl(transcodingUrl, extraParams)
    }

    /**
     * Resolves `DeliveryUrl` for every subtitle the server marks as externally delivered. The URL is
     * relative to the server root and unauthenticated, so it is made absolute and given an
     * `api_key` — mpv fetches it on its own and never sees the request headers we set for the video.
     */
    private suspend fun externalSubtitles(
        streams: List<MediaStreamDto>,
        session: Session,
    ): List<ExternalSubtitle> = streams
        .filter { it.type == "Subtitle" && it.deliveryMethod.equals("External", ignoreCase = true) }
        .mapNotNull { stream ->
            val url = stream.deliveryUrl
                ?.let { client.resolveUrl(it, mapOf("api_key" to session.accessToken)) }
                ?: return@mapNotNull null
            ExternalSubtitle(
                streamIndex = stream.index,
                url = url,
                title = stream.displayTitle ?: stream.language.orEmpty(),
                language = stream.language,
            )
        }

    private fun toPlayerTrack(stream: MediaStreamDto, type: TrackType): PlayerTrack = PlayerTrack(
        id = stream.index,
        type = type,
        label = stream.displayTitle ?: "${stream.codec.orEmpty()} ${stream.language.orEmpty()}".trim(),
        language = stream.language,
        isDefault = stream.isDefault,
        selected = stream.isDefault,
    )

    override suspend fun reportStart(
        r: ResolvedPlayback,
        positionTicks: Long,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ): ApiResult<Unit> = client.postEmpty(
        "/Sessions/Playing",
        PlaybackProgressBody(
            itemId = r.itemId,
            mediaSourceId = r.mediaSourceId,
            playSessionId = r.playSessionId,
            positionTicks = positionTicks,
            playMethod = r.playMethod,
            isPaused = false,
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
        ),
    )

    override suspend fun reportProgress(
        r: ResolvedPlayback,
        positionTicks: Long,
        isPaused: Boolean,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ): ApiResult<Unit> = client.postEmpty(
        "/Sessions/Playing/Progress",
        PlaybackProgressBody(
            itemId = r.itemId,
            mediaSourceId = r.mediaSourceId,
            playSessionId = r.playSessionId,
            positionTicks = positionTicks,
            playMethod = r.playMethod,
            isPaused = isPaused,
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
        ),
    )

    override suspend fun reportStopped(
        r: ResolvedPlayback,
        positionTicks: Long,
        failed: Boolean,
    ): ApiResult<Unit> = client.postEmpty(
        "/Sessions/Playing/Stopped",
        PlaybackStopBody(
            itemId = r.itemId,
            mediaSourceId = r.mediaSourceId,
            playSessionId = r.playSessionId,
            positionTicks = positionTicks,
            failed = failed,
        ),
    )

    /**
     * `POST /Sessions/Playing/Ping?playSessionId` — the server drops a session that goes quiet, and
     * progress reports stop while paused, which is exactly when the session is most likely to be
     * reaped out from under a paused player.
     */
    override suspend fun ping(playSessionId: String): ApiResult<Unit> {
        if (playSessionId.isBlank()) return ApiResult.Success(Unit)
        return client.postEmpty("/Sessions/Playing/Ping", params = mapOf("playSessionId" to playSessionId))
    }

    /**
     * `DELETE /Videos/ActiveEncodings?deviceId&playSessionId` — without this the ffmpeg process the
     * server spawned for a transcode keeps running until it times out on its own.
     *
     * Direct play sessions have no encoder to stop, so they short-circuit; the guard lives here so
     * every caller gets it rather than each having to remember.
     */
    override suspend fun stopTranscode(r: ResolvedPlayback): ApiResult<Unit> {
        if (r.playMethod != PLAY_METHOD_TRANSCODE || r.playSessionId.isBlank()) {
            return ApiResult.Success(Unit)
        }
        val session = client.currentSession() ?: return ApiResult.Failure(ApiError.Unauthorized)
        return client.deleteEmpty(
            "/Videos/ActiveEncodings",
            mapOf("deviceId" to session.deviceId, "playSessionId" to r.playSessionId),
        )
    }
}
