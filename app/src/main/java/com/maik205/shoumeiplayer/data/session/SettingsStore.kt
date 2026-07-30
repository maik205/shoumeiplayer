package com.maik205.shoumeiplayer.data.session

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
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

@Immutable
data class ClientSettings(
    /** Label only: "Auto" | "4K" | "1080p" | "720p" | "480p". Bitrate mapping is player-owned. */
    val preferredQuality: String = "Auto",
    val maxStreamingBitrateMbps: Int? = null,
    val maxRemoteBitrateMbps: Int = 20,
    val refreshRateSwitching: String = "Disabled",
    val autoplayNextEpisode: Boolean = true,
    val resumeBehavior: String = "Ask",
    val seekIntervalSeconds: Int = 10,
    val skipIntroPrompt: Boolean = true,
    val rememberPlaybackSpeed: Boolean = false,

    val renderingProfile: String = "Fast",
    val hardwareDecoding: String = "MediaCodec copy",
    val hardwareCodecs: String = "Automatic",
    val hdrMode: String = "Automatic",
    val toneMapping: String = "Automatic",
    val deinterlaceMode: String = "Automatic",
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
    val subtitleMode: String = "Smart",
    val burnSubtitles: String = "Automatic",
    val subtitleSizePercent: Int = 100,
    val subtitleColor: String = "White",
    val subtitleStroke: String = "Medium",
    val boldSubtitles: Boolean = false,
    val scaleSubtitlesWithWindow: Boolean = true,
    val useVideoMargins: Boolean = false,
    val pgsDirectPlay: Boolean = true,
    val assSsaDirectPlay: String = "Experimental",
    val subtitleDelayMs: Int = 0,

    val networkCacheEnabled: Boolean = true,
    val cacheDurationSeconds: Int = 30,
    val readAheadSeconds: Int = 20,
    val forwardCacheMiB: Int = 64,
    val backwardCacheMiB: Int = 32,
    val resumeBufferSeconds: Int = 1,
    val networkTimeoutSeconds: Int = 15,
    val verifyTlsCertificates: Boolean = true,

    val focusScaleEnabled: Boolean = true,
    val clockInOsd: Boolean = true,
    val displayLanguage: String = "English",
    val interfaceScale: String = "Comfortable",
    val theme: String = "Dark",
    val backdropImages: Boolean = true,
    val backdropRotationSeconds: Int = 20,
    val watchedIndicators: Boolean = true,
    val rememberLastLibrary: Boolean = true,
    val cacheHomeContent: Boolean = true,

    val screensaverTimeoutMinutes: Int = 5,
    val screensaverContent: String = "All libraries",
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
class SettingsStore(private val store: DataStore<Preferences>) {

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("shoumei_settings") },
    )

    val settings: Flow<ClientSettings> = store.data.map(Preferences::toClientSettings)

    suspend fun current(): ClientSettings = settings.first()

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

    suspend fun setPreferredQuality(value: String) {
        store.edit { prefs -> prefs[SettingsKeys.PREFERRED_QUALITY] = value }
    }

    suspend fun setAutoplayNextEpisode(value: Boolean) {
        store.edit { prefs -> prefs[SettingsKeys.AUTOPLAY_NEXT_EPISODE] = value }
    }

    suspend fun setAudioDelayMs(value: Int) {
        store.edit { prefs -> prefs[SettingsKeys.AUDIO_DELAY_MS] = value }
    }

    suspend fun setSubtitleDelayMs(value: Int) {
        store.edit { prefs -> prefs[SettingsKeys.SUBTITLE_DELAY_MS] = value }
    }
}

private fun Preferences.toClientSettings(): ClientSettings = ClientSettings(
    preferredQuality = this[SettingsKeys.PREFERRED_QUALITY] ?: "Auto",
    maxStreamingBitrateMbps = this[SettingsKeys.MAX_STREAMING_BITRATE_MBPS],
    maxRemoteBitrateMbps = this[SettingsKeys.MAX_REMOTE_BITRATE_MBPS] ?: 20,
    refreshRateSwitching = this[SettingsKeys.REFRESH_RATE_SWITCHING] ?: "Disabled",
    autoplayNextEpisode = this[SettingsKeys.AUTOPLAY_NEXT_EPISODE] ?: true,
    resumeBehavior = this[SettingsKeys.RESUME_BEHAVIOR] ?: "Ask",
    seekIntervalSeconds = this[SettingsKeys.SEEK_INTERVAL_SECONDS] ?: 10,
    skipIntroPrompt = this[SettingsKeys.SKIP_INTRO_PROMPT] ?: true,
    rememberPlaybackSpeed = this[SettingsKeys.REMEMBER_PLAYBACK_SPEED] ?: false,
    renderingProfile = this[SettingsKeys.RENDERING_PROFILE] ?: "Fast",
    hardwareDecoding = this[SettingsKeys.HARDWARE_DECODING] ?: "MediaCodec copy",
    hardwareCodecs = this[SettingsKeys.HARDWARE_CODECS] ?: "Automatic",
    hdrMode = this[SettingsKeys.HDR_MODE] ?: "Automatic",
    toneMapping = this[SettingsKeys.TONE_MAPPING] ?: "Automatic",
    deinterlaceMode = this[SettingsKeys.DEINTERLACE_MODE] ?: "Automatic",
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
    subtitleMode = this[SettingsKeys.SUBTITLE_MODE] ?: "Smart",
    burnSubtitles = this[SettingsKeys.BURN_SUBTITLES] ?: "Automatic",
    subtitleSizePercent = this[SettingsKeys.SUBTITLE_SIZE_PERCENT] ?: 100,
    subtitleColor = this[SettingsKeys.SUBTITLE_COLOR] ?: "White",
    subtitleStroke = this[SettingsKeys.SUBTITLE_STROKE] ?: "Medium",
    boldSubtitles = this[SettingsKeys.BOLD_SUBTITLES] ?: false,
    scaleSubtitlesWithWindow = this[SettingsKeys.SCALE_SUBTITLES_WITH_WINDOW] ?: true,
    useVideoMargins = this[SettingsKeys.USE_VIDEO_MARGINS] ?: false,
    pgsDirectPlay = this[SettingsKeys.PGS_DIRECT_PLAY] ?: true,
    assSsaDirectPlay = this[SettingsKeys.ASS_SSA_DIRECT_PLAY] ?: "Experimental",
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
    displayLanguage = this[SettingsKeys.DISPLAY_LANGUAGE] ?: "English",
    interfaceScale = this[SettingsKeys.INTERFACE_SCALE] ?: "Comfortable",
    theme = this[SettingsKeys.THEME] ?: "Dark",
    backdropImages = this[SettingsKeys.BACKDROP_IMAGES] ?: true,
    backdropRotationSeconds = this[SettingsKeys.BACKDROP_ROTATION_SECONDS] ?: 20,
    watchedIndicators = this[SettingsKeys.WATCHED_INDICATORS] ?: true,
    rememberLastLibrary = this[SettingsKeys.REMEMBER_LAST_LIBRARY] ?: true,
    cacheHomeContent = this[SettingsKeys.CACHE_HOME_CONTENT] ?: true,
    screensaverTimeoutMinutes = this[SettingsKeys.SCREENSAVER_TIMEOUT_MINUTES] ?: 5,
    screensaverContent = this[SettingsKeys.SCREENSAVER_CONTENT] ?: "All libraries",
    screensaverImageDurationSeconds = this[SettingsKeys.SCREENSAVER_IMAGE_DURATION_SECONDS] ?: 20,
    screensaverShuffle = this[SettingsKeys.SCREENSAVER_SHUFFLE] ?: true,
    screensaverAvoidRepeats = this[SettingsKeys.SCREENSAVER_AVOID_REPEATS] ?: true,
    screensaverClock = this[SettingsKeys.SCREENSAVER_CLOCK] ?: true,
    kidsMode = this[SettingsKeys.KIDS_MODE] ?: false,
)

private fun androidx.datastore.preferences.core.MutablePreferences.write(settings: ClientSettings) {
    this[SettingsKeys.PREFERRED_QUALITY] = settings.preferredQuality
    settings.maxStreamingBitrateMbps?.let { this[SettingsKeys.MAX_STREAMING_BITRATE_MBPS] = it }
        ?: remove(SettingsKeys.MAX_STREAMING_BITRATE_MBPS)
    this[SettingsKeys.MAX_REMOTE_BITRATE_MBPS] = settings.maxRemoteBitrateMbps
    this[SettingsKeys.REFRESH_RATE_SWITCHING] = settings.refreshRateSwitching
    this[SettingsKeys.AUTOPLAY_NEXT_EPISODE] = settings.autoplayNextEpisode
    this[SettingsKeys.RESUME_BEHAVIOR] = settings.resumeBehavior
    this[SettingsKeys.SEEK_INTERVAL_SECONDS] = settings.seekIntervalSeconds
    this[SettingsKeys.SKIP_INTRO_PROMPT] = settings.skipIntroPrompt
    this[SettingsKeys.REMEMBER_PLAYBACK_SPEED] = settings.rememberPlaybackSpeed
    this[SettingsKeys.RENDERING_PROFILE] = settings.renderingProfile
    this[SettingsKeys.HARDWARE_DECODING] = settings.hardwareDecoding
    this[SettingsKeys.HARDWARE_CODECS] = settings.hardwareCodecs
    this[SettingsKeys.HDR_MODE] = settings.hdrMode
    this[SettingsKeys.TONE_MAPPING] = settings.toneMapping
    this[SettingsKeys.DEINTERLACE_MODE] = settings.deinterlaceMode
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
    this[SettingsKeys.SUBTITLE_MODE] = settings.subtitleMode
    this[SettingsKeys.BURN_SUBTITLES] = settings.burnSubtitles
    this[SettingsKeys.SUBTITLE_SIZE_PERCENT] = settings.subtitleSizePercent
    this[SettingsKeys.SUBTITLE_COLOR] = settings.subtitleColor
    this[SettingsKeys.SUBTITLE_STROKE] = settings.subtitleStroke
    this[SettingsKeys.BOLD_SUBTITLES] = settings.boldSubtitles
    this[SettingsKeys.SCALE_SUBTITLES_WITH_WINDOW] = settings.scaleSubtitlesWithWindow
    this[SettingsKeys.USE_VIDEO_MARGINS] = settings.useVideoMargins
    this[SettingsKeys.PGS_DIRECT_PLAY] = settings.pgsDirectPlay
    this[SettingsKeys.ASS_SSA_DIRECT_PLAY] = settings.assSsaDirectPlay
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
    this[SettingsKeys.DISPLAY_LANGUAGE] = settings.displayLanguage
    this[SettingsKeys.INTERFACE_SCALE] = settings.interfaceScale
    this[SettingsKeys.THEME] = settings.theme
    this[SettingsKeys.BACKDROP_IMAGES] = settings.backdropImages
    this[SettingsKeys.BACKDROP_ROTATION_SECONDS] = settings.backdropRotationSeconds
    this[SettingsKeys.WATCHED_INDICATORS] = settings.watchedIndicators
    this[SettingsKeys.REMEMBER_LAST_LIBRARY] = settings.rememberLastLibrary
    this[SettingsKeys.CACHE_HOME_CONTENT] = settings.cacheHomeContent
    this[SettingsKeys.SCREENSAVER_TIMEOUT_MINUTES] = settings.screensaverTimeoutMinutes
    this[SettingsKeys.SCREENSAVER_CONTENT] = settings.screensaverContent
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
