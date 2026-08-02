package com.maik205.shoumeiplayer.di

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import com.maik205.shoumeiplayer.BuildConfig
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.cache.ArtworkCache
import com.maik205.shoumeiplayer.data.cache.LibraryCacheStore
import com.maik205.shoumeiplayer.data.platform.AndroidDevicePlaybackCapabilityProvider
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.repo.JellyfinDiscoveryRepository
import com.maik205.shoumeiplayer.data.repo.JellyfinMediaCatalog
import com.maik205.shoumeiplayer.data.repo.JellyfinMediaDetailsRepository
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.repo.PlaybackRepository
import com.maik205.shoumeiplayer.data.session.PreferenceStore
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SessionManager
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.data.session.UserConfigurationStore
import com.maik205.shoumeiplayer.data.session.UserScope
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.player.PlayerEngineFactory
import com.maik205.shoumeiplayer.player.PlaybackOwnershipCoordinator
import com.maik205.shoumeiplayer.player.PlaybackProgressReporter
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.platform.media.AndroidAudioFocusPlayerEngine
import com.maik205.shoumeiplayer.platform.media.AndroidAudioRoutePlayerEngine
import com.maik205.shoumeiplayer.platform.media.AndroidFrameRatePlayerEngine
import com.maik205.shoumeiplayer.platform.media.AndroidCaptionPreferencesPlayerEngine
import com.maik205.shoumeiplayer.platform.media.AndroidHdrPolicyPlayerEngine
import com.maik205.shoumeiplayer.platform.media.AndroidPlaybackMetricsSink
import com.maik205.shoumeiplayer.platform.media.AudioServicePlayerEngine
import com.maik205.shoumeiplayer.platform.media.AudioResumptionStore
import com.maik205.shoumeiplayer.platform.network.AndroidNetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.maik205.shoumeiplayer.player.PlaybackMetricsSink

class AppContainer(
    private val context: Context,
    val artworkCache: ArtworkCache,
) {
    val appName: String = "Shoumei Player"
    val appVersion: String = BuildConfig.VERSION_NAME
    val audioDescriptionRequested: Boolean by lazy {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.getSystemService(AccessibilityManager::class.java)?.isAudioDescriptionRequested == true
    }
    val sessionStore: SessionStore by lazy { SessionStore(context) }
    val sessionManager: SessionManager by lazy { SessionManager(sessionStore) }

    /**
     * The account every *personal* preference is stored under. Derived from the live session, so
     * signing in, signing out or switching profiles re-points the settings and preference stores
     * without anything having to remember to tell them.
     */
    private val userScopes by lazy { sessionStore.session.map { session -> UserScope.of(session) } }
    val settingsStore: SettingsStore by lazy { SettingsStore(context, userScopes) }

    /**
     * Per-item/series/library playback state. Separate from [settingsStore] because it is churn:
     * bounded, evictable and never backed up, where a setting is deliberate and durable.
     */
    val preferenceStore: PreferenceStore by lazy { PreferenceStore(context) }
    internal val networkMonitor by lazy { AndroidNetworkMonitor(context) }
    fun newPlaybackMetricsSink(): PlaybackMetricsSink? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) AndroidPlaybackMetricsSink(context) else null
    val devicePlaybackCapabilities by lazy {
        devicePlaybackProfile.capabilities
    }
    val devicePlaybackProfile by lazy {
        AndroidDevicePlaybackCapabilityProvider(context).profile()
    }
    val libraryCacheStore: LibraryCacheStore by lazy { LibraryCacheStore(context) }
    val jellyfinClient: JellyfinClient by lazy {
        JellyfinClient(
            sessions = sessionStore,
            appName = appName,
            appVersion = appVersion,
            enableLogging = BuildConfig.DEBUG,
            onUnauthorized = sessionManager::expireSession,
        )
    }
    val imageUrlBuilder: ImageUrlBuilder by lazy {
        ImageUrlBuilder(
            serverUrlProvider = { cachedServerUrl },
            accessTokenProvider = { cachedAccessToken },
        )
    }
    val userConfigurationStore: UserConfigurationStore by lazy { UserConfigurationStore(context) }
    val authRepository: AuthRepository by lazy {
        AuthRepository(jellyfinClient, sessionStore, userConfigurationStore)
    }
    val discoveryRepository: JellyfinDiscoveryRepository by lazy { JellyfinDiscoveryRepository() }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(jellyfinClient) }
    val mediaCatalog by lazy { JellyfinMediaCatalog(libraryRepository, imageUrlBuilder) }
    val mediaDetailsRepository by lazy {
        JellyfinMediaDetailsRepository(libraryRepository, imageUrlBuilder)
    }
    val playbackRepository: PlaybackRepository by lazy {
        PlaybackRepository(
            client = jellyfinClient,
            deviceCapabilities = { devicePlaybackProfile.capabilities },
            // settingsStore.current() reads the live DataStore snapshot on every PlaybackInfo/
            // OpenLiveStream call, so a bitrate cap or codec choice changed on the Settings screen
            // takes effect on the very next resolve() without restarting playback.
            clientSettings = settingsStore::current,
        )
    }
    val playbackOwnershipCoordinator: PlaybackOwnershipCoordinator by lazy {
        PlaybackOwnershipCoordinator()
    }
    // Official libmpv uses the app-owned JNI bridge in both build types.
    // SimulatedPlayerEngine remains available for JVM unit tests.
    //
    // The engine chain is built with the default (mpv) backend rather than blocking here on the
    // persisted setting: PlayerViewModel.startInitialPlayback() reads settingsStore and calls
    // engine.configure() before anything is loaded, and SwitchingPlayerEngine swaps backends
    // inside configure() if the persisted choice differs -- so the real backend is always in
    // place before playback starts, without a synchronous DataStore read on the main thread.
    private val audioRouteEngine by lazy {
        AndroidAudioRoutePlayerEngine(context, hdrEngine)
    }
    private val frameRateEngine by lazy {
        AndroidFrameRatePlayerEngine(PlayerEngineFactory.create(context))
    }
    private val hdrEngine by lazy {
        AndroidHdrPolicyPlayerEngine(context, frameRateEngine)
    }
    val audioRouteLabel by lazy {
        audioRouteEngine.route
            .map { it.description }
            .stateIn(applicationScope, SharingStarted.Eagerly, audioRouteEngine.route.value.description)
    }
    private val captionEngine by lazy {
        AndroidCaptionPreferencesPlayerEngine(context, audioRouteEngine)
    }
    val effectiveHdrMode by lazy {
        hdrEngine.effectiveMode
            .stateIn(applicationScope, SharingStarted.Eagerly, hdrEngine.effectiveMode.value)
    }
    val playerEngine: PlayerEngine by lazy { AndroidAudioFocusPlayerEngine(context, captionEngine) }
    fun newAudioServicePlayerEngine(): PlayerEngine = AudioServicePlayerEngine(context) { activeAccountId }
    val progressReporter: PlaybackProgressReporter by lazy {
        PlaybackProgressReporter(playbackRepository)
    }

    /**
     * Lives as long as the process. Used for work that must survive the screen that started it —
     * notably the player's final stop report and transcode teardown.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var cachedServerUrl: String? = null
    @Volatile
    private var cachedAccessToken: String? = null
    @Volatile
    private var cachedAccountId: String? = null

    val activeAccountId: String?
        get() = cachedAccountId

    init {
        sessionStore.serverUrl
            .onEach { cachedServerUrl = it }
            .launchIn(applicationScope)
        sessionStore.session
            .onEach {
                cachedAccessToken = it?.accessToken
                cachedAccountId = it?.userId
                if (it == null) AudioResumptionStore(context).clear()
            }
            .launchIn(applicationScope)
        // "Retry when the server returns": a preference the user changed while the TV was offline
        // was accepted optimistically and queued, and the network coming back is the first moment
        // it can actually reach the account. Distinct-until-changed so a flapping connection does
        // not spam /Users/Configuration; the retry is a no-op when nothing is queued.
        applicationScope.launch {
            // Inside the coroutine so neither the connectivity callback nor the Ktor client is
            // constructed on whatever thread built the container.
            networkMonitor.snapshot
                .map { it.validated }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    migrateRetiredPreferencesToAccount()
                    authRepository.retryPendingUserConfiguration()
                }
        }
    }

    /**
     * One-time upgrade path for installs that set preferred audio/subtitle language, subtitle mode
     * or next-episode autoplay back when those were local settings. They now live on the Jellyfin
     * account, so without this the viewer silently loses four choices they had already made.
     *
     * Only fields the account has not set are seeded: an account value was chosen more recently, on
     * a client that already understood these as account-level, and must win. The local copies are
     * cleared only after the write lands, so a failed attempt is retried on the next connection
     * rather than dropping the values.
     */
    private suspend fun migrateRetiredPreferencesToAccount() {
        val retired = settingsStore.retiredAccountPreferences() ?: return
        val result = authRepository.editUserConfiguration { configuration ->
            configuration.copy(
                audioLanguagePreference = configuration.audioLanguagePreference
                    ?: retired.audioLanguage,
                subtitleLanguagePreference = configuration.subtitleLanguagePreference
                    ?: retired.subtitleLanguage,
                subtitleMode = configuration.subtitleMode ?: retired.subtitleMode,
                // Only a local `false` is evidence of a choice. The old store wrote every key on
                // every save, so a stored `true` is indistinguishable from never having touched the
                // toggle -- carrying that over would flip an account the viewer had deliberately
                // set to off on another client.
                enableNextEpisodeAutoPlay = if (retired.autoplayNextEpisode == false) {
                    false
                } else {
                    configuration.enableNextEpisodeAutoPlay
                },
            )
        }
        if (result is ApiResult.Success) settingsStore.clearRetiredAccountPreferences()
    }

    fun clearArtworkCache() {
        applicationScope.launch(Dispatchers.IO) {
            artworkCache.clear()
        }
    }
}
