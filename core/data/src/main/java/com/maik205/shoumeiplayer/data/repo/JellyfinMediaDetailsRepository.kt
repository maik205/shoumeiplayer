package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.domain.result.mapping.toDetailItem
import com.maik205.shoumeiplayer.domain.result.mapping.toMediaItem
import com.maik205.shoumeiplayer.domain.result.map
import com.maik205.shoumeiplayer.domain.model.DetailItem
import com.maik205.shoumeiplayer.domain.model.MediaItem
import com.maik205.shoumeiplayer.domain.model.PlayableTarget
import com.maik205.shoumeiplayer.domain.repository.MediaDetailsRepository

class JellyfinMediaDetailsRepository(
    private val repository: LibraryRepository,
    private val images: ImageUrlBuilder,
) : MediaDetailsRepository {
    override suspend fun item(itemId: String): ApiResult<DetailItem> =
        repository.item(itemId).map { it.toDetailItem(images) }

    override suspend fun seasons(seriesId: String): ApiResult<List<MediaItem>> =
        repository.seasons(seriesId).mediaItems()

    override suspend fun episodes(seriesId: String, seasonId: String?): ApiResult<List<MediaItem>> =
        repository.episodes(seriesId, seasonId).mediaItems()

    override suspend fun similar(itemId: String, limit: Int): ApiResult<List<MediaItem>> =
        repository.similar(itemId, limit).mediaItems()

    override suspend fun albumTracks(albumId: String): ApiResult<List<MediaItem>> =
        repository.albumTracks(albumId).mediaItems()

    override suspend fun playlistItems(playlistId: String): ApiResult<List<MediaItem>> =
        repository.playlistItems(playlistId).map { result -> result.items.map { it.toMediaItem(images) } }

    override suspend fun audioBookItems(audioBookId: String): ApiResult<List<MediaItem>> =
        repository.items(
            parentId = audioBookId,
            includeItemTypes = listOf("AudioBook", "Audio"),
            recursive = true,
            sortBy = "IndexNumber",
            sortOrder = "Ascending",
            limit = 500,
        ).map { result -> result.items.map { it.toMediaItem(images) } }

    override suspend fun collectionItems(collectionId: String): ApiResult<List<MediaItem>> =
        repository.collectionItems(collectionId).map { result -> result.items.map { it.toMediaItem(images) } }

    override suspend fun artistAlbums(artistId: String): ApiResult<List<MediaItem>> =
        repository.artistAlbums(artistId).mediaItems()

    override suspend fun artistSongs(artistId: String): ApiResult<List<MediaItem>> =
        repository.artistSongs(artistId).mediaItems()

    override suspend fun personCredits(personId: String): ApiResult<List<MediaItem>> =
        repository.personCredits(personId, limit = 100)
            .map { result -> result.items.map { it.toMediaItem(images) } }

    override suspend fun resolvePlayableTarget(item: DetailItem): ApiResult<PlayableTarget> {
        if (!item.type.equals("Series", ignoreCase = true)) {
            return ApiResult.Success(PlayableTarget(item.id, item.resumeTicks))
        }
        return repository.resolveSeriesPlayableTarget(item.id).map {
            PlayableTarget(it.itemId, it.startPositionTicks)
        }
    }

    override suspend fun setFavorite(item: DetailItem, favorite: Boolean): ApiResult<DetailItem> =
        repository.setFavorite(item.id, favorite).map {
            item.copy(
                media = item.media.copy(
                    favorite = it.isFavorite,
                    watched = it.played,
                    resumeTicks = it.playbackPositionTicks,
                ),
                favorite = it.isFavorite,
                played = it.played,
                resumeTicks = it.playbackPositionTicks,
            )
        }

    override suspend fun setPlayed(item: DetailItem, played: Boolean): ApiResult<DetailItem> =
        repository.setPlayed(item.id, played).map {
            item.copy(
                media = item.media.copy(
                    favorite = it.isFavorite,
                    watched = it.played,
                    resumeTicks = it.playbackPositionTicks,
                ),
                favorite = it.isFavorite,
                played = it.played,
                resumeTicks = it.playbackPositionTicks,
            )
        }

    private fun ApiResult<List<com.maik205.shoumeiplayer.data.api.dto.BaseItemDto>>.mediaItems():
        ApiResult<List<MediaItem>> = map { items -> items.map { it.toMediaItem(images) } }
}
