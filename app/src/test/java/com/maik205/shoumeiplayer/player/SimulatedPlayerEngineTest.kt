package com.maik205.shoumeiplayer.player

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SimulatedPlayerEngineTest {

    @Test
    fun `load transitions to Playing and ticker advances position`() = runTest {
        val engine = SimulatedPlayerEngine(scope = this)

        engine.load(PlayRequest(url = "http://example/stream", startPositionMs = 0))
        assertEquals(PlayerState.Loading, engine.state.value)

        // 150ms -> Buffering, +150ms -> Playing
        advanceTimeBy(301)
        runCurrent()
        assertEquals(PlayerState.Playing, engine.state.value)
        assertEquals(0L, engine.positionMs.value)

        // advance 5s of virtual time; ticker adds 1000ms per second
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(5000L, engine.positionMs.value)

        engine.pause()
        assertEquals(PlayerState.Paused, engine.state.value)
        val positionAtPause = engine.positionMs.value
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(positionAtPause, engine.positionMs.value)

        engine.seekTo(60_000)
        assertEquals(60_000L, engine.positionMs.value)

        engine.play()
        assertEquals(PlayerState.Playing, engine.state.value)

        // durationMs defaults to 45 minutes; seek near the end and let it run out.
        val duration = engine.durationMs.value!!
        engine.seekTo(duration - 500)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(PlayerState.Ended, engine.state.value)
        assertEquals(duration, engine.positionMs.value)

        engine.release()
    }

    @Test
    fun `selectTrack marks only the chosen track as selected within its type`() = runTest {
        val engine = SimulatedPlayerEngine(scope = this)
        engine.load(PlayRequest(url = "http://example/stream"))

        val japaneseAudio = engine.tracks.value.first { it.type == TrackType.AUDIO && it.id == 1 }
        engine.selectTrack(japaneseAudio)

        val audioTracks = engine.tracks.value.filter { it.type == TrackType.AUDIO }
        assertTrue(audioTracks.single { it.id == 1 }.selected)
        assertTrue(audioTracks.filter { it.id != 1 }.none { it.selected })

        // subtitle selection untouched by audio selection
        val subtitleTracks = engine.tracks.value.filter { it.type == TrackType.SUBTITLE }
        assertTrue(subtitleTracks.single { it.id == 3 }.selected)
    }

    /** docs/osd-v3.md §5 — the simulated clock has to honour the rate, or speed is untestable. */
    @Test
    fun `the ticker advances the playhead at the configured speed`() = runTest {
        val engine = SimulatedPlayerEngine(scope = this)
        assertEquals(1.0f, engine.speed.value)

        engine.load(PlayRequest(url = "http://example/stream", startPositionMs = 0))
        advanceTimeBy(301)
        runCurrent()
        assertEquals(PlayerState.Playing, engine.state.value)

        // 1× — five seconds of wall clock is five seconds of media.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(5_000L, engine.positionMs.value)

        // 2× — the same five seconds of wall clock now covers ten of media.
        engine.setSpeed(2.0f)
        assertEquals(2.0f, engine.speed.value)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(15_000L, engine.positionMs.value)

        // 0.5× — and half.
        engine.setSpeed(0.5f)
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(17_000L, engine.positionMs.value)

        engine.release()
    }

    @Test
    fun `setSpeed is clamped to the ladder`() = runTest {
        val engine = SimulatedPlayerEngine(scope = this)

        engine.setSpeed(9f)
        assertEquals(PlaybackSpeed.Max, engine.speed.value)

        engine.setSpeed(0.01f)
        assertEquals(PlaybackSpeed.Min, engine.speed.value)

        engine.release()
    }

    @Test
    fun `stop resets to Idle and zero position`() = runTest {
        val engine = SimulatedPlayerEngine(scope = this)
        engine.load(PlayRequest(url = "http://example/stream"))
        advanceTimeBy(301)
        runCurrent()
        advanceTimeBy(2000)
        runCurrent()

        engine.stop()

        assertEquals(PlayerState.Idle, engine.state.value)
        assertEquals(0L, engine.positionMs.value)
    }

    @Test
    fun `bufferedMs tracks the playhead and never runs past the duration`() = runTest {
        val engine = SimulatedPlayerEngine(scope = this)
        assertEquals(null, engine.bufferedMs.value)

        engine.load(PlayRequest(url = "http://example/stream", durationMs = 60_000))
        // 30s lookahead from position 0.
        assertEquals(30_000L, engine.bufferedMs.value)

        advanceTimeBy(301)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(engine.positionMs.value + 30_000L, engine.bufferedMs.value)

        // Clamped to the duration near the end of the item.
        engine.seekTo(55_000)
        assertEquals(60_000L, engine.bufferedMs.value)

        engine.stop()
        assertEquals(null, engine.bufferedMs.value)
    }
}
