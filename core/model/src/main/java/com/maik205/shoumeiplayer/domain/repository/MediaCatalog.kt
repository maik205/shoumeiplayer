package com.maik205.shoumeiplayer.domain.repository

import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.domain.model.LibraryDestination
import com.maik205.shoumeiplayer.domain.model.MediaItem
import com.maik205.shoumeiplayer.domain.model.MediaPage
import com.maik205.shoumeiplayer.domain.model.MediaPageRequest

/**
 * Browse-facing application contract.
 *
 * Consumers operate on application models and intentions; Jellyfin endpoint paths, field lists,
 * item-type strings, and transport DTOs stay behind the data implementation.
 */
interface MediaCatalog {
    suspend fun libraries(): ApiResult<List<LibraryDestination>>
    suspend fun resumeItems(limit: Int): ApiResult<List<MediaItem>>
    suspend fun nextUp(limit: Int): ApiResult<List<MediaItem>>
    suspend fun favoriteItems(limit: Int): ApiResult<List<MediaItem>>
    suspend fun latest(libraryId: String, limit: Int): ApiResult<List<MediaItem>>
    suspend fun page(request: MediaPageRequest): ApiResult<MediaPage>
    suspend fun search(term: String, limit: Int): ApiResult<List<MediaItem>>
    suspend fun setFavorite(itemId: String, favorite: Boolean): ApiResult<Boolean>
}
