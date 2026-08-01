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
    const val MAX_AGE_MS = 5 * 60 * 1000L

    data class Record(
        val accountId: String?,
        val request: PlayRequest?,
        val resolved: ResolvedPlayback?,
        val createdAtMs: Long,
    )

    private val records = mutableMapOf<String, Record>()

    @Synchronized
    fun offer(accountId: String?, request: PlayRequest, nowMs: Long = System.currentTimeMillis()) {
        request.itemId?.let { itemId ->
            val existing = records[itemId]
            records[itemId] = Record(
                accountId = accountId ?: existing?.accountId,
                request = request,
                resolved = existing?.resolved,
                createdAtMs = existing?.createdAtMs ?: nowMs,
            )
        }
    }

    @Synchronized
    fun offerResolved(
        accountId: String?,
        resolved: ResolvedPlayback,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        val existing = records[resolved.itemId]
        records[resolved.itemId] = Record(
            accountId = accountId ?: existing?.accountId,
            request = existing?.request,
            resolved = resolved,
            createdAtMs = existing?.createdAtMs ?: nowMs,
        )
    }

    @Synchronized
    fun consume(itemId: String, accountId: String?, nowMs: Long = System.currentTimeMillis()): Record? {
        val record = records[itemId] ?: return null
        if (nowMs - record.createdAtMs > MAX_AGE_MS) {
            records.remove(itemId)
            return null
        }
        if (record.accountId != null && record.accountId != accountId) return null
        if (record.request == null) return null
        return records.remove(itemId)
    }

    @Synchronized
    fun clear(itemId: String? = null) {
        if (itemId == null) records.clear() else records.remove(itemId)
    }
}
