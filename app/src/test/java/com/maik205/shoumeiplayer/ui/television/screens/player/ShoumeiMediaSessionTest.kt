package com.maik205.shoumeiplayer.ui.television.screens.player

import androidx.media3.common.Player
import com.maik205.shoumeiplayer.player.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Test

class ShoumeiMediaSessionTest {
    @Test
    fun `player states map to Media3 states`() {
        assertEquals(Player.STATE_IDLE, PlayerState.Idle.toMedia3PlaybackState())
        assertEquals(Player.STATE_BUFFERING, PlayerState.Loading.toMedia3PlaybackState())
        assertEquals(Player.STATE_BUFFERING, PlayerState.Buffering.toMedia3PlaybackState())
        assertEquals(Player.STATE_READY, PlayerState.Playing.toMedia3PlaybackState())
        assertEquals(Player.STATE_READY, PlayerState.Paused.toMedia3PlaybackState())
        assertEquals(Player.STATE_ENDED, PlayerState.Ended.toMedia3PlaybackState())
        assertEquals(Player.STATE_IDLE, PlayerState.Error("failed").toMedia3PlaybackState())
    }

    @Test
    fun `session seek commands delegate to navigation or position`() {
        assertEquals(
            MediaSessionSeekAction.Previous,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM.toMediaSessionSeekAction(),
        )
        assertEquals(
            MediaSessionSeekAction.Next,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM.toMediaSessionSeekAction(),
        )
        assertEquals(
            MediaSessionSeekAction.Position,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM.toMediaSessionSeekAction(),
        )
    }
}
