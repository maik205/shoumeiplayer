package com.maik205.shoumeiplayer.player

import android.content.Context
import android.app.ActivityManager
import android.util.Log
import android.view.Surface
import com.maik205.mpvroid.MpvNative
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.DeinterlaceMode
import com.maik205.shoumeiplayer.domain.settings.HardwareCodecs
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

/** Every codec mpv may hand to MediaCodec, i.e. the unrestricted [HardwareCodecs.Automatic] case. */
private const val ALL_HWDEC_CODECS = "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1"

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

    private val _readRateBytesPerSecond = MutableStateFlow<Long?>(null)
    override val readRateBytesPerSecond: StateFlow<Long?> = _readRateBytesPerSecond.asStateFlow()

    private val _videoBitrateBitsPerSecond = MutableStateFlow<Long?>(null)
    override val videoBitrateBitsPerSecond: StateFlow<Long?> = _videoBitrateBitsPerSecond.asStateFlow()

    private val _audioBitrateBitsPerSecond = MutableStateFlow<Long?>(null)
    override val audioBitrateBitsPerSecond: StateFlow<Long?> = _audioBitrateBitsPerSecond.asStateFlow()

    private val _cacheIdle = MutableStateFlow<Boolean?>(null)
    override val cacheIdle: StateFlow<Boolean?> = _cacheIdle.asStateFlow()

    private val _seeking = MutableStateFlow(false)
    override val seeking: StateFlow<Boolean> = _seeking.asStateFlow()

    private val _pausedForCache = MutableStateFlow(false)
    override val pausedForCache: StateFlow<Boolean> = _pausedForCache.asStateFlow()

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
    @Volatile private var corePausedForCache = false
    @Volatile private var eofReached = false
    @Volatile private var coreSeeking = false
    private var systemCaBundlePath: String? = null

    /**
     * Serializes every write to the subtitle options that [configure] and [setSystemCaptionStyle]
     * both touch, together with the flag that decides which side owns them.
     *
     * The two callers run on different threads -- a `CaptioningManager` listener callback versus
     * the coroutine that applies settings -- and each one needs several `setOption` calls to land
     * as a unit. Guarding only the flag (it used to be a bare `@Volatile`) leaves the *sequence*
     * interleavable: the accessibility style could win the flag and then have half of the in-app
     * values written over the top of it.
     */
    private val subtitleOptionsLock = Any()

    /**
     * The mpv values [configure] derives from [ClientSettings] for the appearance options
     * [setSystemCaptionStyle] can also drive. Kept around so that when system captions are toggled
     * off, the in-app values can be reapplied instead of leaving whatever the system style last
     * wrote in place. Guarded by [subtitleOptionsLock].
     */
    private var subtitleAppearance: SubtitleAppearance = ClientSettings().toSubtitleAppearance()

    /**
     * The active system caption style, or null when captions are off. Retained (rather than only a
     * boolean) because it is one of the two inputs to `slang`. Guarded by [subtitleOptionsLock].
     */
    private var systemCaptionStyle: SystemCaptionStyle? = null

    /**
     * The account's subtitle language, resolved from the server preference at load time. Guarded
     * by [subtitleOptionsLock].
     */
    private var subtitleLanguagePreference: String = ""

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
        setOption("hwdec-codecs", ALL_HWDEC_CODECS)
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
        // These scalar properties make a rebuffer explainable without exposing mpv's full
        // demuxer-cache-state node tree to the UI.
        MpvNative.observeProperty("cache-speed", MpvNative.MPV_FORMAT_INT64)
        MpvNative.observeProperty("video-bitrate", MpvNative.MPV_FORMAT_INT64)
        MpvNative.observeProperty("audio-bitrate", MpvNative.MPV_FORMAT_INT64)
        MpvNative.observeProperty("demuxer-cache-idle", MpvNative.MPV_FORMAT_FLAG)
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
        // Which codecs may use the MediaCodec path. This is what "Hardware codecs" means -- a
        // decode-side choice, sitting next to "Hardware decoding" in the same settings section.
        // It deliberately does NOT narrow the device profile sent to Jellyfin: what the server is
        // allowed to hand back is a separate question from what this device hardware-decodes, and
        // conflating them turned "AV1" into "transcode my whole h264 library".
        setOption(
            "hwdec-codecs",
            when (settings.hardwareCodecs) {
                HardwareCodecs.Automatic -> ALL_HWDEC_CODECS
                HardwareCodecs.H264Hevc -> "h264,hevc"
                HardwareCodecs.Av1 -> "av1"
                HardwareCodecs.Disabled -> ""
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

        setOption("sub-bold", settings.boldSubtitles.yesNo())
        setOption("sub-scale-with-window", settings.scaleSubtitlesWithWindow.yesNo())
        setOption("sub-use-margins", settings.useVideoMargins.yesNo())
        // sub-scale/sub-color/sub-back-color/sub-border-color/sub-border-size overlap with
        // setSystemCaptionStyle: the new value is always recorded so it is ready the moment system
        // captions turn off, but it only reaches mpv now if an enabled system caption style is not
        // currently in charge.
        synchronized(subtitleOptionsLock) {
            subtitleAppearance = settings.toSubtitleAppearance()
            applySubtitleOptionsLocked()
        }

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

    /**
     * System captions are an accessibility signal and win over in-app subtitle *appearance*, but
     * only while [SystemCaptionStyle.enabled] is actually true -- [style] itself can still be
     * non-null while carrying `enabled = false` (Android reports a caption style even when captions
     * are off). Whenever the enabled system override goes away, every option it can write is
     * restored to the in-app value captured by [configure], including `sub-back-color` and
     * `sub-border-color`: mpv's core is process-scoped, so an option nobody resets stays on the
     * accessibility value for the life of the process.
     */
    override fun setSystemCaptionStyle(style: SystemCaptionStyle?) {
        if (released) return
        synchronized(subtitleOptionsLock) {
            systemCaptionStyle = style?.takeIf { it.enabled }
            applySubtitleOptionsLocked()
        }
    }

    /** Callers must hold [subtitleOptionsLock]. */
    private fun applySubtitleOptionsLocked() {
        resolveSubtitleOptions(
            appearance = subtitleAppearance,
            systemCaptions = systemCaptionStyle,
            subtitleLanguagePreference = subtitleLanguagePreference,
        ).forEach { (option, value) -> setOption(option, value) }
    }

    /**
     * Points mpv's own language fallback at the account preference that just chose the track
     * indices for this stream. See [ServerTrackPreferences] for why it is read here, at load, and
     * not passed into [configure].
     */
    private fun applyServerLanguagePreferences() {
        val preferences = ServerTrackPreferences.latestOrNull()
        setOption("alang", preferences?.audioLanguagePreference.toMpvLanguagePreference())
        synchronized(subtitleOptionsLock) {
            subtitleLanguagePreference =
                preferences?.subtitleLanguagePreference.toMpvLanguagePreference()
            applySubtitleOptionsLocked()
        }
    }

    override fun load(item: PlayRequest) {
        if (released) return
        // Before any mpv state for this stream exists: TrackController has just resolved the
        // account preference into item.preferredAudio/SubtitleTrackId, so alang/slang are set from
        // the same document and mpv's fallback agrees with the indices about to be applied.
        applyServerLanguagePreferences()
        hasRequest = true
        fileLoaded = false
        eofReached = false
        coreSeeking = false
        corePausedForCache = false
        lastErrorMessage = null
        pendingAudioTrackId = item.preferredAudioTrackId
        pendingSubtitleTrackId = item.preferredSubtitleTrackId
        pendingExternalSubtitles = item.externalSubtitles
        mpvTracks = emptyList()
        externalIndexByMpvId = emptyMap()
        _tracks.value = emptyList()
        _bufferedMs.value = null
        _videoFps.value = null
        _readRateBytesPerSecond.value = null
        _videoBitrateBitsPerSecond.value = null
        _audioBitrateBitsPerSecond.value = null
        _cacheIdle.value = null
        _seeking.value = false
        _pausedForCache.value = false
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
        coreSeeking = true
        _seeking.value = true
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
        coreSeeking = false
        corePausedForCache = false
        MpvNative.command(arrayOf("stop"))
        mpvTracks = emptyList()
        externalIndexByMpvId = emptyMap()
        pendingExternalSubtitles = emptyList()
        _positionMs.value = 0
        _durationMs.value = null
        _bufferedMs.value = null
        _videoFps.value = null
        _readRateBytesPerSecond.value = null
        _videoBitrateBitsPerSecond.value = null
        _audioBitrateBitsPerSecond.value = null
        _cacheIdle.value = null
        _seeking.value = false
        _pausedForCache.value = false
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
            "cache-speed" -> _readRateBytesPerSecond.value = value.takeIf { it > 0L }
            "video-bitrate" -> _videoBitrateBitsPerSecond.value = value.takeIf { it > 0L }
            "audio-bitrate" -> _audioBitrateBitsPerSecond.value = value.takeIf { it > 0L }
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
            "paused-for-cache" -> {
                corePausedForCache = value
                _pausedForCache.value = value
            }
            "eof-reached" -> eofReached = value
            "seeking" -> {
                coreSeeking = value
                _seeking.value = value
            }
            "demuxer-cache-idle" -> _cacheIdle.value = value
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
                coreSeeking = true
                _seeking.value = true
                updateState()
            }
            MpvNative.MPV_EVENT_PLAYBACK_RESTART -> {
                coreSeeking = false
                _seeking.value = false
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
            corePausedForCache || coreSeeking -> PlayerState.Buffering
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

}

/**
 * Maps one account language value onto mpv's `alang`/`slang` list.
 *
 * Jellyfin stores an ISO 639-2 code, but older Shoumei builds wrote the English display name into
 * the same field, so both are accepted. Every alias of the language is emitted because a stream can
 * be tagged with any of them and mpv compares literally.
 */
internal fun String?.toMpvLanguagePreference(): String = when (this?.trim()?.lowercase(Locale.ROOT)) {
    "eng", "en", "english" -> "eng,en"
    "jpn", "ja", "japanese" -> "jpn,ja"
    "vie", "vi", "vietnamese" -> "vie,vi"
    "fra", "fre", "fr", "french" -> "fra,fre,fr"
    "deu", "ger", "de", "german" -> "deu,ger,de"
    else -> ""
}

/**
 * mpv option values `MpvEngine.configure` derives from [ClientSettings]'s subtitle appearance
 * fields.
 *
 * [backColor], [borderColor] and [font] have no in-app control, but they still need a defined value
 * here: an enabled system caption style writes all three, and mpv's core outlives any one playback,
 * so without an in-app value to restore they would stay on the accessibility style for the rest of
 * the process once system captions had been enabled even briefly. These are mpv's own defaults --
 * transparent background, opaque black outline, sans-serif.
 */
internal data class SubtitleAppearance(
    val scale: String,
    val color: String,
    val backColor: String = "#00000000",
    val borderColor: String = "#FF000000",
    val borderSize: String,
    val font: String = "sans-serif",
)

internal fun ClientSettings.toSubtitleAppearance() = SubtitleAppearance(
    scale = (subtitleSizePercent / 100f).toString(),
    color = when (subtitleColor) {
        SubtitleColor.Yellow -> "#FFF176"
        SubtitleColor.Grey -> "#C8C8C8"
        SubtitleColor.White -> "#FFFFFF"
    },
    borderSize = when (subtitleStroke) {
        SubtitleStroke.Off -> "0"
        SubtitleStroke.Light -> "1"
        SubtitleStroke.Heavy -> "4"
        SubtitleStroke.Medium -> "2"
    },
)

/**
 * Every mpv subtitle option that both `configure` and `setSystemCaptionStyle` can drive, resolved
 * in one place from all three inputs.
 *
 * Two properties matter and are what the previous split implementation got wrong:
 *
 * 1. **The key set never varies.** Whichever side owns the options, all six appearance keys plus
 *    `slang` are written. An option that only one side ever set (`sub-back-color`,
 *    `sub-border-color`, `sub-font`) used to stay on the accessibility value forever after system
 *    captions were turned back off, because mpv's core is process-scoped and nothing reset it.
 * 2. **`slang` has exactly one writer.** It used to be written by `configure` *or* conditionally by
 *    `setSystemCaptionStyle` *or*, in the common case where the user never picked a caption
 *    language, by neither -- so the account's subtitle language silently stopped being honoured.
 *    An enabled caption *locale* is an explicit accessibility choice and wins; otherwise the
 *    account preference does, which is the same document that chose the subtitle stream index.
 */
internal fun resolveSubtitleOptions(
    appearance: SubtitleAppearance,
    systemCaptions: SystemCaptionStyle?,
    subtitleLanguagePreference: String,
): Map<String, String> {
    val active = systemCaptions?.takeIf { it.enabled }
    val language = active?.localeTag?.takeIf(String::isNotBlank) ?: subtitleLanguagePreference
    if (active == null) {
        return mapOf(
            "sub-scale" to appearance.scale,
            "sub-color" to appearance.color,
            "sub-back-color" to appearance.backColor,
            "sub-border-color" to appearance.borderColor,
            "sub-border-size" to appearance.borderSize,
            "sub-font" to appearance.font,
            "slang" to language,
        )
    }
    return mapOf(
        "sub-scale" to active.fontScale.coerceIn(0.5f, 3f).toString(),
        "sub-color" to active.foregroundColor.toMpvColor(),
        "sub-back-color" to active.backgroundColor.toMpvColor(),
        "sub-border-color" to active.edgeColor.toMpvColor(),
        "sub-border-size" to if (active.edgeType == 0) "0" else "1",
        // Android reports no caption typeface far more often than it reports one; the in-app value
        // is the honest fallback rather than leaving whatever font was last in force.
        "sub-font" to (active.typefaceName?.takeIf(String::isNotBlank) ?: appearance.font),
        "slang" to language,
    )
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
