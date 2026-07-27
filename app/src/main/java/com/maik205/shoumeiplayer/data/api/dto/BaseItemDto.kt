package com.maik205.shoumeiplayer.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BaseItemDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("CommunityRating") val communityRating: Float? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("IsFolder") val isFolder: Boolean = false,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("ParentThumbItemId") val parentThumbItemId: String? = null,
    @SerialName("ParentThumbImageTag") val parentThumbImageTag: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryImageTag: String? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("UserData") val userData: UserItemDataDto? = null,
    // --- image tags & fallbacks (§5.1/§5.2 art chains) ---
    @SerialName("ImageBlurHashes") val imageBlurHashes: Map<String, Map<String, String>> = emptyMap(),
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String> = emptyList(),
    @SerialName("ParentLogoItemId") val parentLogoItemId: String? = null,
    @SerialName("ParentLogoImageTag") val parentLogoImageTag: String? = null,
    @SerialName("ParentPrimaryImageItemId") val parentPrimaryImageItemId: String? = null,
    @SerialName("ParentPrimaryImageTag") val parentPrimaryImageTag: String? = null,
    @SerialName("SeriesThumbImageTag") val seriesThumbImageTag: String? = null,
    @SerialName("ScreenshotImageTags") val screenshotImageTags: List<String> = emptyList(),
    // --- metadata (§5.2 copy column + spec rail) ---
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("CriticRating") val criticRating: Float? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("Studios") val studios: List<NameGuidPairDto> = emptyList(),
    @SerialName("People") val people: List<BaseItemPersonDto> = emptyList(),
    @SerialName("Chapters") val chapters: List<ChapterInfoDto> = emptyList(),
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, TrickplayInfoDto>> = emptyMap(),
)

@Serializable
data class UserItemDataDto(
    @SerialName("Played") val played: Boolean = false,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayCount") val playCount: Int = 0,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
)
