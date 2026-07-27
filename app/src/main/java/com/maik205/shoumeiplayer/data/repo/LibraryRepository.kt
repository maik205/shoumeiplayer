package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.data.ApiError
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.QueryResult
import com.maik205.shoumeiplayer.data.api.dto.SearchHintDto
import com.maik205.shoumeiplayer.data.api.dto.SearchHintResult
import com.maik205.shoumeiplayer.data.flatMap
import com.maik205.shoumeiplayer.data.map

private const val CARD_FIELDS = "Overview,PrimaryImageAspectRatio"
private const val DETAIL_FIELDS =
    "Overview,Genres,Taglines,Studios,People,Chapters,MediaStreams,MediaSources,PrimaryImageAspectRatio,Trickplay"
private const val CARD_IMAGE_TYPES = "Primary,Backdrop,Thumb,Logo"
private const val DETAIL_IMAGE_TYPES = "Primary,Backdrop,Thumb,Logo,Banner,Art"

/**
 * Item-browsing repository. Every call requires an authenticated user id from
 * [JellyfinClient.currentSession]; absent that, calls fail fast with
 * [ApiError.Unauthorized] rather than hitting the network with a null userId.
 */
class LibraryRepository(private val client: JellyfinClient) {

    suspend fun userViews(): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/UserViews",
            mapOf(
                "userId" to userId,
                "enableImageTypes" to "Primary,Thumb,Backdrop",
                "imageTypeLimit" to 1,
            ),
        ).map { it.items }
    }

    suspend fun resumeItems(limit: Int = 20): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/UserItems/Resume",
            mapOf(
                "userId" to userId,
                "limit" to limit,
                "mediaTypes" to "Video",
                "enableUserData" to true,
                "fields" to CARD_FIELDS,
                "enableImageTypes" to CARD_IMAGE_TYPES,
                "imageTypeLimit" to 1,
            ),
        ).map { it.items }
    }

    suspend fun nextUp(limit: Int = 20): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/Shows/NextUp",
            mapOf(
                "userId" to userId,
                "limit" to limit,
                "enableUserData" to true,
                "fields" to CARD_FIELDS,
                "enableImageTypes" to CARD_IMAGE_TYPES,
                "imageTypeLimit" to 1,
            ),
        ).map { it.items }
    }

    suspend fun latest(parentId: String?, limit: Int = 20): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<List<BaseItemDto>>(
            "/Items/Latest",
            mapOf(
                "userId" to userId,
                "parentId" to parentId,
                "limit" to limit,
                "enableUserData" to true,
                "fields" to CARD_FIELDS,
                "enableImageTypes" to CARD_IMAGE_TYPES,
                "imageTypeLimit" to 1,
            ),
        )
    }

    suspend fun items(
        parentId: String? = null,
        includeItemTypes: List<String> = emptyList(),
        recursive: Boolean = true,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        filters: List<String> = emptyList(),
        startIndex: Int = 0,
        limit: Int = 100,
        searchTerm: String? = null,
        /** `GET /Items` `genreIds` — filters to items in the given genres. Use the uuid, never the name. */
        genreIds: List<String> = emptyList(),
        /**
         * `GET /Items` `personIds` — filters to items the given people appear in. This is what backs
         * the OSD Cast shelf (docs/osd-v3.md §5): tapping a person reuses the existing library grid
         * with the filter applied instead of a new screen.
         */
        personIds: List<String> = emptyList(),
        nameStartsWith: String? = null,
    ): ApiResult<QueryResult<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get(
            "/Items",
            mapOf(
                "userId" to userId,
                "parentId" to parentId,
                "genreIds" to genreIds.ifEmpty { null },
                "personIds" to personIds.ifEmpty { null },
                "includeItemTypes" to includeItemTypes.ifEmpty { null },
                "recursive" to recursive,
                "sortBy" to sortBy,
                "sortOrder" to sortOrder,
                "filters" to filters.ifEmpty { null },
                "startIndex" to startIndex,
                "limit" to limit,
                "searchTerm" to searchTerm,
                "nameStartsWith" to nameStartsWith,
                "fields" to "$CARD_FIELDS,Genres",
                "enableUserData" to true,
                "imageTypeLimit" to 1,
                "enableImageTypes" to CARD_IMAGE_TYPES,
            ),
        )
    }

    /** GET /Genres. Items are genres: `id` is a uuid usable as `genreIds`. */
    suspend fun genres(parentId: String? = null, limit: Int = 40): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/Genres",
            mapOf(
                "userId" to userId,
                "parentId" to parentId,
                "limit" to limit,
                "sortBy" to "SortName",
                "enableImages" to false,
                "enableTotalRecordCount" to false,
            ),
        ).map { it.items }
    }

    /** GET /Search/Hints. Media only by default; people/genres/studios are opt-in. */
    suspend fun searchHints(
        term: String,
        limit: Int = 20,
        includePeople: Boolean = false,
        includeGenres: Boolean = false,
        includeStudios: Boolean = false,
    ): ApiResult<List<SearchHintDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<SearchHintResult>(
            "/Search/Hints",
            mapOf(
                "userId" to userId,
                "searchTerm" to term,
                "limit" to limit,
                "includeMedia" to true,
                "includePeople" to includePeople,
                "includeGenres" to includeGenres,
                "includeStudios" to includeStudios,
            ),
        ).map { it.searchHints }
    }

    /**
     * `GET /Items/{itemId}` accepts only `userId` — there is no `fields` param on it, so the
     * detail fetch is routed through `/Items?ids=` which does accept the full field set.
     */
    suspend fun item(itemId: String): ApiResult<BaseItemDto> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/Items",
            mapOf(
                "userId" to userId,
                "ids" to itemId,
                "fields" to DETAIL_FIELDS,
                "enableUserData" to true,
                "imageTypeLimit" to 3,
                "enableImageTypes" to DETAIL_IMAGE_TYPES,
            ),
        ).flatMap { result ->
            result.items.firstOrNull()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(ApiError.Http(404, "Item $itemId not found"))
        }
    }

    suspend fun seasons(seriesId: String): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/Shows/$seriesId/Seasons",
            mapOf(
                "userId" to userId,
                "enableUserData" to true,
                "fields" to CARD_FIELDS,
                "enableImageTypes" to "Primary,Thumb,Banner",
                "imageTypeLimit" to 1,
            ),
        ).map { it.items }
    }

    suspend fun episodes(seriesId: String, seasonId: String?): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/Shows/$seriesId/Episodes",
            mapOf(
                "userId" to userId,
                "seasonId" to seasonId,
                "enableUserData" to true,
                "fields" to CARD_FIELDS,
                "enableImageTypes" to "Primary,Thumb,Backdrop",
                "imageTypeLimit" to 1,
            ),
        ).map { it.items }
    }

    /**
     * `GET /Items/{itemId}/Similar` — the "More like this" shelf (docs/osd-v3.md §5).
     *
     * The endpoint accepts only `userId`, `limit`, `fields` and `excludeArtistIds`
     * (jellyfin-openapi.json, `GetSimilarItems`); it has no `enableImageTypes`/`imageTypeLimit`/
     * `enableUserData` of its own, so the card art comes from the image tags the default response
     * already carries plus the requested `fields`.
     */
    suspend fun similar(itemId: String, limit: Int = 12): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/Items/$itemId/Similar",
            mapOf(
                "userId" to userId,
                "limit" to limit,
                "fields" to CARD_FIELDS,
            ),
        ).map { it.items }
    }

    suspend fun search(term: String, limit: Int = 60): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/Items",
            mapOf(
                "userId" to userId,
                "searchTerm" to term,
                "recursive" to true,
                "includeItemTypes" to "Movie,Series,Episode",
                "limit" to limit,
                "enableUserData" to true,
                "fields" to CARD_FIELDS,
                "enableImageTypes" to "Primary,Thumb",
                "imageTypeLimit" to 1,
            ),
        ).map { it.items }
    }

    private suspend fun userIdOrFail(): String? = client.currentSession()?.userId

    private fun <T> unauthorized(): ApiResult<T> = ApiResult.Failure(ApiError.Unauthorized)
}
