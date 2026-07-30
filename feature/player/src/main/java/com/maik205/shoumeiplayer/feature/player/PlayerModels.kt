package com.maik205.shoumeiplayer.feature.player

import com.maik205.shoumeiplayer.player.TrickplaySource
import com.maik205.shoumeiplayer.player.PlaybackSpeed
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.VideoQuality

internal const val PLAYER_CAST_LIMIT = 24
internal const val UP_NEXT_WINDOW_MS = 30_000L

data class ChapterMark(val positionMs: Long, val name: String?)

data class UpNextUi(
    val itemId: String,
    val title: String,
    val subtitle: String?,
    val thumbUrl: String?,
    val autoPlay: Boolean,
)

data class CastMemberUi(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
    val blurHash: String?,
)

data class AudioQueueItemUi(
    val itemId: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val playing: Boolean = false,
)

data class LyricLineUi(
    val text: String,
    val startMs: Long?,
)

data class PlayerTimelineState(
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val bufferedMs: Long? = null,
    val upNextVisible: Boolean = false,
)

data class PlayerShelfItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
)

data class PlayerUiState(
    val loading: Boolean = true,
    val title: String = "",
    val error: String? = null,
    val notice: String? = null,
    val state: PlayerState = PlayerState.Idle,
    val durationMs: Long? = null,
    val audioTracks: List<PlayerTrack> = emptyList(),
    val subtitleTracks: List<PlayerTrack> = emptyList(),
    val videoTracks: List<PlayerTrack> = emptyList(),
    val chapters: List<ChapterMark> = emptyList(),
    val speed: Float = PlaybackSpeed.Normal,
    val quality: VideoQuality = VideoQuality.AUTO,
    val swapping: Boolean = false,
    val trickplay: TrickplaySource? = null,
    val logoUrl: String? = null,
    val isEpisode: Boolean = false,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val year: Int? = null,
    val previousEpisodeId: String? = null,
    val nextEpisodeId: String? = null,
    val upNext: UpNextUi? = null,
    val postPlayEpisodes: List<UpNextUi> = emptyList(),
    val similar: List<PlayerShelfItem> = emptyList(),
    val cast: List<CastMemberUi> = emptyList(),
    val shelvesLoading: Boolean = false,
    val isAudio: Boolean = false,
    val artist: String? = null,
    val album: String? = null,
    val albumArtworkUrl: String? = null,
    val artistArtworkUrl: String? = null,
    val queue: List<AudioQueueItemUi> = emptyList(),
    val suggestedAudio: List<AudioQueueItemUi> = emptyList(),
    val lyrics: List<LyricLineUi> = emptyList(),
    val lyricsSynced: Boolean = false,
    val musicContextLoading: Boolean = false,
    val audioDelayMs: Long = 0,
    val subtitleDelayMs: Long = 0,
    val seekIntervalSeconds: Int = 10,
    val playMethod: String? = null,
    val container: String? = null,
    val videoDescription: String? = null,
    val audioDescription: String? = null,
    val activeAudioRoute: String = "System default",
    val effectiveHdrMode: String = "Automatic",
    val displayDescription: String? = null,
    val favorite: Boolean = false,
    val played: Boolean = false,
)

internal fun chapterMarks(chapters: List<ChapterMark>, durationMs: Long?): List<ChapterMark> =
    chapters
        .filter { it.positionMs >= 0 && (durationMs == null || durationMs <= 0 || it.positionMs < durationMs) }
        .sortedBy { it.positionMs }

internal fun currentChapter(chapters: List<ChapterMark>, positionMs: Long): ChapterMark? =
    chapters.lastOrNull { it.positionMs <= positionMs }

internal fun isUpNextDue(positionMs: Long, durationMs: Long?): Boolean {
    if (durationMs == null || durationMs <= 0) return false
    return durationMs - positionMs in 0..UP_NEXT_WINDOW_MS
}
