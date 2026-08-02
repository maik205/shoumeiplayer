package com.maik205.shoumeiplayer.feature.player

import com.maik205.shoumeiplayer.player.PlaybackMediaStream
import com.maik205.shoumeiplayer.player.PlaybackTrackPreferenceProvider
import com.maik205.shoumeiplayer.player.PlaybackTrackPreferences
import com.maik205.shoumeiplayer.player.ResolvedPlayback
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure unit coverage of [TrackController.prepare]'s §88 `rememberedAudioIndex` precedence, isolated
 * from the ViewModel-level, engine-driven tests in `PlayerViewModelTest` (which exercise the same
 * behaviour end to end but cannot cheaply enumerate every precedence edge case).
 */
class TrackControllerTest {

    private fun resolved(streams: List<PlaybackMediaStream>, defaultAudioIndex: Int? = null): ResolvedPlayback =
        ResolvedPlayback(
            itemId = "item-1",
            mediaSourceId = "media-1",
            playSessionId = "session-1",
            streamUrl = "http://server/stream",
            playMethod = "DirectPlay",
            runTimeTicks = null,
            audioTracks = emptyList(),
            subtitleTracks = emptyList(),
            defaultAudioIndex = defaultAudioIndex,
            defaultSubtitleIndex = null,
            mediaStreams = streams,
            headers = emptyMap(),
        )

    /** English (default, index 1), Japanese (index 2), French (index 3). */
    private val threeAudioStreams = listOf(
        PlaybackMediaStream(index = 0, type = "Video"),
        PlaybackMediaStream(index = 1, type = "Audio", language = "eng", isDefault = true),
        PlaybackMediaStream(index = 2, type = "Audio", language = "jpn"),
        PlaybackMediaStream(index = 3, type = "Audio", language = "fre"),
    )

    private val noServerPreferences = object : PlaybackTrackPreferenceProvider {
        override suspend fun preferences(): PlaybackTrackPreferences? = null
    }

    private fun controller(initialAudioStreamIndex: Int? = null) = TrackController(
        preferenceProvider = noServerPreferences,
        initialAudioStreamIndex = initialAudioStreamIndex,
        initialSubtitleStreamIndex = null,
    )

    @Test
    fun `a remembered index is adopted as the default when there is no explicit request`() = runTest {
        val controller = controller()

        controller.prepare(
            resolved(threeAudioStreams, defaultAudioIndex = 1),
            keepCurrent = false,
            rememberedAudioIndex = 3,
        )

        assertEquals(3, controller.selectedAudioIndex)
    }

    @Test
    fun `an explicit per-item request wins over a remembered series pick`() = runTest {
        val controller = controller(initialAudioStreamIndex = 1)

        controller.prepare(
            resolved(threeAudioStreams, defaultAudioIndex = 1),
            keepCurrent = false,
            rememberedAudioIndex = 3,
        )

        assertEquals(1, controller.selectedAudioIndex)
    }

    @Test
    fun `a remembered index absent from this stream falls back to the ordinary default`() = runTest {
        val controller = controller()

        // Index 9 does not exist on this stream (a re-mux could drop a track) -- must not select a
        // non-existent index, and must not silently substitute something else either; the ordinary
        // TrackSelection default is the only sane fallback.
        controller.prepare(
            resolved(threeAudioStreams, defaultAudioIndex = 1),
            keepCurrent = false,
            rememberedAudioIndex = 9,
        )

        assertEquals(1, controller.selectedAudioIndex)
    }

    @Test
    fun `a remembered index is re-validated on every prepare, not stuck from an earlier item`() = runTest {
        // Simulates one TrackController living across an episode swap (switchTo reuses the same
        // instance): a remembered pick that was valid for one item must still be re-checked against
        // the next item's own stream list rather than assumed to still apply.
        val controller = controller()
        controller.prepare(
            resolved(threeAudioStreams, defaultAudioIndex = 1),
            keepCurrent = false,
            rememberedAudioIndex = 3,
        )
        assertEquals(3, controller.selectedAudioIndex)

        val siblingWithoutFrench = threeAudioStreams.filterNot { it.index == 3 }
        controller.prepare(
            resolved(siblingWithoutFrench, defaultAudioIndex = 1),
            keepCurrent = false,
            rememberedAudioIndex = 3,
        )
        assertEquals(1, controller.selectedAudioIndex)
    }

    @Test
    fun `keepCurrent leaves an already-selected track untouched regardless of a remembered index`() = runTest {
        val controller = controller()
        controller.prepare(
            resolved(threeAudioStreams, defaultAudioIndex = 1),
            keepCurrent = false,
            rememberedAudioIndex = 3,
        )
        assertEquals(3, controller.selectedAudioIndex)

        // A same-item quality swap must never re-derive the selection, remembered index or not.
        controller.prepare(
            resolved(threeAudioStreams, defaultAudioIndex = 1),
            keepCurrent = true,
            rememberedAudioIndex = 2,
        )
        assertEquals(3, controller.selectedAudioIndex)
    }
}
