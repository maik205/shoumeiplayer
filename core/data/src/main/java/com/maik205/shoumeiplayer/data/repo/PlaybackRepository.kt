package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.domain.result.ApiError
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.api.ShoumeiDeviceProfile
import com.maik205.shoumeiplayer.data.api.dto.LiveStreamResponse
import com.maik205.shoumeiplayer.data.api.dto.MediaSourceInfoDto
import com.maik205.shoumeiplayer.data.api.dto.MediaStreamDto
import com.maik205.shoumeiplayer.data.api.dto.OpenLiveStreamDto
import com.maik205.shoumeiplayer.data.api.dto.PlaybackInfoDto
import com.maik205.shoumeiplayer.data.api.dto.PlaybackInfoResponse
import com.maik205.shoumeiplayer.data.api.dto.PlaybackProgressBody
import com.maik205.shoumeiplayer.data.api.dto.PlaybackStopBody
import com.maik205.shoumeiplayer.data.api.dto.TrickplayInfoDto
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import com.maik205.shoumeiplayer.player.ExternalSubtitle
import com.maik205.shoumeiplayer.player.DEFAULT_MAX_STREAMING_BITRATE
import com.maik205.shoumeiplayer.player.PLAY_METHOD_DIRECT
import com.maik205.shoumeiplayer.player.PLAY_METHOD_DIRECT_STREAM
import com.maik205.shoumeiplayer.player.PLAY_METHOD_TRANSCODE
import com.maik205.shoumeiplayer.player.PlaybackMediaStream
import com.maik205.shoumeiplayer.player.PlaybackReporting
import com.maik205.shoumeiplayer.player.PlaybackResolutionRequest
import com.maik205.shoumeiplayer.player.PlaybackResolver
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.ResolvedPlayback
import com.maik205.shoumeiplayer.player.TrackType
import com.maik205.shoumeiplayer.player.TrickplayInfo
import com.maik205.shoumeiplayer.player.TrickplayMath
import com.maik205.shoumeiplayer.player.TrickplaySource

/**
 * Resolves a playable stream URL for an item via `/PlaybackInfo`, and reports
 * playback start/progress/stop to the server so resume points and "now
 * playing" state stay in sync.
 */
class PlaybackRepository(
    private val client: JellyfinClient,
    private val deviceCapabilities: () -> DevicePlaybackCapabilities = { DevicePlaybackCapabilities() },
) : PlaybackReporting, PlaybackResolver {

    override suspend fun resolve(request: PlaybackResolutionRequest): ApiResult<ResolvedPlayback> =
        resolve(
            itemId = request.itemId,
            startPositionTicks = request.startPositionTicks,
            mediaSourceId = request.mediaSourceId,
            audioStreamIndex = request.audioStreamIndex,
            subtitleStreamIndex = request.subtitleStreamIndex,
            maxStreamingBitrate = request.maxStreamingBitrate,
            forceTranscode = request.forceTranscode,
        )

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
            deviceProfile = ShoumeiDeviceProfile.build(deviceCapabilities()),
            startTimeTicks = startPositionTicks,
            maxStreamingBitrate = maxStreamingBitrate,
            mediaSourceId = mediaSourceId,
            autoOpenLiveStream = false,
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

        var source = if (forceTranscode) {
            info.mediaSources.firstOrNull { !it.transcodingUrl.isNullOrBlank() }
        } else {
            info.mediaSources.firstOrNull { it.supportsDirectPlay }
                ?: info.mediaSources.firstOrNull { it.supportsDirectStream }
        }
            ?: info.mediaSources.firstOrNull()
            ?: return ApiResult.Failure(ApiError.Unknown("No playable media source returned"))
        val playSessionId = info.playSessionId ?: ""

        if (source.requiresOpening) {
            val opened = openLiveStream(
                OpenLiveStreamDto(
                    openToken = source.openToken,
                    userId = session.userId,
                    playSessionId = playSessionId.takeIf(String::isNotBlank),
                    maxStreamingBitrate = maxStreamingBitrate.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    startTimeTicks = startPositionTicks,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    itemId = itemId,
                    enableDirectPlay = !forceTranscode,
                    enableDirectStream = !forceTranscode,
                    deviceProfile = ShoumeiDeviceProfile.build(deviceCapabilities()),
                ),
            )
            source = when (opened) {
                is ApiResult.Failure -> return opened
                is ApiResult.Success -> opened.data
            }
        }

        val sourceId = source.id ?: return ApiResult.Failure(ApiError.Unknown("Media source missing id"))
        // A server that answers a forced-transcode request with direct-play flags anyway would
        // otherwise get a `static=true` URL and quietly ignore the bitrate cap.
        val directPlay = !forceTranscode && source.supportsDirectPlay
        val directStream = !directPlay && !forceTranscode && source.supportsDirectStream
        val transcoding = !directPlay && !directStream
        if (directStream && source.transcodingUrl.isNullOrBlank()) {
            return ApiResult.Failure(ApiError.Unknown("Server offered no direct-stream URL"))
        }
        if (transcoding && source.transcodingUrl.isNullOrBlank()) {
            return ApiResult.Failure(ApiError.Unknown("Server offered no transcode for this quality"))
        }
        val streamUrl = buildStreamUrl(itemId, source, sourceId, playSessionId, session, directPlay)
            ?: return ApiResult.Failure(ApiError.Network("No server configured"))
        val externalSubtitles = externalSubtitles(source.mediaStreams, session)
        val playMethod = when {
            directPlay -> PLAY_METHOD_DIRECT
            directStream -> PLAY_METHOD_DIRECT_STREAM
            else -> PLAY_METHOD_TRANSCODE
        }
        val requiredHeaders = source.requiredHttpHeaders
            .orEmpty()
            .mapNotNull { (name, value) -> value?.let { name to it } }
            .toMap()

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
                mediaStreams = source.mediaStreams.map(::toPlaybackMediaStream),
                externalSubtitles = externalSubtitles,
                headers = requiredHeaders + ("Authorization" to client.authHeader()),
                liveStreamId = source.liveStreamId,
                requiresLiveStreamClose = source.requiresClosing && !source.liveStreamId.isNullOrBlank(),
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
        val info = TrickplayMath.selectBand(
            bands = bands.mapValues { (_, band) -> band.toPlayerInfo() },
            targetWidth = targetWidth,
        ) ?: return null
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
        directPlay: Boolean,
    ): String? = if (directPlay) {
        val isAudioOnly = source.mediaStreams.any { it.type.equals("Audio", ignoreCase = true) } &&
            source.mediaStreams.none { it.type.equals("Video", ignoreCase = true) }
        val streamPath = if (isAudioOnly) {
            "/Audio/$itemId/stream"
        } else {
            "/Videos/$itemId/stream"
        }
        client.resolveUrl(
            streamPath,
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

    suspend fun openLiveStream(request: OpenLiveStreamDto): ApiResult<MediaSourceInfoDto> =
        when (val result = client.post<LiveStreamResponse>("/LiveStreams/Open", request)) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> result.data.mediaSource
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(ApiError.Unknown("Live stream response missing media source"))
        }

    suspend fun closeLiveStream(liveStreamId: String): ApiResult<Unit> {
        if (liveStreamId.isBlank()) return ApiResult.Success(Unit)
        return client.postEmpty(
            "/LiveStreams/Close",
            params = mapOf("liveStreamId" to liveStreamId),
        )
    }

    suspend fun closeLiveStream(r: ResolvedPlayback): ApiResult<Unit> =
        if (r.requiresLiveStreamClose) {
            closeLiveStream(r.liveStreamId.orEmpty())
        } else {
            ApiResult.Success(Unit)
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

    private fun toPlaybackMediaStream(stream: MediaStreamDto) = PlaybackMediaStream(
        index = stream.index,
        type = stream.type,
        codec = stream.codec,
        language = stream.language,
        isDefault = stream.isDefault,
        isForced = stream.isForced,
        channels = stream.channels,
        width = stream.width,
        height = stream.height,
    )

    private fun TrickplayInfoDto.toPlayerInfo() = TrickplayInfo(
        width = width,
        height = height,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
    )

    override suspend fun reportStart(
        resolved: ResolvedPlayback,
        positionTicks: Long,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ): ApiResult<Unit> = client.postEmpty(
        "/Sessions/Playing",
        PlaybackProgressBody(
            itemId = resolved.itemId,
            mediaSourceId = resolved.mediaSourceId,
            playSessionId = resolved.playSessionId,
            liveStreamId = resolved.liveStreamId,
            positionTicks = positionTicks,
            playMethod = resolved.playMethod,
            isPaused = false,
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
        ),
    )

    override suspend fun reportProgress(
        resolved: ResolvedPlayback,
        positionTicks: Long,
        isPaused: Boolean,
        audioIndex: Int?,
        subtitleIndex: Int?,
    ): ApiResult<Unit> = client.postEmpty(
        "/Sessions/Playing/Progress",
        PlaybackProgressBody(
            itemId = resolved.itemId,
            mediaSourceId = resolved.mediaSourceId,
            playSessionId = resolved.playSessionId,
            liveStreamId = resolved.liveStreamId,
            positionTicks = positionTicks,
            playMethod = resolved.playMethod,
            isPaused = isPaused,
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
        ),
    )

    override suspend fun reportStopped(
        resolved: ResolvedPlayback,
        positionTicks: Long,
        failed: Boolean,
    ): ApiResult<Unit> = client.postEmpty(
        "/Sessions/Playing/Stopped",
        PlaybackStopBody(
            itemId = resolved.itemId,
            mediaSourceId = resolved.mediaSourceId,
            playSessionId = resolved.playSessionId,
            liveStreamId = resolved.liveStreamId,
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
    override suspend fun stopTranscode(resolved: ResolvedPlayback): ApiResult<Unit> {
        if (resolved.playMethod != PLAY_METHOD_DIRECT && resolved.playSessionId.isNotBlank()) {
            val session = client.currentSession() ?: return ApiResult.Failure(ApiError.Unauthorized)
            val stopped = client.deleteEmpty(
                "/Videos/ActiveEncodings",
                mapOf("deviceId" to session.deviceId, "playSessionId" to resolved.playSessionId),
            )
            if (stopped is ApiResult.Failure) return stopped
        }
        return closeLiveStream(resolved)
    }
}
