package com.maik205.shoumeiplayer.feature.player

import com.maik205.shoumeiplayer.player.ResolvedPlayback
import com.maik205.shoumeiplayer.player.PlaybackProgressReporter
import com.maik205.shoumeiplayer.player.PlayerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PlaybackReporter(
    private val delegate: PlaybackProgressReporter,
    private val playbackScope: CoroutineScope,
    private val teardownScope: CoroutineScope,
) {
    private var activeJob: Job? = null

    fun start(
        engine: PlayerEngine,
        resolved: ResolvedPlayback,
        selectedAudioIndex: () -> Int?,
        selectedSubtitleIndex: () -> Int?,
    ) {
        activeJob?.cancel()
        activeJob = playbackScope.launch {
            delegate.run(engine, resolved, selectedAudioIndex, selectedSubtitleIndex)
        }
    }

    fun reportProgressNow(
        resolved: ResolvedPlayback,
        positionMs: Long,
        paused: Boolean,
        selectedAudioIndex: Int?,
        selectedSubtitleIndex: Int?,
    ) {
        playbackScope.launch {
            delegate.reportProgressNow(
                resolved = resolved,
                positionMs = positionMs,
                paused = paused,
                selectedAudioIndex = selectedAudioIndex,
                selectedSubtitleIndex = selectedSubtitleIndex,
            )
        }
    }

    fun stopActive(resolved: ResolvedPlayback, positionMs: Long, failed: Boolean) {
        activeJob?.cancel()
        activeJob = null
        stopDetached(resolved, positionMs, failed)
    }

    fun stopDetached(resolved: ResolvedPlayback, positionMs: Long, failed: Boolean) {
        teardownScope.launch {
            delegate.reportStopped(resolved, positionMs, failed)
        }
    }
}
