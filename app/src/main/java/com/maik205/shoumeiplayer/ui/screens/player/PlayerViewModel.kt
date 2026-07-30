package com.maik205.shoumeiplayer.ui.screens.player

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.TrickplaySource
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.ChapterInfoDto
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.repo.DEFAULT_MAX_STREAMING_BITRATE
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.repo.PLAY_METHOD_TRANSCODE
import com.maik205.shoumeiplayer.data.repo.PlaybackRepository
import com.maik205.shoumeiplayer.data.repo.ResolvedPlayback
import com.maik205.shoumeiplayer.data.repo.TrackSelection
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.player.PlayRequest
import com.maik205.shoumeiplayer.player.PlaybackProgressReporter
import com.maik205.shoumeiplayer.player.PlaybackSpeed
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.TrackType
import com.maik205.shoumeiplayer.player.VideoQuality
import com.maik205.shoumeiplayer.ui.components.MediaCardUi
import com.maik205.shoumeiplayer.ui.components.toCardUi
import com.maik205.shoumeiplayer.util.Ticks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** The trickplay width band a scrub preview asks for; §3 draws it a little under 320dp wide. */
private const val TRICKPLAY_TARGET_WIDTH = 320

/** How many people the Cast shelf carries — beyond this a TV row is scrolling for its own sake. */
private const val CAST_LIMIT = 24

/** §5 — the Up Next card appears in the last 30 seconds of an episode. */
internal const val UP_NEXT_WINDOW_MS = 30_000L

/**
 * One chapter boundary, already converted out of Jellyfin ticks (100ns) into milliseconds so the
 * OSD never has to know about the tick unit.
 */
data class ChapterMark(val positionMs: Long, val name: String?)

/** §5 — the next episode, ready to offer at the end of the current one. */
data class UpNextUi(
    val itemId: String,
    val title: String,
    val subtitle: String?,
    val thumbUrl: String?,
    val blurHash: String?,
    /** User config `EnableNextEpisodeAutoPlay`: false means the card offers but never counts down. */
    val autoPlay: Boolean,
)

/** §5 — one person on the Cast shelf. */
data class CastMemberUi(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
    val blurHash: String?,
)

/**
 * A compact, player-owned queue row. Keeping this model outside the television package lets the
 * playback state remain usable by any future presentation without leaking Compose concerns into it.
 */
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
    /** Jellyfin lyric timestamps converted from ticks to the player timeline. Null means unsynced. */
    val startMs: Long?,
)

data class PlayerUiState(
    val loading: Boolean = true,
    val title: String = "",
    val error: String? = null,
    /**
     * A non-fatal message about the *last action* (a quality swap the server refused, an episode
     * that would not resolve). Unlike [error] this never replaces the video: playback is still
     * running, so the OSD shows it and moves on.
     */
    val notice: String? = null,
    val state: PlayerState = PlayerState.Idle,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    /** End of the buffered range on the same timeline as [positionMs]; null when unknown. */
    val bufferedMs: Long? = null,
    val audioTracks: List<PlayerTrack> = emptyList(),
    val subtitleTracks: List<PlayerTrack> = emptyList(),
    val videoTracks: List<PlayerTrack> = emptyList(),
    val chapters: List<ChapterMark> = emptyList(),
    // --- §5 features -------------------------------------------------------------------------
    val speed: Float = PlaybackSpeed.Normal,
    val quality: VideoQuality = VideoQuality.AUTO,
    /** A stream swap (quality or episode) is in flight; the old stream is still on screen. */
    val swapping: Boolean = false,
    val trickplay: TrickplaySource? = null,
    // --- §4 identity block -------------------------------------------------------------------
    val logoUrl: String? = null,
    val isEpisode: Boolean = false,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val year: Int? = null,
    // --- §5 adjacency / Up Next / shelves ------------------------------------------------------
    val previousEpisodeId: String? = null,
    val nextEpisodeId: String? = null,
    val upNext: UpNextUi? = null,
    val postPlayEpisodes: List<UpNextUi> = emptyList(),
    /** True once the playhead is inside the Up Next window and the card has not been dismissed. */
    val upNextVisible: Boolean = false,
    val similar: List<MediaCardUi> = emptyList(),
    val cast: List<CastMemberUi> = emptyList(),
    val shelvesLoading: Boolean = false,
    // --- music playback ------------------------------------------------------------------------
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
    // --- engine-side playback options ----------------------------------------------------------
    val audioDelayMs: Long = 0,
    val subtitleDelayMs: Long = 0,
    /** Remote and on-screen seek step, loaded from the persisted television playback settings. */
    val seekIntervalSeconds: Int = 10,
    val playMethod: String? = null,
    val container: String? = null,
    val videoDescription: String? = null,
    val audioDescription: String? = null,
    val displayDescription: String? = null,
)

/**
 * Converts `BaseItemDto.Chapters[]` into ordered [ChapterMark]s. Chapters at or past [durationMs]
 * are dropped — a marker at or beyond the end of the lane is noise, not navigation. A null or
 * non-positive [durationMs] means "duration not known yet", in which case nothing is dropped.
 */
internal fun chapterMarks(chapters: List<ChapterInfoDto>, durationMs: Long?): List<ChapterMark> =
    chapters
        .map {
            ChapterMark(
                positionMs = Ticks.toMs(it.startPositionTicks),
                name = it.name?.takeIf(String::isNotBlank),
            )
        }
        .filter { it.positionMs >= 0 && (durationMs == null || durationMs <= 0 || it.positionMs < durationMs) }
        .sortedBy { it.positionMs }

/** The chapter the playhead currently sits in: the last mark at or before [positionMs]. */
internal fun currentChapter(chapters: List<ChapterMark>, positionMs: Long): ChapterMark? =
    chapters.lastOrNull { it.positionMs <= positionMs }

/**
 * §5 — is the playhead inside the Up Next window? An unknown or zero duration means "no", because
 * without an end there is no "near the end".
 */
internal fun isUpNextDue(positionMs: Long, durationMs: Long?): Boolean {
    if (durationMs == null || durationMs <= 0) return false
    return durationMs - positionMs in 0..UP_NEXT_WINDOW_MS
}

/**
 * Resolves [itemId] into a playable stream via [PlaybackRepository], hands it
 * to the [PlayerEngine], and keeps a background [PlaybackProgressReporter] loop
 * running for the lifetime of the ViewModel to report start/progress/stop.
 *
 * The stream on screen can be replaced without ending playback — [setQuality] re-resolves the same
 * item at a different bitrate cap, [switchTo] moves to an adjacent episode (docs/osd-v3.md §5). Both
 * go through [swapStream], which retires the outgoing server session (stop report + transcode
 * teardown) while leaving the ViewModel's own final teardown latch untouched.
 */
class PlayerViewModel(
    private val engine: PlayerEngine,
    private val playbackRepository: PlaybackRepository,
    private val libraryRepository: LibraryRepository,
    private val authRepository: AuthRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
    private val reporter: PlaybackProgressReporter,
    private val itemId: String,
    private val startPositionTicks: Long,
    /**
     * Scope that outlives this ViewModel, used only for the final stop report and transcode
     * teardown. `viewModelScope` is cancelled by the time [onCleared] runs, so anything launched
     * there during teardown would be killed before the request left the device.
     */
    private val teardownScope: CoroutineScope,
    private val settingsStore: SettingsStore? = null,
    private val initialAudioStreamIndex: Int? = null,
    private val initialSubtitleStreamIndex: Int? = null,
    private val initialQualityLabel: String? = null,
) : ViewModel() {

    private data class LocalState(
        val loading: Boolean = true,
        val title: String = "",
        val error: String? = null,
        val notice: String? = null,
        val chapters: List<ChapterInfoDto> = emptyList(),
        val itemDurationMs: Long? = null,
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
        val playMethod: String? = null,
        val container: String? = null,
        val videoDescription: String? = null,
        val audioDescription: String? = null,
        val displayDescription: String? = null,
        val upNextDismissed: Boolean = false,
        val similar: List<MediaCardUi> = emptyList(),
        val cast: List<CastMemberUi> = emptyList(),
        val shelvesLoading: Boolean = false,
        val shelvesLoadedFor: String? = null,
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
    )

    private val localState = MutableStateFlow(LocalState())

    private var resolved: ResolvedPlayback? = null

    /** Latches when the one final stop report has been sent, so the pair of teardown paths is idempotent. */
    private val teardownStarted = AtomicBoolean(false)

    /**
     * Latches as soon as teardown is *requested*, resolved session or not. A stream swap consults
     * this before handing anything to the engine: a swap that resolves after the screen is gone must
     * stop its new session rather than start playing into a dead ViewModel.
     */
    private val screenGone = AtomicBoolean(false)
    private var selectedAudioIndex: Int? = null
    private var selectedSubtitleIndex: Int? = null
    /** Bitrate used by Auto quality; explicit quality rungs always replace this with their own cap. */
    private var automaticMaxStreamingBitrate: Long = DEFAULT_MAX_STREAMING_BITRATE
    private var initialSelectionPending =
        initialAudioStreamIndex != null || initialSubtitleStreamIndex != null

    /** The item actually on screen — [itemId] only until the first [switchTo]. */
    private var currentItemId: String = itemId

    private var reporterJob: Job? = null
    private var initialJob: Job? = null
    private var swapJob: Job? = null
    private var shelvesJob: Job? = null
    private var musicContextJob: Job? = null

    /** Position + duration + buffered end, grouped so the outer [combine] stays within arity. */
    private data class Timeline(val positionMs: Long, val durationMs: Long?, val bufferedMs: Long?)

    private val timeline = combine(
        engine.positionMs,
        engine.durationMs,
        engine.bufferedMs,
    ) { positionMs, durationMs, bufferedMs -> Timeline(positionMs, durationMs, bufferedMs) }

    /** Engine state + rate, grouped for the same reason as [Timeline]. */
    private data class Playback(val state: PlayerState, val speed: Float)

    private val playback = combine(engine.state, engine.speed) { state, speed -> Playback(state, speed) }

    val uiState: StateFlow<PlayerUiState> = combine(
        playback,
        timeline,
        engine.tracks,
        localState,
    ) { play, time, tracks, local ->
        val duration = time.durationMs ?: local.itemDurationMs
        PlayerUiState(
            loading = local.loading,
            title = local.title,
            error = local.error,
            notice = local.notice,
            state = play.state,
            positionMs = time.positionMs,
            durationMs = duration,
            bufferedMs = time.bufferedMs,
            audioTracks = tracks.filter { it.type == TrackType.AUDIO },
            subtitleTracks = tracks.filter { it.type == TrackType.SUBTITLE },
            videoTracks = tracks.filter { it.type == TrackType.VIDEO },
            chapters = chapterMarks(local.chapters, duration),
            speed = play.speed,
            quality = local.quality,
            swapping = local.swapping,
            trickplay = local.trickplay,
            logoUrl = local.logoUrl,
            isEpisode = local.isEpisode,
            seriesName = local.seriesName,
            seasonNumber = local.seasonNumber,
            episodeNumber = local.episodeNumber,
            year = local.year,
            previousEpisodeId = local.previousEpisodeId,
            nextEpisodeId = local.nextEpisodeId,
            upNext = local.upNext,
            postPlayEpisodes = local.postPlayEpisodes,
            upNextVisible = local.upNext != null &&
                !local.upNextDismissed &&
                isUpNextDue(time.positionMs, duration),
            similar = local.similar,
            cast = local.cast,
            shelvesLoading = local.shelvesLoading,
            isAudio = local.isAudio,
            artist = local.artist,
            album = local.album,
            albumArtworkUrl = local.albumArtworkUrl,
            artistArtworkUrl = local.artistArtworkUrl,
            queue = local.queue,
            suggestedAudio = local.suggestedAudio,
            lyrics = local.lyrics,
            lyricsSynced = local.lyricsSynced,
            musicContextLoading = local.musicContextLoading,
            audioDelayMs = local.audioDelayMs,
            subtitleDelayMs = local.subtitleDelayMs,
            seekIntervalSeconds = local.seekIntervalSeconds,
            playMethod = local.playMethod,
            container = local.container,
            videoDescription = local.videoDescription,
            audioDescription = local.audioDescription,
            displayDescription = local.displayDescription,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    init {
        startInitialPlayback()
    }

    private fun startInitialPlayback() {
        if (initialJob?.isActive == true || screenGone.get()) return
        initialJob = viewModelScope.launch {
            localState.update { it.copy(loading = true, error = null, notice = null) }
            val settings = settingsStore?.let { runCatching { it.current() }.getOrNull() }
            val preferredQuality = VideoQuality.forLabel(initialQualityLabel ?: settings?.preferredQuality)
            val audioDelay = settings?.audioDelayMs?.toLong() ?: 0L
            val subtitleDelay = settings?.subtitleDelayMs?.toLong() ?: 0L
            val seekIntervalSeconds = settings?.seekIntervalSeconds?.coerceAtLeast(1) ?: 10
            automaticMaxStreamingBitrate = settings
                ?.maxRemoteBitrateMbps
                ?.takeIf { it > 0 }
                ?.toLong()
                ?.times(1_000_000L)
                ?: DEFAULT_MAX_STREAMING_BITRATE
            settings?.let(engine::configure)
            localState.update {
                it.copy(
                    quality = preferredQuality,
                    audioDelayMs = audioDelay,
                    subtitleDelayMs = subtitleDelay,
                    seekIntervalSeconds = seekIntervalSeconds,
                )
            }
            engine.setAudioDelayMs(audioDelay)
            engine.setSubtitleDelayMs(subtitleDelay)

            val item = loadItemMetadata(itemId)
            if (screenGone.get()) return@launch
            // Streaming quality is a video concern. Carrying a saved 720p/1080p preference into an
            // audio item would unnecessarily forbid direct play and can make the server transcode a
            // track that mpv could have consumed untouched.
            val quality = if (item?.type == "Audio" || item?.mediaType == "Audio") {
                VideoQuality.AUTO
            } else {
                preferredQuality
            }
            if (quality != preferredQuality) localState.update { it.copy(quality = quality) }
            when (
                val result = resolveFor(
                    itemId = itemId,
                    startPositionTicks = startPositionTicks,
                    quality = quality,
                    audioStreamIndex = initialAudioStreamIndex,
                    subtitleStreamIndex = initialSubtitleStreamIndex,
                )
            ) {
                is ApiResult.Failure -> {
                    localState.update { it.copy(loading = false, error = result.error.displayMessage) }
                }
                is ApiResult.Success -> {
                    if (screenGone.get()) {
                        teardownScope.launch {
                            reporter.reportStopped(result.data, Ticks.toMs(startPositionTicks), failed = false)
                        }
                        return@launch
                    }
                    localState.update { it.copy(loading = false, error = null) }
                    attachStream(result.data, item, Ticks.toMs(startPositionTicks), keepTracks = false)
                }
            }
        }
    }

    // --- transport ---------------------------------------------------------------------------

    fun setSurface(surface: Surface?) {
        engine.setSurface(surface)
    }

    fun setSurfaceSize(width: Int, height: Int) {
        engine.setSurfaceSize(width, height)
    }

    fun togglePlayPause() {
        when (engine.state.value) {
            PlayerState.Playing -> engine.pause()
            PlayerState.Buffering, PlayerState.Loading -> engine.pause()
            PlayerState.Paused -> engine.play()
            PlayerState.Ended -> {
                engine.seekTo(0)
                engine.play()
            }
            else -> Unit
        }
    }

    fun play() {
        engine.play()
    }

    fun pause() {
        engine.pause()
    }

    fun seekBy(deltaMs: Long) {
        val duration = engine.durationMs.value ?: Long.MAX_VALUE
        val target = (engine.positionMs.value + deltaMs).coerceIn(0L, duration)
        engine.seekTo(target)
    }

    fun seekTo(ms: Long) {
        engine.seekTo(ms)
    }

    fun selectTrack(track: PlayerTrack) {
        if (track.type == TrackType.VIDEO) {
            engine.selectTrack(track)
            return
        }
        val current = resolved
        if (current?.playMethod == PLAY_METHOD_TRANSCODE && swapJob?.isActive == true) return
        val previousAudioIndex = selectedAudioIndex
        val previousSubtitleIndex = selectedSubtitleIndex
        when (track.type) {
            TrackType.VIDEO -> Unit
            TrackType.AUDIO -> selectedAudioIndex = track.id
            TrackType.SUBTITLE -> selectedSubtitleIndex = track.id
        }
        if (current?.playMethod == PLAY_METHOD_TRANSCODE) {
            val positionMs = engine.positionMs.value
            swapJob = viewModelScope.launch {
                val changed = swapStream(
                    positionMs = positionMs,
                    resolve = {
                        resolveFor(
                            itemId = current.itemId,
                            startPositionTicks = Ticks.fromMs(positionMs),
                            quality = localState.value.quality,
                            mediaSourceId = current.mediaSourceId,
                            audioStreamIndex = selectedAudioIndex,
                            subtitleStreamIndex = selectedSubtitleIndex,
                        )
                    },
                    attach = { fresh ->
                        attachStream(fresh, item = null, startMs = positionMs, keepTracks = true)
                    },
                )
                if (!changed) {
                    selectedAudioIndex = previousAudioIndex
                    selectedSubtitleIndex = previousSubtitleIndex
                }
            }
        } else {
            engine.selectTrack(track)
            if (current != null) {
                viewModelScope.launch {
                    reporter.reportProgressNow(
                        resolved = current,
                        positionMs = engine.positionMs.value,
                        paused = engine.state.value == PlayerState.Paused,
                        selectedAudioIndex = selectedAudioIndex,
                        selectedSubtitleIndex = selectedSubtitleIndex,
                    )
                }
            }
        }
    }

    /** §5 — 0.5×…2×. Purely an engine-side rate change; the stream is untouched. */
    fun setSpeed(speed: Float) {
        engine.setSpeed(PlaybackSpeed.clamp(speed))
    }

    fun setAudioDelayMs(value: Long) {
        localState.update { it.copy(audioDelayMs = value) }
        engine.setAudioDelayMs(value)
        settingsStore?.let { store ->
            viewModelScope.launch { store.setAudioDelayMs(value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()) }
        }
    }

    fun setSubtitleDelayMs(value: Long) {
        localState.update { it.copy(subtitleDelayMs = value) }
        engine.setSubtitleDelayMs(value)
        settingsStore?.let { store ->
            viewModelScope.launch {
                store.setSubtitleDelayMs(value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
            }
        }
    }

    fun setFrameMode(value: String) = engine.setFrameMode(value)

    fun setHdrMode(value: String) = engine.setHdrMode(value)

    fun setDeinterlaceMode(value: String) = engine.setDeinterlaceMode(value)

    fun resetPlaybackDelays() {
        setAudioDelayMs(0)
        setSubtitleDelayMs(0)
    }

    /**
     * Re-resolves the current item after either a repository failure or an engine failure. The old
     * resolved session is retired only after a replacement exists, so retry never destroys the last
     * playable URL before the server has produced another one.
     */
    fun retryPlayback() {
        if (swapJob?.isActive == true) return
        val positionMs = engine.positionMs.value
        val current = resolved
        if (current == null) {
            screenGone.set(false)
            startInitialPlayback()
            return
        }
        swapJob = viewModelScope.launch {
            localState.update { it.copy(error = null, notice = null) }
            swapStream(
                positionMs = positionMs,
                resolve = {
                    resolveFor(
                        itemId = current.itemId,
                        startPositionTicks = Ticks.fromMs(positionMs),
                        quality = localState.value.quality,
                        mediaSourceId = current.mediaSourceId,
                        audioStreamIndex = selectedAudioIndex,
                        subtitleStreamIndex = selectedSubtitleIndex,
                    )
                },
                attach = { fresh ->
                    attachStream(fresh, item = null, startMs = positionMs, keepTracks = true)
                },
            )
        }
    }

    fun dismissNotice() {
        localState.update { it.copy(notice = null) }
    }

    // --- §5 quality --------------------------------------------------------------------------

    /**
     * Re-resolves the *same* item under a different bitrate cap and swaps the stream in place,
     * keeping the playhead and the selected audio/subtitle streams.
     *
     * A failed re-resolve leaves the current stream playing and reports a [PlayerUiState.notice] —
     * throwing away a working stream because a cap could not be honoured would be strictly worse
     * than ignoring the request.
     */
    fun setQuality(quality: VideoQuality) {
        if (quality == localState.value.quality) return
        val current = resolved ?: return
        if (swapJob?.isActive == true) return
        swapJob = viewModelScope.launch {
            val positionMs = engine.positionMs.value
            // Commit the requested rung only after a replacement resolves. A failed request must
            // not leave the chip claiming a quality nothing is playing at.
            val changed = swapStream(
                positionMs = positionMs,
                resolve = {
                    resolveFor(
                        itemId = current.itemId,
                        startPositionTicks = Ticks.fromMs(positionMs),
                        quality = quality,
                        mediaSourceId = current.mediaSourceId,
                        audioStreamIndex = selectedAudioIndex,
                        subtitleStreamIndex = selectedSubtitleIndex,
                    )
                },
                attach = { fresh -> attachStream(fresh, item = null, startMs = positionMs, keepTracks = true) },
            )
            if (changed) {
                localState.update { it.copy(quality = quality) }
                settingsStore?.setPreferredQuality(quality.label)
            }
        }
    }

    // --- §5 episode adjacency ------------------------------------------------------------------

    fun playPreviousEpisode() {
        localState.value.previousEpisodeId?.let(::switchTo)
    }

    fun playNextEpisode() {
        localState.value.nextEpisodeId?.let(::switchTo)
    }

    fun playPreviousAudio() {
        val queue = localState.value.queue
        val index = queue.indexOfFirst { it.playing }
        queue.getOrNull(index - 1)?.itemId?.let(::switchTo)
    }

    fun playNextAudio() {
        val queue = localState.value.queue
        val index = queue.indexOfFirst { it.playing }
        queue.getOrNull(index + 1)?.itemId?.let(::switchTo)
    }

    /**
     * Swaps the player over to [targetItemId] without leaving the screen (docs/osd-v3.md §5): the
     * outgoing session gets its stop report and transcode teardown, the new item is resolved at its
     * own resume position, and the identity/chapter/adjacency block is rebuilt around it.
     */
    fun switchTo(targetItemId: String) {
        if (targetItemId == currentItemId) return
        if (swapJob?.isActive == true) return
        swapJob = viewModelScope.launch {
            val positionMs = engine.positionMs.value
            var target: BaseItemDto? = null
            var startMs = 0L
            swapStream(
                positionMs = positionMs,
                resolve = {
                    target = (libraryRepository.item(targetItemId) as? ApiResult.Success)?.data
                    // Resume where the user left the target, exactly as entering it from Detail would.
                    val resumeTicks = target?.userData?.playbackPositionTicks ?: 0L
                    startMs = Ticks.toMs(resumeTicks)
                    resolveFor(targetItemId, resumeTicks, localState.value.quality)
                },
                attach = { fresh ->
                    currentItemId = targetItemId
                    // The new item owns its own shelves and its own Up Next dismissal.
                    localState.update {
                        it.copy(similar = emptyList(), shelvesLoadedFor = null, upNextDismissed = false)
                    }
                    applyItemMetadata(target)
                    attachStream(fresh, target, startMs, keepTracks = false)
                    loadPlaybackContext(targetItemId, target)
                },
            )
        }
    }

    // --- §5 Up Next / shelves ------------------------------------------------------------------

    /** BACK on the Up Next card: gone for this item, per §5. */
    fun dismissUpNext() {
        localState.update { it.copy(upNextDismissed = true) }
    }

    fun playUpNext() {
        localState.value.upNext?.let { switchTo(it.itemId) }
    }

    /**
     * Loads the "More like this" shelf on first open (§5: rows are lazily loaded). Cast needs no
     * request — it rides along on the item detail already fetched.
     */
    fun loadShelves() {
        val state = localState.value
        if (state.shelvesLoadedFor == currentItemId || shelvesJob?.isActive == true) return
        val target = currentItemId
        shelvesJob = viewModelScope.launch {
            localState.update { it.copy(shelvesLoading = true) }
            val similar = (libraryRepository.similar(target) as? ApiResult.Success)?.data
                .orEmpty()
                .map { it.toCardUi(imageUrlBuilder, wide = true) }
            localState.update {
                if (target != currentItemId) {
                    it.copy(shelvesLoading = false)
                } else {
                    it.copy(similar = similar, shelvesLoading = false, shelvesLoadedFor = target)
                }
            }
        }
    }

    // --- stream lifecycle ----------------------------------------------------------------------

    private suspend fun resolveFor(
        itemId: String,
        startPositionTicks: Long,
        quality: VideoQuality,
        mediaSourceId: String? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ): ApiResult<ResolvedPlayback> = playbackRepository.resolve(
        itemId = itemId,
        startPositionTicks = startPositionTicks,
        mediaSourceId = mediaSourceId,
        audioStreamIndex = audioStreamIndex,
        subtitleStreamIndex = subtitleStreamIndex,
        maxStreamingBitrate = quality.maxStreamingBitrate ?: automaticMaxStreamingBitrate,
        forceTranscode = quality.forcesTranscode,
    )

    /**
     * Shared body of every in-place stream replacement.
     *
     * Order matters: resolve first (the old stream keeps playing throughout), and only once a
     * replacement exists retire the old session and hand the new one to the engine. If the screen
     * tore down while the resolve was in flight, the new session is stopped immediately rather than
     * left holding a transcode nobody will ever watch.
     *
     * Returns true when the swap actually happened.
     */
    private suspend fun swapStream(
        positionMs: Long,
        resolve: suspend () -> ApiResult<ResolvedPlayback>,
        attach: suspend (ResolvedPlayback) -> Unit,
    ): Boolean {
        localState.update { it.copy(swapping = true, notice = null) }
        return when (val result = resolve()) {
            is ApiResult.Failure -> {
                localState.update { it.copy(swapping = false, notice = result.error.displayMessage) }
                false
            }
            is ApiResult.Success -> {
                if (screenGone.get()) {
                    teardownScope.launch { reporter.reportStopped(result.data, positionMs, failed = false) }
                    localState.update { it.copy(swapping = false) }
                    return false
                }
                retireCurrentSession(positionMs)
                attach(result.data)
                localState.update { it.copy(swapping = false) }
                true
            }
        }
    }

    /**
     * Ends the *outgoing stream's* server session: its reporter loop stops, and it gets the stop
     * report plus transcode teardown that [PlaybackProgressReporter.reportStopped] pairs together.
     *
     * Deliberately does not touch [teardownStarted]: this retires one stream, not the playback. The
     * replacement stream still owes the server exactly one stop of its own when the screen goes.
     */
    private fun retireCurrentSession(positionMs: Long) {
        val old = resolved ?: return
        resolved = null
        reporterJob?.cancel()
        reporterJob = null
        teardownScope.launch { reporter.reportStopped(old, positionMs, failed = false) }
    }

    /**
     * Hands a resolved stream to the engine and starts reporting for it.
     *
     * [keepTracks] preserves the current audio/subtitle selection across a quality swap; a new item
     * instead re-runs [TrackSelection] against the user's Jellyfin defaults.
     */
    private suspend fun attachStream(
        r: ResolvedPlayback,
        item: BaseItemDto?,
        startMs: Long,
        keepTracks: Boolean,
    ) {
        if (!keepTracks) {
            // The user's Jellyfin defaults (SubtitleMode, language preferences, …) decide which of
            // the resolved streams playback starts on.
            val configuration = authRepository.userConfiguration()
            val audioIndex = TrackSelection.selectAudioIndex(
                streams = r.mediaStreams,
                configuration = configuration,
                defaultAudioStreamIndex = r.defaultAudioIndex,
            )
            selectedAudioIndex = if (initialSelectionPending) {
                initialAudioStreamIndex ?: audioIndex
            } else {
                audioIndex
            }
            val subtitleIndex = TrackSelection.selectSubtitleIndex(
                streams = r.mediaStreams,
                configuration = configuration,
                defaultSubtitleStreamIndex = r.defaultSubtitleIndex,
                selectedAudioIndex = selectedAudioIndex,
            )
            selectedSubtitleIndex = if (initialSelectionPending) {
                initialSubtitleStreamIndex ?: subtitleIndex
            } else {
                subtitleIndex
            }
            initialSelectionPending = false
        }
        resolved = r
        val videoStream = r.mediaStreams.firstOrNull { it.type.equals("Video", ignoreCase = true) }
        val audioStream = r.mediaStreams.firstOrNull { it.type.equals("Audio", ignoreCase = true) }
        localState.update {
            it.copy(
                playMethod = r.playMethod,
                container = r.streamUrl
                    .substringBefore('?')
                    .substringAfterLast('.', missingDelimiterValue = "")
                    .uppercase()
                    .takeIf(String::isNotBlank),
                videoDescription = listOfNotNull(
                    videoStream?.codec?.uppercase(),
                    videoStream?.width?.let { width ->
                        videoStream.height?.let { height -> "${width}×$height" }
                    },
                ).joinToString(" · ").takeIf(String::isNotBlank),
                audioDescription = listOfNotNull(
                    audioStream?.codec?.uppercase(),
                    audioStream?.channels?.let { "$it ch" },
                    audioStream?.language,
                ).joinToString(" · ").takeIf(String::isNotBlank),
                displayDescription = videoStream?.width?.let { width ->
                    videoStream.height?.let { height -> "${width}×$height" }
                },
            )
        }

        // `fields=Trickplay` rides on the item query, so the manifest is only ever available here.
        // A quality swap passes item = null on purpose: trickplay sheets are generated per item and
        // media source, not per bitrate, so the manifest already in state survives the swap intact.
        val bands = item?.trickplay?.let { it[r.mediaSourceId] ?: it.values.firstOrNull() }
        if (item != null) {
            localState.update {
                it.copy(
                    trickplay = bands
                        ?.let { b -> playbackRepository.trickplaySource(r.itemId, r.mediaSourceId, b, TRICKPLAY_TARGET_WIDTH) },
                )
            }
        }

        engine.load(
            PlayRequest(
                url = r.streamUrl,
                title = localState.value.title,
                headers = r.headers,
                startPositionMs = startMs,
                durationMs = r.runTimeTicks?.let { Ticks.toMs(it) },
                requiresVideoSurface = item?.let {
                    it.type != "Audio" && it.mediaType != "Audio"
                } ?: true,
                preferredAudioTrackId = selectedAudioIndex,
                preferredSubtitleTrackId = selectedSubtitleIndex,
                externalSubtitles = r.externalSubtitles,
            ),
        )

        reporterJob?.cancel()
        reporterJob = viewModelScope.launch {
            reporter.run(
                engine = engine,
                resolved = r,
                selectedAudioIndex = { selectedAudioIndex },
                selectedSubtitleIndex = { selectedSubtitleIndex },
            )
        }
    }

    // --- item metadata -------------------------------------------------------------------------

    /** Fetches item detail, folds it into state, then starts the matching video/music context. */
    private suspend fun loadItemMetadata(targetItemId: String): BaseItemDto? {
        val item = (libraryRepository.item(targetItemId) as? ApiResult.Success)?.data
        applyItemMetadata(item)
        loadPlaybackContext(targetItemId, item)
        return item
    }

    /**
     * Folds one item's detail into state. Everything that belongs to a *specific* item — adjacency,
     * Up Next, the trickplay manifest — is cleared here rather than left standing: on an episode
     * swap the replacements arrive a moment later, and stale neighbours are worse than none.
     */
    private fun applyItemMetadata(item: BaseItemDto?) {
        val isAudio = item?.type == "Audio" || item?.mediaType == "Audio"
        val artist = item?.artists
            ?.filter(String::isNotBlank)
            ?.joinToString(", ")
            ?.takeIf(String::isNotBlank)
            ?: item?.albumArtist?.takeIf(String::isNotBlank)
        val albumArtwork = item?.albumId
            ?.let { albumId -> imageUrlBuilder.primary(albumId, item.albumPrimaryImageTag, maxWidth = 900) }
            ?: item?.let { dto -> imageUrlBuilder.primaryWithParentFallback(dto, maxWidth = 900) }
        localState.update {
            it.copy(
                previousEpisodeId = null,
                nextEpisodeId = null,
                upNext = null,
                postPlayEpisodes = emptyList(),
                trickplay = null,
                title = item?.name.orEmpty(),
                // `fields=Chapters` is requested by LibraryRepository.item(); /PlaybackInfo never
                // returns them, so this is the only place chapter data enters the player.
                chapters = item?.chapters.orEmpty(),
                itemDurationMs = item?.runTimeTicks?.let { ticks -> Ticks.toMs(ticks) },
                // §4 — series logo for an episode, own logo otherwise.
                logoUrl = item?.let { dto -> imageUrlBuilder.logoWithParentFallback(dto) },
                isEpisode = item?.type == "Episode",
                seriesName = item?.seriesName,
                seasonNumber = item?.parentIndexNumber,
                episodeNumber = item?.indexNumber,
                year = item?.productionYear,
                isAudio = isAudio,
                artist = artist,
                album = item?.album?.takeIf(String::isNotBlank),
                albumArtworkUrl = albumArtwork,
                artistArtworkUrl = null,
                queue = emptyList(),
                suggestedAudio = emptyList(),
                lyrics = emptyList(),
                lyricsSynced = false,
                musicContextLoading = isAudio,
                cast = item?.people.orEmpty().take(CAST_LIMIT).map { person ->
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
    }

    private fun loadPlaybackContext(targetItemId: String, item: BaseItemDto?) {
        if (item?.type == "Audio" || item?.mediaType == "Audio") {
            loadMusicContext(targetItemId, item)
        } else {
            musicContextJob?.cancel()
            viewModelScope.launch { loadAdjacency(targetItemId, item) }
        }
    }

    private fun loadMusicContext(targetItemId: String, item: BaseItemDto) {
        musicContextJob?.cancel()
        musicContextJob = viewModelScope.launch {
            val artistId = (item.artistItems + item.albumArtists)
                .firstOrNull { it.id.isNotBlank() }
                ?.id
            val queueItems = when {
                !item.albumId.isNullOrBlank() ->
                    (libraryRepository.albumTracks(item.albumId) as? ApiResult.Success)?.data.orEmpty()
                !artistId.isNullOrBlank() ->
                    (libraryRepository.artistSongs(artistId) as? ApiResult.Success)?.data.orEmpty()
                else -> emptyList()
            }
            val queueSeed = if (queueItems.any { it.id == targetItemId }) {
                queueItems
            } else {
                listOf(item) + queueItems
            }
            val queue = queueSeed
                .distinctBy { it.id }
                .map { it.toAudioQueueItem(currentId = targetItemId) }

            val suggested = (libraryRepository.similar(targetItemId, limit = 20) as? ApiResult.Success)
                ?.data
                .orEmpty()
                .filter { it.type == "Audio" || it.mediaType == "Audio" }
                .filterNot { candidate -> queue.any { queued -> queued.itemId == candidate.id } }
                .map { it.toAudioQueueItem(currentId = targetItemId) }

            val lyricDto = if (item.hasLyrics) {
                (libraryRepository.lyrics(targetItemId) as? ApiResult.Success)?.data
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
                    ?.let { person -> imageUrlBuilder.personPrimary(person.id, person.primaryImageTag, maxWidth = 480) }

            if (targetItemId != currentItemId) return@launch
            localState.update {
                it.copy(
                    queue = queue,
                    suggestedAudio = suggested,
                    lyrics = lyrics,
                    lyricsSynced = lyricDto?.metadata?.isSynced == true || lyrics.any { line -> line.startMs != null },
                    artistArtworkUrl = artistArtwork,
                    musicContextLoading = false,
                )
            }
        }
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

    /**
     * §5 — prev/next come from `/Shows/{seriesId}/Episodes` with no `seasonId`, so adjacency crosses
     * season boundaries the way a binge does. Movies have no neighbours and skip the request.
     */
    private suspend fun loadAdjacency(targetItemId: String, item: BaseItemDto?) {
        val seriesId = item?.seriesId?.takeIf { item.type == "Episode" } ?: return
        val episodes = (libraryRepository.episodes(seriesId, seasonId = null) as? ApiResult.Success)
            ?.data
            .orEmpty()
        val index = episodes.indexOfFirst { it.id == targetItemId }
        if (index < 0) return
        val previous = episodes.getOrNull(index - 1)
        val next = episodes.getOrNull(index + 1)
        val autoPlay = settingsStore
            ?.let { runCatching { it.current().autoplayNextEpisode }.getOrNull() }
            ?: authRepository.userConfiguration()?.enableNextEpisodeAutoPlay
            ?: true
        // Guard against a slow adjacency response landing after the user already moved on.
        if (targetItemId != currentItemId) return
        localState.update { state ->
            state.copy(
                previousEpisodeId = previous?.id,
                nextEpisodeId = next?.id,
                upNext = next?.let { episode ->
                    val card = episode.toCardUi(imageUrlBuilder, wide = true)
                    UpNextUi(
                        itemId = episode.id,
                        title = card.title,
                        subtitle = card.subtitle,
                        thumbUrl = card.imageUrl,
                        blurHash = card.blurHash,
                        autoPlay = autoPlay,
                    )
                },
                postPlayEpisodes = episodes.drop(index + 1).map { episode ->
                    val card = episode.toCardUi(imageUrlBuilder, wide = true)
                    UpNextUi(
                        itemId = episode.id,
                        title = card.title,
                        subtitle = card.subtitle,
                        thumbUrl = card.imageUrl,
                        blurHash = card.blurHash,
                        autoPlay = autoPlay,
                    )
                },
            )
        }
    }

    // --- teardown ------------------------------------------------------------------------------

    /**
     * Reports playback stopped and tears down any server-side transcode.
     *
     * Called from explicit user exit and [onCleared]. The CAS makes those teardown paths idempotent
     * so the server sees exactly one stop. A mere surface loss or recomposition intentionally does
     * not reach this method. A session that never finished resolving has nothing to report and
     * nothing to tear down.
     */
    private fun finishPlayback() {
        screenGone.set(true)
        val r = resolved ?: return
        if (!teardownStarted.compareAndSet(false, true)) return
        val position = engine.positionMs.value
        val failed = engine.state.value is PlayerState.Error
        teardownScope.launch {
            reporter.reportStopped(r, position, failed = failed)
        }
    }

    /** Legacy presentation hook; replacement surfaces should use [stopAndReport] on explicit exit. */
    fun onStopped() {
        finishPlayback()
    }

    /** Explicit user exit. Recomposition and ordinary surface loss must never call this. */
    fun stopAndReport() {
        finishPlayback()
        engine.stop()
    }

    override fun onCleared() {
        finishPlayback()
        engine.stop()
    }
}
