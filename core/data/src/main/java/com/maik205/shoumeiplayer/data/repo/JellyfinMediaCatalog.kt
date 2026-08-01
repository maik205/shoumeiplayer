package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.domain.result.map
import com.maik205.shoumeiplayer.domain.result.mapping.toMediaItem
import com.maik205.shoumeiplayer.domain.model.LibraryDestination
import com.maik205.shoumeiplayer.domain.model.MediaItem
import com.maik205.shoumeiplayer.domain.model.MediaPage
import com.maik205.shoumeiplayer.domain.model.MediaPageRequest
import com.maik205.shoumeiplayer.domain.model.MediaSort
import com.maik205.shoumeiplayer.domain.model.MediaView
import com.maik205.shoumeiplayer.domain.repository.MediaCatalog
import java.time.Instant
import java.time.temporal.ChronoUnit

class JellyfinMediaCatalog(
    private val repository: LibraryRepository,
    private val images: ImageUrlBuilder,
) : MediaCatalog {
    override suspend fun libraries(): ApiResult<List<LibraryDestination>> =
        repository.userViews().map { views ->
            views
                .filterNot { it.collectionType.equals("photos", ignoreCase = true) }
                .map {
                    LibraryDestination(
                        id = it.id,
                        title = it.name.orEmpty(),
                        collectionType = it.collectionType,
                    )
                }
        }

    override suspend fun resumeItems(limit: Int): ApiResult<List<MediaItem>> =
        repository.resumeItems(limit).mapItems()

    override suspend fun nextUp(limit: Int): ApiResult<List<MediaItem>> =
        repository.nextUp(limit).mapItems()

    override suspend fun favoriteItems(limit: Int): ApiResult<List<MediaItem>> =
        repository.items(
            filters = listOf("IsFavorite"),
            sortBy = "DateCreated",
            sortOrder = "Descending",
            limit = limit,
        ).map { result -> result.items.map { it.toMediaItem(images) } }

    override suspend fun latest(libraryId: String, limit: Int): ApiResult<List<MediaItem>> =
        repository.latest(libraryId, limit).mapItems()

    override suspend fun page(request: MediaPageRequest): ApiResult<MediaPage> =
        repository.items(
            parentId = request.libraryId.ifBlank { null },
            includeItemTypes = collectionItemTypes(request.collectionType),
            recursive = true,
            sortBy = request.sort.apiValue,
            sortOrder = if (request.sort == MediaSort.Name) "Ascending" else "Descending",
            filters = if (request.view == MediaView.Favorites) listOf("IsFavorite") else emptyList(),
            minDateLastSavedForUser = if (request.view == MediaView.New) newItemsCutoff else null,
            startIndex = request.startIndex,
            limit = request.limit,
        ).map { result ->
            MediaPage(
                items = result.items.map { it.toMediaItem(images) },
                totalCount = result.totalRecordCount,
            )
        }

    override suspend fun search(term: String, limit: Int): ApiResult<List<MediaItem>> =
        repository.searchAllMedia(term, limit).mapItems()

    override suspend fun setFavorite(itemId: String, favorite: Boolean): ApiResult<Boolean> =
        repository.setFavorite(itemId, favorite).map { it.isFavorite }

    private fun ApiResult<List<com.maik205.shoumeiplayer.data.api.dto.BaseItemDto>>.mapItems() =
        map { items -> items.map { it.toMediaItem(images) } }

    private companion object {
        val newItemsCutoff: String = Instant.now().minus(365, ChronoUnit.DAYS).toString()
    }
}

private val MediaSort.apiValue: String
    get() = when (this) {
        MediaSort.Name -> "SortName"
        MediaSort.Recent -> "DateLastContentAdded"
        MediaSort.PremiereDate -> "PremiereDate"
        MediaSort.CommunityRating -> "CommunityRating"
    }

private fun collectionItemTypes(collectionType: String?): List<String> = when (
    collectionType?.lowercase()
) {
    "movies" -> listOf("Movie")
    "tvshows" -> listOf("Series")
    "music" -> listOf("MusicAlbum", "MusicArtist", "Audio", "Playlist")
    "musicvideos" -> listOf("MusicVideo")
    "books" -> listOf("AudioBook", "Book")
    "boxsets" -> listOf("BoxSet")
    "playlists" -> listOf("Playlist")
    "livetv" -> listOf("LiveTvChannel", "Recording")
    "photos" -> listOf("PhotoAlbum")
    else -> listOf(
        "Movie",
        "Series",
        "Episode",
        "Video",
        "MusicAlbum",
        "MusicArtist",
        "Audio",
        "AudioBook",
        "Book",
        "BoxSet",
        "Playlist",
        "Recording",
    )
}
