package com.maik205.shoumeiplayer.data.cache

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toOkioPath

/**
 * Owns the process-wide Coil loader used for Jellyfin artwork.
 *
 * A single loader is important because each loader otherwise owns separate memory and disk caches.
 * The disk cache is bounded by both free space and a hard cap so it remains useful on TV devices
 * without growing indefinitely.
 */
class ArtworkCache(context: Context) {
    private val applicationContext = context.applicationContext

    val imageLoader: ImageLoader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ImageLoader.Builder(applicationContext)
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_FETCHES))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_DECODES))
            .components {
                add(ArtworkCacheKeyInterceptor())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(applicationContext, MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve(CACHE_DIRECTORY).toOkioPath())
                    .maxSizePercent(DISK_CACHE_FREE_SPACE_PERCENT)
                    .minimumMaxSizeBytes(MIN_DISK_CACHE_BYTES)
                    .maximumMaxSizeBytes(MAX_DISK_CACHE_BYTES)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    /** Current decoded-memory plus downloaded-source cache usage, in bytes. */
    fun estimatedSizeBytes(): Long =
        (imageLoader.memoryCache?.size ?: 0L) + (imageLoader.diskCache?.size ?: 0L)

    /** Clears decoded images and their downloaded source files, returning the estimated freed size. */
    fun clear(): Long {
        val sizeBeforeClear = estimatedSizeBytes()
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
        return sizeBeforeClear
    }

    private companion object {
        // Keep Coil's conventional directory so an upgrade reuses already-downloaded artwork.
        const val CACHE_DIRECTORY = "image_cache"
        const val MEMORY_CACHE_PERCENT = 0.20
        const val DISK_CACHE_FREE_SPACE_PERCENT = 0.02
        const val MIN_DISK_CACHE_BYTES = 10L * 1024 * 1024
        const val MAX_DISK_CACHE_BYTES = 250L * 1024 * 1024
        const val MAX_CONCURRENT_FETCHES = 4
        const val MAX_CONCURRENT_DECODES = 2
    }
}
