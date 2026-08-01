package com.maik205.shoumeiplayer.domain.repository

import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.domain.model.DetailItem
import com.maik205.shoumeiplayer.domain.model.MediaItem
import com.maik205.shoumeiplayer.domain.model.PlayableTarget

interface MediaDetailsRepository {
    suspend fun item(itemId: String): ApiResult<DetailItem>
    suspend fun seasons(seriesId: String): ApiResult<List<MediaItem>>
    suspend fun episodes(seriesId: String, seasonId: String?): ApiResult<List<MediaItem>>
    suspend fun similar(itemId: String, limit: Int): ApiResult<List<MediaItem>>
    suspend fun albumTracks(albumId: String): ApiResult<List<MediaItem>>
    suspend fun playlistItems(playlistId: String): ApiResult<List<MediaItem>>
    suspend fun audioBookItems(audioBookId: String): ApiResult<List<MediaItem>>
    suspend fun collectionItems(collectionId: String): ApiResult<List<MediaItem>>
    suspend fun artistAlbums(artistId: String): ApiResult<List<MediaItem>>
    suspend fun artistSongs(artistId: String): ApiResult<List<MediaItem>>
    suspend fun personCredits(personId: String): ApiResult<List<MediaItem>>
    suspend fun resolvePlayableTarget(item: DetailItem): ApiResult<PlayableTarget>
    suspend fun setFavorite(item: DetailItem, favorite: Boolean): ApiResult<DetailItem>
    suspend fun setPlayed(item: DetailItem, played: Boolean): ApiResult<DetailItem>
}
