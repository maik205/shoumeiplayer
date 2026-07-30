package com.maik205.shoumeiplayer.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SearchHintResult(
    @SerialName("SearchHints") val searchHints: List<SearchHintDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)

/** SearchHint carries no blurhash and no UserData: map to GridTileUi with both null. */
@Serializable
data class SearchHintDto(
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("ThumbImageTag") val thumbImageTag: String? = null,
    @SerialName("ThumbImageItemId") val thumbImageItemId: String? = null,
    @SerialName("Series") val series: String? = null,
    @SerialName("IsFolder") val isFolder: Boolean = false,
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
)
