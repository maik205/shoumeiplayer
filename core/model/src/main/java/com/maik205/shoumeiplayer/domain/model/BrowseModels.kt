package com.maik205.shoumeiplayer.domain.model

import kotlinx.serialization.Serializable

/**
 * Presentation-neutral media shape used by browse, persistence, and platform-specific UIs.
 *
 * Keep these models independent of Compose and Jellyfin DTOs. A different UI or media provider
 * can therefore consume the same application state without depending on television packages or
 * Jellyfin's wire format.
 */
@Serializable
enum class ArtworkShape {
    Poster,
    Landscape,
    Square,
    Portrait,
}

@Serializable
data class MediaItem(
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

@Serializable
data class MediaShelf(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
)

@Serializable
data class LibraryDestination(
    val id: String,
    val title: String,
    val collectionType: String?,
)

data class MediaPage(
    val items: List<MediaItem>,
    val totalCount: Int,
)

enum class MediaSort {
    Name,
    Recent,
    PremiereDate,
    CommunityRating,
}

enum class MediaView {
    All,
    New,
    Favorites,
}

data class MediaPageRequest(
    val libraryId: String,
    val collectionType: String?,
    val sort: MediaSort,
    val view: MediaView,
    val startIndex: Int,
    val limit: Int,
)
