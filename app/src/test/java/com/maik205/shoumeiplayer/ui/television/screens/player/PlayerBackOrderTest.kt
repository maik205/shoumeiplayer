package com.maik205.shoumeiplayer.ui.television.screens.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The register's PLAYER-001, PLAYER-002, PLAYER-003, and PLAYER-016 are all one bug wearing four
 * hats: Back consulted playback state before it looked at what was on screen. These pin the order
 * down so it cannot quietly drift back.
 */
class PlayerBackOrderTest {

    private fun back(
        resumePromptVisible: Boolean = false,
        panelOpen: Boolean = false,
        queueVisible: Boolean = false,
        lyricsVisible: Boolean = false,
        postPlayBrowsing: Boolean = false,
        whileWatchingVisible: Boolean = false,
        stillWatching: Boolean = false,
        hasPlaybackError: Boolean = false,
        playbackEnded: Boolean = false,
        osdVisible: Boolean = false,
        miniSeekVisible: Boolean = false,
        audioOnly: Boolean = false,
    ) = playerBackAction(
        resumePromptVisible = resumePromptVisible,
        panelOpen = panelOpen,
        queueVisible = queueVisible,
        lyricsVisible = lyricsVisible,
        postPlayBrowsing = postPlayBrowsing,
        whileWatchingVisible = whileWatchingVisible,
        stillWatching = stillWatching,
        hasPlaybackError = hasPlaybackError,
        playbackEnded = playbackEnded,
        osdVisible = osdVisible,
        miniSeekVisible = miniSeekVisible,
        audioOnly = audioOnly,
    )

    @Test
    fun `plain Back asks for confirmation instead of stopping playback outright`() {
        assertEquals(PlayerBackAction.ConfirmExit, back())
    }

    @Test
    fun `an open drawer closes before playback state is consulted`() {
        assertEquals(
            PlayerBackAction.ClosePanel,
            back(panelOpen = true, playbackEnded = true, hasPlaybackError = true),
        )
    }

    @Test
    fun `the post-play episode browser closes rather than quitting the player`() {
        assertEquals(
            PlayerBackAction.ClosePostPlayBrowser,
            back(postPlayBrowsing = true, playbackEnded = true),
        )
    }

    @Test
    fun `Still Watching does not exit while a drawer is still open over it`() {
        assertEquals(
            PlayerBackAction.ClosePanel,
            back(panelOpen = true, stillWatching = true),
        )
    }

    @Test
    fun `the resume prompt outranks every other layer`() {
        assertEquals(
            PlayerBackAction.AnswerResumePrompt,
            back(
                resumePromptVisible = true,
                panelOpen = true,
                queueVisible = true,
                lyricsVisible = true,
                postPlayBrowsing = true,
                stillWatching = true,
            ),
        )
    }

    @Test
    fun `panels close before the queue and lyrics beneath them`() {
        assertEquals(
            PlayerBackAction.ClosePanel,
            back(panelOpen = true, queueVisible = true, lyricsVisible = true),
        )
        assertEquals(
            PlayerBackAction.CloseQueue,
            back(queueVisible = true, lyricsVisible = true),
        )
    }

    @Test
    fun `terminal states exit once nothing dismissable is left`() {
        assertEquals(PlayerBackAction.ExitPlayer, back(stillWatching = true))
        assertEquals(PlayerBackAction.ExitPlayer, back(hasPlaybackError = true))
        assertEquals(PlayerBackAction.ExitPlayer, back(playbackEnded = true))
    }

    @Test
    fun `terminal states still lose to a visible layer above them`() {
        assertEquals(
            PlayerBackAction.CloseWhileWatching,
            back(whileWatchingVisible = true, playbackEnded = true),
        )
    }

    @Test
    fun `the OSD and mini seek are only dismissed in video mode`() {
        assertEquals(PlayerBackAction.HideOsd, back(osdVisible = true))
        assertEquals(PlayerBackAction.HideMiniSeek, back(miniSeekVisible = true))
        // Audio mode keeps its OSD up permanently, so Back there means leaving.
        assertEquals(PlayerBackAction.ConfirmExit, back(osdVisible = true, audioOnly = true))
        assertEquals(PlayerBackAction.ConfirmExit, back(miniSeekVisible = true, audioOnly = true))
    }

    @Test
    fun `the While Watching shelf is a video-only layer`() {
        assertEquals(
            PlayerBackAction.CloseWhileWatching,
            back(whileWatchingVisible = true),
        )
        assertEquals(
            PlayerBackAction.ConfirmExit,
            back(whileWatchingVisible = true, audioOnly = true),
        )
    }
}
