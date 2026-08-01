package com.maik205.shoumeiplayer.player

import android.view.Surface
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.util.Ticks
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PlaybackProgressReporterTest {

    /** Records the ticks/flags passed to each [PlaybackReporting] call. */
    private class FakeReporting : PlaybackReporting {
        val startCalls = mutableListOf<Long>()
        val progressCalls = mutableListOf<Pair<Long, Boolean>>()
        val progressSelections = mutableListOf<Pair<Int?, Int?>>()
        var stoppedCall: Pair<Long, Boolean>? = null

        override suspend fun reportStart(
            resolved: ResolvedPlayback,
            positionTicks: Long,
            audioIndex: Int?,
            subtitleIndex: Int?,
        ): ApiResult<Unit> {
            startCalls += positionTicks
            return ApiResult.Success(Unit)
        }

        override suspend fun reportProgress(
            resolved: ResolvedPlayback,
            positionTicks: Long,
            isPaused: Boolean,
            audioIndex: Int?,
            subtitleIndex: Int?,
        ): ApiResult<Unit> {
            progressCalls += positionTicks to isPaused
            progressSelections += audioIndex to subtitleIndex
            return ApiResult.Success(Unit)
        }

        override suspend fun reportStopped(
            resolved: ResolvedPlayback,
            positionTicks: Long,
            failed: Boolean,
        ): ApiResult<Unit> {
            stoppedCall = positionTicks to failed
            return ApiResult.Success(Unit)
        }

        val pingCalls = mutableListOf<String>()
        var stopTranscodeCalls = 0

        override suspend fun ping(playSessionId: String): ApiResult<Unit> {
            pingCalls += playSessionId
            return ApiResult.Success(Unit)
        }

        override suspend fun stopTranscode(resolved: ResolvedPlayback): ApiResult<Unit> {
            stopTranscodeCalls++
            return ApiResult.Success(Unit)
        }
    }

    /** Minimal [PlayerEngine] whose position ticks +1000ms/s of virtual time while Playing. */
    private class FakePlayerEngine(private val scope: CoroutineScope) : PlayerEngine {
        private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
        private val _positionMs = MutableStateFlow(0L)
        private val _durationMs = MutableStateFlow<Long?>(null)
        private val _tracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
        private val _bufferedMs = MutableStateFlow<Long?>(null)
        private val _speed = MutableStateFlow(PlaybackSpeed.Normal)
        private val _videoFps = MutableStateFlow<Double?>(null)
        override val state: StateFlow<PlayerState> = _state
        override val positionMs: StateFlow<Long> = _positionMs
        override val durationMs: StateFlow<Long?> = _durationMs
        override val bufferedMs: StateFlow<Long?> = _bufferedMs
        override val tracks: StateFlow<List<PlayerTrack>> = _tracks
        override val speed: StateFlow<Float> = _speed
        override val videoFps: StateFlow<Double?> = _videoFps
        private var tickerJob: Job? = null

        override fun setSurface(surface: Surface?) {}

        override fun load(item: PlayRequest) {
            _positionMs.value = item.startPositionMs
            _state.value = PlayerState.Playing
            startTicker()
        }

        override fun play() {
            _state.value = PlayerState.Playing
            startTicker()
        }

        override fun pause() {
            tickerJob?.cancel()
            _state.value = PlayerState.Paused
        }

        override fun seekTo(ms: Long) {
            _positionMs.value = ms
        }

        override fun setSpeed(speed: Float) {
            _speed.value = PlaybackSpeed.clamp(speed)
        }

        override fun selectTrack(track: PlayerTrack) {}

        override fun stop() {
            tickerJob?.cancel()
            _state.value = PlayerState.Idle
            _positionMs.value = 0
        }

        override fun release() {
            tickerJob?.cancel()
        }

        private fun startTicker() {
            tickerJob?.cancel()
            tickerJob = scope.launch {
                while (true) {
                    delay(1000)
                    _positionMs.value += 1000
                }
            }
        }
    }

    private val resolved = ResolvedPlayback(
        itemId = "item-1",
        mediaSourceId = "source-1",
        playSessionId = "session-1",
        streamUrl = "http://example/stream",
        playMethod = "DirectPlay",
        runTimeTicks = null,
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
        defaultAudioIndex = null,
        defaultSubtitleIndex = null,
        headers = emptyMap(),
    )

    @Test
    fun `reports one start and progress every interval, immediately on state change`() = runTest {
        val reporting = FakeReporting()
        val reporter = PlaybackProgressReporter(reporting, intervalMs = 10_000L)
        val engine = FakePlayerEngine(scope = backgroundScope)

        backgroundScope.launch {
            reporter.run(engine, resolved, selectedAudioIndex = { 0 }, selectedSubtitleIndex = { null })
        }
        runCurrent()

        engine.load(PlayRequest(url = resolved.streamUrl))
        runCurrent()

        // 1 start, at position 0.
        assertEquals(listOf(0L), reporting.startCalls)
        assertEquals(emptyList(), reporting.progressCalls)

        // 30s of virtual time -> position crosses the 10s bucket 3 times.
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(1, reporting.startCalls.size)
        assertEquals(3, reporting.progressCalls.size)
        assertEquals(
            listOf(10_000L * 10_000L, 20_000L * 10_000L, 30_000L * 10_000L),
            reporting.progressCalls.map { it.first },
        )
        assertEquals(listOf(false, false, false), reporting.progressCalls.map { it.second })

        // Pausing mid-bucket reports immediately even without crossing a new bucket.
        engine.pause()
        runCurrent()

        assertEquals(4, reporting.progressCalls.size)
        assertEquals(true, reporting.progressCalls.last().second)
        assertEquals(30_000L * 10_000L, reporting.progressCalls.last().first)
    }

    @Test
    fun `reportStopped carries the final position in ticks`() = runTest {
        val reporting = FakeReporting()
        val reporter = PlaybackProgressReporter(reporting)

        reporter.reportStopped(resolved, positionMs = 42_500L, failed = false)

        assertEquals(42_500L * 10_000L to false, reporting.stoppedCall)
    }

    @Test
    fun `track-only progress update reports selected indices immediately`() = runTest {
        val reporting = FakeReporting()
        val reporter = PlaybackProgressReporter(reporting)

        reporter.reportProgressNow(
            resolved = resolved,
            positionMs = 7_250L,
            paused = true,
            selectedAudioIndex = 4,
            selectedSubtitleIndex = -1,
        )

        assertEquals(listOf(Ticks.fromMs(7_250L) to true), reporting.progressCalls)
        assertEquals(
            listOf<Pair<Int?, Int?>>(4 to -1),
            reporting.progressSelections,
        )
    }

    @Test
    fun `no progress or stopped call is made before playback ever starts`() = runTest {
        val reporting = FakeReporting()
        val reporter = PlaybackProgressReporter(reporting)
        val engine = FakePlayerEngine(scope = backgroundScope)

        backgroundScope.launch {
            reporter.run(engine, resolved, selectedAudioIndex = { null }, selectedSubtitleIndex = { null })
        }
        runCurrent()

        assertEquals(emptyList(), reporting.startCalls)
        assertEquals(emptyList(), reporting.progressCalls)
        assertNull(reporting.stoppedCall)
    }

    @Test
    fun `keep-alive pings on the report interval and stops when the reporter is cancelled`() = runTest {
        val reporting = FakeReporting()
        val engine = FakePlayerEngine(this)
        val reporter = PlaybackProgressReporter(reporting, intervalMs = 10_000)

        val job = launch {
            reporter.run(engine, resolved, selectedAudioIndex = { null }, selectedSubtitleIndex = { null })
        }
        engine.load(PlayRequest(url = "http://example/stream"))
        runCurrent()

        advanceTimeBy(35_000)
        runCurrent()
        assertEquals(3, reporting.pingCalls.size)
        assertEquals(List(3) { resolved.playSessionId }, reporting.pingCalls)

        // Pausing must not silence the keep-alive — a paused session is exactly the one at risk of
        // being reaped, because progress reports go quiet.
        engine.pause()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(5, reporting.pingCalls.size)

        job.cancel()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(5, reporting.pingCalls.size)

        engine.release()
    }

    @Test
    fun `reportStopped also tears down the transcode`() = runTest {
        val reporting = FakeReporting()
        val reporter = PlaybackProgressReporter(reporting)

        reporter.reportStopped(resolved, positionMs = 12_000, failed = false)

        assertEquals(Ticks.fromMs(12_000) to false, reporting.stoppedCall)
        assertEquals(1, reporting.stopTranscodeCalls)
    }
}
