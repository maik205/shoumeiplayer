package com.maik205.shoumeiplayer.player

/**
 * The quality ladder from docs/osd-v3.md §5, mapped to the `MaxStreamingBitrate` a `/PlaybackInfo`
 * re-resolve is given.
 *
 * The numbers are deliberately *generous* headroom figures rather than encoder targets: the cap
 * exists to stop the server handing back a stream the link cannot carry, so it has to sit above a
 * good encode at that resolution and below the next tier up. A cap that is too tight makes the
 * server transcode a 1080p title down to mush; too loose and picking "720p" changes nothing.
 *
 *  - 4K    80 Mbps — comfortably above HDR10 HEVC UHD remuxes (typ. 50-70), below full BD remux.
 *  - 1080p 20 Mbps — above a Blu-ray-rate 1080p encode (typ. 8-15) with room for lossless audio.
 *  - 720p   8 Mbps — above a high-bitrate 720p encode (typ. 4-6).
 *  - 480p   3 Mbps — above a DVD-rate encode (typ. 1.5-2.5); the "make it play on anything" rung.
 *
 * [AUTO] carries no cap of its own: the resolve path falls back to
 * [DEFAULT_MAX_STREAMING_BITRATE], the same effectively-unlimited
 * ceiling playback has always started with, and lets direct play win.
 */
enum class VideoQuality(val label: String, val maxStreamingBitrate: Long?) {
    AUTO("Auto", null),
    UHD("4K", 80_000_000L),
    FHD("1080p", 20_000_000L),
    HD("720p", 8_000_000L),
    SD("480p", 3_000_000L),
    ;

    /**
     * Anything but [AUTO] has to forbid direct play on the re-resolve. Jellyfin only honours
     * `MaxStreamingBitrate` when it is actually building a stream: leave direct play enabled and the
     * server hands back the original file untouched and the cap is silently ignored.
     */
    val forcesTranscode: Boolean get() = this != AUTO

    companion object {
        /** Ladder order for the panel: Auto first, then descending resolution. */
        val Ladder: List<VideoQuality> = listOf(AUTO, UHD, FHD, HD, SD)

        /**
         * The rung a given cap belongs to — used to show what an already-running session resolved
         * to. An unrecognised or absent cap is [AUTO].
         */
        fun forBitrate(maxStreamingBitrate: Long?): VideoQuality =
            entries.firstOrNull { it.maxStreamingBitrate != null && it.maxStreamingBitrate == maxStreamingBitrate }
                ?: AUTO

        /** Resolves either the user-facing label or enum name stored in client settings. */
        fun forLabel(label: String?): VideoQuality =
            entries.firstOrNull {
                it.label.equals(label, ignoreCase = true) ||
                    it.name.equals(label, ignoreCase = true)
            } ?: AUTO
    }
}
