package com.maik205.shoumeiplayer.player

import android.view.Surface
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val unavailableLongTelemetry: StateFlow<Long?> = MutableStateFlow(null)
private val unavailableBooleanTelemetry: StateFlow<Boolean?> = MutableStateFlow(null)
private val unavailableFlagTelemetry: StateFlow<Boolean> = MutableStateFlow(false)

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
    /** Native source frame rate, when the current stream reports one. */
    val videoFps: StateFlow<Double?>

    /** Network/cache read rate reported by the native backend, in bytes per second. */
    val readRateBytesPerSecond: StateFlow<Long?>
        get() = unavailableLongTelemetry

    /** Recent packet-level video bitrate reported by the native backend, in bits per second. */
    val videoBitrateBitsPerSecond: StateFlow<Long?>
        get() = unavailableLongTelemetry

    /** Recent packet-level audio bitrate reported by the native backend, in bits per second. */
    val audioBitrateBitsPerSecond: StateFlow<Long?>
        get() = unavailableLongTelemetry

    /** Whether the backend has filled the cache to its requested target, when known. */
    val cacheIdle: StateFlow<Boolean?>
        get() = unavailableBooleanTelemetry

    /** True while the backend is resolving a seek and refilling the target range. */
    val seeking: StateFlow<Boolean>
        get() = unavailableFlagTelemetry

    /** True when playback is paused specifically because the cache ran dry. */
    val pausedForCache: StateFlow<Boolean>
        get() = unavailableFlagTelemetry

    fun setSystemCaptionStyle(style: SystemCaptionStyle?) = Unit
    fun setSurface(surface: Surface?)

    /**
     * Reports the render target's pixel size. Engines that scale against an explicitly configured
     * output size (libmpv does) need this on every surface change; others may ignore it.
     */
    fun setSurfaceSize(width: Int, height: Int) = Unit

    /** Applies persisted client playback preferences before the next media load. */
    fun configure(settings: ClientSettings) = Unit

    fun load(item: PlayRequest)
    fun setQueue(itemIds: List<String>, currentItemId: String) = Unit
    fun play()
    fun pause()
    fun seekTo(ms: Long)

    /**
     * Sets the playback rate. Implementations are expected to clamp to their supported range and to
     * leave [speed] untouched when the rate could not actually be applied — a speed the engine is
     * not really running at must never be advertised.
     */
    fun setSpeed(speed: Float)

    /** Applies an unbounded audio offset relative to the video timeline. */
    fun setAudioDelayMs(delayMs: Long) = Unit

    /** Applies an unbounded subtitle offset relative to the video timeline. */
    fun setSubtitleDelayMs(delayMs: Long) = Unit

    /** Changes how the decoded picture is fitted into the output surface. */
    fun setFrameMode(mode: String) = Unit

    /** Selects the active HDR output/tone-mapping policy. */
    fun setHdrMode(mode: String) = Unit

    /** Selects automatic, forced, or disabled deinterlacing for the current stream. */
    fun setDeinterlaceMode(mode: String) = Unit

    fun selectTrack(track: PlayerTrack)
    fun stop()
    fun release()
}
