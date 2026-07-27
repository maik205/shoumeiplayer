package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.api.dto.TrickplayInfoDto

/**
 * One trickplay thumbnail, located inside its tile sheet.
 *
 * Jellyfin packs `tileWidth × tileHeight` thumbnails into each sheet, left to right then top to
 * bottom. Drawing a preview therefore needs two things: which sheet to fetch ([tileIndex]) and which
 * rectangle of it to show ([left]/[top]/[width]/[height], in source pixels).
 */
data class TrickplayTile(
    /** Index of the thumbnail across the whole video — `position / interval`. */
    val thumbnailIndex: Int,
    /** Index of the tile sheet, i.e. the `{index}` path segment of the tile URL. */
    val tileIndex: Int,
    val row: Int,
    val column: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
)

/**
 * Pure geometry for Jellyfin trickplay sheets. No Android types, no network — everything here is a
 * function of the manifest [TrickplayInfoDto] the item query already returned.
 */
object TrickplayMath {

    /**
     * Locates the thumbnail covering [positionMs], or null when the manifest cannot be used
     * (zero interval, empty grid, no thumbnails).
     *
     * The index is clamped into `0..thumbnailCount-1` rather than returning null past the end: the
     * scrub playhead can legitimately sit a few hundred milliseconds beyond the last generated
     * thumbnail, and showing the final frame there beats blanking the preview.
     */
    fun tileAt(info: TrickplayInfoDto, positionMs: Long): TrickplayTile? {
        val perSheet = info.tileWidth * info.tileHeight
        if (info.interval <= 0 || perSheet <= 0 || info.thumbnailCount <= 0) return null
        if (info.width <= 0 || info.height <= 0) return null

        val thumbnailIndex = (positionMs.coerceAtLeast(0) / info.interval)
            .coerceAtMost((info.thumbnailCount - 1).toLong())
            .toInt()
        val offsetInSheet = thumbnailIndex % perSheet
        val row = offsetInSheet / info.tileWidth
        val column = offsetInSheet % info.tileWidth

        return TrickplayTile(
            thumbnailIndex = thumbnailIndex,
            tileIndex = thumbnailIndex / perSheet,
            row = row,
            column = column,
            left = column * info.width,
            top = row * info.height,
            width = info.width,
            height = info.height,
        )
    }

    /**
     * Picks the width band to load for a preview drawn [targetWidth] pixels wide: the widest band
     * that still fits, or — when every band is wider than the target — the narrowest one, so a
     * small preview never drags down a 640px sheet it cannot use.
     *
     * Keys of [bands] are the width in pixels as a string, exactly as Jellyfin serialises them.
     */
    fun selectBand(bands: Map<String, TrickplayInfoDto>, targetWidth: Int): TrickplayInfoDto? {
        val usable = bands.values.filter { it.width > 0 && it.thumbnailCount > 0 }
        if (usable.isEmpty()) return null
        return usable.filter { it.width <= targetWidth }.maxByOrNull { it.width }
            ?: usable.minByOrNull { it.width }
    }
}

/**
 * Builds `/Videos/{itemId}/Trickplay/{width}/{index}.jpg` URLs.
 *
 * `mediaSourceId` is the only query parameter the endpoint documents (jellyfin-openapi.json,
 * `GetTrickplayTileImage`). `api_key` is Jellyfin's universal query-string credential — the endpoint
 * requires `DefaultAuthorization` and Coil cannot be given the `Authorization` header the rest of
 * this client sends, exactly as with the stream and external-subtitle URLs.
 */
object TrickplayUrl {
    /**
     * [thumbnailWidth] is the `{width}` path segment: the width of a *single thumbnail*
     * ([TrickplayInfoDto.width], which is also the key of its band in the manifest map) — not the
     * pixel width of the composed sheet.
     */
    fun tile(
        serverUrl: String,
        itemId: String,
        thumbnailWidth: Int,
        tileIndex: Int,
        apiKey: String?,
        mediaSourceId: String?,
    ): String {
        val query = buildList {
            if (!apiKey.isNullOrBlank()) add("api_key=$apiKey")
            if (!mediaSourceId.isNullOrBlank()) add("mediaSourceId=$mediaSourceId")
        }.joinToString("&")
        val base = "${serverUrl.trimEnd('/')}/Videos/$itemId/Trickplay/$thumbnailWidth/$tileIndex.jpg"
        return if (query.isEmpty()) base else "$base?$query"
    }
}

/**
 * Everything the OSD needs to draw trickplay for the running item: the chosen width band's manifest
 * plus enough context to address its sheets. Coil loads [tileUrl]; [TrickplayMath.tileAt] says which
 * sub-rect of it to paint.
 */
data class TrickplaySource(
    val info: TrickplayInfoDto,
    private val serverUrl: String,
    private val itemId: String,
    private val apiKey: String?,
    private val mediaSourceId: String?,
) {
    fun tileUrl(tileIndex: Int): String = TrickplayUrl.tile(
        serverUrl = serverUrl,
        itemId = itemId,
        thumbnailWidth = info.width,
        tileIndex = tileIndex,
        apiKey = apiKey,
        mediaSourceId = mediaSourceId,
    )

    /** Convenience: the sheet URL plus sub-rect for a position, or null when the manifest is unusable. */
    fun tileAt(positionMs: Long): Pair<String, TrickplayTile>? {
        val tile = TrickplayMath.tileAt(info, positionMs) ?: return null
        return tileUrl(tile.tileIndex) to tile
    }
}
