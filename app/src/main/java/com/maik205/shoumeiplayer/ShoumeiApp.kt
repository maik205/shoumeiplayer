package com.maik205.shoumeiplayer

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.maik205.shoumeiplayer.data.cache.ArtworkCache
import com.maik205.shoumeiplayer.di.AppContainer

class ShoumeiApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer

    private val artworkCache by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ArtworkCache(this)
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, artworkCache)
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader =
        artworkCache.imageLoader
}
