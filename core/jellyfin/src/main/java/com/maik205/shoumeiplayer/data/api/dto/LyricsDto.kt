package com.maik205.shoumeiplayer.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LyricDto(
    @SerialName("Metadata") val metadata: LyricMetadataDto? = null,
    @SerialName("Lyrics") val lyrics: List<LyricLineDto> = emptyList(),
)

@Serializable
data class LyricLineDto(
    @SerialName("Text") val text: String = "",
    @SerialName("Start") val start: Long? = null,
    @SerialName("Cues") val cues: List<LyricLineCueDto>? = null,
)

@Serializable
data class LyricLineCueDto(
    @SerialName("Position") val position: Int = 0,
    @SerialName("EndPosition") val endPosition: Int = 0,
    @SerialName("Start") val start: Long = 0,
    @SerialName("End") val end: Long? = null,
)

@Serializable
data class LyricMetadataDto(
    @SerialName("Artist") val artist: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Author") val author: String? = null,
    @SerialName("Length") val length: Long? = null,
    @SerialName("By") val by: String? = null,
    @SerialName("Offset") val offset: Long? = null,
    @SerialName("Creator") val creator: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("IsSynced") val isSynced: Boolean? = null,
)
