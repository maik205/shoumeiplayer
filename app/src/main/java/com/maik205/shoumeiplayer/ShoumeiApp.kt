package com.maik205.shoumeiplayer

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.maik205.shoumeiplayer.data.cache.ArtworkCache
import com.maik205.shoumeiplayer.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShoumeiApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val artworkCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ArtworkCache(this)
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, artworkCache)
        // Eagerly pre-warm persistent session, settings, and home snapshot on background thread
        // so file I/O overlaps with activity creation and Compose runtime initialization.
        appScope.launch {
            runCatching { container.sessionStore.session.first() }
            runCatching { container.settingsStore.settings.first() }
            runCatching {
                val session = container.sessionStore.current()
                if (session != null) {
                    container.libraryCacheStore.readHome(session.serverUrl, session.userId)
                }
            }
        }
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        artworkCache.imageLoader
}
