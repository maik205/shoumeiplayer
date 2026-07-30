package com.maik205.shoumeiplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackSelectionAudioDescriptionTest {
    @Test
    fun `system audio description preference chooses a described audio stream`() {
        val streams = listOf(
            PlaybackMediaStream(index = 1, type = "Audio", isDefault = true),
            PlaybackMediaStream(index = 2, type = "Audio", isAudioDescription = true),
        )

        assertEquals(2, TrackSelection.selectAudioIndex(streams, null, 1, preferAudioDescription = true))
        assertEquals(1, TrackSelection.selectAudioIndex(streams, null, 1, preferAudioDescription = false))
    }
}
