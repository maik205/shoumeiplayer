package com.maik205.shoumeiplayer.ui.television.screens.browse

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicLibraryNavigationTest {
    @Test
    fun `up from first shelf returns to hero`() {
        assertEquals(
            MusicVerticalMove.Hero,
            resolveMusicVerticalMove(0, 3, 4, MusicMoveDirection.Up),
        )
    }

    @Test
    fun `vertical shelf moves preserve the item column`() {
        assertEquals(
            MusicVerticalMove.Shelf(shelfIndex = 1, itemIndex = 4),
            resolveMusicVerticalMove(0, 3, 4, MusicMoveDirection.Down),
        )
        assertEquals(
            MusicVerticalMove.Shelf(shelfIndex = 0, itemIndex = 4),
            resolveMusicVerticalMove(1, 3, 4, MusicMoveDirection.Up),
        )
    }

    @Test
    fun `down from final shelf stays in the final shelf`() {
        assertEquals(
            MusicVerticalMove.None,
            resolveMusicVerticalMove(2, 3, 4, MusicMoveDirection.Down),
        )
    }
}
