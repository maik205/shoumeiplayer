package com.maik205.shoumeiplayer.player

import com.maik205.shoumeiplayer.domain.result.ApiResult

data class PlaybackResolutionRequest(
    val itemId: String,
    val startPositionTicks: Long,
    val mediaSourceId: String?,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val maxStreamingBitrate: Long,
    val forceTranscode: Boolean,
)

fun interface PlaybackResolver {
    suspend fun resolve(request: PlaybackResolutionRequest): ApiResult<ResolvedPlayback>
}
