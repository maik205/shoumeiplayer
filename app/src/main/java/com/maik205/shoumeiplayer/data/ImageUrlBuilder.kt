package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto

/** Builds Jellyfin image URLs against the currently stored server URL. */
class ImageUrlBuilder(private val serverUrlProvider: () -> String?) {

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

    /** `/Items/{id}/Images/Backdrop/{index}` — the indexed path form (§6 of the API surface). */
    fun backdropAtIndex(itemId: String, index: Int, tag: String?, maxWidth: Int = 1280): String? =
        imageAtIndex(itemId, "Backdrop", index, tag, maxWidth)

    /** `/Items/{id}/Images/Chapter/{index}` — `tag` comes from `ChapterInfoDto.imageTag`. */
    fun chapterImage(itemId: String, chapterIndex: Int, tag: String?, maxWidth: Int = 320): String? =
        imageAtIndex(itemId, "Chapter", chapterIndex, tag, maxWidth)

    fun image(itemId: String, type: String, tag: String?, maxWidth: Int): String? {
        val server = serverUrlProvider() ?: return null
        val base = "$server/Items/$itemId/Images/$type?maxWidth=$maxWidth&quality=90"
        return if (tag != null) "$base&tag=$tag" else base
    }

    fun imageAtIndex(itemId: String, type: String, index: Int, tag: String?, maxWidth: Int): String? {
        val server = serverUrlProvider() ?: return null
        val base = "$server/Items/$itemId/Images/$type/$index?maxWidth=$maxWidth&quality=90"
        return if (tag != null) "$base&tag=$tag" else base
    }

    // --- DTO-aware fallback chains. Each returns the first link that has BOTH an id and a tag. ---

    /** Episode → season/series thumb. Never pairs a parent tag with the child's id. */
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
            ?: item.seriesPrimaryImageTag?.let { t -> item.seriesId?.let { primary(it, t, maxWidth) } }
            ?: item.parentPrimaryImageTag
                ?.let { t -> item.parentPrimaryImageItemId?.let { primary(it, t, maxWidth) } }

    fun logoWithParentFallback(item: BaseItemDto, maxWidth: Int = 480): String? =
        item.imageTags["Logo"]?.let { logo(item.id, it, maxWidth) }
            ?: item.parentLogoImageTag?.let { t -> item.parentLogoItemId?.let { logo(it, t, maxWidth) } }
}
