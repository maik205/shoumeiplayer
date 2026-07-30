package com.maik205.shoumeiplayer.di.player

import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.repo.PlaybackRepository
import com.maik205.shoumeiplayer.feature.player.AudioQueueItemUi
import com.maik205.shoumeiplayer.feature.player.CastMemberUi
import com.maik205.shoumeiplayer.feature.player.ChapterMark
import com.maik205.shoumeiplayer.feature.player.EpisodePlaybackContext
import com.maik205.shoumeiplayer.feature.player.LyricLineUi
import com.maik205.shoumeiplayer.feature.player.MusicPlaybackContext
import com.maik205.shoumeiplayer.feature.player.PlaybackMetadataLoader
import com.maik205.shoumeiplayer.feature.player.PlayerItemMetadata
import com.maik205.shoumeiplayer.feature.player.PlayerShelfItem
import com.maik205.shoumeiplayer.feature.player.UpNextUi
import com.maik205.shoumeiplayer.util.Ticks

class JellyfinPlaybackMetadataLoader(
    private val libraryRepository: LibraryRepository,
    private val authRepository: AuthRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
    private val settingsStore: SettingsStore?,
    private val playbackRepository: PlaybackRepository,
) : PlaybackMetadataLoader {
    private val itemCache = object : LinkedHashMap<String, BaseItemDto>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BaseItemDto>?): Boolean =
            size > 8
    }

    override suspend fun loadItem(itemId: String): PlayerItemMetadata {
        val item = (libraryRepository.item(itemId) as? ApiResult.Success)?.data
        if (item != null) synchronized(itemCache) { itemCache[itemId] = item }
        val isAudio = item?.type == "Audio" || item?.mediaType == "Audio"
        val artist = item?.artists
            ?.filter(String::isNotBlank)
            ?.joinToString(", ")
            ?.takeIf(String::isNotBlank)
            ?: item?.albumArtist?.takeIf(String::isNotBlank)
        val albumArtwork = item?.albumId
            ?.let { albumId -> imageUrlBuilder.primary(albumId, item.albumPrimaryImageTag, maxWidth = 900) }
            ?: item?.let { imageUrlBuilder.primaryWithParentFallback(it, maxWidth = 900) }
        return PlayerItemMetadata(
            itemId = itemId,
            title = item?.name.orEmpty(),
            isAudio = isAudio,
            resumeTicks = item?.userData?.playbackPositionTicks ?: 0,
            durationMs = item?.runTimeTicks?.let(Ticks::toMs),
            chapters = item?.chapters.orEmpty().map {
                ChapterMark(
                    positionMs = Ticks.toMs(it.startPositionTicks),
                    name = it.name?.takeIf(String::isNotBlank),
                )
            },
            artist = artist,
            album = item?.album?.takeIf(String::isNotBlank),
            albumArtworkUrl = albumArtwork,
            logoUrl = item?.let(imageUrlBuilder::logoWithParentFallback),
            isEpisode = item?.type == "Episode",
            seriesName = item?.seriesName,
            seasonNumber = item?.parentIndexNumber,
            episodeNumber = item?.indexNumber,
            year = item?.productionYear,
            cast = item?.people.orEmpty().take(PLAYER_CAST_LIMIT).map { person ->
                CastMemberUi(
                    id = person.id,
                    name = person.name.orEmpty(),
                    role = person.role?.takeIf(String::isNotBlank),
                    imageUrl = person.primaryImageTag
                        ?.let { tag -> imageUrlBuilder.personPrimary(person.id, tag) },
                    blurHash = person.imageBlurHashes["Primary"]?.get(person.primaryImageTag),
                )
            },
        )
    }

    override suspend fun loadMusicContext(
        itemId: String,
    ): MusicPlaybackContext {
        val item = cachedItem(itemId) ?: return MusicPlaybackContext(
            queue = emptyList(),
            suggested = emptyList(),
            lyrics = emptyList(),
            lyricsSynced = false,
            artistArtworkUrl = null,
        )
        val artistId = (item.artistItems + item.albumArtists)
            .firstOrNull { it.id.isNotBlank() }
            ?.id
        val albumId = item.albumId
        val queueItems = when {
            !albumId.isNullOrBlank() ->
                (libraryRepository.albumTracks(albumId) as? ApiResult.Success)?.data.orEmpty()
            !artistId.isNullOrBlank() ->
                (libraryRepository.artistSongs(artistId) as? ApiResult.Success)?.data.orEmpty()
            else -> emptyList()
        }
        val queueSeed = if (queueItems.any { it.id == itemId }) queueItems else listOf(item) + queueItems
        val queue = queueSeed
            .distinctBy { it.id }
            .map { it.toAudioQueueItem(currentId = itemId) }
        val suggested = (libraryRepository.similar(itemId, limit = 20) as? ApiResult.Success)
            ?.data
            .orEmpty()
            .filter { it.type == "Audio" || it.mediaType == "Audio" }
            .filterNot { candidate -> queue.any { queued -> queued.itemId == candidate.id } }
            .map { it.toAudioQueueItem(currentId = itemId) }
        val lyricDto = if (item.hasLyrics) {
            (libraryRepository.lyrics(itemId) as? ApiResult.Success)?.data
        } else {
            null
        }
        val lyrics = lyricDto?.lyrics
            .orEmpty()
            .filter { it.text.isNotBlank() }
            .map { line ->
                LyricLineUi(
                    text = line.text,
                    startMs = line.start?.let(Ticks::toMs),
                )
            }
        val artistArtwork = artistId
            ?.let { (libraryRepository.item(it) as? ApiResult.Success)?.data }
            ?.let { imageUrlBuilder.primaryWithParentFallback(it, maxWidth = 480) }
            ?: item.people
                .firstOrNull { person ->
                    person.id == artistId || person.type.equals("MusicArtist", ignoreCase = true)
                }
                ?.let { imageUrlBuilder.personPrimary(it.id, it.primaryImageTag, maxWidth = 480) }
        return MusicPlaybackContext(
            queue = queue,
            suggested = suggested,
            lyrics = lyrics,
            lyricsSynced = lyricDto?.metadata?.isSynced == true || lyrics.any { it.startMs != null },
            artistArtworkUrl = artistArtwork,
        )
    }

    override suspend fun loadEpisodeContext(
        itemId: String,
    ): EpisodePlaybackContext? {
        val item = cachedItem(itemId)
        val seriesId = item?.seriesId?.takeIf { item.type == "Episode" } ?: return null
        val episodes = (libraryRepository.episodes(seriesId, seasonId = null) as? ApiResult.Success)
            ?.data
            .orEmpty()
        val index = episodes.indexOfFirst { it.id == itemId }
        if (index < 0) return null
        val previous = episodes.getOrNull(index - 1)
        val next = episodes.getOrNull(index + 1)
        val autoPlay = settingsStore
            ?.let { runCatching { it.current().autoplayNextEpisode }.getOrNull() }
            ?: authRepository.userConfiguration()?.enableNextEpisodeAutoPlay
            ?: true
        fun BaseItemDto.toUpNext() = UpNextUi(
            itemId = id,
            title = name.orEmpty(),
            subtitle = playerShelfSubtitle(),
            thumbUrl = playerShelfArtwork(),
            autoPlay = autoPlay,
        )
        return EpisodePlaybackContext(
            previousEpisodeId = previous?.id,
            nextEpisodeId = next?.id,
            upNext = next?.toUpNext(),
            postPlayEpisodes = episodes.drop(index + 1).map(BaseItemDto::toUpNext),
        )
    }

    override suspend fun loadSimilar(itemId: String): List<PlayerShelfItem> =
        (libraryRepository.similar(itemId) as? ApiResult.Success)
            ?.data
            .orEmpty()
            .map { item ->
                PlayerShelfItem(
                    id = item.id,
                    title = item.name.orEmpty(),
                    subtitle = item.playerShelfSubtitle(),
                    artworkUrl = item.playerShelfArtwork(),
                )
            }

    override suspend fun trickplaySource(
        itemId: String,
        mediaSourceId: String?,
        targetWidth: Int,
    ): com.maik205.shoumeiplayer.player.TrickplaySource? {
        val item = cachedItem(itemId) ?: return null
        val bands = item.trickplay[mediaSourceId] ?: item.trickplay.values.firstOrNull() ?: return null
        return playbackRepository.trickplaySource(itemId, mediaSourceId, bands, targetWidth)
    }

    private suspend fun cachedItem(itemId: String): BaseItemDto? {
        synchronized(itemCache) { itemCache[itemId] }?.let { return it }
        val item = (libraryRepository.item(itemId) as? ApiResult.Success)?.data ?: return null
        synchronized(itemCache) { itemCache[itemId] = item }
        return item
    }

    private fun BaseItemDto.toAudioQueueItem(currentId: String): AudioQueueItemUi {
        val displayArtist = artists
            .filter(String::isNotBlank)
            .joinToString(", ")
            .takeIf(String::isNotBlank)
            ?: albumArtist?.takeIf(String::isNotBlank)
        val artwork = albumId
            ?.let { imageUrlBuilder.primary(it, albumPrimaryImageTag, maxWidth = 360) }
            ?: imageUrlBuilder.primaryWithParentFallback(this, maxWidth = 360)
        return AudioQueueItemUi(
            itemId = id,
            title = name.orEmpty(),
            artist = displayArtist,
            album = album,
            artworkUrl = artwork,
            durationMs = runTimeTicks?.let(Ticks::toMs),
            playing = id == currentId,
        )
    }

    private fun BaseItemDto.playerShelfSubtitle(): String? = when (type) {
        "Episode" -> "S${parentIndexNumber ?: 0}:E${indexNumber ?: 0}"
        else -> productionYear?.toString()
    }

    private fun BaseItemDto.playerShelfArtwork(): String? =
        (if (type == "Episode") imageUrlBuilder.episodePreview(this, maxWidth = 720) else null)
            ?: imageUrlBuilder.thumbWithSeriesFallback(this, maxWidth = 720)
            ?: imageUrlBuilder.backdropWithParentFallback(this, maxWidth = 720)
            ?: imageUrlBuilder.primaryWithParentFallback(this, maxWidth = 720)
}

private const val PLAYER_CAST_LIMIT = 24
