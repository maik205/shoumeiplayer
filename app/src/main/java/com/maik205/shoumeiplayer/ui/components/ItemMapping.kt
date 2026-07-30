package com.maik205.shoumeiplayer.ui.components

import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.blurHash
import com.maik205.shoumeiplayer.util.Ticks

/** §5 — Coil `maxWidth` per card class: poster 320, wide 560, episode thumb 640, ambient 1280. */
private const val POSTER_MAX_WIDTH = 320
private const val WIDE_MAX_WIDTH = 560
private const val THUMB_MAX_WIDTH = 640
private const val AMBIENT_MAX_WIDTH = 1280

/**
 * §3.2 — a metadata line carries **at most one** `·`, and never a pipe. Two fields take the dot;
 * three or more take [SpecTab] instead, because a dot chain is a v1 tell (the genre bug put five
 * dots on one line). A list that needs its own separators gets its own line.
 */
const val SpecSeparator = " · "

/**
 * §3.2 — the tab rhythm for three or more fields: `2019    TV-MA    2h 44m`. Spacing does the
 * separating, so nothing decorative has to.
 */
const val SpecTab = "    "

/** §3.2 — genres are a list, so they get their own line, joined plainly. */
private const val GenreSeparator = ", "

/** §5.1 — the metadata line carries at most two genres, so it stays one line. */
private const val SPEC_LINE_GENRES = 2

/**
 * One resolved piece of artwork: the URL that won its fallback chain, paired with the blurhash of
 * the *same* tag that won it. Keeping them together is the point — a hash from a tag we did not
 * request would blur up in the wrong colour.
 */
private data class Art(val url: String, val blurHash: String?)

/**
 * Maps a Jellyfin [BaseItemDto] to the trimmed UI model consumed by [MediaCard].
 *
 * Image sizing follows §5 — don't pay for 1920 twice. Episode wide cards prefer the episode
 * preview still, while other wide cards prefer `Thumb`, then a backdrop, then the primary poster;
 * posters always request 320px. Every chain runs through
 * [ImageUrlBuilder]'s DTO-aware fallbacks, which only ever pair a parent tag with that parent's own
 * id. [MediaCardUi.backdropUrl] is what the Home ambient layer washes the room with (§5.1), falling
 * back to the parent series when the episode itself has no backdrop of its own.
 */
fun BaseItemDto.toCardUi(
    images: ImageUrlBuilder,
    wide: Boolean = false,
    labelInsideArt: Boolean = false,
): MediaCardUi {
    val subtitle = when (type) {
        "Episode" -> "S${parentIndexNumber ?: 0}:E${indexNumber ?: 0}"
        else -> productionYear?.toString()
    }
    val progress = userData?.playedPercentage
        ?.toFloat()
        ?.takeIf { it > 0f && it < 100f }
        ?.div(100f)

    val art = if (wide) {
        (if (type == "Episode") episodePreviewArt(images, THUMB_MAX_WIDTH) else null)
            ?: thumbArt(images, THUMB_MAX_WIDTH)
            ?: backdropArt(images, WIDE_MAX_WIDTH)
            ?: primaryArt(images, WIDE_MAX_WIDTH)
    } else {
        primaryArt(images, POSTER_MAX_WIDTH)
    }

    return MediaCardUi(
        id = id,
        title = name.orEmpty(),
        subtitle = subtitle,
        imageUrl = art?.url,
        progressFraction = progress,
        wide = wide,
        backdropUrl = backdropArt(images, AMBIENT_MAX_WIDTH)?.url,
        labelInsideArt = labelInsideArt,
        watched = userData?.played == true,
        blurHash = art?.blurHash,
        // Only a poster is drawn at the server's declared ratio; a wide card is always 280×158.
        aspect = primaryImageAspectRatio?.toFloat()?.takeIf { !wide },
        tagline = taglines.firstOrNull()?.takeIf { it.isNotBlank() },
    )
}

private fun BaseItemDto.episodePreviewArt(images: ImageUrlBuilder, maxWidth: Int): Art? {
    val (imageType, tag) = when {
        imageTags["Primary"] != null -> "Primary" to imageTags.getValue("Primary")
        imageTags["Thumb"] != null -> "Thumb" to imageTags.getValue("Thumb")
        backdropImageTags.firstOrNull() != null ->
            "Backdrop" to backdropImageTags.first()
        parentThumbImageTag != null && parentThumbItemId != null ->
            "Thumb" to parentThumbImageTag
        seriesThumbImageTag != null && seriesId != null ->
            "Thumb" to seriesThumbImageTag
        parentBackdropImageTags.firstOrNull() != null && parentBackdropItemId != null ->
            "Backdrop" to parentBackdropImageTags.first()
        else -> return null
    }
    val url = images.episodePreview(this, maxWidth) ?: return null
    return Art(url, imageBlurHashes.blurHash(imageType, tag))
}

/**
 * The tag [ImageUrlBuilder.thumbWithSeriesFallback] would have used — same order, same guards, so
 * the hash and the URL can never come from different links of the chain.
 */
private fun BaseItemDto.thumbArt(images: ImageUrlBuilder, maxWidth: Int): Art? {
    val tag = imageTags["Thumb"]
        ?: parentThumbImageTag?.takeIf { parentThumbItemId != null }
        ?: seriesThumbImageTag?.takeIf { seriesId != null }
        ?: return null
    val url = images.thumbWithSeriesFallback(this, maxWidth) ?: return null
    return Art(url, imageBlurHashes.blurHash("Thumb", tag))
}

/** Mirrors [ImageUrlBuilder.backdropWithParentFallback]. */
private fun BaseItemDto.backdropArt(images: ImageUrlBuilder, maxWidth: Int): Art? {
    val tag = backdropImageTags.firstOrNull()
        ?: parentBackdropImageTags.firstOrNull()?.takeIf { parentBackdropItemId != null }
        ?: return null
    val url = images.backdropWithParentFallback(this, maxWidth) ?: return null
    return Art(url, imageBlurHashes.blurHash("Backdrop", tag))
}

/** Mirrors [ImageUrlBuilder.primaryWithParentFallback]. */
private fun BaseItemDto.primaryArt(images: ImageUrlBuilder, maxWidth: Int): Art? {
    val tag = imageTags["Primary"]
        ?: seriesPrimaryImageTag?.takeIf { seriesId != null }
        ?: parentPrimaryImageTag?.takeIf { parentPrimaryImageItemId != null }
        ?: return null
    val url = images.primaryWithParentFallback(this, maxWidth) ?: return null
    return Art(url, imageBlurHashes.blurHash("Primary", tag))
}

/**
 * §3.2 — joins a metadata line from whatever actually exists, dropping blanks so a missing year
 * never leaves a dangling separator.
 *
 * Two surviving fields are joined by the single permitted `·`; three or more switch to the
 * [SpecTab] rhythm, so no line ever carries a chain of dots. Callers must not pass an
 * already-joined list as one part - give it its own line ([genreLine]).
 */
fun specLine(vararg parts: String?): String {
    val present = parts.filterNot { it.isNullOrBlank() }
    return when {
        present.size <= 2 -> present.joinToString(SpecSeparator)
        else -> present.joinToString(SpecTab)
    }
}

/** §5.1 / §5.2 — the metadata line for a focused item: never a genre chain, never two dots. */
fun BaseItemDto.toSpecLine(): String = specLine(
    type?.takeIf { it == "Episode" }?.let { "S${parentIndexNumber ?: 0}:E${indexNumber ?: 0}" }
        ?: seriesName,
    productionYear?.toString(),
    formatRuntime(runTimeTicks),
    officialRating,
)

/** §3.2 / §5.2 — genres take their own line, joined by `, `, never nested inside another join. */
fun BaseItemDto.genreLine(limit: Int = SPEC_LINE_GENRES): String? =
    genres.filter { it.isNotBlank() }
        .take(limit)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(GenreSeparator)

/** §3.1 — a duration reads the way a person says it: `2h 44m`, `1h`, `40m`. */
fun formatRuntime(ticks: Long?): String? {
    if (ticks == null || ticks <= 0L) return null
    val totalMinutes = Ticks.toMs(ticks) / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
        hours > 0L -> "${hours}h"
        else -> "${minutes}m"
    }
}
