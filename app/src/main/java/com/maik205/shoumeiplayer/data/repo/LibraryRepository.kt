package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.data.ApiError
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.LyricDto
import com.maik205.shoumeiplayer.data.api.dto.QueryResult
import com.maik205.shoumeiplayer.data.api.dto.SearchHintDto
import com.maik205.shoumeiplayer.data.api.dto.SearchHintResult
import com.maik205.shoumeiplayer.data.api.dto.UserItemDataDto
import com.maik205.shoumeiplayer.data.flatMap
import com.maik205.shoumeiplayer.data.map

private const val CARD_FIELDS = "Overview,PrimaryImageAspectRatio"
private const val DETAIL_FIELDS =
    "Overview,Genres,Taglines,Studios,People,Chapters,MediaStreams,MediaSources,PrimaryImageAspectRatio,Trickplay"
private const val CARD_IMAGE_TYPES = "Primary,Backdrop,Thumb,Logo"
private const val DETAIL_IMAGE_TYPES = "Primary,Backdrop,Thumb,Logo,Banner,Art"
private const val BROAD_SEARCH_TYPES =
    "Movie,Series,Episode,MusicAlbum,MusicArtist,Audio,Playlist,AudioBook,BoxSet,Person,LiveTvChannel,Program,Recording"

data class PlayableTarget(
    val itemId: String,
    val startPositionTicks: Long = 0,
)

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

    suspend fun nextUp(limit: Int = 20, seriesId: String? = null): ApiResult<List<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/Shows/NextUp",
            mapOf(
                "userId" to userId,
                "limit" to limit,
                "seriesId" to seriesId,
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
        artistIds: List<String> = emptyList(),
        albumArtistIds: List<String> = emptyList(),
        albumIds: List<String> = emptyList(),
        mediaTypes: List<String> = emptyList(),
        minDateLastSavedForUser: String? = null,
    ): ApiResult<QueryResult<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get(
            "/Items",
            mapOf(
                "userId" to userId,
                "parentId" to parentId,
                "genreIds" to genreIds.ifEmpty { null },
                "personIds" to personIds.ifEmpty { null },
                "artistIds" to artistIds.ifEmpty { null },
                "albumArtistIds" to albumArtistIds.ifEmpty { null },
                "albumIds" to albumIds.ifEmpty { null },
                "mediaTypes" to mediaTypes.ifEmpty { null },
                "minDateLastSavedForUser" to minDateLastSavedForUser,
                "includeItemTypes" to includeItemTypes.ifEmpty { null },
                "recursive" to recursive,
                "sortBy" to sortBy,
                "sortOrder" to sortOrder,
                "filters" to filters.ifEmpty { null },
                "startIndex" to startIndex,
                "limit" to limit,
                "searchTerm" to searchTerm,
                "nameStartsWith" to nameStartsWith,
                "enableTotalRecordCount" to true,
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
                "fields" to "$DETAIL_FIELDS,ItemCounts",
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
                "fields" to "$CARD_FIELDS,MediaStreams",
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

    /**
     * Search across every media family represented by the TV shell instead of
     * limiting results to the legacy movie/series/episode subset.
     */
    suspend fun broadSearch(
        term: String,
        startIndex: Int = 0,
        limit: Int = 100,
    ): ApiResult<QueryResult<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get(
            "/Items",
            mapOf(
                "userId" to userId,
                "searchTerm" to term,
                "recursive" to true,
                "includeItemTypes" to BROAD_SEARCH_TYPES,
                "startIndex" to startIndex,
                "limit" to limit,
                "enableUserData" to true,
                "fields" to "$DETAIL_FIELDS,ItemCounts",
                "enableImageTypes" to DETAIL_IMAGE_TYPES,
                "imageTypeLimit" to 2,
            ),
        )
    }

    suspend fun searchAllMedia(term: String, limit: Int = 100): ApiResult<List<BaseItemDto>> =
        broadSearch(term, limit = limit).map { it.items }

    suspend fun musicAlbums(
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): ApiResult<QueryResult<BaseItemDto>> = items(
        parentId = parentId,
        includeItemTypes = listOf("MusicAlbum"),
        startIndex = startIndex,
        limit = limit,
    )

    suspend fun artists(
        query: String? = null,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): ApiResult<QueryResult<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get(
            "/Artists",
            mapOf(
                "userId" to userId,
                "parentId" to parentId,
                "searchTerm" to query,
                "startIndex" to startIndex,
                "limit" to limit,
                "sortBy" to "SortName",
                "sortOrder" to "Ascending",
                "fields" to "$CARD_FIELDS,Genres,ItemCounts",
                "enableUserData" to true,
                "enableImageTypes" to CARD_IMAGE_TYPES,
                "imageTypeLimit" to 1,
            ),
        )
    }

    suspend fun albumTracks(albumId: String, limit: Int = 500): ApiResult<List<BaseItemDto>> =
        items(
            parentId = albumId,
            includeItemTypes = listOf("Audio"),
            recursive = true,
            sortBy = "ParentIndexNumber,IndexNumber,SortName",
            limit = limit,
            albumIds = listOf(albumId),
        ).map { it.items }

    suspend fun artistAlbums(artistId: String, limit: Int = 200): ApiResult<List<BaseItemDto>> =
        items(
            includeItemTypes = listOf("MusicAlbum"),
            recursive = true,
            sortBy = "ProductionYear,SortName",
            limit = limit,
            albumArtistIds = listOf(artistId),
        ).map { it.items }

    suspend fun artistSongs(artistId: String, limit: Int = 500): ApiResult<List<BaseItemDto>> =
        items(
            includeItemTypes = listOf("Audio"),
            recursive = true,
            sortBy = "Album,ParentIndexNumber,IndexNumber,SortName",
            limit = limit,
            artistIds = listOf(artistId),
        ).map { it.items }

    suspend fun playlists(limit: Int = 100): ApiResult<List<BaseItemDto>> =
        items(includeItemTypes = listOf("Playlist"), limit = limit).map { it.items }

    suspend fun playlistItems(
        playlistId: String,
        startIndex: Int = 0,
        limit: Int = 500,
    ): ApiResult<QueryResult<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get(
            "/Playlists/$playlistId/Items",
            mapOf(
                "userId" to userId,
                "startIndex" to startIndex,
                "limit" to limit,
                "fields" to "$DETAIL_FIELDS,ItemCounts",
                "enableUserData" to true,
                "enableImages" to true,
            ),
        )
    }

    suspend fun audioBooks(limit: Int = 100): ApiResult<List<BaseItemDto>> =
        items(includeItemTypes = listOf("AudioBook"), limit = limit).map { it.items }

    suspend fun boxSets(limit: Int = 100): ApiResult<List<BaseItemDto>> =
        items(includeItemTypes = listOf("BoxSet"), limit = limit).map { it.items }

    suspend fun collections(limit: Int = 100): ApiResult<List<BaseItemDto>> = boxSets(limit)

    suspend fun collectionItems(
        boxSetId: String,
        startIndex: Int = 0,
        limit: Int = 200,
    ): ApiResult<QueryResult<BaseItemDto>> = items(
        parentId = boxSetId,
        startIndex = startIndex,
        limit = limit,
        sortBy = "SortName",
    )

    suspend fun people(
        query: String? = null,
        appearsInItemId: String? = null,
        personTypes: List<String> = emptyList(),
        limit: Int = 100,
    ): ApiResult<QueryResult<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get(
            "/Persons",
            mapOf(
                "userId" to userId,
                "searchTerm" to query,
                "appearsInItemId" to appearsInItemId,
                "personTypes" to personTypes.ifEmpty { null },
                "limit" to limit,
                "fields" to "$CARD_FIELDS,People",
                "enableImages" to true,
            ),
        )
    }

    suspend fun personCredits(
        personId: String,
        startIndex: Int = 0,
        limit: Int = 200,
    ): ApiResult<QueryResult<BaseItemDto>> = items(
        personIds = listOf(personId),
        startIndex = startIndex,
        limit = limit,
    )

    suspend fun lyrics(audioItemId: String): ApiResult<LyricDto> {
        if (userIdOrFail() == null) return unauthorized()
        return client.get("/Audio/$audioItemId/Lyrics")
    }

    suspend fun liveTvChannels(
        channelType: String? = null,
        startIndex: Int = 0,
        limit: Int = 200,
        addCurrentProgram: Boolean = true,
        favoritesOnly: Boolean = false,
    ): ApiResult<QueryResult<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get(
            "/LiveTv/Channels",
            mapOf(
                "userId" to userId,
                "type" to channelType,
                "startIndex" to startIndex,
                "limit" to limit,
                "isFavorite" to favoritesOnly.takeIf { it },
                "addCurrentProgram" to addCurrentProgram,
                "enableUserData" to true,
                "fields" to "$DETAIL_FIELDS,ChannelInfo",
                "enableImageTypes" to DETAIL_IMAGE_TYPES,
                "imageTypeLimit" to 2,
            ),
        )
    }

    suspend fun liveTvPrograms(
        channelIds: List<String> = emptyList(),
        minStartDate: String? = null,
        maxStartDate: String? = null,
        isAiring: Boolean? = null,
        startIndex: Int = 0,
        limit: Int = 500,
    ): ApiResult<QueryResult<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get(
            "/LiveTv/Programs",
            mapOf(
                "userId" to userId,
                "channelIds" to channelIds.ifEmpty { null },
                "minStartDate" to minStartDate,
                "maxStartDate" to maxStartDate,
                "isAiring" to isAiring,
                "startIndex" to startIndex,
                "limit" to limit,
                "enableUserData" to true,
                "fields" to DETAIL_FIELDS,
                "enableImageTypes" to DETAIL_IMAGE_TYPES,
                "imageTypeLimit" to 2,
            ),
        )
    }

    suspend fun liveTvRecordings(
        status: String? = null,
        startIndex: Int = 0,
        limit: Int = 200,
    ): ApiResult<QueryResult<BaseItemDto>> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get(
            "/LiveTv/Recordings",
            mapOf(
                "userId" to userId,
                "status" to status,
                "startIndex" to startIndex,
                "limit" to limit,
                "enableUserData" to true,
                "fields" to DETAIL_FIELDS,
                "enableImageTypes" to DETAIL_IMAGE_TYPES,
                "imageTypeLimit" to 2,
            ),
        )
    }

    suspend fun setFavorite(itemId: String, favorite: Boolean): ApiResult<UserItemDataDto> {
        val userId = userIdOrFail() ?: return unauthorized()
        return if (favorite) {
            client.post("/UserFavoriteItems/$itemId", params = mapOf("userId" to userId))
        } else {
            client.delete("/UserFavoriteItems/$itemId", params = mapOf("userId" to userId))
        }
    }

    suspend fun setPlayed(itemId: String, played: Boolean): ApiResult<UserItemDataDto> {
        val userId = userIdOrFail() ?: return unauthorized()
        return if (played) {
            client.post("/UserPlayedItems/$itemId", params = mapOf("userId" to userId))
        } else {
            client.delete("/UserPlayedItems/$itemId", params = mapOf("userId" to userId))
        }
    }

    suspend fun resolvePlayableTarget(itemId: String): ApiResult<PlayableTarget> =
        item(itemId).flatMap { resolvePlayableTarget(it) }

    suspend fun resolvePlayableTarget(item: BaseItemDto): ApiResult<PlayableTarget> {
        if (!item.type.equals("Series", ignoreCase = true)) {
            return ApiResult.Success(
                PlayableTarget(item.id, item.userData?.playbackPositionTicks ?: 0),
            )
        }

        val nextEpisode = (nextUp(limit = 1, seriesId = item.id) as? ApiResult.Success)
            ?.data
            ?.firstOrNull()
        if (nextEpisode != null) {
            return ApiResult.Success(
                PlayableTarget(nextEpisode.id, nextEpisode.userData?.playbackPositionTicks ?: 0),
            )
        }

        return episodes(item.id, seasonId = null).flatMap { episodeList ->
            val target = episodeList.firstOrNull { episode ->
                val userData = episode.userData
                userData != null &&
                    !userData.played &&
                    (userData.playbackPositionTicks > 0 ||
                        (userData.playedPercentage ?: 0.0) in 0.000001..99.999999)
            } ?: episodeList.firstOrNull { it.userData?.played != true }
                ?: episodeList.firstOrNull()
                ?: return@flatMap ApiResult.Failure(
                    ApiError.Http(404, "Series ${item.id} contains no playable episodes"),
                )
            ApiResult.Success(
                PlayableTarget(target.id, target.userData?.playbackPositionTicks ?: 0),
            )
        }
    }

    private suspend fun userIdOrFail(): String? = client.currentSession()?.userId

    private fun <T> unauthorized(): ApiResult<T> = ApiResult.Failure(ApiError.Unauthorized)
}
