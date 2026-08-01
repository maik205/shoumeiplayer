package com.maik205.shoumeiplayer.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `BaseItemDto.People[]` — cast/crew with a portrait tag. Fetched via `fields=People`. */
@Serializable
data class BaseItemPersonDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Role") val role: String? = null,
    /** `PersonKind`: Actor, Director, Writer, Producer, GuestStar, Composer, … */
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("ImageBlurHashes") val imageBlurHashes: Map<String, Map<String, String>> = emptyMap(),
)

/** `BaseItemDto.Chapters[]`. `ImagePath` is deliberately dropped — it is a server-local FS path. */
@Serializable
data class ChapterInfoDto(
    @SerialName("StartPositionTicks") val startPositionTicks: Long = 0,
    @SerialName("Name") val name: String? = null,
    @SerialName("ImageTag") val imageTag: String? = null,
    @SerialName("ImageDateModified") val imageDateModified: String? = null,
)

/** One trickplay width band. `Interval` is milliseconds between thumbnails. */
@Serializable
data class TrickplayInfoDto(
    @SerialName("Width") val width: Int = 0,
    @SerialName("Height") val height: Int = 0,
    @SerialName("TileWidth") val tileWidth: Int = 0,
    @SerialName("TileHeight") val tileHeight: Int = 0,
    @SerialName("ThumbnailCount") val thumbnailCount: Int = 0,
    @SerialName("Interval") val interval: Int = 0,
    @SerialName("Bandwidth") val bandwidth: Int = 0,
)

@Serializable
data class NameGuidPairDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
)

/** `ImageBlurHashes` is `{ imageType -> { tag -> hash } }`; look a hash up by the tag we already hold. */
fun Map<String, Map<String, String>>.blurHash(type: String, tag: String?): String? =
    if (tag == null) this[type]?.values?.firstOrNull() else this[type]?.get(tag)
