package com.maik205.shoumeiplayer.ui.television.model

import androidx.compose.runtime.Immutable
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.util.Ticks

@Immutable
enum class ArtworkShape {
    Poster,
    Landscape,
    Square,
    Portrait,
}

@Immutable
data class MediaItemUi(
    val id: String,
    val title: String,
    val type: String,
    val subtitle: String? = null,
    val metadata: List<String> = emptyList(),
    val overview: String? = null,
    val imageUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val progress: Float? = null,
    val watched: Boolean = false,
    val favorite: Boolean = false,
    val shape: ArtworkShape = ArtworkShape.Poster,
    val collectionType: String? = null,
    val parentId: String? = null,
    val resumeTicks: Long = 0,
    val seriesName: String? = null,
    val seasonName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val premiereDate: String? = null,
    val runtimeLabel: String? = null,
    val officialRating: String? = null,
    val videoHeight: Int? = null,
)

@Immutable
data class MediaShelfUi(
    val id: String,
    val title: String,
    val items: List<MediaItemUi>,
)

@Immutable
data class LibraryDestinationUi(
    val id: String,
    val title: String,
    val collectionType: String?,
)

@Immutable
data class HeroUi(
    val item: MediaItemUi,
    val eyebrow: String? = null,
    val actionLabel: String = if (item.resumeTicks > 0) "Resume" else "Play",
)

fun BaseItemDto.toTelevisionUi(images: ImageUrlBuilder): MediaItemUi {
    val normalizedType = type.orEmpty()
    val shape = when (normalizedType) {
        "Episode", "Video", "Trailer", "Recording", "LiveTvChannel", "LiveTvProgram",
        "Program", "TvChannel", "TvProgram" -> ArtworkShape.Landscape

        "Audio", "MusicAlbum", "MusicArtist", "Playlist", "AudioBook", "MusicGenre" ->
            ArtworkShape.Square

        "Person" -> ArtworkShape.Portrait
        else -> ArtworkShape.Poster
    }

    val primary = images.primaryWithParentFallback(
        item = this,
        maxWidth = when (shape) {
            ArtworkShape.Landscape -> 720
            ArtworkShape.Square -> 600
            ArtworkShape.Portrait -> 480
            ArtworkShape.Poster -> 480
        },
    )
    val landscape = if (normalizedType == "Episode") {
        images.episodePreview(this, 720)
    } else {
        images.thumbWithSeriesFallback(this, 720)
            ?: images.backdropWithParentFallback(this, 720)
            ?: primary
    }
    val image = if (shape == ArtworkShape.Landscape) landscape else primary
    val progress = userData?.playedPercentage
        ?.toFloat()
        ?.div(100f)
        ?.coerceIn(0f, 1f)
        ?.takeIf { it > 0f && it < 1f }

    return MediaItemUi(
        id = id,
        title = name.orEmpty(),
        type = normalizedType,
        subtitle = televisionSubtitle(),
        metadata = televisionMetadata(),
        overview = overview?.takeIf(String::isNotBlank),
        imageUrl = image,
        backdropUrl = images.backdropWithParentFallback(this, 1600) ?: landscape,
        logoUrl = images.logoWithParentFallback(this, 600),
        progress = progress,
        watched = userData?.played == true,
        favorite = userData?.isFavorite == true,
        shape = shape,
        collectionType = collectionType,
        parentId = parentId,
        resumeTicks = userData?.playbackPositionTicks ?: 0L,
        seriesName = seriesName,
        seasonName = seasonName,
        seasonNumber = parentIndexNumber,
        episodeNumber = indexNumber,
        premiereDate = premiereDate,
        runtimeLabel = formattedRuntime(),
        officialRating = officialRating,
        videoHeight = mediaStreams.firstOrNull {
            it.type.equals("Video", ignoreCase = true)
        }?.height,
    )
}

private fun BaseItemDto.televisionSubtitle(): String? = when (type) {
    "Episode" -> buildList {
        seriesName?.takeIf(String::isNotBlank)?.let(::add)
        val season = parentIndexNumber
        val episode = indexNumber
        if (season != null || episode != null) add("S${season ?: 0} E${episode ?: 0}")
    }.joinToString(" · ").ifBlank { null }

    "Audio" -> listOfNotNull(
        runCatching { albumArtist }.getOrNull()?.takeIf(String::isNotBlank),
        runCatching { album }.getOrNull()?.takeIf(String::isNotBlank),
    ).joinToString(" · ").ifBlank { null }

    "MusicAlbum" -> runCatching { albumArtist }.getOrNull()?.takeIf(String::isNotBlank)
    "LiveTvChannel", "TvChannel" -> runCatching { channelNumber }.getOrNull()
    else -> productionYear?.toString()
}

private fun BaseItemDto.televisionMetadata(): List<String> = buildList {
    when (type) {
        "Episode" -> {
            productionYear?.let { add(it.toString()) }
            formattedRuntime()?.let(::add)
            officialRating?.takeIf(String::isNotBlank)?.let(::add)
        }

        "Audio" -> {
            runCatching { album }.getOrNull()?.takeIf(String::isNotBlank)?.let(::add)
            formattedRuntime()?.let(::add)
        }

        "MusicAlbum" -> {
            productionYear?.let { add(it.toString()) }
            runCatching { songCount }.getOrNull()?.takeIf { it > 0 }?.let { add("$it tracks") }
            formattedRuntime()?.let(::add)
        }

        "MusicArtist" -> {
            runCatching { albumCount }.getOrNull()?.takeIf { it > 0 }?.let { add("$it albums") }
        }

        "Playlist" -> {
            childCount?.takeIf { it > 0 }?.let { add("$it tracks") }
            formattedRuntime()?.let(::add)
        }

        else -> {
            productionYear?.let { add(it.toString()) }
            formattedRuntime()?.let(::add)
            officialRating?.takeIf(String::isNotBlank)?.let(::add)
            genres.firstOrNull()?.takeIf(String::isNotBlank)?.let(::add)
        }
    }
}

private fun BaseItemDto.formattedRuntime(): String? {
    val ticks = runTimeTicks?.takeIf { it > 0 } ?: return null
    val totalMinutes = Ticks.toMs(ticks) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}
