package com.maik205.shoumeiplayer.domain.settings

data class ClientSettings(
    val playbackBackend: PlaybackBackend = PlaybackBackend.Mpv,
    val preferredQuality: PreferredQuality = PreferredQuality.Auto,
    val maxStreamingBitrateMbps: Int? = null,
    val maxRemoteBitrateMbps: Int = 20,
    val refreshRateSwitching: RefreshRateSwitching = RefreshRateSwitching.Disabled,
    val autoplayNextEpisode: Boolean = true,
    val resumeBehavior: ResumeBehavior = ResumeBehavior.Ask,
    val seekIntervalSeconds: Int = 10,
    val skipIntroPrompt: Boolean = true,
    val rememberPlaybackSpeed: Boolean = false,

    val renderingProfile: RenderingProfile = RenderingProfile.Fast,
    val hardwareDecoding: HardwareDecoding = HardwareDecoding.MediaCodecCopy,
    val hardwareCodecs: HardwareCodecs = HardwareCodecs.Automatic,
    val hdrMode: HdrMode = HdrMode.Automatic,
    val toneMapping: ToneMapping = ToneMapping.Automatic,
    val deinterlaceMode: DeinterlaceMode = DeinterlaceMode.Automatic,
    val frameInterpolation: Boolean = false,

    val preferredAudioLanguage: String? = null,
    val rememberSeriesAudio: Boolean = true,
    val pitchCorrection: Boolean = true,
    val downmixStereo: Boolean = false,
    val dolbyDigitalPassthrough: Boolean = false,
    val dolbyDigitalPlusPassthrough: Boolean = false,
    val dtsPassthrough: Boolean = false,
    val audioDelayMs: Int = 0,

    val preferredSubtitleLanguage: String? = null,
    val subtitleMode: SubtitleMode = SubtitleMode.Smart,
    val burnSubtitles: BurnSubtitles = BurnSubtitles.Automatic,
    val subtitleSizePercent: Int = 100,
    val subtitleColor: SubtitleColor = SubtitleColor.White,
    val subtitleStroke: SubtitleStroke = SubtitleStroke.Medium,
    val boldSubtitles: Boolean = false,
    val scaleSubtitlesWithWindow: Boolean = true,
    val useVideoMargins: Boolean = false,
    val pgsDirectPlay: Boolean = true,
    val assSsaDirectPlay: AssSsaDirectPlay = AssSsaDirectPlay.Experimental,
    val subtitleDelayMs: Int = 0,

    val networkCacheEnabled: Boolean = true,
    val cacheDurationSeconds: Int = 30,
    val readAheadSeconds: Int = 20,
    val forwardCacheMiB: Int = 64,
    val backwardCacheMiB: Int = 32,
    val resumeBufferSeconds: Int = 1,
    val networkTimeoutSeconds: Int = 15,
    val verifyTlsCertificates: Boolean = true,
    val tlsTrustSource: TlsTrustSource = TlsTrustSource.AndroidSystem,

    val focusScaleEnabled: Boolean = true,
    val clockInOsd: Boolean = true,
    val displayLanguage: DisplayLanguage = DisplayLanguage.English,
    val interfaceScale: InterfaceScale = InterfaceScale.Comfortable,
    val theme: AppTheme = AppTheme.Dark,
    val colorPalette: ColorPalette = ColorPalette.Midnight,
    val backdropImages: Boolean = true,
    val backdropRotationSeconds: Int = 20,
    val watchedIndicators: Boolean = true,
    val rememberLastLibrary: Boolean = true,
    val cacheHomeContent: Boolean = true,

    val screensaverTimeoutMinutes: Int = 5,
    val screensaverContent: ScreensaverContent = ScreensaverContent.AllLibraries,
    val screensaverImageDurationSeconds: Int = 20,
    val screensaverShuffle: Boolean = true,
    val screensaverAvoidRepeats: Boolean = true,
    val screensaverClock: Boolean = true,

    val kidsMode: Boolean = false,
)

/**
 * Client-only settings, persisted separately from [SessionStore] (`shoumei_settings`).
 *
 * The primary constructor takes the backing [DataStore] so JVM unit tests can
 * substitute an in-memory implementation; production code uses the [Context]
 * secondary constructor, mirroring [SessionStore]'s shape.
 */
