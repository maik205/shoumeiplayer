package com.maik205.shoumeiplayer.data.cache

import android.content.Context
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.app.ActivityManager
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
    private val memoryManager = applicationContext.getSystemService(ActivityManager::class.java)
    private val budget = resolveArtworkCacheBudget(
        isLowRamDevice = memoryManager?.isLowRamDevice == true,
        memoryClassMiB = memoryManager?.memoryClass ?: 256,
    )

    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() {
            clear()
        }
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) clear()
        }
    }

    init {
        applicationContext.registerComponentCallbacks(memoryCallbacks)
    }

    val imageLoader: ImageLoader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ImageLoader.Builder(applicationContext)
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_FETCHES))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(MAX_CONCURRENT_DECODES))
            .components {
                add(ArtworkCacheKeyInterceptor())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(applicationContext, budget.memoryCachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(applicationContext.cacheDir.resolve(CACHE_DIRECTORY).toOkioPath())
                    .maxSizePercent(DISK_CACHE_FREE_SPACE_PERCENT)
                    .minimumMaxSizeBytes(budget.minimumDiskCacheBytes)
                    .maximumMaxSizeBytes(budget.maximumDiskCacheBytes)
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
        const val DISK_CACHE_FREE_SPACE_PERCENT = 0.02
        const val MIN_DISK_CACHE_BYTES = 10L * 1024 * 1024
        const val MAX_DISK_CACHE_BYTES = 250L * 1024 * 1024
        const val MAX_CONCURRENT_FETCHES = 6
        val MAX_CONCURRENT_DECODES = minOf(Runtime.getRuntime().availableProcessors(), 4)
    }
}

internal data class ArtworkCacheBudget(
    val memoryCachePercent: Double,
    val minimumDiskCacheBytes: Long,
    val maximumDiskCacheBytes: Long,
)

internal fun resolveArtworkCacheBudget(
    isLowRamDevice: Boolean,
    memoryClassMiB: Int,
): ArtworkCacheBudget {
    val constrained = isLowRamDevice || memoryClassMiB <= 256
    return if (constrained) {
        ArtworkCacheBudget(
            memoryCachePercent = 0.10,
            minimumDiskCacheBytes = 5L * 1024 * 1024,
            maximumDiskCacheBytes = 100L * 1024 * 1024,
        )
    } else {
        ArtworkCacheBudget(
            memoryCachePercent = 0.20,
            minimumDiskCacheBytes = 10L * 1024 * 1024,
            maximumDiskCacheBytes = 250L * 1024 * 1024,
        )
    }
}
