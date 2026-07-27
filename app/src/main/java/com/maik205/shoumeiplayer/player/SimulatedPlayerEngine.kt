package com.maik205.shoumeiplayer.player

import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** How far ahead of the playhead the fake engine claims to have buffered. */
private const val BufferLookaheadMs = 30_000L

/** Wall-clock period of the fake playback ticker. */
private const val TickMs = 1000L

/**
 * A fake [PlayerEngine] used for debug builds until the mplayer JNI bridge
 * exists. Simulates load latency, a playback ticker, and track selection
 * without touching any real media.
 */
class SimulatedPlayerEngine(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PlayerEngine {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow<Long?>(null)
    override val durationMs: StateFlow<Long?> = _durationMs.asStateFlow()

    private val _bufferedMs = MutableStateFlow<Long?>(null)
    override val bufferedMs: StateFlow<Long?> = _bufferedMs.asStateFlow()

    private val _tracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val tracks: StateFlow<List<PlayerTrack>> = _tracks.asStateFlow()

    private val _speed = MutableStateFlow(PlaybackSpeed.Normal)
    override val speed: StateFlow<Float> = _speed.asStateFlow()

    private var tickerJob: Job? = null

    /**
     * The simulated engine pretends a fixed read-ahead window: buffered always sits
     * [BufferLookaheadMs] past the playhead, clamped to the duration.
     */
    private fun updateBuffered() {
        val duration = _durationMs.value
        val ahead = _positionMs.value + BufferLookaheadMs
        _bufferedMs.value = if (duration != null) ahead.coerceAtMost(duration) else ahead
    }

    override fun setSurface(surface: Surface?) {
        // no-op for the simulated engine
    }

    override fun load(item: PlayRequest) {
        tickerJob?.cancel()
        val duration = item.durationMs ?: (45 * 60 * 1000L)
        _durationMs.value = duration
        _positionMs.value = item.startPositionMs
        _tracks.value = listOf(
            PlayerTrack(0, TrackType.AUDIO, "English (AAC 5.1)", "eng", isDefault = true, selected = true),
            PlayerTrack(1, TrackType.AUDIO, "Japanese (FLAC)", "jpn"),
            PlayerTrack(2, TrackType.SUBTITLE, "English (SRT)", "eng"),
            PlayerTrack(3, TrackType.SUBTITLE, "Off", null, selected = true),
        )
        updateBuffered()
        _state.value = PlayerState.Loading
        tickerJob = scope.launch {
            delay(150)
            _state.value = PlayerState.Buffering
            delay(150)
            _state.value = PlayerState.Playing
            runTicker()
        }
    }

    private suspend fun runTicker() {
        while (true) {
            delay(TickMs)
            if (_state.value != PlayerState.Playing) continue
            val duration = _durationMs.value
            // Real time keeps ticking at 1s; the *playhead* is what the rate multiplies, exactly as
            // a real engine behaves — 2× advances two seconds of media per second of wall clock.
            val next = _positionMs.value + (TickMs * _speed.value).toLong()
            if (duration != null && next >= duration) {
                _positionMs.value = duration
                updateBuffered()
                _state.value = PlayerState.Ended
                return
            }
            _positionMs.value = next
            updateBuffered()
        }
    }

    override fun play() {
        if (_state.value == PlayerState.Paused || _state.value == PlayerState.Buffering) {
            _state.value = PlayerState.Playing
        }
    }

    override fun pause() {
        if (_state.value == PlayerState.Playing) {
            _state.value = PlayerState.Paused
        }
    }

    override fun seekTo(ms: Long) {
        val duration = _durationMs.value
        val clamped = if (duration != null) ms.coerceIn(0, duration) else ms.coerceAtLeast(0)
        _positionMs.value = clamped
        updateBuffered()
        if (_state.value == PlayerState.Ended && duration != null && clamped < duration) {
            _state.value = PlayerState.Playing
        }
    }

    override fun setSpeed(speed: Float) {
        _speed.value = PlaybackSpeed.clamp(speed)
    }

    override fun selectTrack(track: PlayerTrack) {
        _tracks.value = _tracks.value.map { existing ->
            if (existing.type != track.type) {
                existing
            } else {
                existing.copy(selected = existing.id == track.id)
            }
        }
    }

    override fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        _state.value = PlayerState.Idle
        _positionMs.value = 0
        _bufferedMs.value = null
    }

    override fun release() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
