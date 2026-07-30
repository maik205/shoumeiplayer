package com.maik205.shoumeiplayer.di

import android.content.Context
import com.maik205.shoumeiplayer.BuildConfig
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.repo.AuthRepository
import com.maik205.shoumeiplayer.data.repo.JellyfinDiscoveryRepository
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.data.repo.PlaybackRepository
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.player.MpvEngine
import com.maik205.shoumeiplayer.player.PlaybackProgressReporter
import com.maik205.shoumeiplayer.player.PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class AppContainer(private val context: Context) {
    val appName: String = "Shoumei Player"
    val appVersion: String = BuildConfig.VERSION_NAME
    val sessionStore: SessionStore by lazy { SessionStore(context) }
    val settingsStore: SettingsStore by lazy { SettingsStore(context) }
    val jellyfinClient: JellyfinClient by lazy { JellyfinClient(sessionStore, appName, appVersion) }
    val imageUrlBuilder: ImageUrlBuilder by lazy { ImageUrlBuilder { cachedServerUrl } }
    val authRepository: AuthRepository by lazy { AuthRepository(jellyfinClient, sessionStore) }
    val discoveryRepository: JellyfinDiscoveryRepository by lazy { JellyfinDiscoveryRepository() }
    val libraryRepository: LibraryRepository by lazy { LibraryRepository(jellyfinClient) }
    val playbackRepository: PlaybackRepository by lazy { PlaybackRepository(jellyfinClient) }
    // Official libmpv via the app-owned JNI bridge is the real engine in both build types.
    // MplayerEngine remains the
    // planned native successor behind this same PlayerEngine interface (see
    // docs/mplayer-integration.md); SimulatedPlayerEngine stays for JVM unit tests.
    val playerEngine: PlayerEngine by lazy { MpvEngine(context) }
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

    init {
        sessionStore.serverUrl
            .onEach { cachedServerUrl = it }
            .launchIn(applicationScope)
    }
}
