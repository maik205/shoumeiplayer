package com.maik205.shoumeiplayer.ui.television.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TelevisionFocusRestoreTest {

    private val items = listOf("a", "b", "c", "d")

    @Test
    fun `restores the same item wherever it now sits`() {
        assertEquals(2, televisionRestoreTarget(items, restoreId = "c", previousIndex = 2))
    }

    @Test
    fun `follows an item that moved rather than trusting the old position`() {
        val reordered = listOf("d", "c", "b", "a")
        assertEquals(3, televisionRestoreTarget(reordered, restoreId = "a", previousIndex = 0))
    }

    @Test
    fun `falls back to whatever occupies the position of a removed item`() {
        val withoutB = listOf("a", "c", "d")
        assertEquals(1, televisionRestoreTarget(withoutB, restoreId = "b", previousIndex = 1))
    }

    @Test
    fun `clamps to the last item when the list shrank past the old position`() {
        val shortened = listOf("a", "b")
        assertEquals(1, televisionRestoreTarget(shortened, restoreId = "gone", previousIndex = 9))
    }

    @Test
    fun `clamps a negative previous position to the start`() {
        assertEquals(0, televisionRestoreTarget(items, restoreId = "gone", previousIndex = -3))
    }

    @Test
    fun `leaves focus alone when nothing was remembered`() {
        assertNull(televisionRestoreTarget(items, restoreId = null, previousIndex = 2))
    }

    @Test
    fun `leaves focus alone when the list is empty`() {
        assertNull(televisionRestoreTarget(emptyList(), restoreId = "a", previousIndex = 0))
    }
}
