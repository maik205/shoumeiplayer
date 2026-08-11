package com.maik205.shoumeiplayer.ui.television.screens.browse

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchNavigationTest {
    @Test
    fun `up from first row returns to query field`() {
        assertEquals(SearchGridMove.Field, resolveSearchGridMove(3, 12, Key.DirectionUp))
    }

    @Test
    fun `vertical moves preserve the result column`() {
        assertEquals(SearchGridMove.Result(7), resolveSearchGridMove(3, 12, Key.DirectionDown))
        assertEquals(SearchGridMove.Result(3), resolveSearchGridMove(7, 12, Key.DirectionUp))
    }

    @Test
    fun `grid edges do not leak focus to unrelated controls`() {
        assertEquals(SearchGridMove.Blocked, resolveSearchGridMove(0, 12, Key.DirectionLeft))
        assertEquals(SearchGridMove.Blocked, resolveSearchGridMove(3, 12, Key.DirectionRight))
        assertEquals(SearchGridMove.Blocked, resolveSearchGridMove(6, 7, Key.DirectionDown))
    }
}
