package com.maik205.shoumeiplayer.player

/**
 * A subtitle Jellyfin serves as a separate file rather than muxed into the stream.
 *
 * The server marks these `DeliveryMethod: "External"` with a `DeliveryUrl` whenever the device
 * profile advertises external subtitle support (ours does, for srt/vtt). They are invisible to the
 * player unless it explicitly sideloads them, so [streamIndex] is carried along to keep the app's
 * Jellyfin-index addressing intact once the file is attached.
 */
data class ExternalSubtitle(
    /** Jellyfin `MediaStream.Index` of the subtitle stream this file corresponds to. */
    val streamIndex: Int,
    val url: String,
    val title: String = "",
    val language: String? = null,
)

data class PlayRequest(
    val itemId: String? = null,
    val url: String,
    val title: String = "",
    val headers: Map<String, String> = emptyMap(),
    val startPositionMs: Long = 0,
    val durationMs: Long? = null,
    /** Video requests wait for an attached Android Surface before opening MediaCodec. */
    val requiresVideoSurface: Boolean = true,
    /** Jellyfin `MediaStream.Index` of the audio track to start on. */
    val preferredAudioTrackId: Int? = null,
    /** Jellyfin `MediaStream.Index` of the subtitle to start on, or -1 for off. */
    val preferredSubtitleTrackId: Int? = null,
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
)

object AudioPlaybackHandoff {
    private val requests = java.util.concurrent.ConcurrentHashMap<String, PlayRequest>()
    private val resolvedItems = java.util.concurrent.ConcurrentHashMap<String, ResolvedPlayback>()

    fun offer(request: PlayRequest) {
        request.itemId?.let { requests[it] = request }
    }

    fun take(itemId: String): PlayRequest? = requests.remove(itemId)

    fun offerResolved(resolved: ResolvedPlayback) {
        resolvedItems[resolved.itemId] = resolved
    }

    fun takeResolved(itemId: String): ResolvedPlayback? = resolvedItems.remove(itemId)
}
