package com.maik205.shoumeiplayer.player

import android.view.Surface
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.PlaybackBackend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.assertEquals
import org.junit.Test

class SwitchingPlayerEngineTest {
    @Test
    fun `backend switch releases the old engine before creating the new engine`() {
        val events = mutableListOf<String>()
        val engine = SwitchingPlayerEngine(PlaybackBackend.Mpv) { backend ->
            events += "create-$backend"
            RecordingEngine(backend, events)
        }

        engine.configure(ClientSettings(playbackBackend = PlaybackBackend.System))

        assertEquals(
            listOf(
                "create-Mpv",
                "stop-Mpv",
                "release-Mpv",
                "create-System",
                "configure-System",
            ),
            events,
        )
    }

    private class RecordingEngine(
        private val backend: PlaybackBackend,
        private val events: MutableList<String>,
    ) : PlayerEngine {
        override val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState.Idle)
        override val positionMs: StateFlow<Long> = MutableStateFlow(0L)
        override val durationMs: StateFlow<Long?> = MutableStateFlow(null)
        override val bufferedMs: StateFlow<Long?> = MutableStateFlow(null)
        override val tracks: StateFlow<List<PlayerTrack>> = MutableStateFlow(emptyList())
        override val speed: StateFlow<Float> = MutableStateFlow(1f)
        override val videoFps: StateFlow<Double?> = MutableStateFlow(null)
        override fun setSurface(surface: Surface?) = Unit
        override fun configure(settings: ClientSettings) { events += "configure-$backend" }
        override fun load(item: PlayRequest) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(ms: Long) = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun selectTrack(track: PlayerTrack) = Unit
        override fun stop() { events += "stop-$backend" }
        override fun release() { events += "release-$backend" }
    }
}
