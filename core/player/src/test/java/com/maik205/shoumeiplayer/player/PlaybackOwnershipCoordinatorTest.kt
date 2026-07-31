package com.maik205.shoumeiplayer.player

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class PlaybackOwnershipCoordinatorTest {
    @Test
    fun `acquiring a new owner stops the previous owner before handing over`() {
        val events = mutableListOf<String>()
        val coordinator = PlaybackOwnershipCoordinator()

        coordinator.acquire(PlaybackOwner.AUDIO_SERVICE) { events += "stop-audio" }
        coordinator.acquire(PlaybackOwner.VIDEO) { events += "stop-video" }

        assertEquals(listOf("stop-audio"), events)
        assertEquals(PlaybackOwner.VIDEO, coordinator.owner)
    }

    @Test
    fun `releasing a non-owner does not affect the active owner`() {
        val coordinator = PlaybackOwnershipCoordinator()
        coordinator.acquire(PlaybackOwner.VIDEO) {}

        coordinator.release(PlaybackOwner.AUDIO_SERVICE)

        assertEquals(PlaybackOwner.VIDEO, coordinator.owner)
        coordinator.release(PlaybackOwner.VIDEO)
        assertNull(coordinator.owner)
    }

    @Test
    fun `ownership transition clears pending audio handoff`() {
        val coordinator = PlaybackOwnershipCoordinator()
        AudioPlaybackHandoff.offer(null, PlayRequest(itemId = "item-a", url = "https://example.test/a"))

        coordinator.acquire(PlaybackOwner.AUDIO_SERVICE) {}
        coordinator.acquire(PlaybackOwner.VIDEO) {}

        assertNull(AudioPlaybackHandoff.consume("item-a", null))
    }
}
