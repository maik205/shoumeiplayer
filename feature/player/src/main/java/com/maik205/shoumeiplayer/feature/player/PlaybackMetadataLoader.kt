package com.maik205.shoumeiplayer.feature.player

import com.maik205.shoumeiplayer.player.TrickplaySource

data class PlayerItemMetadata(
    val itemId: String,
    val title: String,
    val isAudio: Boolean,
    val resumeTicks: Long,
    val durationMs: Long?,
    val chapters: List<ChapterMark>,
    val artist: String?,
    val album: String?,
    val albumArtworkUrl: String?,
    val logoUrl: String?,
    val isEpisode: Boolean,
    val seriesName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val year: Int?,
    val cast: List<CastMemberUi>,
)

data class MusicPlaybackContext(
    val queue: List<AudioQueueItemUi>,
    val suggested: List<AudioQueueItemUi>,
    val lyrics: List<LyricLineUi>,
    val lyricsSynced: Boolean,
    val artistArtworkUrl: String?,
)

data class EpisodePlaybackContext(
    val previousEpisodeId: String?,
    val nextEpisodeId: String?,
    val upNext: UpNextUi?,
    val postPlayEpisodes: List<UpNextUi>,
)

interface PlaybackMetadataLoader {
    suspend fun loadItem(itemId: String): PlayerItemMetadata
    suspend fun loadMusicContext(itemId: String): MusicPlaybackContext
    suspend fun loadEpisodeContext(itemId: String): EpisodePlaybackContext?
    suspend fun loadSimilar(itemId: String): List<PlayerShelfItem>

    suspend fun trickplaySource(
        itemId: String,
        mediaSourceId: String?,
        targetWidth: Int,
    ): TrickplaySource?
}
