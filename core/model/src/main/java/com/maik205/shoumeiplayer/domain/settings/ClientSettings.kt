package com.maik205.shoumeiplayer.domain.settings

/**
 * Client-owned settings: everything Jellyfin does not store for us.
 *
 * Preferences that Jellyfin also stores per account (`UserConfiguration`) deliberately do **not**
 * live here: audio/subtitle language, subtitle mode, "play default audio track" and next-episode
 * autoplay are server-owned so a change made on this TV is the same change the Jellyfin web client
 * and every other device sees. They are read and written through
 * `AuthRepository.userConfiguration` / `AuthRepository.editUserConfiguration`.
 *
 * What is left splits in two, and `SettingsStore` persists the halves in different places (#85):
 *
 * - **Device-wide**, shared by every profile on every server, because the answer is a fact about
 *   this television rather than about a person: [playbackBackend], [refreshRateSwitching],
 *   [renderingProfile], [hardwareDecoding], [hardwareCodecs], [hdrMode], [toneMapping],
 *   [deinterlaceMode], [frameInterpolation], the audio-path settings ([pitchCorrection],
 *   [downmixStereo] and the three passthrough toggles — properties of the attached receiver), the
 *   cache budgets ([networkCacheEnabled] through [networkTimeoutSeconds] — a RAM/bandwidth budget
 *   for the box), the TLS trust settings ([verifyTlsCertificates], [tlsTrustSource]) and
 *   [displayLanguage], which the launcher applies before anyone has signed in.
 * - **Per `(serverUrl, userId)`**, because they are matters of taste and one viewer's choices
 *   following another into their profile is the bug #85 describes: subtitle appearance and delays,
 *   the interface block ([focusScaleEnabled] through [cacheHomeContent]), the whole screensaver
 *   block, the resume/seek/skip habits, [rememberSeriesAudio], and the quality ceiling
 *   ([preferredQuality], [maxStreamingBitrateMbps], [maxRemoteBitrateMbps]) a viewer is willing to
 *   accept.
 */
data class ClientSettings(
    val playbackBackend: PlaybackBackend = PlaybackBackend.Mpv,
    val preferredQuality: PreferredQuality = PreferredQuality.Auto,
    val maxStreamingBitrateMbps: Int? = null,
    val maxRemoteBitrateMbps: Int = 20,
    val refreshRateSwitching: RefreshRateSwitching = RefreshRateSwitching.Disabled,
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

    val rememberSeriesAudio: Boolean = true,
    val pitchCorrection: Boolean = true,
    val downmixStereo: Boolean = false,
    val dolbyDigitalPassthrough: Boolean = false,
    val dolbyDigitalPlusPassthrough: Boolean = false,
    val dtsPassthrough: Boolean = false,
    val audioDelayMs: Int = 0,

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
    val displayLanguage: DisplayLanguage = DisplayLanguage.SystemDefault,
    val interfaceScale: InterfaceScale = InterfaceScale.Comfortable,
    val theme: AppTheme = AppTheme.Dark,
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
)

/**
 * Client-only settings, persisted separately from [SessionStore] (`shoumei_settings`).
 *
 * The primary constructor takes the backing [DataStore] so JVM unit tests can
 * substitute an in-memory implementation; production code uses the [Context]
 * secondary constructor, mirroring [SessionStore]'s shape.
 */
