package com.maik205.shoumeiplayer.ui.television.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TelevisionMediaTest {
    @Test
    fun `prefetch window contains only the next two items`() {
        assertEquals(4..5, artworkPrefetchIndices(lastVisibleIndex = 3, itemCount = 10))
    }

    @Test
    fun `prefetch window clips to the remaining items`() {
        assertEquals(9..9, artworkPrefetchIndices(lastVisibleIndex = 8, itemCount = 10))
    }

    @Test
    fun `prefetch window is empty at the end of the rail`() {
        assertEquals(IntRange.EMPTY, artworkPrefetchIndices(lastVisibleIndex = 9, itemCount = 10))
    }
}
