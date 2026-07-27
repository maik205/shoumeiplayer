package com.maik205.shoumeiplayer.player

import android.view.Surface
import kotlinx.coroutines.flow.StateFlow

interface PlayerEngine {
    val state: StateFlow<PlayerState>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long?>

    /**
     * Absolute playback position, in milliseconds, up to which media is already buffered —
     * i.e. the end of the buffered range, on the same timeline as [positionMs].
     * `null` means the engine cannot report it.
     */
    val bufferedMs: StateFlow<Long?>
    val tracks: StateFlow<List<PlayerTrack>>

    /**
     * Playback rate as a multiplier of real time: `1.0` is normal speed. Engines that cannot vary
     * the rate hold this at `1.0` forever, so the OSD always has a truthful value to render.
     */
    val speed: StateFlow<Float>
    fun setSurface(surface: Surface?)

    /**
     * Reports the render target's pixel size. Engines that scale against an explicitly configured
     * output size (libmpv does) need this on every surface change; others may ignore it.
     */
    fun setSurfaceSize(width: Int, height: Int) = Unit
    fun load(item: PlayRequest)
    fun play()
    fun pause()
    fun seekTo(ms: Long)

    /**
     * Sets the playback rate. Implementations are expected to clamp to their supported range and to
     * leave [speed] untouched when the rate could not actually be applied — a speed the engine is
     * not really running at must never be advertised.
     */
    fun setSpeed(speed: Float)
    fun selectTrack(track: PlayerTrack)
    fun stop()
    fun release()
}
