package com.maik205.shoumeiplayer.player

import android.content.Context
import android.app.ActivityManager
import android.util.Log
import android.view.Surface
import com.maik205.mpvroid.MpvNative
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.DeinterlaceMode
import com.maik205.shoumeiplayer.domain.settings.HardwareDecoding
import com.maik205.shoumeiplayer.domain.settings.HdrMode
import com.maik205.shoumeiplayer.domain.settings.RenderingProfile
import com.maik205.shoumeiplayer.domain.settings.SubtitleColor
import com.maik205.shoumeiplayer.domain.settings.SubtitleStroke
import com.maik205.shoumeiplayer.domain.settings.ToneMapping
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import kotlin.math.min

private const val TAG = "MpvEngine"

/**
 * Real [PlayerEngine] backed directly by mpv v0.41.0 through the app-owned
 * [MpvNative] JNI bridge. The option set follows the established Android
 * configuration so behaviour remains suitable for Android TV hardware.
 *
 * MpvNative callbacks arrive on mpv's own thread; every field written from them is
 * either a [MutableStateFlow] (thread-safe) or `@Volatile`, and no view is ever
 * touched from here.
 */
internal class MpvEngine(context: Context) : PlayerEngine, MpvNative.EventObserver, MpvNative.LogObserver {

    private val memoryBudgetMiB: Int = run {
        val activityManager = context.applicationContext
            .getSystemService(ActivityManager::class.java)
        // Reserve most of the heap for decoded artwork, Compose, and Jellyfin responses. The
        // stream cache is a resilience budget, not a reason to pressure the entire TV process.
        ((activityManager?.memoryClass ?: 256) * 0.20f).toInt().coerceIn(32, 256)
    }

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

    private val _videoFps = MutableStateFlow<Double?>(null)
    override val videoFps: StateFlow<Double?> = _videoFps.asStateFlow()

    @Volatile private var released = false
    @Volatile private var surfaceAttached = false
    @Volatile private var pendingLoad: PlayRequest? = null
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
    private var systemCaBundlePath: String? = null

    init {
        if (!MpvNative.create(context.applicationContext)) {
            _state.value = PlayerState.Error("mpv failed to initialize (mpv_create)")
        }
        // Findroid MPVPlayer option set (player/local/.../mpv/MPVPlayer.kt).
        setOption("config", "no")
        setOption("profile", "fast")
        setOption("vo", "gpu")
        setOption("gpu-context", "android")
        setOption("opengl-es", "yes")
        setOption("ao", "audiotrack")
        setOption("audio-set-media-role", "yes")
        // mediacodec-copy is the TV-safe variant: it survives surface loss and
        // keeps subtitle/filter paths working where direct rendering does not.
        setOption("hwdec", "mediacodec-copy")
        setOption("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        systemCaBundlePath = run {
            // The app's own HTTPS trust (Ktor/OkHttp, via network_security_config.xml) includes
            // user-installed CAs, but mpv gets its trust anchors from a plain file it reads
            // itself. Scanning /system/etc/security/cacerts alone misses anything the user
            // installed, so those are appended from AndroidCAStore -- the same trust source
            // Android's own HTTPS stack consults -- rather than the unreadable
            // /data/misc/user/{uid}/cacerts-added directory the OS keeps them in on disk.
            val bundleFile = File(context.cacheDir, "mpv-system-ca-bundle.pem")
            createSystemCaBundle(File("/system/etc/security/cacerts"), bundleFile)
            appendUserCaCertificates(bundleFile)
            bundleFile.takeIf { it.exists() && it.length() > 0L }?.absolutePath
        }
        setOption("tls-verify", "yes")
        setOption("tls-ca-file", systemCaBundlePath.orEmpty())
        // --- network stream cache -------------------------------------------------
        // Jellyfin streams are remote HTTP; without a real cache every hiccup on the LAN
        // becomes a visible stall. The values below are the Findroid-era defaults.
        // cache: enable the stream cache for network sources (mpv disables it for some).
        setOption("cache", "yes")
        // cache-secs: how much decoded-ahead material the cache is allowed to keep; 30s is
        // enough to ride out a Wi-Fi dropout without hoarding memory on a TV box.
        setOption("cache-secs", "30")
        // demuxer-readahead-secs: how far the demuxer reads past the playhead. 20s keeps the
        // buffered bar meaningfully ahead while staying under demuxer-max-bytes for HD video.
        setOption("demuxer-readahead-secs", "20")
        // demuxer-max-bytes: hard ceiling on the forward cache — 64MiB is the practical cap
        // for an Android TV heap and comfortably holds the 20s readahead above.
        setOption("demuxer-max-bytes", "${min(64, memoryBudgetMiB)}MiB")
        // demuxer-max-back-bytes: backwards cache, so short back-seeks are instant instead of
        // forcing a new HTTP range request.
        setOption("demuxer-max-back-bytes", "${min(32, memoryBudgetMiB / 2)}MiB")
        // cache-pause-initial: hold playback until the cache has filled once, so the first
        // frame is followed by continuous video instead of an immediate re-buffer.
        setOption("cache-pause-initial", "yes")
        // cache-pause-wait: seconds of data to accumulate before resuming after an underrun.
        // Small (1s) so a brief stall does not turn into a long visible freeze.
        setOption("cache-pause-wait", "1")
        // network-timeout: give up on a dead connection after 15s instead of hanging forever;
        // the resulting end-file surfaces as PlayerState.Error.
        setOption("network-timeout", "15")
        // stream-buffer-size is deliberately left at mpv's default — raising it only adds
        // latency in front of the demuxer cache that is already sized above.
        setOption("sub-scale-with-window", "yes")
        setOption("sub-use-margins", "no")
        setOption("save-position-on-quit", "no")
        setOption("ytdl", "no")
        setOption("idle", "yes")
        // keep-open so the Ended state is observable instead of mpv going idle.
        setOption("keep-open", "yes")
        // No window until a Surface is attached.
        setOption("force-window", "no")
        if (!MpvNative.init()) {
            _state.value = PlayerState.Error("mpv failed to initialize (mpv_initialize)")
        }
        MpvNative.addObserver(this)
        MpvNative.addLogObserver(this)
        // DOUBLE rather than INT64: an integer-seconds position makes the seek bar and the
        // timecode advance in visible 1s jumps and rounds away accurate resume points.
        MpvNative.observeProperty("time-pos", MpvNative.MPV_FORMAT_DOUBLE)
        MpvNative.observeProperty("duration", MpvNative.MPV_FORMAT_DOUBLE)
        MpvNative.observeProperty("pause", MpvNative.MPV_FORMAT_FLAG)
        MpvNative.observeProperty("core-idle", MpvNative.MPV_FORMAT_FLAG)
        MpvNative.observeProperty("paused-for-cache", MpvNative.MPV_FORMAT_FLAG)
        MpvNative.observeProperty("eof-reached", MpvNative.MPV_FORMAT_FLAG)
        // `seeking` flips true while mpv refills after a seek: surfaced as Buffering so the OSD
        // shows motion instead of a frozen Playing state.
        MpvNative.observeProperty("seeking", MpvNative.MPV_FORMAT_FLAG)
        // demuxer-cache-time is the absolute playback timestamp (seconds) the cache reaches.
        MpvNative.observeProperty("demuxer-cache-time", MpvNative.MPV_FORMAT_DOUBLE)
        // mpv can refuse or round a rate change (and keeps `speed` across loadfile), so the flow is
        // driven by what mpv reports rather than by what was asked for.
        MpvNative.observeProperty("speed", MpvNative.MPV_FORMAT_DOUBLE)
        MpvNative.observeProperty("container-fps", MpvNative.MPV_FORMAT_DOUBLE)
        MpvNative.observeProperty("track-list", MpvNative.MPV_FORMAT_NONE)
    }

    override fun setSurface(surface: Surface?) {
        if (released) return
        if (surface != null) {
            MpvNative.attachSurface(surface)
            setOption("force-window", "yes")
            setOption("vo", "gpu")
            surfaceAttached = true
            pendingLoad?.let { request ->
                pendingLoad = null
                startLoad(request)
            }
        } else if (surfaceAttached) {
            // Must detach before the surface is destroyed.
            setOption("vo", "null")
            setOption("force-window", "no")
            MpvNative.detachSurface()
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
        MpvNative.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun configure(settings: ClientSettings) {
        if (released) return

        setOption(
            "profile",
            when (settings.renderingProfile) {
                RenderingProfile.Quality -> "gpu-hq"
                RenderingProfile.Balanced -> "default"
                RenderingProfile.Fast -> "fast"
            },
        )
        setOption(
            "hwdec",
            when (settings.hardwareDecoding) {
                HardwareDecoding.Software -> "no"
                HardwareDecoding.MediaCodec -> "mediacodec"
                HardwareDecoding.MediaCodecCopy -> "mediacodec-copy"
            },
        )
        setOption(
            "target-colorspace-hint",
            if (settings.hdrMode == HdrMode.Off || settings.hdrMode == HdrMode.ForceSdr) "no" else "yes",
        )
        setOption(
            "tone-mapping",
            when (settings.toneMapping) {
                ToneMapping.Bt2390 -> "bt.2390"
                ToneMapping.Reinhard -> "reinhard"
                ToneMapping.Mobius -> "mobius"
                ToneMapping.Off -> "clip"
                ToneMapping.Automatic -> "auto"
            },
        )
        setOption(
            "deinterlace",
            when (settings.deinterlaceMode) {
                DeinterlaceMode.On, DeinterlaceMode.Bob -> "yes"
                DeinterlaceMode.Off -> "no"
                DeinterlaceMode.Automatic -> "auto"
            },
        )
        setOption("interpolation", settings.frameInterpolation.yesNo())

        setOption("audio-pitch-correction", settings.pitchCorrection.yesNo())
        setOption("audio-channels", if (settings.downmixStereo) "stereo" else "auto")
        val passthrough = buildList {
            if (settings.dolbyDigitalPassthrough) add("ac3")
            if (settings.dolbyDigitalPlusPassthrough) add("eac3")
            if (settings.dtsPassthrough) addAll(listOf("dts", "dts-hd"))
        }
        setOption("audio-spdif", passthrough.joinToString(","))
        setOption(
            "alang",
            settings.preferredAudioLanguage.toMpvLanguagePreference(),
        )

        setOption("sub-scale", (settings.subtitleSizePercent / 100f).toString())
        setOption(
            "sub-color",
            when (settings.subtitleColor) {
                SubtitleColor.Yellow -> "#FFF176"
                SubtitleColor.Grey -> "#C8C8C8"
                SubtitleColor.White -> "#FFFFFF"
            },
        )
        setOption(
            "sub-border-size",
            when (settings.subtitleStroke) {
                SubtitleStroke.Off -> "0"
                SubtitleStroke.Light -> "1"
                SubtitleStroke.Heavy -> "4"
                SubtitleStroke.Medium -> "2"
            },
        )
        setOption("sub-bold", settings.boldSubtitles.yesNo())
        setOption("sub-scale-with-window", settings.scaleSubtitlesWithWindow.yesNo())
        setOption("sub-use-margins", settings.useVideoMargins.yesNo())
        setOption(
            "slang",
            settings.preferredSubtitleLanguage.toMpvLanguagePreference(),
        )

        setOption("cache", settings.networkCacheEnabled.yesNo())
        setOption("cache-secs", settings.cacheDurationSeconds.toString())
        setOption("demuxer-readahead-secs", settings.readAheadSeconds.toString())
        val forwardCacheMiB = min(settings.forwardCacheMiB, memoryBudgetMiB)
        val backwardCacheMiB = min(settings.backwardCacheMiB, memoryBudgetMiB / 2)
        setOption("demuxer-max-bytes", "${forwardCacheMiB}MiB")
        setOption("demuxer-max-back-bytes", "${backwardCacheMiB}MiB")
        setOption("cache-pause-wait", settings.resumeBufferSeconds.toString())
        setOption("network-timeout", settings.networkTimeoutSeconds.toString())
        val tlsOptions = tlsMpvOptions(settings, systemCaBundlePath)
        setOption("tls-verify", tlsOptions.verify)
        // An empty value restores mpv's native CA lookup and prevents a prior Android bundle
        // from leaking into a later trust-source selection.
        setOption("tls-ca-file", tlsOptions.caFile.orEmpty())
    }

    override fun setSystemCaptionStyle(style: SystemCaptionStyle?) {
        if (released || style == null) return
        setOption("sub-scale", style.fontScale.coerceIn(0.5f, 3f).toString())
        setOption("sub-color", style.foregroundColor.toMpvColor())
        setOption("sub-back-color", style.backgroundColor.toMpvColor())
        setOption("sub-border-color", style.edgeColor.toMpvColor())
        setOption("sub-border-size", if (style.edgeType == 0) "0" else "1")
        style.typefaceName?.takeIf(String::isNotBlank)?.let { setOption("sub-font", it) }
        style.localeTag?.let { setOption("slang", it) }
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
        _videoFps.value = null
        _positionMs.value = item.startPositionMs
        _durationMs.value = item.durationMs
        _state.value = PlayerState.Loading
        if (item.requiresVideoSurface && !surfaceAttached) {
            pendingLoad = item
            return
        }
        pendingLoad = null
        startLoad(item)
    }

    private fun startLoad(item: PlayRequest) {
        if (released || !hasRequest) return
        // http-header-fields is parsed as a comma-separated list, and Jellyfin's
        // MediaBrowser Authorization value contains commas — joining would split it
        // into malformed header lines and the server answers 400. change-list
        // appends each header as one atomic list entry instead.
        MpvNative.command(arrayOf("change-list", "http-header-fields", "clr", ""))
        item.headers.forEach { (key, value) ->
            MpvNative.command(arrayOf("change-list", "http-header-fields", "append", "$key: $value"))
        }
        setOption(
            "start",
            if (item.startPositionMs > 0) {
                String.format(Locale.ROOT, "+%.3f", item.startPositionMs / 1000.0)
            } else {
                "none"
            },
        )
        MpvNative.setPropertyBoolean("pause", false)
        MpvNative.command(arrayOf("loadfile", item.url))
    }

    override fun play() {
        if (!released) MpvNative.setPropertyBoolean("pause", false)
    }

    override fun pause() {
        if (!released) MpvNative.setPropertyBoolean("pause", true)
    }

    override fun seekTo(ms: Long) {
        if (released) return
        val target = ms.coerceAtLeast(0)
        _positionMs.value = target
        // Enter Buffering immediately; the `seeking` property event clears it once mpv has refilled.
        seeking = true
        updateState()
        MpvNative.command(
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
        MpvNative.setPropertyDouble("speed", clamped.toDouble())
        // Optimistic; the observed `speed` property corrects this if mpv lands somewhere else.
        _speed.value = clamped
    }

    override fun setAudioDelayMs(delayMs: Long) {
        if (!released) MpvNative.setPropertyDouble("audio-delay", delayMs / 1_000.0)
    }

    override fun setSubtitleDelayMs(delayMs: Long) {
        if (!released) MpvNative.setPropertyDouble("sub-delay", delayMs / 1_000.0)
    }

    override fun setFrameMode(mode: String) {
        if (released) return
        MpvNative.setPropertyDouble("panscan", if (mode == "Fill") 1.0 else 0.0)
        MpvNative.setPropertyBoolean("video-unscaled", mode == "Original")
        MpvNative.setPropertyString(
            "video-aspect-override",
            when (mode) {
                "16:9" -> "16:9"
                "4:3" -> "4:3"
                else -> "-1"
            },
        )
    }

    override fun setHdrMode(mode: String) {
        if (released) return
        when (mode) {
            "Passthrough" -> {
                MpvNative.setPropertyString("target-colorspace-hint", "yes")
                MpvNative.setPropertyString("target-trc", "auto")
            }
            "Tone map" -> {
                MpvNative.setPropertyString("target-colorspace-hint", "no")
                MpvNative.setPropertyString("target-trc", "auto")
                MpvNative.setPropertyString("tone-mapping", "auto")
            }
            "Convert to SDR" -> {
                MpvNative.setPropertyString("target-colorspace-hint", "no")
                MpvNative.setPropertyString("target-trc", "bt.1886")
                MpvNative.setPropertyString("tone-mapping", "auto")
            }
            else -> {
                MpvNative.setPropertyString("target-colorspace-hint", "auto")
                MpvNative.setPropertyString("target-trc", "auto")
                MpvNative.setPropertyString("tone-mapping", "auto")
            }
        }
    }

    override fun setDeinterlaceMode(mode: String) {
        if (released) return
        MpvNative.setPropertyString(
            "deinterlace",
            when (mode) {
                "On" -> "yes"
                "Off" -> "no"
                else -> "auto"
            },
        )
    }

    /**
     * [PlayerTrack.id] is a Jellyfin `MediaStream.Index`; mpv addresses tracks by its own per-type
     * id, so the index is translated before it reaches `aid`/`sid`.
     */
    override fun selectTrack(track: PlayerTrack) {
        if (released) return
        val property = when (track.type) {
            TrackType.VIDEO -> "vid"
            TrackType.AUDIO -> "aid"
            TrackType.SUBTITLE -> "sid"
        }
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
        MpvNative.setPropertyString(property, value)
        _tracks.value = _tracks.value.map { existing ->
            if (existing.type != track.type) existing else existing.copy(selected = existing.id == track.id)
        }
    }

    override fun stop() {
        if (released) return
        hasRequest = false
        pendingLoad = null
        fileLoaded = false
        eofReached = false
        seeking = false
        MpvNative.command(arrayOf("stop"))
        mpvTracks = emptyList()
        externalIndexByMpvId = emptyMap()
        pendingExternalSubtitles = emptyList()
        _positionMs.value = 0
        _durationMs.value = null
        _bufferedMs.value = null
        _videoFps.value = null
        _state.value = PlayerState.Idle
    }

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        pendingLoad = null
        MpvNative.removeObserver(this)
        MpvNative.removeLogObserver(this)
        if (surfaceAttached) {
            MpvNative.detachSurface()
            surfaceAttached = false
        }
        MpvNative.destroy()
    }

    // --- MpvNative.EventObserver (mpv thread) ------------------------------------

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
            "container-fps" -> _videoFps.value = value.takeIf { it > 0.0 }
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
            MpvNative.MPV_EVENT_FILE_LOADED -> {
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
            MpvNative.MPV_EVENT_SEEK -> {
                seeking = true
                updateState()
            }
            MpvNative.MPV_EVENT_PLAYBACK_RESTART -> {
                seeking = false
                updateState()
            }
            MpvNative.MPV_EVENT_END_FILE -> {
                // This binding does not expose the end-file reason, so a file
                // that ends without ever having loaded is treated as an error.
                if (hasRequest && !fileLoaded) {
                    _state.value = PlayerState.Error(lastErrorMessage ?: "mpv could not open the stream")
                } else if (fileLoaded) {
                    eofReached = true
                    updateState()
                }
            }
            MpvNative.MPV_EVENT_SHUTDOWN -> {
                hasRequest = false
                _state.value = PlayerState.Idle
            }
        }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        if (level <= MpvNative.MPV_LOG_LEVEL_ERROR && level != MpvNative.MPV_LOG_LEVEL_NONE) {
            val message = text.trim()
            if (message.isNotEmpty()) {
                lastErrorMessage = message
                Log.e(TAG, "[$prefix] $message")
            }
        }
    }

    // --- internals ------------------------------------------------------------

    /**
     * `MpvNative.setOptionString` already returns mpv's error code, but every call site used to
     * discard it, so a rejected option (typo, value out of range, unsupported on this device)
     * failed silently. This just makes the failure observable in logcat instead of changing
     * behavior — most options here are non-critical tuning knobs, not worth surfacing to the UI.
     */
    private fun setOption(name: String, value: String) {
        val result = MpvNative.setOptionString(name, value)
        if (result < 0) Log.w(TAG, "mpv rejected option $name=$value (code $result)")
    }

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
                ?.let { MpvNative.setPropertyString("aid", it.toString()) }
                ?: Log.w(TAG, "preferred audio stream $index not present in track-list")
        }
        pendingSubtitleTrackId?.let { index ->
            if (index < 0) {
                MpvNative.setPropertyString("sid", "no")
            } else {
                MpvTrackList.mpvIdFor(mpvTracks, externalIndexByMpvId, TrackType.SUBTITLE, index)
                    ?.let { MpvNative.setPropertyString("sid", it.toString()) }
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
            MpvNative.command(
                arrayOf("sub-add", subtitle.url, "auto", subtitle.title, subtitle.language.orEmpty()),
            )
        }
    }

    private fun refreshTracks() {
        val json = runCatching { MpvNative.getPropertyString("track-list") }.getOrNull() ?: return
        val parsed = runCatching { MpvTrackList.parse(json) }.getOrElse {
            Log.w(TAG, "track-list parse failed", it)
            return
        }
        mpvTracks = parsed
        externalIndexByMpvId = MpvTrackList.externalIndexByMpvId(parsed, pendingExternalSubtitles)
        _tracks.value = MpvTrackList.toPlayerTracks(parsed, externalIndexByMpvId)
    }

    private fun Boolean.yesNo(): String = if (this) "yes" else "no"

    private fun String?.toMpvLanguagePreference(): String = when (this?.lowercase(Locale.ROOT)) {
        "english" -> "eng,en"
        "japanese" -> "jpn,ja"
        "vietnamese" -> "vie,vi"
        "french" -> "fra,fre,fr"
        "german" -> "deu,ger,de"
        else -> ""
    }
}

internal fun createSystemCaBundle(systemCaDirectory: File, destination: File): File? = runCatching {
    val certificates = systemCaDirectory.listFiles()
        ?.filter(File::isFile)
        ?.sortedBy(File::getName)
        .orEmpty()
    if (certificates.isEmpty()) return null

    val temporary = File(destination.parentFile, "${destination.name}.tmp")
    temporary.outputStream().buffered().use { output ->
        certificates.forEach { certificate ->
            certificate.inputStream().buffered().use { it.copyTo(output) }
            output.write('\n'.code)
        }
    }
    if (!temporary.renameTo(destination)) {
        temporary.copyTo(destination, overwrite = true)
        temporary.delete()
    }
    destination
}.getOrNull()

/**
 * Appends every user-installed CA (Settings > Security > Encryption & credentials > User
 * credentials) to [destination] in PEM form, on top of whatever [createSystemCaBundle] already
 * wrote. `AndroidCAStore` aliases prefixed `user:` are exactly this set, and reading it needs no
 * special permission -- unlike the on-disk `/data/misc/user/{uid}/cacerts-added` directory the OS
 * actually stores them in, which is not readable by an app's own uid.
 */
internal fun appendUserCaCertificates(destination: File): File? = runCatching {
    val keyStore = java.security.KeyStore.getInstance("AndroidCAStore")
    keyStore.load(null, null)
    val userAliases = keyStore.aliases().toList().filter { it.startsWith("user:") }
    if (userAliases.isEmpty()) return destination
    destination.appendText(
        userAliases.joinToString(separator = "") { alias ->
            val certificate = keyStore.getCertificate(alias) as? java.security.cert.X509Certificate
                ?: return@joinToString ""
            certificate.encoded.toPem()
        },
    )
    destination
}.getOrNull()

private fun ByteArray.toPem(): String {
    val body = java.util.Base64.getEncoder().encodeToString(this).chunked(64).joinToString("\n")
    return "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n"
}

private fun Int.toMpvColor(): String = "#%08X".format(Locale.US, this)
