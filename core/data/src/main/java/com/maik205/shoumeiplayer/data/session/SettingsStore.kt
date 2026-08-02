package com.maik205.shoumeiplayer.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
import com.maik205.shoumeiplayer.domain.settings.ColorPalette
import com.maik205.shoumeiplayer.domain.settings.DisplayLanguage
import com.maik205.shoumeiplayer.domain.settings.HardwareCodecs
import com.maik205.shoumeiplayer.domain.settings.HardwareDecoding
import com.maik205.shoumeiplayer.domain.settings.HdrMode
import com.maik205.shoumeiplayer.domain.settings.InterfaceScale
import com.maik205.shoumeiplayer.domain.settings.PreferredQuality
import com.maik205.shoumeiplayer.domain.settings.PlaybackBackend
import com.maik205.shoumeiplayer.domain.settings.PlayerSettingsRepository
import com.maik205.shoumeiplayer.domain.settings.RefreshRateSwitching
import com.maik205.shoumeiplayer.domain.settings.RenderingProfile
import com.maik205.shoumeiplayer.domain.settings.ResumeBehavior
import com.maik205.shoumeiplayer.domain.settings.ScreensaverContent
import com.maik205.shoumeiplayer.domain.settings.SubtitleColor
import com.maik205.shoumeiplayer.domain.settings.SubtitleStroke
import com.maik205.shoumeiplayer.domain.settings.ToneMapping
import com.maik205.shoumeiplayer.domain.settings.TlsTrustSource
import com.maik205.shoumeiplayer.domain.settings.storedOption
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Settings that describe **this television**, not the person watching it.
 *
 * These stay unprefixed and are shared by every profile on every server, because they are answers
 * to questions about hardware and network rather than about taste: which decoder this SoC can
 * actually use, how much RAM the read-ahead buffer may take, whose CA store to trust, which
 * language the launcher shows before anyone has signed in. Copying them per account would make a
 * second profile re-diagnose the same TV from scratch — and [SettingsKeys.DISPLAY_LANGUAGE] in
 * particular is read by `MainActivity` while the session may still be null.
 */
private object SettingsKeys {
    val PLAYBACK_BACKEND = stringPreferencesKey("playback_backend")
    val REFRESH_RATE_SWITCHING = stringPreferencesKey("refresh_rate_switching")

    val RENDERING_PROFILE = stringPreferencesKey("rendering_profile")
    val HARDWARE_DECODING = stringPreferencesKey("hardware_decoding")
    val HARDWARE_CODECS = stringPreferencesKey("hardware_codecs")
    val HDR_MODE = stringPreferencesKey("hdr_mode")
    val TONE_MAPPING = stringPreferencesKey("tone_mapping")
    val DEINTERLACE_MODE = stringPreferencesKey("deinterlace_mode")
    val FRAME_INTERPOLATION = booleanPreferencesKey("frame_interpolation")

    val PITCH_CORRECTION = booleanPreferencesKey("pitch_correction")
    val DOWNMIX_STEREO = booleanPreferencesKey("downmix_stereo")
    val DOLBY_DIGITAL_PASSTHROUGH = booleanPreferencesKey("dolby_digital_passthrough")
    val DOLBY_DIGITAL_PLUS_PASSTHROUGH = booleanPreferencesKey("dolby_digital_plus_passthrough")
    val DTS_PASSTHROUGH = booleanPreferencesKey("dts_passthrough")

    val NETWORK_CACHE_ENABLED = booleanPreferencesKey("network_cache_enabled")
    val CACHE_DURATION_SECONDS = intPreferencesKey("cache_duration_seconds")
    val READ_AHEAD_SECONDS = intPreferencesKey("read_ahead_seconds")
    val FORWARD_CACHE_MIB = intPreferencesKey("forward_cache_mib")
    val BACKWARD_CACHE_MIB = intPreferencesKey("backward_cache_mib")
    val RESUME_BUFFER_SECONDS = intPreferencesKey("resume_buffer_seconds")
    val NETWORK_TIMEOUT_SECONDS = intPreferencesKey("network_timeout_seconds")
    val VERIFY_TLS_CERTIFICATES = booleanPreferencesKey("verify_tls_certificates")
    val TLS_TRUST_SOURCE = stringPreferencesKey("tls_trust_source")

    val DISPLAY_LANGUAGE = stringPreferencesKey("display_language")

    /**
     * Bumped when the on-disk layout changes in a way that needs a one-time rewrite. Absent means
     * "pre-scoping": every personal setting is still sitting in the unprefixed slot, waiting for
     * [SettingsStore.adoptPreScopingSettings] to hand it to the signed-in account.
     */
    val SCOPING_VERSION = intPreferencesKey("settings_scoping_version")

    val ALL: List<Preferences.Key<*>> = listOf(
        PLAYBACK_BACKEND,
        REFRESH_RATE_SWITCHING,
        RENDERING_PROFILE,
        HARDWARE_DECODING,
        HARDWARE_CODECS,
        HDR_MODE,
        TONE_MAPPING,
        DEINTERLACE_MODE,
        FRAME_INTERPOLATION,
        PITCH_CORRECTION,
        DOWNMIX_STEREO,
        DOLBY_DIGITAL_PASSTHROUGH,
        DOLBY_DIGITAL_PLUS_PASSTHROUGH,
        DTS_PASSTHROUGH,
        NETWORK_CACHE_ENABLED,
        CACHE_DURATION_SECONDS,
        READ_AHEAD_SECONDS,
        FORWARD_CACHE_MIB,
        BACKWARD_CACHE_MIB,
        RESUME_BUFFER_SECONDS,
        NETWORK_TIMEOUT_SECONDS,
        VERIFY_TLS_CERTIFICATES,
        TLS_TRUST_SOURCE,
        DISPLAY_LANGUAGE,
        SCOPING_VERSION,
    )

    val NAMES: Set<String> = ALL.mapTo(mutableSetOf()) { it.name }
}

/** The layout version [SettingsKeys.SCOPING_VERSION] holds once personal keys have been scoped. */
private const val SCOPED_LAYOUT_VERSION = 1

/**
 * Settings that belong to a **person**, stored once per `(serverUrl, userId)`.
 *
 * Subtitle appearance, interface chrome, screensaver behaviour, resume/seek habits and the quality
 * ceiling a viewer is willing to accept are all matters of taste, and #85 is exactly the bug of one
 * viewer's taste following another into their profile. The same names are reused for every account;
 * [forScope] prefixes them with [UserScope.storagePrefix].
 *
 * The unprefixed instance ([UNSCOPED]) is byte-for-byte the pre-scoping layout. That is deliberate:
 * it is both what a signed-out app reads and writes, and the source the migration adopts from.
 */
private class UserKeys private constructor(prefix: String) {
    val PREFERRED_QUALITY = stringPreferencesKey("${prefix}preferred_quality")
    val MAX_STREAMING_BITRATE_MBPS = intPreferencesKey("${prefix}max_streaming_bitrate_mbps")
    val MAX_REMOTE_BITRATE_MBPS = intPreferencesKey("${prefix}max_remote_bitrate_mbps")
    val RESUME_BEHAVIOR = stringPreferencesKey("${prefix}resume_behavior")
    val SEEK_INTERVAL_SECONDS = intPreferencesKey("${prefix}seek_interval_seconds")
    val SKIP_INTRO_PROMPT = booleanPreferencesKey("${prefix}skip_intro_prompt")
    val REMEMBER_PLAYBACK_SPEED = booleanPreferencesKey("${prefix}remember_playback_speed")

    val REMEMBER_SERIES_AUDIO = booleanPreferencesKey("${prefix}remember_series_audio")
    val AUDIO_DELAY_MS = intPreferencesKey("${prefix}audio_delay_ms")

    val BURN_SUBTITLES = stringPreferencesKey("${prefix}burn_subtitles")
    val SUBTITLE_SIZE_PERCENT = intPreferencesKey("${prefix}subtitle_size_percent")
    val SUBTITLE_COLOR = stringPreferencesKey("${prefix}subtitle_color")
    val SUBTITLE_STROKE = stringPreferencesKey("${prefix}subtitle_stroke")
    val BOLD_SUBTITLES = booleanPreferencesKey("${prefix}bold_subtitles")
    val SCALE_SUBTITLES_WITH_WINDOW = booleanPreferencesKey("${prefix}scale_subtitles_with_window")
    val USE_VIDEO_MARGINS = booleanPreferencesKey("${prefix}use_video_margins")
    val PGS_DIRECT_PLAY = booleanPreferencesKey("${prefix}pgs_direct_play")
    val ASS_SSA_DIRECT_PLAY = stringPreferencesKey("${prefix}ass_ssa_direct_play")
    val SUBTITLE_DELAY_MS = intPreferencesKey("${prefix}subtitle_delay_ms")

    val FOCUS_SCALE_ENABLED = booleanPreferencesKey("${prefix}focus_scale_enabled")
    val CLOCK_IN_OSD = booleanPreferencesKey("${prefix}clock_in_osd")
    val INTERFACE_SCALE = stringPreferencesKey("${prefix}interface_scale")
    val THEME = stringPreferencesKey("${prefix}theme")
    val COLOR_PALETTE = stringPreferencesKey("${prefix}color_palette")
    val BACKDROP_IMAGES = booleanPreferencesKey("${prefix}backdrop_images")
    val BACKDROP_ROTATION_SECONDS = intPreferencesKey("${prefix}backdrop_rotation_seconds")
    val WATCHED_INDICATORS = booleanPreferencesKey("${prefix}watched_indicators")
    val REMEMBER_LAST_LIBRARY = booleanPreferencesKey("${prefix}remember_last_library")
    val CACHE_HOME_CONTENT = booleanPreferencesKey("${prefix}cache_home_content")

    val SCREENSAVER_TIMEOUT_MINUTES = intPreferencesKey("${prefix}screensaver_timeout_minutes")
    val SCREENSAVER_CONTENT = stringPreferencesKey("${prefix}screensaver_content")
    val SCREENSAVER_IMAGE_DURATION_SECONDS =
        intPreferencesKey("${prefix}screensaver_image_duration_seconds")
    val SCREENSAVER_SHUFFLE = booleanPreferencesKey("${prefix}screensaver_shuffle")
    val SCREENSAVER_AVOID_REPEATS = booleanPreferencesKey("${prefix}screensaver_avoid_repeats")
    val SCREENSAVER_CLOCK = booleanPreferencesKey("${prefix}screensaver_clock")

    companion object {
        /** Signed-out / pre-scoping slot. */
        val UNSCOPED = UserKeys("")

        private val cache = ConcurrentHashMap<String, UserKeys>()

        fun forScope(scope: UserScope?): UserKeys = when (scope) {
            null -> UNSCOPED
            else -> cache.getOrPut(scope.storagePrefix) { UserKeys(scope.storagePrefix) }
        }
    }
}

/**
 * Keys written by builds where audio/subtitle language, subtitle mode and next-episode autoplay
 * were still client settings. They now live in Jellyfin's `UserConfiguration` (see
 * [ClientSettings]), so nothing reads these any more; they are kept until the account has adopted
 * them rather than left behind to shadow the account value forever if the fields were ever
 * reinstated.
 */
private object RetiredKeys {
    val AUTOPLAY_NEXT_EPISODE = booleanPreferencesKey("autoplay_next_episode")
    val PREFERRED_AUDIO_LANGUAGE = stringPreferencesKey("preferred_audio_language")
    val PREFERRED_SUBTITLE_LANGUAGE = stringPreferencesKey("preferred_subtitle_language")
    val SUBTITLE_MODE = stringPreferencesKey("subtitle_mode")

    /**
     * Still owed to the Jellyfin account. These must survive the scoping migration untouched: the
     * account hand-off (`AppContainer.migrateRetiredPreferencesToAccount`) reads them from the
     * unprefixed slot, and moving or deleting them here would lose four settings the viewer had
     * already chosen.
     */
    val ALL: List<Preferences.Key<*>> = listOf(
        AUTOPLAY_NEXT_EPISODE,
        PREFERRED_AUDIO_LANGUAGE,
        PREFERRED_SUBTITLE_LANGUAGE,
        SUBTITLE_MODE,
    )

    /**
     * Kids Mode (#86). It was a toggle that gated nothing — no rating cap, no library restriction,
     * no PIN — so there is no behaviour and no value worth carrying onto an account. Unlike [ALL]
     * these are dropped outright by the scoping migration instead of being moved, so the dead key
     * does not get copied into every profile.
     */
    val REMOVED: List<Preferences.Key<*>> = listOf(
        booleanPreferencesKey("kids_mode"),
    )

    val NAMES: Set<String> = (ALL + REMOVED).mapTo(mutableSetOf()) { it.name }
    val REMOVED_NAMES: Set<String> = REMOVED.mapTo(mutableSetOf()) { it.name }
}

/**
 * What a pre-upgrade install had stored locally for the four preferences that now live on the
 * Jellyfin account. Held only long enough to seed the account with them once
 * ([SettingsStore.retiredAccountPreferences]); discarding these without migrating would silently
 * take away settings the viewer had already chosen.
 */
data class RetiredAccountPreferences(
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val subtitleMode: String? = null,
    val autoplayNextEpisode: Boolean? = null,
)

/**
 * Client-owned settings, split between one device-wide slot and one slot per signed-in account.
 *
 * [scopes] supplies the account currently signed in (see `AppContainer`); it emits `null` while
 * nobody is, in which case reads and writes fall back to the unprefixed device slot — the same
 * bytes a pre-scoping install already has, so onboarding keeps working before there is a user.
 *
 * The primary constructor takes the backing [DataStore] so JVM unit tests can substitute an
 * in-memory implementation; production code uses the [Context] secondary constructor, mirroring
 * [SessionStore]'s shape.
 */
class SettingsStore(
    private val store: DataStore<Preferences>,
    private val scopes: Flow<UserScope?> = flowOf(null),
) : PlayerSettingsRepository {

    constructor(context: Context, scopes: Flow<UserScope?> = flowOf(null)) : this(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("shoumei_settings") },
        scopes,
    )

    /**
     * Cleared once the on-disk marker has been seen, so the steady state costs nothing. It is only
     * an optimisation: [SettingsKeys.SCOPING_VERSION] inside the transaction is what actually makes
     * the migration run exactly once.
     */
    private val scopingMigrationPossible = AtomicBoolean(true)

    /**
     * Re-reads whenever the signed-in account changes, so switching profiles swaps the personal
     * half of the settings without restarting the app.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val settings: Flow<ClientSettings> = scopes
        .distinctUntilChanged()
        .flatMapLatest { scope ->
            // Migrating *before* subscribing to the preferences is what keeps the very first
            // emission -- the one MainActivity and PlaybackRepository act on -- from being the
            // pre-scoping snapshot read through a namespace that is still empty.
            scope?.let { adoptPreScopingSettings(it) }
            val user = UserKeys.forScope(scope)
            store.data.map { preferences -> preferences.toClientSettings(user) }
        }

    override suspend fun current(): ClientSettings = settings.first()

    suspend fun update(transform: (ClientSettings) -> ClientSettings) {
        val user = currentUserKeys()
        store.edit { prefs -> prefs.write(transform(prefs.toClientSettings(user)), user) }
    }

    suspend fun save(settings: ClientSettings) {
        val user = currentUserKeys()
        store.edit { prefs -> prefs.write(settings, user) }
    }

    /**
     * Hands the pre-scoping settings file to [scope] the first time anyone signs in after the
     * upgrade.
     *
     * Without this every existing install would look factory-reset: one populated global file that
     * suddenly nobody reads. Every unprefixed key that is not device-wide, not still owed to the
     * account and not explicitly retired is moved — including keys this build does not recognise,
     * which a newer or older build may have written, because dropping them would destroy settings
     * we merely failed to understand.
     *
     * Move and delete happen in a single DataStore transaction, so the old values are never gone
     * without the new ones being there: a crash mid-migration leaves the pre-scoping file intact
     * and the migration simply runs again next launch.
     */
    private suspend fun adoptPreScopingSettings(scope: UserScope) {
        if (!scopingMigrationPossible.get()) return
        store.edit { prefs ->
            if (prefs[SettingsKeys.SCOPING_VERSION] != null) return@edit
            // Snapshot first: the loop writes into the same MutablePreferences it is walking.
            prefs.asMap().toList().forEach { (key, value) ->
                if (key.name in SettingsKeys.NAMES) return@forEach
                if (key.name in RetiredKeys.NAMES) {
                    if (key.name in RetiredKeys.REMOVED_NAMES) prefs.remove(key)
                    return@forEach
                }
                if (key.name.startsWith(UserScope.NAMESPACE)) return@forEach
                // Preferences.Key is a name wrapper whose type parameter is erased, and the backing
                // map stores values as Any, so re-keying by name preserves the stored type. This is
                // what lets unrecognised keys move across without knowing what they hold.
                @Suppress("UNCHECKED_CAST")
                val scopedKey =
                    stringPreferencesKey(scope.storagePrefix + key.name) as Preferences.Key<Any>
                prefs[scopedKey] = value
                prefs.remove(key)
            }
            prefs[SettingsKeys.SCOPING_VERSION] = SCOPED_LAYOUT_VERSION
        }
        scopingMigrationPossible.set(false)
    }

    /**
     * Resolves the account to write under, migrating first so a fresh write can never be clobbered
     * by a migration that runs afterwards.
     */
    private suspend fun currentUserKeys(): UserKeys {
        val scope = scopes.first()
        scope?.let { adoptPreScopingSettings(it) }
        return UserKeys.forScope(scope)
    }

    /**
     * The pre-upgrade local values for the four preferences that moved to the Jellyfin account, or
     * null once nothing is left to migrate. Callers seed the account with these and then call
     * [clearRetiredAccountPreferences]; until that succeeds the values stay on disk, so a migration
     * that cannot reach the server is retried on a later launch instead of losing the settings.
     */
    suspend fun retiredAccountPreferences(): RetiredAccountPreferences? {
        val prefs = store.data.first()
        val retired = RetiredAccountPreferences(
            audioLanguage = prefs[RetiredKeys.PREFERRED_AUDIO_LANGUAGE],
            subtitleLanguage = prefs[RetiredKeys.PREFERRED_SUBTITLE_LANGUAGE],
            subtitleMode = prefs[RetiredKeys.SUBTITLE_MODE],
            autoplayNextEpisode = prefs[RetiredKeys.AUTOPLAY_NEXT_EPISODE],
        )
        return retired.takeIf { it != RetiredAccountPreferences() }
    }

    suspend fun clearRetiredAccountPreferences() {
        store.edit { prefs -> RetiredKeys.ALL.forEach { key -> prefs.remove(key) } }
    }

    suspend fun setFocusScaleEnabled(value: Boolean) {
        val user = currentUserKeys()
        store.edit { prefs -> prefs[user.FOCUS_SCALE_ENABLED] = value }
    }

    suspend fun setClockInOsd(value: Boolean) {
        val user = currentUserKeys()
        store.edit { prefs -> prefs[user.CLOCK_IN_OSD] = value }
    }

    override suspend fun setPreferredQuality(value: String) {
        val user = currentUserKeys()
        val option = storedOption(value, PreferredQuality.Auto, PreferredQuality.entries.toTypedArray())
        store.edit { prefs -> prefs[user.PREFERRED_QUALITY] = option.storageId }
    }

    override suspend fun setAudioDelayMs(value: Int) {
        val user = currentUserKeys()
        store.edit { prefs -> prefs[user.AUDIO_DELAY_MS] = value }
    }

    override suspend fun setSubtitleDelayMs(value: Int) {
        val user = currentUserKeys()
        store.edit { prefs -> prefs[user.SUBTITLE_DELAY_MS] = value }
    }
}

private fun Preferences.toClientSettings(user: UserKeys): ClientSettings = ClientSettings(
    playbackBackend = storedOption(
        this[SettingsKeys.PLAYBACK_BACKEND],
        PlaybackBackend.Mpv,
        PlaybackBackend.entries.toTypedArray(),
    ),
    preferredQuality = storedOption(
        this[user.PREFERRED_QUALITY],
        PreferredQuality.Auto,
        PreferredQuality.entries.toTypedArray(),
    ),
    maxStreamingBitrateMbps = this[user.MAX_STREAMING_BITRATE_MBPS],
    maxRemoteBitrateMbps = this[user.MAX_REMOTE_BITRATE_MBPS] ?: 20,
    refreshRateSwitching = storedOption(
        this[SettingsKeys.REFRESH_RATE_SWITCHING],
        RefreshRateSwitching.Disabled,
        RefreshRateSwitching.entries.toTypedArray(),
    ),
    resumeBehavior = storedOption(
        this[user.RESUME_BEHAVIOR],
        ResumeBehavior.Ask,
        ResumeBehavior.entries.toTypedArray(),
    ),
    seekIntervalSeconds = this[user.SEEK_INTERVAL_SECONDS] ?: 10,
    skipIntroPrompt = this[user.SKIP_INTRO_PROMPT] ?: true,
    rememberPlaybackSpeed = this[user.REMEMBER_PLAYBACK_SPEED] ?: false,
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
    rememberSeriesAudio = this[user.REMEMBER_SERIES_AUDIO] ?: true,
    pitchCorrection = this[SettingsKeys.PITCH_CORRECTION] ?: true,
    downmixStereo = this[SettingsKeys.DOWNMIX_STEREO] ?: false,
    dolbyDigitalPassthrough = this[SettingsKeys.DOLBY_DIGITAL_PASSTHROUGH] ?: false,
    dolbyDigitalPlusPassthrough = this[SettingsKeys.DOLBY_DIGITAL_PLUS_PASSTHROUGH] ?: false,
    dtsPassthrough = this[SettingsKeys.DTS_PASSTHROUGH] ?: false,
    audioDelayMs = this[user.AUDIO_DELAY_MS] ?: 0,
    burnSubtitles = storedOption(
        this[user.BURN_SUBTITLES],
        BurnSubtitles.Automatic,
        BurnSubtitles.entries.toTypedArray(),
    ),
    subtitleSizePercent = this[user.SUBTITLE_SIZE_PERCENT] ?: 100,
    subtitleColor = storedOption(
        this[user.SUBTITLE_COLOR],
        SubtitleColor.White,
        SubtitleColor.entries.toTypedArray(),
    ),
    subtitleStroke = storedOption(
        this[user.SUBTITLE_STROKE],
        SubtitleStroke.Medium,
        SubtitleStroke.entries.toTypedArray(),
    ),
    boldSubtitles = this[user.BOLD_SUBTITLES] ?: false,
    scaleSubtitlesWithWindow = this[user.SCALE_SUBTITLES_WITH_WINDOW] ?: true,
    useVideoMargins = this[user.USE_VIDEO_MARGINS] ?: false,
    pgsDirectPlay = this[user.PGS_DIRECT_PLAY] ?: true,
    assSsaDirectPlay = storedOption(
        this[user.ASS_SSA_DIRECT_PLAY],
        AssSsaDirectPlay.Experimental,
        AssSsaDirectPlay.entries.toTypedArray(),
    ),
    subtitleDelayMs = this[user.SUBTITLE_DELAY_MS] ?: 0,
    networkCacheEnabled = this[SettingsKeys.NETWORK_CACHE_ENABLED] ?: true,
    cacheDurationSeconds = this[SettingsKeys.CACHE_DURATION_SECONDS] ?: 30,
    readAheadSeconds = this[SettingsKeys.READ_AHEAD_SECONDS] ?: 20,
    forwardCacheMiB = this[SettingsKeys.FORWARD_CACHE_MIB] ?: 64,
    backwardCacheMiB = this[SettingsKeys.BACKWARD_CACHE_MIB] ?: 32,
    resumeBufferSeconds = this[SettingsKeys.RESUME_BUFFER_SECONDS] ?: 1,
    networkTimeoutSeconds = this[SettingsKeys.NETWORK_TIMEOUT_SECONDS] ?: 15,
    verifyTlsCertificates = this[SettingsKeys.VERIFY_TLS_CERTIFICATES] ?: true,
    tlsTrustSource = storedOption(
        this[SettingsKeys.TLS_TRUST_SOURCE],
        TlsTrustSource.AndroidSystem,
        TlsTrustSource.entries.toTypedArray(),
    ),
    focusScaleEnabled = this[user.FOCUS_SCALE_ENABLED] ?: true,
    clockInOsd = this[user.CLOCK_IN_OSD] ?: true,
    // The default here -- not ClientSettings' -- is what a fresh install actually gets: this is the
    // only path that turns persisted preferences into a ClientSettings, and DataStore holds no
    // display_language key until the user picks one. Hardcoding English here is what made a
    // brand-new install on a Japanese TV render in English (#98).
    displayLanguage = storedOption(
        this[SettingsKeys.DISPLAY_LANGUAGE],
        DisplayLanguage.SystemDefault,
        DisplayLanguage.entries.toTypedArray(),
    ),
    interfaceScale = storedOption(
        this[user.INTERFACE_SCALE],
        InterfaceScale.Comfortable,
        InterfaceScale.entries.toTypedArray(),
    ),
    theme = storedOption(
        this[user.THEME],
        AppTheme.Dark,
        AppTheme.entries.toTypedArray(),
    ),
    colorPalette = storedOption(
        this[user.COLOR_PALETTE],
        ColorPalette.Midnight,
        ColorPalette.entries.toTypedArray(),
    ),
    backdropImages = this[user.BACKDROP_IMAGES] ?: true,
    backdropRotationSeconds = this[user.BACKDROP_ROTATION_SECONDS] ?: 20,
    watchedIndicators = this[user.WATCHED_INDICATORS] ?: true,
    rememberLastLibrary = this[user.REMEMBER_LAST_LIBRARY] ?: true,
    cacheHomeContent = this[user.CACHE_HOME_CONTENT] ?: true,
    screensaverTimeoutMinutes = this[user.SCREENSAVER_TIMEOUT_MINUTES] ?: 5,
    screensaverContent = storedOption(
        this[user.SCREENSAVER_CONTENT],
        ScreensaverContent.AllLibraries,
        ScreensaverContent.entries.toTypedArray(),
    ),
    screensaverImageDurationSeconds = this[user.SCREENSAVER_IMAGE_DURATION_SECONDS] ?: 20,
    screensaverShuffle = this[user.SCREENSAVER_SHUFFLE] ?: true,
    screensaverAvoidRepeats = this[user.SCREENSAVER_AVOID_REPEATS] ?: true,
    screensaverClock = this[user.SCREENSAVER_CLOCK] ?: true,
)

private fun MutablePreferences.write(settings: ClientSettings, user: UserKeys) {
    this[SettingsKeys.PLAYBACK_BACKEND] = settings.playbackBackend.storageId
    this[user.PREFERRED_QUALITY] = settings.preferredQuality.storageId
    settings.maxStreamingBitrateMbps?.let { this[user.MAX_STREAMING_BITRATE_MBPS] = it }
        ?: remove(user.MAX_STREAMING_BITRATE_MBPS)
    this[user.MAX_REMOTE_BITRATE_MBPS] = settings.maxRemoteBitrateMbps
    this[SettingsKeys.REFRESH_RATE_SWITCHING] = settings.refreshRateSwitching.storageId
    this[user.RESUME_BEHAVIOR] = settings.resumeBehavior.storageId
    this[user.SEEK_INTERVAL_SECONDS] = settings.seekIntervalSeconds
    this[user.SKIP_INTRO_PROMPT] = settings.skipIntroPrompt
    this[user.REMEMBER_PLAYBACK_SPEED] = settings.rememberPlaybackSpeed
    this[SettingsKeys.RENDERING_PROFILE] = settings.renderingProfile.storageId
    this[SettingsKeys.HARDWARE_DECODING] = settings.hardwareDecoding.storageId
    this[SettingsKeys.HARDWARE_CODECS] = settings.hardwareCodecs.storageId
    this[SettingsKeys.HDR_MODE] = settings.hdrMode.storageId
    this[SettingsKeys.TONE_MAPPING] = settings.toneMapping.storageId
    this[SettingsKeys.DEINTERLACE_MODE] = settings.deinterlaceMode.storageId
    this[SettingsKeys.FRAME_INTERPOLATION] = settings.frameInterpolation
    this[user.REMEMBER_SERIES_AUDIO] = settings.rememberSeriesAudio
    this[SettingsKeys.PITCH_CORRECTION] = settings.pitchCorrection
    this[SettingsKeys.DOWNMIX_STEREO] = settings.downmixStereo
    this[SettingsKeys.DOLBY_DIGITAL_PASSTHROUGH] = settings.dolbyDigitalPassthrough
    this[SettingsKeys.DOLBY_DIGITAL_PLUS_PASSTHROUGH] = settings.dolbyDigitalPlusPassthrough
    this[SettingsKeys.DTS_PASSTHROUGH] = settings.dtsPassthrough
    this[user.AUDIO_DELAY_MS] = settings.audioDelayMs
    this[user.BURN_SUBTITLES] = settings.burnSubtitles.storageId
    this[user.SUBTITLE_SIZE_PERCENT] = settings.subtitleSizePercent
    this[user.SUBTITLE_COLOR] = settings.subtitleColor.storageId
    this[user.SUBTITLE_STROKE] = settings.subtitleStroke.storageId
    this[user.BOLD_SUBTITLES] = settings.boldSubtitles
    this[user.SCALE_SUBTITLES_WITH_WINDOW] = settings.scaleSubtitlesWithWindow
    this[user.USE_VIDEO_MARGINS] = settings.useVideoMargins
    this[user.PGS_DIRECT_PLAY] = settings.pgsDirectPlay
    this[user.ASS_SSA_DIRECT_PLAY] = settings.assSsaDirectPlay.storageId
    this[user.SUBTITLE_DELAY_MS] = settings.subtitleDelayMs
    this[SettingsKeys.NETWORK_CACHE_ENABLED] = settings.networkCacheEnabled
    this[SettingsKeys.CACHE_DURATION_SECONDS] = settings.cacheDurationSeconds
    this[SettingsKeys.READ_AHEAD_SECONDS] = settings.readAheadSeconds
    this[SettingsKeys.FORWARD_CACHE_MIB] = settings.forwardCacheMiB
    this[SettingsKeys.BACKWARD_CACHE_MIB] = settings.backwardCacheMiB
    this[SettingsKeys.RESUME_BUFFER_SECONDS] = settings.resumeBufferSeconds
    this[SettingsKeys.NETWORK_TIMEOUT_SECONDS] = settings.networkTimeoutSeconds
    this[SettingsKeys.VERIFY_TLS_CERTIFICATES] = settings.verifyTlsCertificates
    this[SettingsKeys.TLS_TRUST_SOURCE] = settings.tlsTrustSource.storageId
    this[user.FOCUS_SCALE_ENABLED] = settings.focusScaleEnabled
    this[user.CLOCK_IN_OSD] = settings.clockInOsd
    this[SettingsKeys.DISPLAY_LANGUAGE] = settings.displayLanguage.storageId
    this[user.INTERFACE_SCALE] = settings.interfaceScale.storageId
    this[user.THEME] = settings.theme.storageId
    this[user.COLOR_PALETTE] = settings.colorPalette.storageId
    this[user.BACKDROP_IMAGES] = settings.backdropImages
    this[user.BACKDROP_ROTATION_SECONDS] = settings.backdropRotationSeconds
    this[user.WATCHED_INDICATORS] = settings.watchedIndicators
    this[user.REMEMBER_LAST_LIBRARY] = settings.rememberLastLibrary
    this[user.CACHE_HOME_CONTENT] = settings.cacheHomeContent
    this[user.SCREENSAVER_TIMEOUT_MINUTES] = settings.screensaverTimeoutMinutes
    this[user.SCREENSAVER_CONTENT] = settings.screensaverContent.storageId
    this[user.SCREENSAVER_IMAGE_DURATION_SECONDS] = settings.screensaverImageDurationSeconds
    this[user.SCREENSAVER_SHUFFLE] = settings.screensaverShuffle
    this[user.SCREENSAVER_AVOID_REPEATS] = settings.screensaverAvoidRepeats
    this[user.SCREENSAVER_CLOCK] = settings.screensaverClock
    // The retired keys are deliberately NOT purged here. Saving any unrelated setting would
    // otherwise erase the values before the account migration has had a chance to read them --
    // and once erased they cannot be recovered. Only a completed migration clears them, via
    // SettingsStore.clearRetiredAccountPreferences.
}
