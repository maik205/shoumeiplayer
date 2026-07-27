package com.maik205.shoumeiplayer.util

/** Jellyfin "ticks" are 100-nanosecond units (10,000 ticks per millisecond). */
object Ticks {
    const val PER_MS = 10_000L

    fun toMs(ticks: Long): Long = ticks / PER_MS

    fun fromMs(ms: Long): Long = ms * PER_MS

    /** Formats a duration in milliseconds as "H:MM:SS" or "M:SS". */
    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
}
