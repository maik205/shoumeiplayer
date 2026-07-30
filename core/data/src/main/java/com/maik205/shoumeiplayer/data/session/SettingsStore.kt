package com.maik205.shoumeiplayer.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.maik205.shoumeiplayer.domain.settings.DeinterlaceMode
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.AppTheme
import com.maik205.shoumeiplayer.domain.settings.AssSsaDirectPlay
import com.maik205.shoumeiplayer.domain.settings.BurnSubtitles
import com.maik205.shoumeiplayer.domain.settings.DisplayLanguage
import com.maik205.shoumeiplayer.domain.settings.HardwareCodecs
import com.maik205.shoumeiplayer.domain.settings.HardwareDecoding
import com.maik205.shoumeiplayer.domain.settings.HdrMode
import com.maik205.shoumeiplayer.domain.settings.InterfaceScale
import com.maik205.shoumeiplayer.domain.settings.PreferredQuality
import com.maik205.shoumeiplayer.domain.settings.PlayerSettingsRepository
import com.maik205.shoumeiplayer.domain.settings.RefreshRateSwitching
import com.maik205.shoumeiplayer.domain.settings.RenderingProfile
import com.maik205.shoumeiplayer.domain.settings.ResumeBehavior
import com.maik205.shoumeiplayer.domain.settings.ScreensaverContent
import com.maik205.shoumeiplayer.domain.settings.SubtitleMode
import com.maik205.shoumeiplayer.domain.settings.SubtitleColor
import com.maik205.shoumeiplayer.domain.settings.SubtitleStroke
import com.maik205.shoumeiplayer.domain.settings.ToneMapping
import com.maik205.shoumeiplayer.domain.settings.storedOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private object SettingsKeys {
    val PREFERRED_QUALITY = stringPreferencesKey("preferred_quality")
    val MAX_STREAMING_BITRATE_MBPS = intPreferencesKey("max_streaming_bitrate_mbps")
    val MAX_REMOTE_BITRATE_MBPS = intPreferencesKey("max_remote_bitrate_mbps")
    val REFRESH_RATE_SWITCHING = stringPreferencesKey("refresh_rate_switching")
    val AUTOPLAY_NEXT_EPISODE = booleanPreferencesKey("autoplay_next_episode")
    val RESUME_BEHAVIOR = stringPreferencesKey("resume_behavior")
    val SEEK_INTERVAL_SECONDS = intPreferencesKey("seek_interval_seconds")
    val SKIP_INTRO_PROMPT = booleanPreferencesKey("skip_intro_prompt")
    val REMEMBER_PLAYBACK_SPEED = booleanPreferencesKey("remember_playback_speed")

    val RENDERING_PROFILE = stringPreferencesKey("rendering_profile")
    val HARDWARE_DECODING = stringPreferencesKey("hardware_decoding")
    val HARDWARE_CODECS = stringPreferencesKey("hardware_codecs")
    val HDR_MODE = stringPreferencesKey("hdr_mode")
    val TONE_MAPPING = stringPreferencesKey("tone_mapping")
    val DEINTERLACE_MODE = stringPreferencesKey("deinterlace_mode")
    val FRAME_INTERPOLATION = booleanPreferencesKey("frame_interpolation")

    val PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")
    val REMEMBER_SERIES_AUDIO = booleanPreferencesKey("remember_series_audio")
    val PITCH_CORRECTION = booleanPreferencesKey("pitch_correction")
    val DOWNMIX_STEREO = booleanPreferencesKey("downmix_stereo")
    val DOLBY_DIGITAL_PASSTHROUGH = booleanPreferencesKey("dolby_digital_passthrough")
    val DOLBY_DIGITAL_PLUS_PASSTHROUGH = booleanPreferencesKey("dolby_digital_plus_passthrough")
    val DTS_PASSTHROUGH = booleanPreferencesKey("dts_passthrough")
    val AUDIO_DELAY_MS = intPreferencesKey("audio_delay_ms")

    val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
    val SUBTITLE_MODE = stringPreferencesKey("subtitle_mode")
    val BURN_SUBTITLES = stringPreferencesKey("burn_subtitles")
    val SUBTITLE_SIZE_PERCENT = intPreferencesKey("subtitle_size_percent")
    val SUBTITLE_COLOR = stringPreferencesKey("subtitle_color")
    val SUBTITLE_STROKE = stringPreferencesKey("subtitle_stroke")
    val BOLD_SUBTITLES = booleanPreferencesKey("bold_subtitles")
    val SCALE_SUBTITLES_WITH_WINDOW = booleanPreferencesKey("scale_subtitles_with_window")
    val USE_VIDEO_MARGINS = booleanPreferencesKey("use_video_margins")
    val PGS_DIRECT_PLAY = booleanPreferencesKey("pgs_direct_play")
    val ASS_SSA_DIRECT_PLAY = stringPreferencesKey("ass_ssa_direct_play")
    val SUBTITLE_DELAY_MS = intPreferencesKey("subtitle_delay_ms")

    val NETWORK_CACHE_ENABLED = booleanPreferencesKey("network_cache_enabled")
    val CACHE_DURATION_SECONDS = intPreferencesKey("cache_duration_seconds")
    val READ_AHEAD_SECONDS = intPreferencesKey("read_ahead_seconds")
    val FORWARD_CACHE_MIB = intPreferencesKey("forward_cache_mib")
    val BACKWARD_CACHE_MIB = intPreferencesKey("backward_cache_mib")
    val RESUME_BUFFER_SECONDS = intPreferencesKey("resume_buffer_seconds")
    val NETWORK_TIMEOUT_SECONDS = intPreferencesKey("network_timeout_seconds")
    val VERIFY_TLS_CERTIFICATES = booleanPreferencesKey("verify_tls_certificates")

    val FOCUS_SCALE_ENABLED = booleanPreferencesKey("focus_scale_enabled")
    val CLOCK_IN_OSD = booleanPreferencesKey("clock_in_osd")
    val DISPLAY_LANGUAGE = stringPreferencesKey("display_language")
    val INTERFACE_SCALE = stringPreferencesKey("interface_scale")
    val THEME = stringPreferencesKey("theme")
    val BACKDROP_IMAGES = booleanPreferencesKey("backdrop_images")
    val BACKDROP_ROTATION_SECONDS = intPreferencesKey("backdrop_rotation_seconds")
    val WATCHED_INDICATORS = booleanPreferencesKey("watched_indicators")
    val REMEMBER_LAST_LIBRARY = booleanPreferencesKey("remember_last_library")
    val CACHE_HOME_CONTENT = booleanPreferencesKey("cache_home_content")

    val SCREENSAVER_TIMEOUT_MINUTES = intPreferencesKey("screensaver_timeout_minutes")
    val SCREENSAVER_CONTENT = stringPreferencesKey("screensaver_content")
    val SCREENSAVER_IMAGE_DURATION_SECONDS = intPreferencesKey("screensaver_image_duration_seconds")
    val SCREENSAVER_SHUFFLE = booleanPreferencesKey("screensaver_shuffle")
    val SCREENSAVER_AVOID_REPEATS = booleanPreferencesKey("screensaver_avoid_repeats")
    val SCREENSAVER_CLOCK = booleanPreferencesKey("screensaver_clock")

    val KIDS_MODE = booleanPreferencesKey("kids_mode")
}

class SettingsStore(
    private val store: DataStore<Preferences>,
) : PlayerSettingsRepository {

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("shoumei_settings") },
    )

    val settings: Flow<ClientSettings> = store.data.map(Preferences::toClientSettings)

    override suspend fun current(): ClientSettings = settings.first()

    suspend fun update(transform: (ClientSettings) -> ClientSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toClientSettings())) }
    }

    suspend fun save(settings: ClientSettings) {
        store.edit { prefs -> prefs.write(settings) }
    }

    suspend fun setFocusScaleEnabled(value: Boolean) {
        store.edit { prefs -> prefs[SettingsKeys.FOCUS_SCALE_ENABLED] = value }
    }

    suspend fun setClockInOsd(value: Boolean) {
        store.edit { prefs -> prefs[SettingsKeys.CLOCK_IN_OSD] = value }
    }

    override suspend fun setPreferredQuality(value: String) {
        val option = storedOption(value, PreferredQuality.Auto, PreferredQuality.entries.toTypedArray())
        store.edit { prefs -> prefs[SettingsKeys.PREFERRED_QUALITY] = option.storageId }
    }

    suspend fun setAutoplayNextEpisode(value: Boolean) {
        store.edit { prefs -> prefs[SettingsKeys.AUTOPLAY_NEXT_EPISODE] = value }
    }

    override suspend fun setAudioDelayMs(value: Int) {
        store.edit { prefs -> prefs[SettingsKeys.AUDIO_DELAY_MS] = value }
    }

    override suspend fun setSubtitleDelayMs(value: Int) {
        store.edit { prefs -> prefs[SettingsKeys.SUBTITLE_DELAY_MS] = value }
    }
}

private fun Preferences.toClientSettings(): ClientSettings = ClientSettings(
    preferredQuality = storedOption(
        this[SettingsKeys.PREFERRED_QUALITY],
        PreferredQuality.Auto,
        PreferredQuality.entries.toTypedArray(),
    ),
    maxStreamingBitrateMbps = this[SettingsKeys.MAX_STREAMING_BITRATE_MBPS],
    maxRemoteBitrateMbps = this[SettingsKeys.MAX_REMOTE_BITRATE_MBPS] ?: 20,
    refreshRateSwitching = storedOption(
        this[SettingsKeys.REFRESH_RATE_SWITCHING],
        RefreshRateSwitching.Disabled,
        RefreshRateSwitching.entries.toTypedArray(),
    ),
    autoplayNextEpisode = this[SettingsKeys.AUTOPLAY_NEXT_EPISODE] ?: true,
    resumeBehavior = storedOption(
        this[SettingsKeys.RESUME_BEHAVIOR],
        ResumeBehavior.Ask,
        ResumeBehavior.entries.toTypedArray(),
    ),
    seekIntervalSeconds = this[SettingsKeys.SEEK_INTERVAL_SECONDS] ?: 10,
    skipIntroPrompt = this[SettingsKeys.SKIP_INTRO_PROMPT] ?: true,
    rememberPlaybackSpeed = this[SettingsKeys.REMEMBER_PLAYBACK_SPEED] ?: false,
    renderingProfile = storedOption(
        this[SettingsKeys.RENDERING_PROFILE],
        RenderingProfile.Fast,
        RenderingProfile.entries.toTypedArray(),
    ),
    hardwareDecoding = storedOption(
        this[SettingsKeys.HARDWARE_DECODING],
        HardwareDecoding.MediaCodecCopy,
        HardwareDecoding.entries.toTypedArray(),
    ),
    hardwareCodecs = storedOption(
        this[SettingsKeys.HARDWARE_CODECS],
        HardwareCodecs.Automatic,
        HardwareCodecs.entries.toTypedArray(),
    ),
    hdrMode = storedOption(
        this[SettingsKeys.HDR_MODE],
        HdrMode.Automatic,
        HdrMode.entries.toTypedArray(),
    ),
    toneMapping = storedOption(
        this[SettingsKeys.TONE_MAPPING],
        ToneMapping.Automatic,
        ToneMapping.entries.toTypedArray(),
    ),
    deinterlaceMode = storedOption(
        this[SettingsKeys.DEINTERLACE_MODE],
        DeinterlaceMode.Automatic,
        DeinterlaceMode.entries.toTypedArray(),
    ),
    frameInterpolation = this[SettingsKeys.FRAME_INTERPOLATION] ?: false,
    preferredAudioLanguage = this[SettingsKeys.PREFERRED_AUDIO_LANGUAGE],
    rememberSeriesAudio = this[SettingsKeys.REMEMBER_SERIES_AUDIO] ?: true,
    pitchCorrection = this[SettingsKeys.PITCH_CORRECTION] ?: true,
    downmixStereo = this[SettingsKeys.DOWNMIX_STEREO] ?: false,
    dolbyDigitalPassthrough = this[SettingsKeys.DOLBY_DIGITAL_PASSTHROUGH] ?: false,
    dolbyDigitalPlusPassthrough = this[SettingsKeys.DOLBY_DIGITAL_PLUS_PASSTHROUGH] ?: false,
    dtsPassthrough = this[SettingsKeys.DTS_PASSTHROUGH] ?: false,
    audioDelayMs = this[SettingsKeys.AUDIO_DELAY_MS] ?: 0,
    preferredSubtitleLanguage = this[SettingsKeys.PREFERRED_SUBTITLE_LANGUAGE],
    subtitleMode = storedOption(
        this[SettingsKeys.SUBTITLE_MODE],
        SubtitleMode.Smart,
        SubtitleMode.entries.toTypedArray(),
    ),
    burnSubtitles = storedOption(
        this[SettingsKeys.BURN_SUBTITLES],
        BurnSubtitles.Automatic,
        BurnSubtitles.entries.toTypedArray(),
    ),
    subtitleSizePercent = this[SettingsKeys.SUBTITLE_SIZE_PERCENT] ?: 100,
    subtitleColor = storedOption(
        this[SettingsKeys.SUBTITLE_COLOR],
        SubtitleColor.White,
        SubtitleColor.entries.toTypedArray(),
    ),
    subtitleStroke = storedOption(
        this[SettingsKeys.SUBTITLE_STROKE],
        SubtitleStroke.Medium,
        SubtitleStroke.entries.toTypedArray(),
    ),
    boldSubtitles = this[SettingsKeys.BOLD_SUBTITLES] ?: false,
    scaleSubtitlesWithWindow = this[SettingsKeys.SCALE_SUBTITLES_WITH_WINDOW] ?: true,
    useVideoMargins = this[SettingsKeys.USE_VIDEO_MARGINS] ?: false,
    pgsDirectPlay = this[SettingsKeys.PGS_DIRECT_PLAY] ?: true,
    assSsaDirectPlay = storedOption(
        this[SettingsKeys.ASS_SSA_DIRECT_PLAY],
        AssSsaDirectPlay.Experimental,
        AssSsaDirectPlay.entries.toTypedArray(),
    ),
    subtitleDelayMs = this[SettingsKeys.SUBTITLE_DELAY_MS] ?: 0,
    networkCacheEnabled = this[SettingsKeys.NETWORK_CACHE_ENABLED] ?: true,
    cacheDurationSeconds = this[SettingsKeys.CACHE_DURATION_SECONDS] ?: 30,
    readAheadSeconds = this[SettingsKeys.READ_AHEAD_SECONDS] ?: 20,
    forwardCacheMiB = this[SettingsKeys.FORWARD_CACHE_MIB] ?: 64,
    backwardCacheMiB = this[SettingsKeys.BACKWARD_CACHE_MIB] ?: 32,
    resumeBufferSeconds = this[SettingsKeys.RESUME_BUFFER_SECONDS] ?: 1,
    networkTimeoutSeconds = this[SettingsKeys.NETWORK_TIMEOUT_SECONDS] ?: 15,
    verifyTlsCertificates = this[SettingsKeys.VERIFY_TLS_CERTIFICATES] ?: true,
    focusScaleEnabled = this[SettingsKeys.FOCUS_SCALE_ENABLED] ?: true,
    clockInOsd = this[SettingsKeys.CLOCK_IN_OSD] ?: true,
    displayLanguage = storedOption(
        this[SettingsKeys.DISPLAY_LANGUAGE],
        DisplayLanguage.English,
        DisplayLanguage.entries.toTypedArray(),
    ),
    interfaceScale = storedOption(
        this[SettingsKeys.INTERFACE_SCALE],
        InterfaceScale.Comfortable,
        InterfaceScale.entries.toTypedArray(),
    ),
    theme = storedOption(
        this[SettingsKeys.THEME],
        AppTheme.Dark,
        AppTheme.entries.toTypedArray(),
    ),
    backdropImages = this[SettingsKeys.BACKDROP_IMAGES] ?: true,
    backdropRotationSeconds = this[SettingsKeys.BACKDROP_ROTATION_SECONDS] ?: 20,
    watchedIndicators = this[SettingsKeys.WATCHED_INDICATORS] ?: true,
    rememberLastLibrary = this[SettingsKeys.REMEMBER_LAST_LIBRARY] ?: true,
    cacheHomeContent = this[SettingsKeys.CACHE_HOME_CONTENT] ?: true,
    screensaverTimeoutMinutes = this[SettingsKeys.SCREENSAVER_TIMEOUT_MINUTES] ?: 5,
    screensaverContent = storedOption(
        this[SettingsKeys.SCREENSAVER_CONTENT],
        ScreensaverContent.AllLibraries,
        ScreensaverContent.entries.toTypedArray(),
    ),
    screensaverImageDurationSeconds = this[SettingsKeys.SCREENSAVER_IMAGE_DURATION_SECONDS] ?: 20,
    screensaverShuffle = this[SettingsKeys.SCREENSAVER_SHUFFLE] ?: true,
    screensaverAvoidRepeats = this[SettingsKeys.SCREENSAVER_AVOID_REPEATS] ?: true,
    screensaverClock = this[SettingsKeys.SCREENSAVER_CLOCK] ?: true,
    kidsMode = this[SettingsKeys.KIDS_MODE] ?: false,
)

private fun androidx.datastore.preferences.core.MutablePreferences.write(settings: ClientSettings) {
    this[SettingsKeys.PREFERRED_QUALITY] = settings.preferredQuality.storageId
    settings.maxStreamingBitrateMbps?.let { this[SettingsKeys.MAX_STREAMING_BITRATE_MBPS] = it }
        ?: remove(SettingsKeys.MAX_STREAMING_BITRATE_MBPS)
    this[SettingsKeys.MAX_REMOTE_BITRATE_MBPS] = settings.maxRemoteBitrateMbps
    this[SettingsKeys.REFRESH_RATE_SWITCHING] = settings.refreshRateSwitching.storageId
    this[SettingsKeys.AUTOPLAY_NEXT_EPISODE] = settings.autoplayNextEpisode
    this[SettingsKeys.RESUME_BEHAVIOR] = settings.resumeBehavior.storageId
    this[SettingsKeys.SEEK_INTERVAL_SECONDS] = settings.seekIntervalSeconds
    this[SettingsKeys.SKIP_INTRO_PROMPT] = settings.skipIntroPrompt
    this[SettingsKeys.REMEMBER_PLAYBACK_SPEED] = settings.rememberPlaybackSpeed
    this[SettingsKeys.RENDERING_PROFILE] = settings.renderingProfile.storageId
    this[SettingsKeys.HARDWARE_DECODING] = settings.hardwareDecoding.storageId
    this[SettingsKeys.HARDWARE_CODECS] = settings.hardwareCodecs.storageId
    this[SettingsKeys.HDR_MODE] = settings.hdrMode.storageId
    this[SettingsKeys.TONE_MAPPING] = settings.toneMapping.storageId
    this[SettingsKeys.DEINTERLACE_MODE] = settings.deinterlaceMode.storageId
    this[SettingsKeys.FRAME_INTERPOLATION] = settings.frameInterpolation
    putNullable(SettingsKeys.PREFERRED_AUDIO_LANGUAGE, settings.preferredAudioLanguage)
    this[SettingsKeys.REMEMBER_SERIES_AUDIO] = settings.rememberSeriesAudio
    this[SettingsKeys.PITCH_CORRECTION] = settings.pitchCorrection
    this[SettingsKeys.DOWNMIX_STEREO] = settings.downmixStereo
    this[SettingsKeys.DOLBY_DIGITAL_PASSTHROUGH] = settings.dolbyDigitalPassthrough
    this[SettingsKeys.DOLBY_DIGITAL_PLUS_PASSTHROUGH] = settings.dolbyDigitalPlusPassthrough
    this[SettingsKeys.DTS_PASSTHROUGH] = settings.dtsPassthrough
    this[SettingsKeys.AUDIO_DELAY_MS] = settings.audioDelayMs
    putNullable(SettingsKeys.PREFERRED_SUBTITLE_LANGUAGE, settings.preferredSubtitleLanguage)
    this[SettingsKeys.SUBTITLE_MODE] = settings.subtitleMode.storageId
    this[SettingsKeys.BURN_SUBTITLES] = settings.burnSubtitles.storageId
    this[SettingsKeys.SUBTITLE_SIZE_PERCENT] = settings.subtitleSizePercent
    this[SettingsKeys.SUBTITLE_COLOR] = settings.subtitleColor.storageId
    this[SettingsKeys.SUBTITLE_STROKE] = settings.subtitleStroke.storageId
    this[SettingsKeys.BOLD_SUBTITLES] = settings.boldSubtitles
    this[SettingsKeys.SCALE_SUBTITLES_WITH_WINDOW] = settings.scaleSubtitlesWithWindow
    this[SettingsKeys.USE_VIDEO_MARGINS] = settings.useVideoMargins
    this[SettingsKeys.PGS_DIRECT_PLAY] = settings.pgsDirectPlay
    this[SettingsKeys.ASS_SSA_DIRECT_PLAY] = settings.assSsaDirectPlay.storageId
    this[SettingsKeys.SUBTITLE_DELAY_MS] = settings.subtitleDelayMs
    this[SettingsKeys.NETWORK_CACHE_ENABLED] = settings.networkCacheEnabled
    this[SettingsKeys.CACHE_DURATION_SECONDS] = settings.cacheDurationSeconds
    this[SettingsKeys.READ_AHEAD_SECONDS] = settings.readAheadSeconds
    this[SettingsKeys.FORWARD_CACHE_MIB] = settings.forwardCacheMiB
    this[SettingsKeys.BACKWARD_CACHE_MIB] = settings.backwardCacheMiB
    this[SettingsKeys.RESUME_BUFFER_SECONDS] = settings.resumeBufferSeconds
    this[SettingsKeys.NETWORK_TIMEOUT_SECONDS] = settings.networkTimeoutSeconds
    this[SettingsKeys.VERIFY_TLS_CERTIFICATES] = settings.verifyTlsCertificates
    this[SettingsKeys.FOCUS_SCALE_ENABLED] = settings.focusScaleEnabled
    this[SettingsKeys.CLOCK_IN_OSD] = settings.clockInOsd
    this[SettingsKeys.DISPLAY_LANGUAGE] = settings.displayLanguage.storageId
    this[SettingsKeys.INTERFACE_SCALE] = settings.interfaceScale.storageId
    this[SettingsKeys.THEME] = settings.theme.storageId
    this[SettingsKeys.BACKDROP_IMAGES] = settings.backdropImages
    this[SettingsKeys.BACKDROP_ROTATION_SECONDS] = settings.backdropRotationSeconds
    this[SettingsKeys.WATCHED_INDICATORS] = settings.watchedIndicators
    this[SettingsKeys.REMEMBER_LAST_LIBRARY] = settings.rememberLastLibrary
    this[SettingsKeys.CACHE_HOME_CONTENT] = settings.cacheHomeContent
    this[SettingsKeys.SCREENSAVER_TIMEOUT_MINUTES] = settings.screensaverTimeoutMinutes
    this[SettingsKeys.SCREENSAVER_CONTENT] = settings.screensaverContent.storageId
    this[SettingsKeys.SCREENSAVER_IMAGE_DURATION_SECONDS] = settings.screensaverImageDurationSeconds
    this[SettingsKeys.SCREENSAVER_SHUFFLE] = settings.screensaverShuffle
    this[SettingsKeys.SCREENSAVER_AVOID_REPEATS] = settings.screensaverAvoidRepeats
    this[SettingsKeys.SCREENSAVER_CLOCK] = settings.screensaverClock
    this[SettingsKeys.KIDS_MODE] = settings.kidsMode
}

private fun androidx.datastore.preferences.core.MutablePreferences.putNullable(
    key: Preferences.Key<String>,
    value: String?,
) {
    if (value == null) remove(key) else this[key] = value
}
