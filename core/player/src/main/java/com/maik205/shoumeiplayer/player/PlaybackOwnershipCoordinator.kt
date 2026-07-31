package com.maik205.shoumeiplayer.player

enum class PlaybackOwner {
    VIDEO,
    AUDIO_SERVICE,
}

/** Serializes logical access to the process-global playback engine. */
class PlaybackOwnershipCoordinator {
    private var activeOwner: PlaybackOwner? = null
    private var releaseActive: (() -> Unit)? = null

    val owner: PlaybackOwner?
        @Synchronized get() = activeOwner

    @Synchronized
    fun acquire(owner: PlaybackOwner, releasePrevious: () -> Unit) {
        if (activeOwner == owner) {
            releaseActive = releasePrevious
            return
        }
        releaseActive?.invoke()
        AudioPlaybackHandoff.clear()
        activeOwner = owner
        releaseActive = releasePrevious
    }

    @Synchronized
    fun release(owner: PlaybackOwner) {
        if (activeOwner != owner) return
        releaseActive = null
        activeOwner = null
        AudioPlaybackHandoff.clear()
    }
}
