package com.maik205.shoumeiplayer.player

import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MplayerEngine"

// TODO(mplayer): bind via JNI to ../mplayer
class MplayerEngine : PlayerEngine {

    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow<Long?>(null)
    override val durationMs: StateFlow<Long?> = _durationMs.asStateFlow()

    // TODO(mplayer): report the real demuxer cache once the JNI bridge exists.
    override val bufferedMs: StateFlow<Long?> = MutableStateFlow<Long?>(null).asStateFlow()

    private val _tracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val tracks: StateFlow<List<PlayerTrack>> = _tracks.asStateFlow()

    // TODO(mplayer): drive `speed_mult` over the slave protocol once the JNI bridge exists. Until
    // then the rate is pinned at 1× and never lies about a speed the engine is not running at.
    override val speed: StateFlow<Float> = MutableStateFlow(PlaybackSpeed.Normal).asStateFlow()
    override val videoFps: StateFlow<Double?> = MutableStateFlow<Double?>(null).asStateFlow()

    // TODO(mplayer): bind via JNI to ../mplayer
    override fun setSurface(surface: Surface?) {
        Log.d(TAG, "setSurface(surface=$surface)")
    }

    // TODO(mplayer): bind via JNI to ../mplayer
    override fun load(item: PlayRequest) {
        Log.d(TAG, "load(item=$item)")
        _state.value = PlayerState.Error("mplayer engine not yet implemented")
    }

    // TODO(mplayer): bind via JNI to ../mplayer
    override fun play() {
        Log.d(TAG, "play()")
    }

    // TODO(mplayer): bind via JNI to ../mplayer
    override fun pause() {
        Log.d(TAG, "pause()")
    }

    // TODO(mplayer): bind via JNI to ../mplayer
    override fun seekTo(ms: Long) {
        Log.d(TAG, "seekTo(ms=$ms)")
    }

    // TODO(mplayer): bind via JNI to ../mplayer
    override fun setSpeed(speed: Float) {
        Log.d(TAG, "setSpeed(speed=$speed) — no-op, engine has no rate control yet")
    }

    // TODO(mplayer): bind via JNI to ../mplayer
    override fun selectTrack(track: PlayerTrack) {
        Log.d(TAG, "selectTrack(track=$track)")
    }

    // TODO(mplayer): bind via JNI to ../mplayer
    override fun stop() {
        Log.d(TAG, "stop()")
    }

    // TODO(mplayer): bind via JNI to ../mplayer
    override fun release() {
        Log.d(TAG, "release()")
    }
}
