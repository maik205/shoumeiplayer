package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.PlaybackBackend
import com.maik205.shoumeiplayer.domain.settings.TlsTrustSource
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import org.junit.Test

class PlaybackSettingsTest {
    @Test
    fun `playback backend and TLS trust source persist independently`() = runTest {
        val store = SettingsStore(InMemoryPreferencesDataStore())
        val expected = ClientSettings(
            playbackBackend = PlaybackBackend.System,
            tlsTrustSource = TlsTrustSource.AndroidSystem,
        )

        store.save(expected)

        assertEquals(expected, store.current())
    }
}
