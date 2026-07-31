package com.maik205.shoumeiplayer.player

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.After
import org.junit.Test

class AudioPlaybackHandoffTest {
    @After
    fun tearDown() {
        AudioPlaybackHandoff.clear()
    }

    @Test
    fun `request and resolved playback are consumed as one record`() {
        val request = PlayRequest(itemId = "item-a", url = "https://example.test/a")
        val resolved = resolvedPlayback("item-a")

        AudioPlaybackHandoff.offer("account-a", request, nowMs = 1000L)
        AudioPlaybackHandoff.offerResolved("account-a", resolved, nowMs = 1100L)

        assertEquals(
            AudioPlaybackHandoff.Record("account-a", request, resolved, 1000L),
            AudioPlaybackHandoff.consume("item-a", "account-a", nowMs = 1200L),
        )
        assertNull(AudioPlaybackHandoff.consume("item-a", "account-a", nowMs = 1201L))
    }

    @Test
    fun `account mismatch and expired records cannot be consumed`() {
        AudioPlaybackHandoff.offer(
            "account-a",
            PlayRequest(itemId = "item-a", url = "https://example.test/a"),
            nowMs = 0L,
        )

        assertNull(AudioPlaybackHandoff.consume("item-a", "account-b", nowMs = 1L))
        assertNull(AudioPlaybackHandoff.consume("item-a", "account-a", nowMs = 5 * 60 * 1000L + 1L))
    }

    @Test
    fun `clearing an item removes both request and resolved state`() {
        AudioPlaybackHandoff.offer(
            "account-a",
            PlayRequest(itemId = "item-a", url = "https://example.test/a"),
            nowMs = 0L,
        )
        AudioPlaybackHandoff.offerResolved("account-a", resolvedPlayback("item-a"), nowMs = 1L)

        AudioPlaybackHandoff.clear("item-a")

        assertNull(AudioPlaybackHandoff.consume("item-a", "account-a", nowMs = 2L))
    }

    private fun resolvedPlayback(itemId: String) = ResolvedPlayback(
        itemId = itemId,
        mediaSourceId = "source",
        playSessionId = "session",
        streamUrl = "https://example.test/stream",
        playMethod = "DirectPlay",
        runTimeTicks = null,
        audioTracks = emptyList(),
        subtitleTracks = emptyList(),
        defaultAudioIndex = null,
        defaultSubtitleIndex = null,
        headers = emptyMap(),
    )
}
