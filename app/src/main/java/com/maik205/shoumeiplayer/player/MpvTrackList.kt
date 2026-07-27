package com.maik205.shoumeiplayer.player

import org.json.JSONArray

/**
 * One entry of mpv's `track-list`, parsed.
 *
 * The important subtlety is that **mpv ids are not Jellyfin stream indices**. mpv numbers tracks
 * per type, starting at 1 (`aid=1` is the first audio track), while the rest of this app addresses
 * tracks by `MediaStream.Index` — the ffmpeg stream index inside the container. mpv exposes that
 * original index as `ff-index`, which is the bridge between the two namespaces.
 */
internal data class MpvTrack(
    /** mpv's own per-type id — the value that goes into the `aid`/`sid` properties. */
    val mpvId: Int,
    val type: TrackType,
    /** Container/ffmpeg stream index; matches Jellyfin's `MediaStream.Index` for direct play. */
    val ffIndex: Int?,
    val language: String? = null,
    val title: String? = null,
    val codec: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val selected: Boolean = false,
)

/**
 * Pure translation between mpv's `track-list` and the app's [PlayerTrack] model.
 *
 * Kept free of MPVLib so it is unit-testable on the JVM — the native library is device-only.
 */
internal object MpvTrackList {

    /** Id used for the synthetic "Subtitles off" entry. */
    const val NO_TRACK = -1

    fun parse(json: String): List<MpvTrack> {
        val array = JSONArray(json)
        val result = mutableListOf<MpvTrack>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val type = when (entry.optString("type")) {
                "audio" -> TrackType.AUDIO
                "sub" -> TrackType.SUBTITLE
                else -> continue
            }
            val mpvId = entry.optInt("id", -1)
            if (mpvId < 0) continue
            result += MpvTrack(
                mpvId = mpvId,
                type = type,
                ffIndex = if (entry.has("ff-index")) entry.optInt("ff-index") else null,
                language = entry.optString("lang").takeIf { it.isNotEmpty() },
                title = entry.optString("title").takeIf { it.isNotEmpty() },
                codec = entry.optString("codec").takeIf { it.isNotEmpty() },
                isDefault = entry.optBoolean("default", false),
                isForced = entry.optBoolean("forced", false),
                isExternal = entry.optBoolean("external", false),
                selected = entry.optBoolean("selected", false),
            )
        }
        return result
    }

    /**
     * Jellyfin stream index for [track].
     *
     * Sideloaded subtitles carry no `ff-index` (they are separate files), so their index comes from
     * [externalIndexByMpvId], recorded when the engine issued `sub-add`. Anything else falls back to
     * the mpv id: that happens for transcoded output, where the remuxed container's indices are
     * unrelated to the original item's anyway and the server has already baked in the track choice.
     */
    fun streamIndexOf(track: MpvTrack, externalIndexByMpvId: Map<Int, Int>): Int =
        externalIndexByMpvId[track.mpvId] ?: track.ffIndex ?: track.mpvId

    /**
     * mpv id to write into `aid`/`sid` for a Jellyfin [streamIndex], or null when this source has no
     * such track (e.g. the server's default index refers to a stream the transcode dropped).
     */
    fun mpvIdFor(
        tracks: List<MpvTrack>,
        externalIndexByMpvId: Map<Int, Int>,
        type: TrackType,
        streamIndex: Int,
    ): Int? = tracks.firstOrNull {
        it.type == type && streamIndexOf(it, externalIndexByMpvId) == streamIndex
    }?.mpvId

    /**
     * Maps sideloaded subtitle files onto their Jellyfin stream indices. mpv appends external tracks
     * to `track-list` in the order they were added, so zipping the external subtitle tracks (in mpv
     * id order) against the request's list recovers the pairing without guessing from filenames.
     */
    fun externalIndexByMpvId(
        tracks: List<MpvTrack>,
        requested: List<ExternalSubtitle>,
    ): Map<Int, Int> = tracks
        .filter { it.type == TrackType.SUBTITLE && it.isExternal }
        .sortedBy { it.mpvId }
        .zip(requested)
        .associate { (track, subtitle) -> track.mpvId to subtitle.streamIndex }

    /**
     * The engine-facing track list: ids are Jellyfin stream indices, plus a synthetic "Off" entry
     * whenever the source has any subtitles at all.
     */
    fun toPlayerTracks(
        tracks: List<MpvTrack>,
        externalIndexByMpvId: Map<Int, Int>,
        offLabel: String = "Off",
    ): List<PlayerTrack> {
        val result = tracks.map { track ->
            PlayerTrack(
                id = streamIndexOf(track, externalIndexByMpvId),
                type = track.type,
                label = label(track),
                language = track.language,
                isDefault = track.isDefault,
                selected = track.selected,
            )
        }
        val subtitles = tracks.filter { it.type == TrackType.SUBTITLE }
        if (subtitles.isEmpty()) return result
        return result + PlayerTrack(
            id = NO_TRACK,
            type = TrackType.SUBTITLE,
            label = offLabel,
            language = null,
            isDefault = false,
            selected = subtitles.none { it.selected },
        )
    }

    private fun label(track: MpvTrack): String {
        val base = track.title
            ?: track.language
            ?: track.codec
            ?: "Track ${track.mpvId}"
        return if (track.isForced) "$base (Forced)" else base
    }
}
