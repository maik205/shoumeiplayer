package com.maik205.shoumeiplayer.feature.player

import com.maik205.shoumeiplayer.player.ResolvedPlayback
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.player.PlayerState
import java.util.concurrent.atomic.AtomicBoolean

class PlaybackSessionCoordinator(
    private val reporter: PlaybackReporter,
) {
    private var current: ResolvedPlayback? = null
    private val screenGone = AtomicBoolean(false)
    private val finalStopStarted = AtomicBoolean(false)

    val resolved: ResolvedPlayback?
        get() = current

    val isScreenGone: Boolean
        get() = screenGone.get()

    fun prepareRetry() {
        screenGone.set(false)
    }

    fun rejectIfScreenGone(
        resolved: ResolvedPlayback,
        positionMs: Long,
    ): Boolean {
        if (!screenGone.get()) return false
        reporter.stopDetached(resolved, positionMs, failed = false)
        return true
    }

    fun attach(
        resolved: ResolvedPlayback,
        engine: PlayerEngine,
        selectedAudioIndex: () -> Int?,
        selectedSubtitleIndex: () -> Int?,
    ) {
        current = resolved
        reporter.start(engine, resolved, selectedAudioIndex, selectedSubtitleIndex)
    }

    fun retire(positionMs: Long) {
        val outgoing = current ?: return
        current = null
        reporter.stopActive(outgoing, positionMs, failed = false)
    }

    fun reportProgressNow(
        engine: PlayerEngine,
        selectedAudioIndex: Int?,
        selectedSubtitleIndex: Int?,
    ) {
        val active = current ?: return
        reporter.reportProgressNow(
            resolved = active,
            positionMs = engine.positionMs.value,
            paused = engine.state.value == PlayerState.Paused,
            selectedAudioIndex = selectedAudioIndex,
            selectedSubtitleIndex = selectedSubtitleIndex,
        )
    }

    fun finish(engine: PlayerEngine) {
        screenGone.set(true)
        val active = current ?: return
        if (!finalStopStarted.compareAndSet(false, true)) return
        current = null
        reporter.stopActive(
            resolved = active,
            positionMs = engine.positionMs.value,
            failed = engine.state.value is PlayerState.Error,
        )
    }
}
