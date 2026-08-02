package com.maik205.shoumeiplayer.feature.player

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.player.TrickplaySource
import com.maik205.shoumeiplayer.player.DEFAULT_MAX_STREAMING_BITRATE
import com.maik205.shoumeiplayer.player.PLAY_METHOD_TRANSCODE
import com.maik205.shoumeiplayer.player.ResolvedPlayback
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.PlayerSettingsRepository
import com.maik205.shoumeiplayer.domain.settings.ResumeBehavior
import com.maik205.shoumeiplayer.player.PlayRequest
import com.maik205.shoumeiplayer.player.PlaybackResolutionRequest
import com.maik205.shoumeiplayer.player.PlaybackResolver
import com.maik205.shoumeiplayer.player.PlaybackProgressReporter
import com.maik205.shoumeiplayer.player.PlaybackSpeed
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.PlaybackMetricsEvent
import com.maik205.shoumeiplayer.player.AudioPlaybackHandoff
import com.maik205.shoumeiplayer.player.PlaybackOwner
import com.maik205.shoumeiplayer.player.toPlaybackMetricsState
import com.maik205.shoumeiplayer.player.TrackType
import com.maik205.shoumeiplayer.player.VideoQuality
import com.maik205.shoumeiplayer.util.Ticks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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
 * item at a different bitrate cap, [switchTo] moves to an adjacent episode (docs/player-controls.md §5). Both
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
    private val userDataMutator: PlaybackUserDataMutator? = null,
    /**
     * Per-item/per-series playback memory (#85/#88/#95) — see [PlaybackPreferenceMemory]'s KDoc for
     * why this is an interface owned by `feature:player` rather than a direct `core:data`
     * dependency. `null` (the default, and what every existing caller still gets) behaves exactly
     * like a build with no memory at all: every lookup misses and every write is skipped.
     */
    private val preferenceMemory: PlaybackPreferenceMemory? = null,
    private val observability: PlayerObservabilityInputs = PlayerObservabilityInputs(),
) : ViewModel() {

    private data class LocalState(
        val loading: Boolean = true,
        val title: String = "",
        val error: PlayerMessage? = null,
        val notice: PlayerMessage? = null,
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
        val videoInfo: PlayerVideoInfo? = null,
        val audioInfo: PlayerAudioInfo? = null,
        val displayWidth: Int? = null,
        val displayHeight: Int? = null,
        val upNextDismissed: Boolean = false,
        val similar: List<PlayerShelfItem> = emptyList(),
        val cast: List<CastMemberUi> = emptyList(),
        val shelvesLoading: Boolean = false,
        val shelvesError: PlayerMessage? = null,
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
        val musicContextError: PlayerMessage? = null,
        val audioDelayMs: Long = 0,
        val subtitleDelayMs: Long = 0,
        val seekIntervalSeconds: Int = 10,
        val favorite: Boolean = false,
        val played: Boolean = false,
        val resumePrompt: ResumePromptUi? = null,
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

    private data class BufferRates(
        val readRateBytesPerSecond: Long?,
        val videoBitrateBitsPerSecond: Long?,
        val audioBitrateBitsPerSecond: Long?,
    )

    private data class BufferFlags(
        val cacheIdle: Boolean?,
        val seeking: Boolean,
        val pausedForCache: Boolean,
    )

    private data class BufferTelemetry(
        val rates: BufferRates,
        val flags: BufferFlags,
    )

    private val sampledTimeline = combine(
        engine.positionMs,
        engine.durationMs,
        engine.bufferedMs,
    ) { positionMs, durationMs, bufferedMs -> Timeline(positionMs, durationMs, bufferedMs) }
        // mpv can emit position updates much faster than a TV display can present them. Keep
        // exact values on the engine flows for seeking/reporting, but bound UI state churn.
        .sample(100)

    private val sampledBufferTelemetry = combine(
        combine(
            engine.readRateBytesPerSecond,
            engine.videoBitrateBitsPerSecond,
            engine.audioBitrateBitsPerSecond,
        ) { readRateBytesPerSecond, videoBitrateBitsPerSecond, audioBitrateBitsPerSecond ->
            BufferRates(readRateBytesPerSecond, videoBitrateBitsPerSecond, audioBitrateBitsPerSecond)
        },
        combine(
            engine.cacheIdle,
            engine.seeking,
            engine.pausedForCache,
        ) { cacheIdle, seeking, pausedForCache ->
            BufferFlags(cacheIdle, seeking, pausedForCache)
        },
    ) { rates, flags -> BufferTelemetry(rates, flags) }
        // Cache metrics are diagnostic context, not frame-by-frame playback state. Keep their
        // updates calm enough that the compact TV overlay does not churn during a rebuffer.
        .sample(250)

    val timelineState: StateFlow<PlayerTimelineState> = combine(
        sampledTimeline,
        sampledBufferTelemetry,
        localState,
    ) { timeline, telemetry, local ->
        val duration = timeline.durationMs ?: local.itemDurationMs
        PlayerTimelineState(
            positionMs = timeline.positionMs,
            durationMs = duration,
            bufferedMs = timeline.bufferedMs,
            readRateBytesPerSecond = telemetry.rates.readRateBytesPerSecond,
            videoBitrateBitsPerSecond = telemetry.rates.videoBitrateBitsPerSecond,
            audioBitrateBitsPerSecond = telemetry.rates.audioBitrateBitsPerSecond,
            cacheIdle = telemetry.flags.cacheIdle,
            seeking = telemetry.flags.seeking,
            pausedForCache = telemetry.flags.pausedForCache,
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
        observability.audioRouteLabel,
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
            shelvesError = local.shelvesError,
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
            musicContextError = local.musicContextError,
            audioDelayMs = local.audioDelayMs,
            subtitleDelayMs = local.subtitleDelayMs,
            seekIntervalSeconds = local.seekIntervalSeconds,
            playMethod = local.playMethod,
            container = local.container,
            videoInfo = local.videoInfo,
            audioInfo = local.audioInfo,
            activeAudioRoute = activeAudioRoute,
            displayWidth = local.displayWidth,
            displayHeight = local.displayHeight,
            favorite = local.favorite,
            played = local.played,
            resumePrompt = local.resumePrompt,
        )
    }

    val uiState: StateFlow<PlayerUiState> = combine(baseUiState, observability.effectiveHdrMode) { state, hdrMode ->
        state.copy(effectiveHdrMode = hdrMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    init {
        observability.playbackOwnershipCoordinator?.acquire(PlaybackOwner.VIDEO) {
            sessionCoordinator.finish(engine)
            engine.stop()
            AudioPlaybackHandoff.clear()
        }
        startInitialPlayback()
        viewModelScope.launch {
            engine.state.collect { state ->
                observability.metricsSink?.record(
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
            combine(observability.networkAvailable, observability.networkTransport) { available, transport -> available to transport }
                .distinctUntilChanged()
                .collect { (available, transport) -> observability.metricsSink?.recordNetwork(transport, available) }
        }
        viewModelScope.launch {
            engine.tracks.collect { observability.metricsSink?.recordTracks(it) }
        }
        viewModelScope.launch {
            observability.networkAvailable.collect { available ->
                if (!available) {
                    if (engine.state.value == PlayerState.Playing || engine.state.value == PlayerState.Buffering) {
                        pausedForNetwork = true
                        engine.pause()
                        localState.update {
                            it.copy(notice = PlayerMessage(PlayerMessageKind.NetworkPaused))
                        }
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
            try {
            val settings = settingsStore?.let { runCatching { it.current() }.getOrNull() }
            // §95 — an item-pinned override wins over the global default; both fall back the same
            // way when there is neither (fresh item, or no memory implementation at all).
            val itemDelays = preferenceMemory?.trackDelays(itemId)
            val itemQualityLabel = preferenceMemory?.qualityCapLabel(itemId)
            val preferredQuality = VideoQuality.forLabel(
                initialQualityLabel ?: itemQualityLabel ?: settings?.preferredQuality?.label,
            )
            val audioDelay = itemDelays?.audioDelayMs ?: settings?.audioDelayMs?.toLong() ?: 0L
            val subtitleDelay = itemDelays?.subtitleDelayMs ?: settings?.subtitleDelayMs?.toLong() ?: 0L
            val seekIntervalSeconds = settings?.seekIntervalSeconds?.coerceAtLeast(1) ?: 10
            automaticMaxStreamingBitrate = settings
                ?.maxRemoteBitrateMbps
                ?.takeIf { it > 0 }
                ?.toLong()
                ?.times(1_000_000L)
                ?: DEFAULT_MAX_STREAMING_BITRATE
            settings?.let(engine::configure)
            // §91 — Restart never honours the saved position; Resume (and the dead Always rung, see
            // ClientSettings.resumeBehavior) always does. Ask also resumes immediately — there is no
            // owned surface here to block on a user choice — but records a prompt a host screen can
            // use to offer "start over" without re-resolving. [switchTo] applies the same rule via
            // [effectiveStartTicks]/[resumePromptFor] so Up Next and the episode picker behave
            // identically to entering from Detail.
            val resumeBehavior = settings?.resumeBehavior ?: ResumeBehavior.Ask
            val effectiveStartPositionTicks = effectiveStartTicks(resumeBehavior, startPositionTicks)
            val resumePrompt = resumePromptFor(resumeBehavior, startPositionTicks)
            localState.update {
                it.copy(
                    quality = preferredQuality,
                    audioDelayMs = audioDelay,
                    subtitleDelayMs = subtitleDelay,
                    seekIntervalSeconds = seekIntervalSeconds,
                    resumePrompt = resumePrompt,
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
                    startPositionTicks = effectiveStartPositionTicks,
                    quality = quality,
                    audioStreamIndex = trackController.requestedAudioIndex,
                    subtitleStreamIndex = trackController.requestedSubtitleIndex,
                )
            ) {
                is ApiResult.Failure -> {
                    localState.update {
                        it.copy(
                            loading = false,
                            error = PlayerMessage(PlayerMessageKind.PlaybackLoadFailed),
                        )
                    }
                }
                is ApiResult.Success -> {
                    if (sessionCoordinator.rejectIfScreenGone(result.data, Ticks.toMs(effectiveStartPositionTicks))) {
                        return@launch
                    }
                    localState.update { it.copy(loading = false, error = null) }
                    attachStream(
                        result.data,
                        item,
                        Ticks.toMs(effectiveStartPositionTicks),
                        keepTracks = false,
                        rememberedAudioIndex = rememberedAudioIndexFor(item, settings),
                        restoreSpeed = rememberedSpeedFor(itemId, settings),
                    )
                }
            }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                localState.update {
                    it.copy(
                        loading = false,
                        error = PlayerMessage(
                            kind = PlayerMessageKind.MetadataLoadFailed,
                            detail = error.message,
                        ),
                    )
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
                } else if (track.type == TrackType.AUDIO) {
                    // Already inside a coroutine, so no extra launch needed here.
                    rememberSeriesAudioChoice(track.id)
                }
            }
        } else {
            engine.selectTrack(track)
            if (track.type == TrackType.AUDIO) {
                viewModelScope.launch { rememberSeriesAudioChoice(track.id) }
            }
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
            PlayerTrack(id = -1, type = TrackType.SUBTITLE, label = "")
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

    /**
     * §5 — 0.5×…2×. Purely an engine-side rate change; the stream is untouched.
     *
     * §88 `rememberPlaybackSpeed` — persisted **per item** via [preferenceMemory] (see
     * [PlaybackPreferenceMemory]'s KDoc). The backing store's speed slot is keyed per item, not
     * per account or per series, so returning to the exact title you sped up keeps that pace; a
     * sibling episode starts over at the ordinary default. [startInitialPlayback] and [switchTo]
     * are the only readers.
     */
    fun setSpeed(speed: Float) {
        val clamped = PlaybackSpeed.clamp(speed)
        engine.setSpeed(clamped)
        persistPlaybackSpeed(clamped)
    }

    private fun persistPlaybackSpeed(speed: Float) {
        val memory = preferenceMemory ?: return
        val target = currentItemId
        viewModelScope.launch {
            val settings = settingsStore?.let { runCatching { it.current() }.getOrNull() }
            if (settings?.rememberPlaybackSpeed != true) return@launch
            memory.setPlaybackSpeed(target, speed)
        }
    }

    /**
     * §95 — A/V sync nudges made mid-playback are almost always compensating for one badly-muxed
     * file, not a statement about every future title. Persisted **per item** via [preferenceMemory]
     * rather than through [settingsStore]: the correction reapplies the next time *this exact item*
     * is opened (see [startInitialPlayback] / [switchTo], the only readers of the persisted value)
     * but never becomes a permanent global offset and never leaks onto a sibling episode. The
     * Settings screen remains the lone writer of the persisted global default.
     */
    fun setAudioDelayMs(value: Long) {
        localState.update { it.copy(audioDelayMs = value) }
        engine.setAudioDelayMs(value)
        persistTrackDelays()
    }

    /** §95 — item-scoped for the same reason as [setAudioDelayMs]. */
    fun setSubtitleDelayMs(value: Long) {
        localState.update { it.copy(subtitleDelayMs = value) }
        engine.setSubtitleDelayMs(value)
        persistTrackDelays()
    }

    /**
     * Writes the *current, effective* audio+subtitle pair for [currentItemId] — reading
     * [localState] rather than just the field that just changed is what keeps a lone audio nudge
     * from clobbering an already-in-effect subtitle delay (or vice versa) in the stored row.
     */
    private fun persistTrackDelays() {
        val memory = preferenceMemory ?: return
        val target = currentItemId
        val local = localState.value
        viewModelScope.launch {
            memory.setTrackDelays(
                target,
                ItemTrackDelays(audioDelayMs = local.audioDelayMs, subtitleDelayMs = local.subtitleDelayMs),
            )
        }
    }

    /**
     * §91 — lets a host screen offer "start over" after [ResumeBehavior.Ask] resumed automatically.
     * [restart] jumps the already-playing stream back to the beginning instead of re-resolving, since
     * the stream itself does not need to change, only the playhead.
     */
    fun confirmResumePrompt(restart: Boolean) {
        if (localState.value.resumePrompt == null) return
        localState.update { it.copy(resumePrompt = null) }
        if (restart) {
            engine.seekTo(0)
        }
    }

    /** §91 — Restart discards the saved position; Resume and the dead Always rung keep it. */
    private fun effectiveStartTicks(resumeBehavior: ResumeBehavior, rawResumeTicks: Long): Long =
        if (resumeBehavior == ResumeBehavior.Restart) 0L else rawResumeTicks

    /**
     * §91 — Ask still resumes immediately (there is no owned surface here to block on a user
     * choice), but records a prompt a host screen can use to offer "start over" without
     * re-resolving. Shared by [startInitialPlayback] and [switchTo] so Up Next / the episode
     * picker match entering the same item from Detail.
     */
    private fun resumePromptFor(resumeBehavior: ResumeBehavior, rawResumeTicks: Long): ResumePromptUi? =
        if (resumeBehavior == ResumeBehavior.Ask && rawResumeTicks > 0L) {
            ResumePromptUi(positionMs = Ticks.toMs(rawResumeTicks))
        } else {
            null
        }

    fun setFrameMode(value: String) = engine.setFrameMode(value)

    fun setHdrMode(value: String) = engine.setHdrMode(value)

    fun setDeinterlaceMode(value: String) = engine.setDeinterlaceMode(value)

    /**
     * §95 — clears the session state *and* the persisted per-item override in one write, so the
     * item falls back to the global default the next time it is opened rather than reapplying a
     * nudge the viewer just asked to discard. Deliberately does not call [setAudioDelayMs] /
     * [setSubtitleDelayMs]: those each persist the pair on their own, which would leave a `(0, 0)`
     * row behind instead of clearing it — an item with no override and an item explicitly pinned to
     * "no delay" read back identically today, but only clearing avoids relying on that coincidence.
     */
    fun resetPlaybackDelays() {
        localState.update { it.copy(audioDelayMs = 0, subtitleDelayMs = 0) }
        engine.setAudioDelayMs(0)
        engine.setSubtitleDelayMs(0)
        val memory = preferenceMemory ?: return
        val target = currentItemId
        viewModelScope.launch { memory.setTrackDelays(target, null) }
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
                // §95 — item-scoped, same rationale as setAudioDelayMs: a cap chosen for one stream
                // on a weak connection reapplies only to *this* item (see startInitialPlayback /
                // switchTo, the only readers), never as a change to the global default a sibling
                // episode would also pick up. Only the Settings screen persists the global default.
                localState.update { it.copy(quality = quality) }
                preferenceMemory?.setQualityCapLabel(
                    current.itemId,
                    quality.takeIf { it != VideoQuality.AUTO }?.label,
                )
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
     * Swaps the player over to [targetItemId] without leaving the screen (docs/player-controls.md §5): the
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
            // §91 — a prompt referring to the outgoing item must not survive the switch; the target
            // gets its own prompt (or none) once resolved below, via `attach`.
            localState.update { it.copy(resumePrompt = null) }
            // Resolved once per swap so a mid-swap Settings change can't make start position and
            // prompt disagree with each other.
            val settings = settingsStore?.let { runCatching { it.current() }.getOrNull() }
            val resumeBehavior = settings?.resumeBehavior ?: ResumeBehavior.Ask
            // §95 — the *target's own* pinned overrides, never the outgoing item's live session
            // values: a quality cap or A/V nudge made on item A must not leak onto item B just
            // because B happened to be the next thing played.
            val targetQuality = VideoQuality.forLabel(
                preferenceMemory?.qualityCapLabel(targetItemId) ?: settings?.preferredQuality?.label,
            )
            val targetDelays = preferenceMemory?.trackDelays(targetItemId)
            val targetAudioDelay = targetDelays?.audioDelayMs ?: settings?.audioDelayMs?.toLong() ?: 0L
            val targetSubtitleDelay = targetDelays?.subtitleDelayMs ?: settings?.subtitleDelayMs?.toLong() ?: 0L
            swapStream(
                positionMs = positionMs,
                resolve = {
                    val metadata = metadataLoader.loadItem(targetItemId)
                    targetMetadata = metadata
                    // §91 — apply the same resume setting Detail would, exactly as startInitialPlayback does.
                    val rawResumeTicks = metadata.resumeTicks
                    val resumeTicks = effectiveStartTicks(resumeBehavior, rawResumeTicks)
                    startMs = Ticks.toMs(resumeTicks)
                    resolveFor(targetItemId, resumeTicks, targetQuality)
                },
                attach = { fresh ->
                    currentItemId = targetItemId
                    // The new item owns its own shelves, its own Up Next dismissal, its own resume
                    // prompt (Ask records one; Resume/Restart carry none), and — per §95 above — its
                    // own quality/delay overrides rather than whatever the outgoing item left active.
                    localState.update {
                        it.copy(
                            similar = emptyList(),
                            shelvesLoadedFor = null,
                            shelvesError = null,
                            upNextDismissed = false,
                            resumePrompt = resumePromptFor(resumeBehavior, targetMetadata?.resumeTicks ?: 0L),
                            quality = targetQuality,
                            audioDelayMs = targetAudioDelay,
                            subtitleDelayMs = targetSubtitleDelay,
                        )
                    }
                    engine.setAudioDelayMs(targetAudioDelay)
                    engine.setSubtitleDelayMs(targetSubtitleDelay)
                    targetMetadata?.let(::applyItemMetadata)
                    attachStream(
                        fresh,
                        targetMetadata,
                        startMs,
                        keepTracks = false,
                        rememberedAudioIndex = rememberedAudioIndexFor(targetMetadata, settings),
                        restoreSpeed = rememberedSpeedFor(targetItemId, settings),
                    )
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
            localState.update { it.copy(shelvesLoading = true, shelvesError = null) }
            try {
                val similar = metadataLoader.loadSimilar(target)
                localState.update {
                    if (target != currentItemId) {
                        it.copy(shelvesLoading = false)
                    } else {
                        it.copy(
                            similar = similar,
                            shelvesLoading = false,
                            shelvesLoadedFor = target,
                            shelvesError = null,
                        )
                    }
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                localState.update {
                    it.copy(
                        shelvesLoading = false,
                        shelvesError = PlayerMessage(
                            kind = PlayerMessageKind.ShelvesLoadFailed,
                            detail = error.message,
                        ),
                    )
                }
            }
        }
    }

    fun retryMusicContext() {
        if (!localState.value.isAudio) return
        localState.update {
            it.copy(
                musicContextLoading = true,
                musicContextError = null,
            )
        }
        loadMusicContext(currentItemId)
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
        return try {
            when (val result = resolve()) {
                is ApiResult.Failure -> {
                    localState.update {
                        it.copy(
                            swapping = false,
                            notice = PlayerMessage(PlayerMessageKind.StreamSwapFailed),
                        )
                    }
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
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Throwable) {
            localState.update {
                it.copy(
                    swapping = false,
                    notice = PlayerMessage(PlayerMessageKind.StreamSwapFailed),
                )
            }
            false
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
        rememberedAudioIndex: Int? = null,
        restoreSpeed: Float? = null,
    ) {
        trackController.prepare(r, keepCurrent = keepTracks, rememberedAudioIndex = rememberedAudioIndex)
        if (!keepTracks) {
            // §88 rememberPlaybackSpeed — every genuinely new item (not a same-item quality swap,
            // which must leave the in-flight rate alone) gets its own remembered rate applied
            // explicitly. mpv's `speed` property survives `loadfile` on its own (see
            // MpvEngine.setSpeed), so without this an episode swap would silently inherit whatever
            // rate the *previous* item was playing at instead of this item's own memory/default.
            engine.setSpeed(restoreSpeed ?: PlaybackSpeed.Normal)
        }
        val videoStream = r.mediaStreams.firstOrNull { it.type.equals("Video", ignoreCase = true) }
        val audioStream = r.mediaStreams.firstOrNull { it.type.equals("Audio", ignoreCase = true) }
        val videoInfo = videoStream?.let { stream ->
            PlayerVideoInfo(
                codec = stream.codec?.uppercase(),
                width = stream.width,
                height = stream.height,
            ).takeIf { it.codec != null || (it.width != null && it.height != null) }
        }
        val audioInfo = audioStream?.let { stream ->
            PlayerAudioInfo(
                codec = stream.codec?.uppercase(),
                channels = stream.channels,
                language = stream.language,
            ).takeIf { it.codec != null || it.channels != null || it.language != null }
        }
        localState.update {
            it.copy(
                playMethod = r.playMethod,
                container = r.streamUrl
                    .substringBefore('?')
                    .substringAfterLast('.', missingDelimiterValue = "")
                    .uppercase()
                    .takeIf(String::isNotBlank),
                videoInfo = videoInfo,
                audioInfo = audioInfo,
                displayWidth = videoStream?.width,
                displayHeight = videoStream?.height,
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

        if (observability.backgroundAudio && item?.isAudio == true) {
            AudioPlaybackHandoff.offerResolved(null, r)
        }
        engine.load(
            PlayRequest(
                itemId = r.itemId,
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

        if (!observability.backgroundAudio || item?.isAudio != true) {
            sessionCoordinator.attach(
                resolved = r,
                engine = engine,
                selectedAudioIndex = { trackController.selectedAudioIndex },
                selectedSubtitleIndex = { trackController.selectedSubtitleIndex },
            )
        }
    }

    /**
     * §88 `rememberSeriesAudio` — the audio index to prefer as the *default* for [item], or `null`
     * when there is no opinion (movie/non-episode, the setting is off, nothing on record, or this
     * ViewModel was built without a [preferenceMemory]).
     *
     * Deliberately does not decide precedence itself — [TrackController.prepare] is the single place
     * that reconciles this against an explicit per-item request and [TrackSelection]'s own
     * server-preference default, so there is exactly one policy for "what wins" instead of two
     * copies that could disagree.
     */
    private suspend fun rememberedAudioIndexFor(
        item: PlayerItemMetadata?,
        settings: ClientSettings?,
    ): Int? {
        if (item?.isEpisode != true) return null
        if (settings?.rememberSeriesAudio != true) return null
        val seriesId = item.seriesName ?: return null
        return preferenceMemory?.seriesAudioTrack(seriesId)
    }

    /** §88 `rememberPlaybackSpeed` — the rate to restore for [itemId], or `null` for "no opinion". */
    private suspend fun rememberedSpeedFor(itemId: String, settings: ClientSettings?): Float? {
        if (settings?.rememberPlaybackSpeed != true) return null
        return preferenceMemory?.playbackSpeed(itemId)
    }

    /**
     * §88 `rememberSeriesAudio` — persists an *explicit* audio pick against the current series, so a
     * later episode's default can adopt it (via [rememberedAudioIndexFor]). Only ever called after a
     * pick has actually taken effect (see [selectTrack]): a track [TrackController.prepare] merely
     * defaulted to on its own is never written back here, so a series nobody has touched keeps
     * following the ordinary language/server-default logic in `TrackSelection` untouched.
     *
     * Keyed by series *name* rather than a true series id: [PlayerItemMetadata] does not carry a
     * series id through to this layer today, and adding one is outside this file's ownership. Two
     * differently-produced shows that happen to share an exact title would share this memory, which
     * is an accepted, documented trade-off rather than an oversight.
     */
    private suspend fun rememberSeriesAudioChoice(audioIndex: Int) {
        val memory = preferenceMemory ?: return
        val local = localState.value
        if (!local.isEpisode) return
        val seriesId = local.seriesName ?: return
        val settings = settingsStore?.let { runCatching { it.current() }.getOrNull() }
        if (settings?.rememberSeriesAudio != true) return
        memory.setSeriesAudioTrack(seriesId, audioIndex)
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
                musicContextError = null,
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
            try {
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
                        musicContextError = null,
                    )
                }
                engine.setQueue(context.queue.map(AudioQueueItemUi::itemId), targetItemId)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (targetItemId == currentItemId) {
                    localState.update {
                        it.copy(
                            musicContextLoading = false,
                            musicContextError = PlayerMessage(
                                kind = PlayerMessageKind.MusicContextLoadFailed,
                                detail = error.message,
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * §5 — prev/next come from `/Shows/{seriesId}/Episodes` with no `seasonId`, so adjacency crosses
     * season boundaries the way a binge does. Movies have no neighbours and skip the request.
     */
    private suspend fun loadAdjacency(targetItemId: String) {
        try {
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
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (targetItemId == currentItemId) {
                localState.update {
                    it.copy(
                        notice = PlayerMessage(
                            kind = PlayerMessageKind.AdjacencyLoadFailed,
                            detail = error.message,
                        ),
                    )
                }
            }
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
        observability.playbackOwnershipCoordinator?.release(PlaybackOwner.VIDEO)
        if (!observability.backgroundAudio || !localState.value.isAudio) {
            finishPlayback()
            engine.stop()
        }
        observability.metricsSink?.close()
    }
}
