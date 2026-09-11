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
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SessionManager
import com.maik205.shoumeiplayer.data.session.SettingsStore
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.maik205.shoumeiplayer.player.PlaybackMetricsSink
import com.maik205.shoumeiplayer.remote.PairedRemoteStore
import com.maik205.shoumeiplayer.remote.RemoteCommandCoordinator
import com.maik205.shoumeiplayer.remote.RemoteDiscoveryService
import com.maik205.shoumeiplayer.remote.RemoteServer

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
    val settingsStore: SettingsStore by lazy { SettingsStore(context) }
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
    val authRepository: AuthRepository by lazy { AuthRepository(jellyfinClient, sessionStore) }
    val discoveryRepository: JellyfinDiscoveryRepository by lazy { JellyfinDiscoveryRepository() }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(jellyfinClient) }
    val mediaCatalog by lazy { JellyfinMediaCatalog(libraryRepository, imageUrlBuilder) }
    val mediaDetailsRepository by lazy {
        JellyfinMediaDetailsRepository(libraryRepository, imageUrlBuilder)
    }
    val playbackRepository: PlaybackRepository by lazy {
        PlaybackRepository(jellyfinClient) { devicePlaybackProfile.capabilities }
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

    val remoteCoordinator: RemoteCommandCoordinator by lazy {
        RemoteCommandCoordinator(applicationScope, context)
    }
    val pairedRemoteStore: PairedRemoteStore by lazy {
        PairedRemoteStore(context)
    }
    val remoteServer: RemoteServer by lazy {
        RemoteServer(applicationScope, remoteCoordinator, sessionStore, pairedRemoteStore)
    }
    val remoteDiscoveryService: RemoteDiscoveryService by lazy {
        RemoteDiscoveryService(context)
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
    }

    fun clearArtworkCache() {
        applicationScope.launch(Dispatchers.IO) {
            artworkCache.clear()
        }
    }
}
