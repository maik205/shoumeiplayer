package com.maik205.shoumeiplayer.player

import android.content.Context
import android.util.Log
import android.view.Surface
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val TAG = "MpvEngine"

/**
 * Real [PlayerEngine] backed by libmpv (`dev.jdtech.mpv:libmpv`, the prebuilt
 * AAR Findroid ships). Option set follows Findroid's `MPVPlayer` so behaviour
 * on Android TV hardware matches a known-good client.
 *
 * MPVLib callbacks arrive on mpv's own thread; every field written from them is
 * either a [MutableStateFlow] (thread-safe) or `@Volatile`, and no view is ever
 * touched from here.
 */
class MpvEngine(context: Context) : PlayerEngine, MPVLib.EventObserver, MPVLib.LogObserver {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow<Long?>(null)
    override val durationMs: StateFlow<Long?> = _durationMs.asStateFlow()

    private val _bufferedMs = MutableStateFlow<Long?>(null)
    override val bufferedMs: StateFlow<Long?> = _bufferedMs.asStateFlow()

    private val _tracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val tracks: StateFlow<List<PlayerTrack>> = _tracks.asStateFlow()

    private val _speed = MutableStateFlow(PlaybackSpeed.Normal)
    override val speed: StateFlow<Float> = _speed.asStateFlow()

    @Volatile private var released = false
    @Volatile private var surfaceAttached = false
    @Volatile private var hasRequest = false
    @Volatile private var fileLoaded = false
    @Volatile private var lastErrorMessage: String? = null
    @Volatile private var pendingAudioTrackId: Int? = null
    @Volatile private var pendingSubtitleTrackId: Int? = null

    /** Last parsed `track-list`, plus the mpv-id → Jellyfin-index mapping for sideloaded subs. */
    @Volatile private var mpvTracks: List<MpvTrack> = emptyList()
    @Volatile private var externalIndexByMpvId: Map<Int, Int> = emptyMap()
    @Volatile private var pendingExternalSubtitles: List<ExternalSubtitle> = emptyList()

    @Volatile private var corePaused = false
    @Volatile private var coreIdle = true
    @Volatile private var pausedForCache = false
    @Volatile private var eofReached = false
    @Volatile private var seeking = false

    init {
        MPVLib.create(context.applicationContext)
        // Findroid MPVPlayer option set (player/local/.../mpv/MPVPlayer.kt).
        MPVLib.setOptionString("config", "no")
        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("vo", "gpu")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("ao", "audiotrack")
        MPVLib.setOptionString("audio-set-media-role", "yes")
        // mediacodec-copy is the TV-safe variant: it survives surface loss and
        // keeps subtitle/filter paths working where direct rendering does not.
        MPVLib.setOptionString("hwdec", "mediacodec-copy")
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("tls-verify", "no")
        // --- network stream cache -------------------------------------------------
        // Jellyfin streams are remote HTTP; without a real cache every hiccup on the LAN
        // becomes a visible stall. The values below are the Findroid-era defaults.
        // cache: enable the stream cache for network sources (mpv disables it for some).
        MPVLib.setOptionString("cache", "yes")
        // cache-secs: how much decoded-ahead material the cache is allowed to keep; 30s is
        // enough to ride out a Wi-Fi dropout without hoarding memory on a TV box.
        MPVLib.setOptionString("cache-secs", "30")
        // demuxer-readahead-secs: how far the demuxer reads past the playhead. 20s keeps the
        // buffered bar meaningfully ahead while staying under demuxer-max-bytes for HD video.
        MPVLib.setOptionString("demuxer-readahead-secs", "20")
        // demuxer-max-bytes: hard ceiling on the forward cache — 64MiB is the practical cap
        // for an Android TV heap and comfortably holds the 20s readahead above.
        MPVLib.setOptionString("demuxer-max-bytes", "64MiB")
        // demuxer-max-back-bytes: backwards cache, so short back-seeks are instant instead of
        // forcing a new HTTP range request.
        MPVLib.setOptionString("demuxer-max-back-bytes", "32MiB")
        // cache-pause-initial: hold playback until the cache has filled once, so the first
        // frame is followed by continuous video instead of an immediate re-buffer.
        MPVLib.setOptionString("cache-pause-initial", "yes")
        // cache-pause-wait: seconds of data to accumulate before resuming after an underrun.
        // Small (1s) so a brief stall does not turn into a long visible freeze.
        MPVLib.setOptionString("cache-pause-wait", "1")
        // network-timeout: give up on a dead connection after 15s instead of hanging forever;
        // the resulting end-file surfaces as PlayerState.Error.
        MPVLib.setOptionString("network-timeout", "15")
        // stream-buffer-size is deliberately left at mpv's default — raising it only adds
        // latency in front of the demuxer cache that is already sized above.
        MPVLib.setOptionString("sub-scale-with-window", "yes")
        MPVLib.setOptionString("sub-use-margins", "no")
        MPVLib.setOptionString("save-position-on-quit", "no")
        MPVLib.setOptionString("ytdl", "no")
        MPVLib.setOptionString("idle", "yes")
        // keep-open so the Ended state is observable instead of mpv going idle.
        MPVLib.setOptionString("keep-open", "yes")
        // No window until a Surface is attached.
        MPVLib.setOptionString("force-window", "no")
        MPVLib.init()
        MPVLib.addObserver(this)
        MPVLib.addLogObserver(this)
        // DOUBLE rather than INT64: an integer-seconds position makes the seek bar and the
        // timecode advance in visible 1s jumps and rounds away accurate resume points.
        MPVLib.observeProperty("time-pos", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("pause", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("core-idle", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("paused-for-cache", MPVLib.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MPVLib.MPV_FORMAT_FLAG)
        // `seeking` flips true while mpv refills after a seek: surfaced as Buffering so the OSD
        // shows motion instead of a frozen Playing state.
        MPVLib.observeProperty("seeking", MPVLib.MPV_FORMAT_FLAG)
        // demuxer-cache-time is the absolute playback timestamp (seconds) the cache reaches.
        MPVLib.observeProperty("demuxer-cache-time", MPVLib.MPV_FORMAT_DOUBLE)
        // mpv can refuse or round a rate change (and keeps `speed` across loadfile), so the flow is
        // driven by what mpv reports rather than by what was asked for.
        MPVLib.observeProperty("speed", MPVLib.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("track-list", MPVLib.MPV_FORMAT_NONE)
    }

    override fun setSurface(surface: Surface?) {
        if (released) return
        if (surface != null) {
            MPVLib.attachSurface(surface)
            MPVLib.setOptionString("force-window", "yes")
            MPVLib.setOptionString("vo", "gpu")
            surfaceAttached = true
        } else if (surfaceAttached) {
            // Must detach before the surface is destroyed.
            MPVLib.setOptionString("vo", "null")
            MPVLib.setOptionString("force-window", "no")
            MPVLib.detachSurface()
            surfaceAttached = false
        }
    }

    /**
     * mpv sizes its output from `android-surface-size`, not from the Surface itself. Without this
     * the video is scaled against a stale size after any layout change and can render letterboxed
     * into the wrong rectangle.
     */
    override fun setSurfaceSize(width: Int, height: Int) {
        if (released || width <= 0 || height <= 0) return
        MPVLib.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun load(item: PlayRequest) {
        if (released) return
        hasRequest = true
        fileLoaded = false
        eofReached = false
        seeking = false
        lastErrorMessage = null
        pendingAudioTrackId = item.preferredAudioTrackId
        pendingSubtitleTrackId = item.preferredSubtitleTrackId
        pendingExternalSubtitles = item.externalSubtitles
        mpvTracks = emptyList()
        externalIndexByMpvId = emptyMap()
        _tracks.value = emptyList()
        _bufferedMs.value = null
        _positionMs.value = item.startPositionMs
        _durationMs.value = item.durationMs
        _state.value = PlayerState.Loading
        // http-header-fields is parsed as a comma-separated list, and Jellyfin's
        // MediaBrowser Authorization value contains commas — joining would split it
        // into malformed header lines and the server answers 400. change-list
        // appends each header as one atomic list entry instead.
        MPVLib.command(arrayOf("change-list", "http-header-fields", "clr", ""))
        item.headers.forEach { (key, value) ->
            MPVLib.command(arrayOf("change-list", "http-header-fields", "append", "$key: $value"))
        }
        MPVLib.setOptionString(
            "start",
            if (item.startPositionMs > 0) {
                String.format(Locale.ROOT, "+%.3f", item.startPositionMs / 1000.0)
            } else {
                "none"
            },
        )
        MPVLib.setPropertyBoolean("pause", false)
        MPVLib.command(arrayOf("loadfile", item.url))
    }

    override fun play() {
        if (!released) MPVLib.setPropertyBoolean("pause", false)
    }

    override fun pause() {
        if (!released) MPVLib.setPropertyBoolean("pause", true)
    }

    override fun seekTo(ms: Long) {
        if (released) return
        val target = ms.coerceAtLeast(0)
        _positionMs.value = target
        // Enter Buffering immediately; the `seeking` property event clears it once mpv has refilled.
        seeking = true
        updateState()
        MPVLib.command(
            arrayOf("seek", String.format(Locale.ROOT, "%.3f", target / 1000.0), "absolute"),
        )
    }

    /**
     * mpv's `speed` property is a plain rate multiplier and applies to the whole core, so it
     * survives the `loadfile` a quality/episode swap issues. Audio pitch stays corrected because
     * mpv's default `audio-pitch-correction` is on.
     */
    override fun setSpeed(speed: Float) {
        if (released) return
        val clamped = PlaybackSpeed.clamp(speed)
        MPVLib.setPropertyDouble("speed", clamped.toDouble())
        // Optimistic; the observed `speed` property corrects this if mpv lands somewhere else.
        _speed.value = clamped
    }

    /**
     * [PlayerTrack.id] is a Jellyfin `MediaStream.Index`; mpv addresses tracks by its own per-type
     * id, so the index is translated before it reaches `aid`/`sid`.
     */
    override fun selectTrack(track: PlayerTrack) {
        if (released) return
        val property = if (track.type == TrackType.AUDIO) "aid" else "sid"
        val value = if (track.id < 0) {
            "no"
        } else {
            val mpvId = MpvTrackList.mpvIdFor(mpvTracks, externalIndexByMpvId, track.type, track.id)
            if (mpvId == null) {
                Log.w(TAG, "no mpv track for ${track.type} stream index ${track.id}")
                return
            }
            mpvId.toString()
        }
        MPVLib.setPropertyString(property, value)
        _tracks.value = _tracks.value.map { existing ->
            if (existing.type != track.type) existing else existing.copy(selected = existing.id == track.id)
        }
    }

    override fun stop() {
        if (released) return
        hasRequest = false
        fileLoaded = false
        eofReached = false
        seeking = false
        MPVLib.command(arrayOf("stop"))
        mpvTracks = emptyList()
        externalIndexByMpvId = emptyMap()
        pendingExternalSubtitles = emptyList()
        _positionMs.value = 0
        _bufferedMs.value = null
        _state.value = PlayerState.Idle
    }

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        MPVLib.removeObserver(this)
        MPVLib.removeLogObserver(this)
        if (surfaceAttached) {
            MPVLib.detachSurface()
            surfaceAttached = false
        }
        MPVLib.destroy()
    }

    // --- MPVLib.EventObserver (mpv thread) ------------------------------------

    override fun eventProperty(property: String) {
        if (property == "track-list") refreshTracks()
    }

    // Retained as a fallback: time-pos/duration are observed as DOUBLE above, but mpv falls back to
    // an integer format for some sources rather than dropping the notification.
    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "time-pos" -> _positionMs.value = value * 1000
            "duration" -> _durationMs.value = if (value > 0) value * 1000 else null
        }
    }

    override fun eventProperty(property: String, value: Double) {
        if (!value.isFinite()) return
        when (property) {
            "time-pos" -> _positionMs.value = (value * 1000).toLong().coerceAtLeast(0)
            "duration" -> _durationMs.value = (value * 1000).toLong().takeIf { it > 0 }
            // Negative means "cache empty or unknown" — report null rather than a bogus 0.
            "demuxer-cache-time" -> _bufferedMs.value = if (value >= 0) (value * 1000).toLong() else null
            "speed" -> if (value > 0) _speed.value = value.toFloat()
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "pause" -> corePaused = value
            "core-idle" -> coreIdle = value
            "paused-for-cache" -> pausedForCache = value
            "eof-reached" -> eofReached = value
            "seeking" -> seeking = value
            else -> return
        }
        updateState()
    }

    override fun eventProperty(property: String, value: String) {
        if (property == "track-list") refreshTracks()
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MPVLib.MPV_EVENT_FILE_LOADED -> {
                fileLoaded = true
                eofReached = false
                // Order matters: the sideloaded files have to exist in track-list before the map is
                // built, and the map has to exist before a Jellyfin index can be turned into an aid/sid.
                addExternalSubtitles()
                refreshTracks()
                applyPreferredTracks()
                refreshTracks()
                updateState()
            }
            // mpv confirms a seek started, and confirms playback actually resumed afterwards. These
            // bracket the refill far more reliably than the `seeking` property alone, which can
            // settle before the first frame is decoded.
            MPVLib.MPV_EVENT_SEEK -> {
                seeking = true
                updateState()
            }
            MPVLib.MPV_EVENT_PLAYBACK_RESTART -> {
                seeking = false
                updateState()
            }
            MPVLib.MPV_EVENT_END_FILE -> {
                // This binding does not expose the end-file reason, so a file
                // that ends without ever having loaded is treated as an error.
                if (hasRequest && !fileLoaded) {
                    _state.value = PlayerState.Error(lastErrorMessage ?: "mpv could not open the stream")
                } else if (fileLoaded) {
                    eofReached = true
                    updateState()
                }
            }
            MPVLib.MPV_EVENT_SHUTDOWN -> {
                hasRequest = false
                _state.value = PlayerState.Idle
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (level <= MPVLib.MPV_LOG_LEVEL_ERROR && level != MPVLib.MPV_LOG_LEVEL_NONE) {
            val message = text.trim()
            if (message.isNotEmpty()) {
                lastErrorMessage = message
                Log.e(TAG, "[$prefix] $message")
            }
        }
    }

    // --- internals ------------------------------------------------------------

    private fun updateState() {
        if (released || !hasRequest) return
        if (_state.value is PlayerState.Error) return
        _state.value = when {
            !fileLoaded -> PlayerState.Loading
            eofReached -> PlayerState.Ended
            // Cache underrun and post-seek refill both mean "waiting on data", even while the
            // user has playback nominally running.
            pausedForCache || seeking -> PlayerState.Buffering
            corePaused -> PlayerState.Paused
            // core-idle without an explicit pause is mpv waiting on the initial fill.
            coreIdle -> PlayerState.Buffering
            else -> PlayerState.Playing
        }
    }

    /**
     * Applies the Jellyfin-chosen defaults once, on first load. Indices that this source does not
     * actually contain (a transcode can drop streams) are left alone so mpv keeps its own default
     * rather than being switched to nothing.
     */
    private fun applyPreferredTracks() {
        pendingAudioTrackId?.let { index ->
            MpvTrackList.mpvIdFor(mpvTracks, externalIndexByMpvId, TrackType.AUDIO, index)
                ?.let { MPVLib.setPropertyString("aid", it.toString()) }
                ?: Log.w(TAG, "preferred audio stream $index not present in track-list")
        }
        pendingSubtitleTrackId?.let { index ->
            if (index < 0) {
                MPVLib.setPropertyString("sid", "no")
            } else {
                MpvTrackList.mpvIdFor(mpvTracks, externalIndexByMpvId, TrackType.SUBTITLE, index)
                    ?.let { MPVLib.setPropertyString("sid", it.toString()) }
                    ?: Log.w(TAG, "preferred subtitle stream $index not present in track-list")
            }
        }
        pendingAudioTrackId = null
        pendingSubtitleTrackId = null
    }

    /**
     * Attaches subtitles the server delivers as separate files. `auto` rather than `select` so a
     * sideloaded file does not override the selection [applyPreferredTracks] is about to make.
     */
    private fun addExternalSubtitles() {
        pendingExternalSubtitles.forEach { subtitle ->
            MPVLib.command(
                arrayOf("sub-add", subtitle.url, "auto", subtitle.title, subtitle.language.orEmpty()),
            )
        }
    }

    private fun refreshTracks() {
        val json = runCatching { MPVLib.getPropertyString("track-list") }.getOrNull() ?: return
        val parsed = runCatching { MpvTrackList.parse(json) }.getOrElse {
            Log.w(TAG, "track-list parse failed", it)
            return
        }
        mpvTracks = parsed
        externalIndexByMpvId = MpvTrackList.externalIndexByMpvId(parsed, pendingExternalSubtitles)
        _tracks.value = MpvTrackList.toPlayerTracks(parsed, externalIndexByMpvId)
    }
}
