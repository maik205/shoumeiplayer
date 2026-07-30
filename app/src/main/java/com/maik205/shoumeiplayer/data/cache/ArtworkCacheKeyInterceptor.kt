package com.maik205.shoumeiplayer.data.cache

import coil3.intercept.Interceptor
import coil3.request.ImageResult

/**
 * Keeps authenticated Jellyfin artwork stable across session-token changes.
 *
 * Coil otherwise uses the complete request URL for both its memory and disk cache keys. Jellyfin's
 * `api_key` changes when a session is replaced even though the requested artwork does not, which
 * leaves duplicate source files in the shared cache. The network request retains its credential;
 * only cache identity omits it.
 */
internal class ArtworkCacheKeyInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val cacheKey = canonicalArtworkCacheKey(request.data) ?: return chain.proceed()
        if (request.memoryCacheKey != null && request.diskCacheKey != null) {
            return chain.proceed()
        }

        val keyedRequest = request.newBuilder()
            .apply {
                if (request.memoryCacheKey == null) memoryCacheKey(cacheKey)
                if (request.diskCacheKey == null) diskCacheKey(cacheKey)
            }
            .build()
        return chain.withRequest(keyedRequest).proceed()
    }
}

/**
 * Returns an authenticated HTTP URL without its `api_key`, or null when no rewrite is needed.
 *
 * All response-defining components remain in the key: server origin, item/parent owner, image
 * type and index, tag, width, and quality.
 */
internal fun canonicalArtworkCacheKey(data: Any): String? {
    val url = data.toString()
    if (!url.startsWith("http://", ignoreCase = true) &&
        !url.startsWith("https://", ignoreCase = true)
    ) {
        return null
    }

    val fragmentStart = url.indexOf('#')
    val fragment = if (fragmentStart >= 0) url.substring(fragmentStart) else ""
    val withoutFragment = if (fragmentStart >= 0) url.substring(0, fragmentStart) else url
    val queryStart = withoutFragment.indexOf('?')
    if (queryStart < 0) return null

    var removedCredential = false
    val retainedParameters = withoutFragment
        .substring(queryStart + 1)
        .split('&')
        .filter { parameter ->
            val isCredential = parameter
                .substringBefore('=')
                .equals("api_key", ignoreCase = true)
            if (isCredential) removedCredential = true
            !isCredential && parameter.isNotEmpty()
        }
    if (!removedCredential) return null

    return buildString {
        append(withoutFragment, 0, queryStart)
        if (retainedParameters.isNotEmpty()) {
            append('?')
            append(retainedParameters.joinToString("&"))
        }
        append(fragment)
    }
}
