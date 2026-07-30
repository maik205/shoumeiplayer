package com.maik205.shoumeiplayer.player

import com.maik205.shoumeiplayer.util.Ticks
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Drives Jellyfin "now playing" reporting off a [PlayerEngine]'s state while it
 * runs: a single `reportStart` on the first transition into [PlayerState.Playing],
 * then a `reportProgress` every [intervalMs] of playback position **and**
 * immediately on any [PlayerState] change (pause/resume/seek), so the server's
 * resume point and paused flag stay current without spamming it every tick.
 *
 * [run] is a suspend loop meant to be launched in a caller-owned coroutine scope
 * (e.g. `viewModelScope`); it never returns on its own and relies on the caller
 * cancelling it (engine.stop()/onCleared()).
 */
class PlaybackProgressReporter(
    private val repository: PlaybackReporting,
    private val intervalMs: Long = 10_000L,
) {

    suspend fun run(
        engine: PlayerEngine,
        resolved: ResolvedPlayback,
        selectedAudioIndex: () -> Int?,
        selectedSubtitleIndex: () -> Int?,
    ) = coroutineScope {
        // Keep-alive runs as a child of this scope, so cancelling the reporter (engine stop, screen
        // teardown) stops the pings too — there is no separate lifetime to get wrong.
        launch { keepSessionAlive(resolved) }

        var started = false
        var lastState: PlayerState? = null
        var lastBucket: Long? = null

        // Keep the exact position in the engine for seeks and explicit track-switch reports, but
        // do not dispatch a combined flow event for every mpv time-pos callback. State changes
        // remain immediate; position-only events are reduced to the reporting bucket.
        val positionBuckets = engine.positionMs
            .map { it / intervalMs }
            .distinctUntilChanged()
        combine(engine.state, positionBuckets) { state, bucket -> state to bucket }
            .collect { (state, bucket) ->
                val positionMs = engine.positionMs.value
                val positionTicks = Ticks.fromMs(positionMs)

                if (!started) {
                    if (state == PlayerState.Playing) {
                        started = true
                        lastState = state
                        lastBucket = bucket
                        repository.reportStart(
                            resolved,
                            positionTicks,
                            selectedAudioIndex(),
                            selectedSubtitleIndex(),
                        )
                    }
                    return@collect
                }

                val stateChanged = state != lastState
                val bucketChanged = bucket != lastBucket
                if (stateChanged || bucketChanged) {
                    lastState = state
                    lastBucket = bucket
                    repository.reportProgress(
                        resolved,
                        positionTicks,
                        state == PlayerState.Paused,
                        selectedAudioIndex(),
                        selectedSubtitleIndex(),
                    )
                }
            }
    }

    /**
     * `/Sessions/Playing/Ping` on the same cadence as progress reporting. Progress posts stop while
     * playback is paused, so without this the server can reap a paused session; the ping is
     * unconditional for that reason.
     */
    private suspend fun keepSessionAlive(resolved: ResolvedPlayback) {
        if (resolved.playSessionId.isBlank()) return
        while (true) {
            delay(intervalMs)
            repository.ping(resolved.playSessionId)
        }
    }

    /**
     * Sends an out-of-band progress update after a direct-play track switch. The normal reporting
     * loop only observes state and position, so a track-only change would otherwise remain invisible
     * to the Jellyfin session until the next timed bucket.
     */
    suspend fun reportProgressNow(
        resolved: ResolvedPlayback,
        positionMs: Long,
        paused: Boolean,
        selectedAudioIndex: Int?,
        selectedSubtitleIndex: Int?,
    ) {
        repository.reportProgress(
            resolved,
            Ticks.fromMs(positionMs),
            paused,
            selectedAudioIndex,
            selectedSubtitleIndex,
        )
    }

    /**
     * Final report for a session. Also tears down the server-side transcode — the two belong
     * together, and keeping them in one call means no caller can report a stop while leaking an
     * encoder. [PlaybackReporting.stopTranscode] is a no-op for direct play.
     */
    suspend fun reportStopped(resolved: ResolvedPlayback, positionMs: Long, failed: Boolean) {
        repository.reportStopped(resolved, Ticks.fromMs(positionMs), failed)
        repository.stopTranscode(resolved)
    }
}
