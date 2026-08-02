package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.Surface
import com.maik205.shoumeiplayer.feature.player.ResumePromptUi
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §91 — proves the resume-prompt overlay is actually reachable from [PlayerUiState], not just
 * constructible in isolation: [activePlayerModalOverlay] governs whether
 * [TelevisionPlayerContent] renders [ResumePromptOverlay] at all, [shouldPauseForResumePrompt]
 * governs whether playback is held paused behind it, and [resumePromptActions] governs which
 * `restart` argument each button routes to `PlayerViewModel.confirmResumePrompt`. This module has no
 * Robolectric/compose-test-rule dependency, so these are exercised as the same plain functions
 * TelevisionPlayerContent calls, mirroring the existing `shouldKeepScreenOn` /
 * TelevisionPlayerPolicyTest pattern rather than composing the tree.
 */
class ResumePromptWiringTest {

    @Test
    fun `a resumePrompt on state selects the resume overlay ahead of post-play and still-watching`() {
        // A state carrying a resumePrompt must win over the softer overlays it could otherwise
        // coexist with — reverting activePlayerModalOverlay to ignore resumePrompt (or to only check
        // it after showPostPlay/stillWatching) makes this fail.
        assertEquals(
            PlayerModalOverlay.ResumePrompt,
            activePlayerModalOverlay(
                hasError = false,
                resumePrompt = ResumePromptUi(positionMs = 90_000L),
                showPostPlay = true,
                stillWatching = true,
            ),
        )
    }

    @Test
    fun `no resumePrompt never selects the resume overlay`() {
        assertEquals(
            PlayerModalOverlay.None,
            activePlayerModalOverlay(
                hasError = false,
                resumePrompt = null,
                showPostPlay = false,
                stillWatching = false,
            ),
        )
    }

    @Test
    fun `a hard playback error still outranks an outstanding resumePrompt`() {
        assertEquals(
            PlayerModalOverlay.Error,
            activePlayerModalOverlay(
                hasError = true,
                resumePrompt = ResumePromptUi(positionMs = 90_000L),
                showPostPlay = false,
                stillWatching = false,
            ),
        )
    }

    @Test
    fun `playback is held paused while a resumePrompt is outstanding and playing`() {
        // This is the "not silently continue behind the prompt" requirement: if the transport ever
        // reports Playing while resumePrompt is still non-null, the screen must pause it back.
        assertTrue(shouldPauseForResumePrompt(ResumePromptUi(positionMs = 5_000L), PlayerState.Playing))
        assertFalse(shouldPauseForResumePrompt(null, PlayerState.Playing))
        assertFalse(shouldPauseForResumePrompt(ResumePromptUi(positionMs = 5_000L), PlayerState.Paused))
    }

    @Test
    fun `choosing Resume invokes confirmResumePrompt with restart false and resumes the transport`() {
        val controller = RecordingController()
        val actions = resumePromptActions(controller)

        actions.onResume()

        assertEquals(listOf(false), controller.confirmResumePromptCalls)
        assertEquals(1, controller.playCalls)
    }

    @Test
    fun `choosing Start over invokes confirmResumePrompt with restart true and resumes the transport`() {
        val controller = RecordingController()
        val actions = resumePromptActions(controller)

        actions.onStartOver()

        assertEquals(listOf(true), controller.confirmResumePromptCalls)
        assertEquals(1, controller.playCalls)
    }

    /** Records only the calls this test cares about; every other member is an unreachable no-op. */
    private class RecordingController : TelevisionPlayerController {
        val confirmResumePromptCalls = mutableListOf<Boolean>()
        var playCalls = 0

        override fun confirmResumePrompt(restart: Boolean) {
            confirmResumePromptCalls += restart
        }

        override fun play() {
            playCalls++
        }

        override fun stopAndReport() = fail()
        override fun loadShelves() = fail()
        override fun retryMusicContext() = fail()
        override fun pause() = fail()
        override fun togglePlayPause() = fail()
        override fun playUpNext() = fail()
        override fun playPreviousEpisode() = fail()
        override fun playNextEpisode() = fail()
        override fun playPreviousAudio() = fail()
        override fun playNextAudio() = fail()
        override fun playRandomAudio() = fail()
        override fun playFirstAudio() = fail()
        override fun toggleSubtitles() = fail()
        override fun toggleFavorite() = fail()
        override fun togglePlayed() = fail()
        override fun switchTo(itemId: String) = fail()
        override fun seekBy(deltaMs: Long) = fail()
        override fun seekTo(positionMs: Long) = fail()
        override fun setSurface(surface: Surface?) = fail()
        override fun setSurfaceSize(width: Int, height: Int) = fail()
        override fun selectTrack(track: PlayerTrack) = fail()
        override fun setQuality(quality: VideoQuality) = fail()
        override fun setSpeed(speed: Float) = fail()
        override fun setFrameMode(mode: String) = fail()
        override fun setHdrMode(mode: String) = fail()
        override fun setDeinterlaceMode(mode: String) = fail()
        override fun setAudioDelayMs(delayMs: Long) = fail()
        override fun setSubtitleDelayMs(delayMs: Long) = fail()
        override fun resetPlaybackDelays() = fail()
        override fun retryPlayback() = fail()

        private fun fail(): Nothing = throw AssertionError("unexpected controller call in this test")
    }
}
