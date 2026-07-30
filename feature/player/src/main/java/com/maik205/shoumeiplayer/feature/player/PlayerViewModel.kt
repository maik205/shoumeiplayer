package com.maik205.shoumeiplayer.feature.player

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.player.TrickplaySource
import com.maik205.shoumeiplayer.player.DEFAULT_MAX_STREAMING_BITRATE
import com.maik205.shoumeiplayer.player.PLAY_METHOD_TRANSCODE
import com.maik205.shoumeiplayer.player.ResolvedPlayback
import com.maik205.shoumeiplayer.domain.settings.PlayerSettingsRepository
import com.maik205.shoumeiplayer.player.PlayRequest
import com.maik205.shoumeiplayer.player.PlaybackResolutionRequest
import com.maik205.shoumeiplayer.player.PlaybackResolver
import com.maik205.shoumeiplayer.player.PlaybackProgressReporter
import com.maik205.shoumeiplayer.player.PlaybackSpeed
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.PlaybackMetricsEvent
import com.maik205.shoumeiplayer.player.PlaybackMetricsSink
import com.maik205.shoumeiplayer.player.toPlaybackMetricsState
import com.maik205.shoumeiplayer.player.TrackType
import com.maik205.shoumeiplayer.player.VideoQuality
import com.maik205.shoumeiplayer.util.Ticks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The trickplay width band a scrub preview asks for; §3 draws it a little under 320dp wide. */
private const val TRICKPLAY_TARGET_WIDTH = 320

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
@OptIn(FlowPreview::class)
class PlayerViewModel(
    private val engine: PlayerEngine,
    private val playbackResolver: PlaybackResolver,
    private val trackController: TrackController,
    private val metadataLoader: PlaybackMetadataLoader,
    private val reporter: PlaybackProgressReporter,
    private val itemId: String,
    private val startPositionTicks: Long,
    /**
     * Scope that outlives this ViewModel, used only for the final stop report and transcode
     * teardown. `viewModelScope` is cancelled by the time [onCleared] runs, so anything launched
     * there during teardown would be killed before the request left the device.
     */
    private val teardownScope: CoroutineScope,
    private val settingsStore: PlayerSettingsRepository? = null,
    private val initialQualityLabel: String? = null,
    private val audioRouteLabel: StateFlow<String> = MutableStateFlow("System default"),
    private val effectiveHdrMode: StateFlow<String> = MutableStateFlow("Automatic"),
    private val networkAvailable: StateFlow<Boolean> = MutableStateFlow(true),
    private val metricsSink: PlaybackMetricsSink? = null,
    private val userDataMutator: PlaybackUserDataMutator? = null,
) : ViewModel() {

    private data class LocalState(
        val loading: Boolean = true,
        val title: String = "",
        val error: String? = null,
        val notice: String? = null,
        val chapters: List<ChapterMark> = emptyList(),
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
        val similar: List<PlayerShelfItem> = emptyList(),
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
        val favorite: Boolean = false,
        val played: Boolean = false,
    )

    private val localState = MutableStateFlow(LocalState())

    private val sessionCoordinator = PlaybackSessionCoordinator(
        PlaybackReporter(
            delegate = reporter,
            playbackScope = viewModelScope,
            teardownScope = teardownScope,
        ),
    )
    private val queueNavigator = QueueNavigator()
    /** Bitrate used by Auto quality; explicit quality rungs always replace this with their own cap. */
    private var automaticMaxStreamingBitrate: Long = DEFAULT_MAX_STREAMING_BITRATE

    /** The item actually on screen — [itemId] only until the first [switchTo]. */
    private var currentItemId: String = itemId

    val activeItemId: String
        get() = currentItemId

    private var initialJob: Job? = null
    private var swapJob: Job? = null
    private var shelvesJob: Job? = null
    private var musicContextJob: Job? = null
    private var pausedForNetwork = false

    /** Position + duration + buffered end, grouped so the outer [combine] stays within arity. */
    private data class Timeline(val positionMs: Long, val durationMs: Long?, val bufferedMs: Long?)

    private val sampledTimeline = combine(
        engine.positionMs,
        engine.durationMs,
        engine.bufferedMs,
    ) { positionMs, durationMs, bufferedMs -> Timeline(positionMs, durationMs, bufferedMs) }
        // mpv can emit position updates much faster than a TV display can present them. Keep
        // exact values on the engine flows for seeking/reporting, but bound UI state churn.
        .sample(100)

    val timelineState: StateFlow<PlayerTimelineState> = combine(
        sampledTimeline,
        localState,
    ) { timeline, local ->
        val duration = timeline.durationMs ?: local.itemDurationMs
        PlayerTimelineState(
            positionMs = timeline.positionMs,
            durationMs = duration,
            bufferedMs = timeline.bufferedMs,
            upNextVisible = local.upNext != null &&
                !local.upNextDismissed &&
                isUpNextDue(timeline.positionMs, duration),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerTimelineState())

    private val trackGroups = engine.tracks
        .map { tracks ->
            TrackGroups(
                audio = tracks.filter { it.type == TrackType.AUDIO },
                subtitles = tracks.filter { it.type == TrackType.SUBTITLE },
                video = tracks.filter { it.type == TrackType.VIDEO },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackGroups())

    private data class TrackGroups(
        val audio: List<PlayerTrack> = emptyList(),
        val subtitles: List<PlayerTrack> = emptyList(),
        val video: List<PlayerTrack> = emptyList(),
    )

    /** Engine state + rate, grouped for the same reason as [Timeline]. */
    private data class Playback(val state: PlayerState, val speed: Float)

    private val playback = combine(engine.state, engine.speed) { state, speed -> Playback(state, speed) }

    private val baseUiState = combine(
        playback,
        engine.durationMs,
        trackGroups,
        localState,
        audioRouteLabel,
    ) { play, engineDurationMs, tracks, local, activeAudioRoute ->
        val duration = engineDurationMs ?: local.itemDurationMs
        PlayerUiState(
            loading = local.loading,
            title = local.title,
            error = local.error,
            notice = local.notice,
            state = play.state,
            durationMs = duration,
            audioTracks = tracks.audio,
            subtitleTracks = tracks.subtitles,
            videoTracks = tracks.video,
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
            activeAudioRoute = activeAudioRoute,
            displayDescription = local.displayDescription,
            favorite = local.favorite,
            played = local.played,
        )
    }

    val uiState: StateFlow<PlayerUiState> = combine(baseUiState, effectiveHdrMode) { state, hdrMode ->
        state.copy(effectiveHdrMode = hdrMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    init {
        startInitialPlayback()
        viewModelScope.launch {
            engine.state.distinctUntilChanged().collect { state ->
                metricsSink?.record(
                    PlaybackMetricsEvent(
                        itemId = currentItemId,
                        state = state.toPlaybackMetricsState(),
                        positionMs = engine.positionMs.value,
                        durationMs = engine.durationMs.value,
                        playMethod = localState.value.playMethod,
                        errorCode = (state as? PlayerState.Error)?.message,
                    ),
                )
            }
        }
        viewModelScope.launch {
            networkAvailable.collect { available ->
                if (!available) {
                    if (engine.state.value == PlayerState.Playing || engine.state.value == PlayerState.Buffering) {
                        pausedForNetwork = true
                        engine.pause()
                        localState.update { it.copy(notice = "Network connection lost; playback is paused") }
                    }
                } else if (pausedForNetwork && !sessionCoordinator.isScreenGone) {
                    pausedForNetwork = false
                    retryPlayback()
                }
            }
        }
    }

    private fun startInitialPlayback() {
        if (initialJob?.isActive == true || sessionCoordinator.isScreenGone) return
        initialJob = viewModelScope.launch {
            localState.update { it.copy(loading = true, error = null, notice = null) }
            val settings = settingsStore?.let { runCatching { it.current() }.getOrNull() }
            val preferredQuality = VideoQuality.forLabel(
                initialQualityLabel ?: settings?.preferredQuality?.label,
            )
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
            if (sessionCoordinator.isScreenGone) return@launch
            // Streaming quality is a video concern. Carrying a saved 720p/1080p preference into an
            // audio item would unnecessarily forbid direct play and can make the server transcode a
            // track that mpv could have consumed untouched.
            val quality = if (item.isAudio) {
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
                    audioStreamIndex = trackController.requestedAudioIndex,
                    subtitleStreamIndex = trackController.requestedSubtitleIndex,
                )
            ) {
                is ApiResult.Failure -> {
                    localState.update { it.copy(loading = false, error = result.error.displayMessage) }
                }
                is ApiResult.Success -> {
                    if (sessionCoordinator.rejectIfScreenGone(result.data, Ticks.toMs(startPositionTicks))) {
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
        val current = sessionCoordinator.resolved
        if (current?.playMethod == PLAY_METHOD_TRANSCODE && swapJob?.isActive == true) return
        val previousSelection = trackController.select(track)
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
                            audioStreamIndex = trackController.selectedAudioIndex,
                            subtitleStreamIndex = trackController.selectedSubtitleIndex,
                        )
                    },
                    attach = { fresh ->
                        attachStream(fresh, item = null, startMs = positionMs, keepTracks = true)
                    },
                )
                if (!changed) {
                    trackController.restore(previousSelection)
                }
            }
        } else {
            engine.selectTrack(track)
            if (current != null) {
                sessionCoordinator.reportProgressNow(
                    engine,
                    trackController.selectedAudioIndex,
                    trackController.selectedSubtitleIndex,
                )
            }
        }
    }

    fun toggleSubtitles() {
        val selected = uiState.value.subtitleTracks.firstOrNull(PlayerTrack::selected)
        val target = if (selected != null) {
            PlayerTrack(id = -1, type = TrackType.SUBTITLE, label = "Off")
        } else {
            uiState.value.subtitleTracks.firstOrNull { it.id >= 0 }
        }
        target?.let(::selectTrack)
    }

    fun toggleFavorite() {
        val mutator = userDataMutator ?: return
        val target = !localState.value.favorite
        viewModelScope.launch {
            if (mutator.setFavorite(currentItemId, target)) {
                localState.update { it.copy(favorite = target) }
            }
        }
    }

    fun togglePlayed() {
        val mutator = userDataMutator ?: return
        val target = !localState.value.played
        viewModelScope.launch {
            if (mutator.setPlayed(currentItemId, target)) {
                localState.update { it.copy(played = target) }
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
        val current = sessionCoordinator.resolved
        if (current == null) {
            sessionCoordinator.prepareRetry()
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
                        audioStreamIndex = trackController.selectedAudioIndex,
                        subtitleStreamIndex = trackController.selectedSubtitleIndex,
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
        val current = sessionCoordinator.resolved ?: return
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
                        audioStreamIndex = trackController.selectedAudioIndex,
                        subtitleStreamIndex = trackController.selectedSubtitleIndex,
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
        queueNavigator.previousEpisode(localState.value.previousEpisodeId)?.let(::switchTo)
    }

    fun playNextEpisode() {
        queueNavigator.nextEpisode(localState.value.nextEpisodeId)?.let(::switchTo)
    }

    fun playPreviousAudio() {
        queueNavigator.previousAudio(localState.value.queue)?.let(::switchTo)
    }

    fun playNextAudio() {
        queueNavigator.nextAudio(localState.value.queue)?.let(::switchTo)
    }

    fun playRandomAudio() {
        localState.value.queue
            .filterNot { it.playing }
            .randomOrNull()
            ?.itemId
            ?.let(::switchTo)
    }

    fun playFirstAudio() {
        localState.value.queue.firstOrNull()?.itemId?.let(::switchTo)
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
            var targetMetadata: PlayerItemMetadata? = null
            var startMs = 0L
            swapStream(
                positionMs = positionMs,
                resolve = {
                    val metadata = metadataLoader.loadItem(targetItemId)
                    targetMetadata = metadata
                    // Resume where the user left the target, exactly as entering it from Detail would.
                    val resumeTicks = metadata.resumeTicks
                    startMs = Ticks.toMs(resumeTicks)
                    resolveFor(targetItemId, resumeTicks, localState.value.quality)
                },
                attach = { fresh ->
                    currentItemId = targetItemId
                    // The new item owns its own shelves and its own Up Next dismissal.
                    localState.update {
                        it.copy(similar = emptyList(), shelvesLoadedFor = null, upNextDismissed = false)
                    }
                    targetMetadata?.let(::applyItemMetadata)
                    attachStream(fresh, targetMetadata, startMs, keepTracks = false)
                    targetMetadata?.let { loadPlaybackContext(targetItemId, it) }
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
        queueNavigator.upNext(localState.value.upNext)?.let(::switchTo)
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
            val similar = metadataLoader.loadSimilar(target)
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
    ): ApiResult<ResolvedPlayback> = playbackResolver.resolve(
        PlaybackResolutionRequest(
            itemId = itemId,
            startPositionTicks = startPositionTicks,
            mediaSourceId = mediaSourceId,
            audioStreamIndex = audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex,
            maxStreamingBitrate = quality.maxStreamingBitrate ?: automaticMaxStreamingBitrate,
            forceTranscode = quality.forcesTranscode,
        ),
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
                if (sessionCoordinator.rejectIfScreenGone(result.data, positionMs)) {
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
        sessionCoordinator.retire(positionMs)
    }

    /**
     * Hands a resolved stream to the engine and starts reporting for it.
     *
     * [keepTracks] preserves the current audio/subtitle selection across a quality swap; a new item
     * instead asks [TrackController] to apply the user's Jellyfin defaults.
     */
    private suspend fun attachStream(
        r: ResolvedPlayback,
        item: PlayerItemMetadata?,
        startMs: Long,
        keepTracks: Boolean,
    ) {
        trackController.prepare(r, keepCurrent = keepTracks)
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

        if (item != null) {
            localState.update {
                it.copy(
                    trickplay = metadataLoader.trickplaySource(
                        itemId = item.itemId,
                        mediaSourceId = r.mediaSourceId,
                        targetWidth = TRICKPLAY_TARGET_WIDTH,
                    ),
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
                requiresVideoSurface = item?.isAudio != true,
                preferredAudioTrackId = trackController.selectedAudioIndex,
                preferredSubtitleTrackId = trackController.selectedSubtitleIndex,
                externalSubtitles = r.externalSubtitles,
            ),
        )

        sessionCoordinator.attach(
            resolved = r,
            engine = engine,
            selectedAudioIndex = { trackController.selectedAudioIndex },
            selectedSubtitleIndex = { trackController.selectedSubtitleIndex },
        )
    }

    // --- item metadata -------------------------------------------------------------------------

    /** Fetches item detail, folds it into state, then starts the matching video/music context. */
    private suspend fun loadItemMetadata(targetItemId: String): PlayerItemMetadata {
        val metadata = metadataLoader.loadItem(targetItemId)
        applyItemMetadata(metadata)
        loadPlaybackContext(targetItemId, metadata)
        return metadata
    }

    /**
     * Folds one item's detail into state. Everything that belongs to a *specific* item — adjacency,
     * Up Next, the trickplay manifest — is cleared here rather than left standing: on an episode
     * swap the replacements arrive a moment later, and stale neighbours are worse than none.
     */
    private fun applyItemMetadata(metadata: PlayerItemMetadata) {
        localState.update {
            it.copy(
                previousEpisodeId = null,
                nextEpisodeId = null,
                upNext = null,
                postPlayEpisodes = emptyList(),
                trickplay = null,
                title = metadata.title,
                // `fields=Chapters` is requested by LibraryRepository.item(); /PlaybackInfo never
                // returns them, so this is the only place chapter data enters the player.
                chapters = metadata.chapters,
                itemDurationMs = metadata.durationMs,
                // §4 — series logo for an episode, own logo otherwise.
                logoUrl = metadata.logoUrl,
                isEpisode = metadata.isEpisode,
                seriesName = metadata.seriesName,
                seasonNumber = metadata.seasonNumber,
                episodeNumber = metadata.episodeNumber,
                year = metadata.year,
                isAudio = metadata.isAudio,
                artist = metadata.artist,
                album = metadata.album,
                albumArtworkUrl = metadata.albumArtworkUrl,
                artistArtworkUrl = null,
                queue = emptyList(),
                suggestedAudio = emptyList(),
                lyrics = emptyList(),
                lyricsSynced = false,
                musicContextLoading = metadata.isAudio,
                cast = metadata.cast,
                favorite = metadata.favorite,
                played = metadata.played,
            )
        }
    }

    private fun loadPlaybackContext(targetItemId: String, item: PlayerItemMetadata) {
        if (item.isAudio) {
            loadMusicContext(targetItemId)
        } else {
            musicContextJob?.cancel()
            viewModelScope.launch { loadAdjacency(targetItemId) }
        }
    }

    private fun loadMusicContext(targetItemId: String) {
        musicContextJob?.cancel()
        musicContextJob = viewModelScope.launch {
            val context = metadataLoader.loadMusicContext(targetItemId)
            if (targetItemId != currentItemId) return@launch
            localState.update {
                it.copy(
                    queue = context.queue,
                    suggestedAudio = context.suggested,
                    lyrics = context.lyrics,
                    lyricsSynced = context.lyricsSynced,
                    artistArtworkUrl = context.artistArtworkUrl,
                    musicContextLoading = false,
                )
            }
        }
    }

    /**
     * §5 — prev/next come from `/Shows/{seriesId}/Episodes` with no `seasonId`, so adjacency crosses
     * season boundaries the way a binge does. Movies have no neighbours and skip the request.
     */
    private suspend fun loadAdjacency(targetItemId: String) {
        val context = metadataLoader.loadEpisodeContext(targetItemId) ?: return
        // Guard against a slow adjacency response landing after the user already moved on.
        if (targetItemId != currentItemId) return
        localState.update { state ->
            state.copy(
                previousEpisodeId = context.previousEpisodeId,
                nextEpisodeId = context.nextEpisodeId,
                upNext = context.upNext,
                postPlayEpisodes = context.postPlayEpisodes,
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
        sessionCoordinator.finish(engine)
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
        metricsSink?.close()
    }
}
