package com.maik205.shoumeiplayer.domain.model

data class DetailPerson(
    val id: String,
    val name: String,
    val role: String?,
    val type: String?,
    val imageUrl: String?,
)

data class DetailMediaStream(
    val index: Int,
    val type: String?,
    val codec: String?,
    val language: String?,
    val displayTitle: String?,
    val channels: Int?,
    val width: Int?,
    val height: Int?,
    val isDefault: Boolean,
)

data class DetailItem(
    val media: MediaItem,
    val id: String,
    val name: String,
    val type: String?,
    val productionYear: Int?,
    val premiereDate: String?,
    val communityRating: Float?,
    val officialRating: String?,
    val indexNumber: Int?,
    val parentIndexNumber: Int?,
    val seriesId: String?,
    val seriesName: String?,
    val seasonId: String?,
    val seasonName: String?,
    val episodeCount: Int?,
    val genres: List<String>,
    val runTimeTicks: Long?,
    val status: String?,
    val channelId: String?,
    val studios: List<String>,
    val people: List<DetailPerson>,
    val mediaStreams: List<DetailMediaStream>,
    val favorite: Boolean,
    val played: Boolean,
    val resumeTicks: Long,
)

data class PlayableTarget(
    val itemId: String,
    val startPositionTicks: Long = 0,
)
