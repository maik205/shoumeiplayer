package com.maik205.shoumeiplayer.ui.television.screens.player

import com.maik205.shoumeiplayer.player.PlayerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelevisionPlayerPolicyTest {
    @Test
    fun `only active video playback suppresses ambient mode`() {
        assertTrue(shouldKeepScreenOn(audio = false, state = PlayerState.Playing))
        assertTrue(shouldKeepScreenOn(audio = false, state = PlayerState.Buffering))
        assertFalse(shouldKeepScreenOn(audio = true, state = PlayerState.Playing))
        assertFalse(shouldKeepScreenOn(audio = false, state = PlayerState.Paused))
        assertFalse(shouldKeepScreenOn(audio = false, state = PlayerState.Ended))
        assertFalse(shouldKeepScreenOn(audio = false, state = PlayerState.Error("failed")))
    }
}
