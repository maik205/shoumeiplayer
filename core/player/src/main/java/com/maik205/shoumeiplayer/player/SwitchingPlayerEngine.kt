package com.maik205.shoumeiplayer.player

import android.view.Surface
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.PlaybackBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class SwitchingPlayerEngine(
    initialBackend: PlaybackBackend,
    private val create: (PlaybackBackend) -> PlayerEngine,
) : PlayerEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var released = false
    private var backend = initialBackend
    private var delegate = create(initialBackend)
    private var observationJobs: List<Job> = emptyList()
    private var surface: Surface? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val _state = MutableStateFlow(delegate.state.value)
    override val state: StateFlow<PlayerState> = _state
    private val _positionMs = MutableStateFlow(delegate.positionMs.value)
    override val positionMs: StateFlow<Long> = _positionMs
    private val _durationMs = MutableStateFlow(delegate.durationMs.value)
    override val durationMs: StateFlow<Long?> = _durationMs
    private val _bufferedMs = MutableStateFlow(delegate.bufferedMs.value)
    override val bufferedMs: StateFlow<Long?> = _bufferedMs
    private val _tracks = MutableStateFlow(delegate.tracks.value)
    override val tracks: StateFlow<List<PlayerTrack>> = _tracks
    private val _speed = MutableStateFlow(delegate.speed.value)
    override val speed: StateFlow<Float> = _speed
    private val _videoFps = MutableStateFlow(delegate.videoFps.value)
    override val videoFps: StateFlow<Double?> = _videoFps
    private val _readRateBytesPerSecond = MutableStateFlow(delegate.readRateBytesPerSecond.value)
    override val readRateBytesPerSecond: StateFlow<Long?> = _readRateBytesPerSecond
    private val _videoBitrateBitsPerSecond = MutableStateFlow(delegate.videoBitrateBitsPerSecond.value)
    override val videoBitrateBitsPerSecond: StateFlow<Long?> = _videoBitrateBitsPerSecond
    private val _audioBitrateBitsPerSecond = MutableStateFlow(delegate.audioBitrateBitsPerSecond.value)
    override val audioBitrateBitsPerSecond: StateFlow<Long?> = _audioBitrateBitsPerSecond
    private val _cacheIdle = MutableStateFlow(delegate.cacheIdle.value)
    override val cacheIdle: StateFlow<Boolean?> = _cacheIdle
    private val _seeking = MutableStateFlow(delegate.seeking.value)
    override val seeking: StateFlow<Boolean> = _seeking
    private val _pausedForCache = MutableStateFlow(delegate.pausedForCache.value)
    override val pausedForCache: StateFlow<Boolean> = _pausedForCache

    init {
        observeDelegate()
    }

    override fun setSystemCaptionStyle(style: SystemCaptionStyle?) = delegate.setSystemCaptionStyle(style)

    override fun setSurface(surface: Surface?) {
        this.surface = surface
        delegate.setSurface(surface)
    }

    override fun setSurfaceSize(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        delegate.setSurfaceSize(width, height)
    }

    @Synchronized
    override fun configure(settings: ClientSettings) {
        if (released) return
        if (settings.playbackBackend != backend) {
            delegate.stop()
            delegate.release()
            backend = settings.playbackBackend
            delegate = create(backend)
            delegate.setSurface(surface)
            delegate.setSurfaceSize(surfaceWidth, surfaceHeight)
            observeDelegate()
        }
        delegate.configure(settings)
    }

    override fun load(item: PlayRequest) = delegate.load(item)
    override fun setQueue(itemIds: List<String>, currentItemId: String) = delegate.setQueue(itemIds, currentItemId)
    override fun play() = delegate.play()
    override fun pause() = delegate.pause()
    override fun seekTo(ms: Long) = delegate.seekTo(ms)
    override fun setSpeed(speed: Float) = delegate.setSpeed(speed)
    override fun setAudioDelayMs(delayMs: Long) = delegate.setAudioDelayMs(delayMs)
    override fun setSubtitleDelayMs(delayMs: Long) = delegate.setSubtitleDelayMs(delayMs)
    override fun setFrameMode(mode: String) = delegate.setFrameMode(mode)
    override fun setHdrMode(mode: String) = delegate.setHdrMode(mode)
    override fun setDeinterlaceMode(mode: String) = delegate.setDeinterlaceMode(mode)
    override fun selectTrack(track: PlayerTrack) = delegate.selectTrack(track)
    override fun stop() = delegate.stop()

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        delegate.release()
        scope.cancel()
    }

    private fun observeDelegate() {
        observationJobs.forEach(Job::cancel)
        _state.value = delegate.state.value
        _positionMs.value = delegate.positionMs.value
        _durationMs.value = delegate.durationMs.value
        _bufferedMs.value = delegate.bufferedMs.value
        _tracks.value = delegate.tracks.value
        _speed.value = delegate.speed.value
        _videoFps.value = delegate.videoFps.value
        _readRateBytesPerSecond.value = delegate.readRateBytesPerSecond.value
        _videoBitrateBitsPerSecond.value = delegate.videoBitrateBitsPerSecond.value
        _audioBitrateBitsPerSecond.value = delegate.audioBitrateBitsPerSecond.value
        _cacheIdle.value = delegate.cacheIdle.value
        _seeking.value = delegate.seeking.value
        _pausedForCache.value = delegate.pausedForCache.value
        observationJobs = listOf(
            scope.launch { delegate.state.collect { _state.value = it } },
            scope.launch { delegate.positionMs.collect { _positionMs.value = it } },
            scope.launch { delegate.durationMs.collect { _durationMs.value = it } },
            scope.launch { delegate.bufferedMs.collect { _bufferedMs.value = it } },
            scope.launch { delegate.tracks.collect { _tracks.value = it } },
            scope.launch { delegate.speed.collect { _speed.value = it } },
            scope.launch { delegate.videoFps.collect { _videoFps.value = it } },
            scope.launch { delegate.readRateBytesPerSecond.collect { _readRateBytesPerSecond.value = it } },
            scope.launch { delegate.videoBitrateBitsPerSecond.collect { _videoBitrateBitsPerSecond.value = it } },
            scope.launch { delegate.audioBitrateBitsPerSecond.collect { _audioBitrateBitsPerSecond.value = it } },
            scope.launch { delegate.cacheIdle.collect { _cacheIdle.value = it } },
            scope.launch { delegate.seeking.collect { _seeking.value = it } },
            scope.launch { delegate.pausedForCache.collect { _pausedForCache.value = it } },
        )
    }
}
