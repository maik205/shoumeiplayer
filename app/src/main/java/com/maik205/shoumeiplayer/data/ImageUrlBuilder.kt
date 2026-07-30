package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto

/** Builds Jellyfin image URLs against the currently stored server URL. */
class ImageUrlBuilder(
    private val serverUrlProvider: () -> String?,
    private val accessTokenProvider: () -> String? = { null },
) {

    /** Server-provided default artwork. Jellyfin serves a flat 404 when no splash image is set. */
    fun serverSplashscreen(): String? =
        serverUrlProvider()?.let { withToken("$it/Branding/Splashscreen") }

    fun primary(itemId: String, tag: String?, maxWidth: Int = 400): String? =
        image(itemId, "Primary", tag, maxWidth)

    fun backdrop(itemId: String, tag: String?, maxWidth: Int = 1280): String? =
        image(itemId, "Backdrop", tag, maxWidth)

    fun thumb(itemId: String, tag: String?, maxWidth: Int = 640): String? =
        image(itemId, "Thumb", tag, maxWidth)

    fun logo(itemId: String, tag: String?, maxWidth: Int = 480): String? =
        image(itemId, "Logo", tag, maxWidth)

    fun personPrimary(personId: String, tag: String?, maxWidth: Int = 240): String? =
        image(personId, "Primary", tag, maxWidth)

    /** Public profile image endpoint (`GET /UserImage`), distinct from item artwork. */
    fun userPrimary(userId: String, tag: String?): String? {
        val server = serverUrlProvider() ?: return null
        val base = "$server/UserImage?userId=$userId"
        return withToken(if (tag != null) "$base&tag=$tag" else base)
    }

    /** `/Items/{id}/Images/Backdrop/{index}` — the indexed path form (§6 of the API surface). */
    fun backdropAtIndex(itemId: String, index: Int, tag: String?, maxWidth: Int = 1280): String? =
        imageAtIndex(itemId, "Backdrop", index, tag, maxWidth)

    /** `/Items/{id}/Images/Chapter/{index}` — `tag` comes from `ChapterInfoDto.imageTag`. */
    fun chapterImage(itemId: String, chapterIndex: Int, tag: String?, maxWidth: Int = 320): String? =
        imageAtIndex(itemId, "Chapter", chapterIndex, tag, maxWidth)

    fun image(itemId: String, type: String, tag: String?, maxWidth: Int): String? {
        val server = serverUrlProvider() ?: return null
        val base = "$server/Items/$itemId/Images/$type?maxWidth=$maxWidth&quality=90"
        return withToken(if (tag != null) "$base&tag=$tag" else base)
    }

    fun imageAtIndex(itemId: String, type: String, index: Int, tag: String?, maxWidth: Int): String? {
        val server = serverUrlProvider() ?: return null
        val base = "$server/Items/$itemId/Images/$type/$index?maxWidth=$maxWidth&quality=90"
        return withToken(if (tag != null) "$base&tag=$tag" else base)
    }

    // --- DTO-aware fallback chains. Each returns the first link that has BOTH an id and a tag. ---

    /** Episode → season/series thumb. Never pairs a parent tag with the child's id. */
    /**
     * Episode preview still.
     *
     * Jellyfin maps an episode-local `SxxExx-thumb` file to the episode item's `Primary` image,
     * despite the filename. Prefer that real still before any `Thumb`/backdrop inherited from the
     * season or series, and never fall back to a portrait series poster for a 16:9 preview.
     */
    fun episodePreview(item: BaseItemDto, maxWidth: Int = 720): String? =
        item.imageTags["Primary"]?.let { primary(item.id, it, maxWidth) }
            ?: item.imageTags["Thumb"]?.let { thumb(item.id, it, maxWidth) }
            ?: item.backdropImageTags.firstOrNull()?.let { backdrop(item.id, it, maxWidth) }
            ?: item.parentThumbImageTag
                ?.let { tag -> item.parentThumbItemId?.let { thumb(it, tag, maxWidth) } }
            ?: item.seriesThumbImageTag
                ?.let { tag -> item.seriesId?.let { thumb(it, tag, maxWidth) } }
            ?: item.parentBackdropImageTags.firstOrNull()
                ?.let { tag -> item.parentBackdropItemId?.let { backdrop(it, tag, maxWidth) } }

    fun thumbWithSeriesFallback(item: BaseItemDto, maxWidth: Int = 640): String? =
        item.imageTags["Thumb"]?.let { thumb(item.id, it, maxWidth) }
            ?: item.parentThumbImageTag?.let { t -> item.parentThumbItemId?.let { thumb(it, t, maxWidth) } }
            ?: item.seriesThumbImageTag?.let { t -> item.seriesId?.let { thumb(it, t, maxWidth) } }

    /** Episode → season → series backdrop (§5.1 ambient wash, §5.2 stage). */
    fun backdropWithParentFallback(item: BaseItemDto, maxWidth: Int = 1280): String? =
        item.backdropImageTags.firstOrNull()?.let { backdrop(item.id, it, maxWidth) }
            ?: item.parentBackdropImageTags.firstOrNull()
                ?.let { t -> item.parentBackdropItemId?.let { backdrop(it, t, maxWidth) } }

    /** Own poster → series poster → parent poster. Replaces the three ad-hoc copies of this chain. */
    fun primaryWithParentFallback(item: BaseItemDto, maxWidth: Int = 320): String? =
        item.imageTags["Primary"]?.let { primary(item.id, it, maxWidth) }
            ?: item.albumPrimaryImageTag?.let { t -> item.albumId?.let { primary(it, t, maxWidth) } }
            ?: item.seriesPrimaryImageTag?.let { t -> item.seriesId?.let { primary(it, t, maxWidth) } }
            ?: item.parentPrimaryImageTag
                ?.let { t -> item.parentPrimaryImageItemId?.let { primary(it, t, maxWidth) } }

    /**
     * Episodes use the series-owned logo URL when Jellyfin supplies one.
     *
     * Jellyfin can expose the inherited series logo in an episode's `ImageTags` as well as through
     * `ParentLogoItemId`/`ParentLogoImageTag`. Addressing it through the episode ID produces a
     * different Coil cache key for every episode even though the response is the same image.
     * Using the declared parent owner keeps one stable URL, network fetch, and disk entry per
     * series. Non-episode items continue to prefer their own logo.
     */
    fun logoWithParentFallback(item: BaseItemDto, maxWidth: Int = 480): String? {
        val ownLogo = item.imageTags["Logo"]?.let { logo(item.id, it, maxWidth) }
        val parentLogo = item.parentLogoImageTag
            ?.let { tag -> item.parentLogoItemId?.let { logo(it, tag, maxWidth) } }

        return if (item.type.equals("Episode", ignoreCase = true)) {
            parentLogo ?: ownLogo
        } else {
            ownLogo ?: parentLogo
        }
    }

    private fun withToken(url: String): String =
        accessTokenProvider()?.takeIf(String::isNotBlank)?.let {
            "$url${if ('?' in url) '&' else '?'}api_key=$it"
        } ?: url
}
