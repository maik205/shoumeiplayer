package com.maik205.shoumeiplayer.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardGridTest {

    // --- applyKey --------------------------------------------------------------------------

    @Test
    fun `applyKey Char appends the character`() {
        assertEquals("ab", applyKey("a", KeyAction.Char('b')))
        assertEquals("a", applyKey("", KeyAction.Char('a')))
    }

    @Test
    fun `applyKey Space appends a space unless empty or already trailing space`() {
        assertEquals("a ", applyKey("a", KeyAction.Space))
        assertEquals("", applyKey("", KeyAction.Space))
        assertEquals("a ", applyKey("a ", KeyAction.Space))
    }

    @Test
    fun `applyKey Delete drops the last character and no-ops when empty`() {
        assertEquals("a", applyKey("ab", KeyAction.Delete))
        assertEquals("", applyKey("", KeyAction.Delete))
    }

    @Test
    fun `applyKey Clear returns empty string`() {
        assertEquals("", applyKey("anything", KeyAction.Clear))
        assertEquals("", applyKey("", KeyAction.Clear))
    }

    // --- move: clamping into a shorter row --------------------------------------------------

    @Test
    fun `move Down clamps column into a shorter target row`() {
        val grid = KeyboardGrid.Default
        // Row 5 (456789) has 6 cols; row 6 (Space3 Delete2 Clear1) has 3 keys, last index 2.
        val cursor = KeyboardCursor(row = 5, col = 5)

        val moved = grid.move(cursor, KeyboardDirection.Down)

        assertEquals(KeyboardCursor(row = 6, col = 2), moved)
    }

    @Test
    fun `move Up keeps the column when the target row is wider`() {
        val grid = KeyboardGrid.Default
        val cursor = KeyboardCursor(row = 6, col = 2)

        val moved = grid.move(cursor, KeyboardDirection.Up)

        assertEquals(KeyboardCursor(row = 5, col = 2), moved)
    }

    // --- move: edges return the cursor unchanged --------------------------------------------

    @Test
    fun `move Left at column 0 returns the cursor unchanged`() {
        val grid = KeyboardGrid.Default
        val cursor = KeyboardCursor(row = 0, col = 0)

        assertEquals(cursor, grid.move(cursor, KeyboardDirection.Left))
    }

    @Test
    fun `move Right at the last column of a row returns the cursor unchanged`() {
        val grid = KeyboardGrid.Default
        // Row 0 (ABCDEF) has last index 5.
        val cursor = KeyboardCursor(row = 0, col = 5)

        assertEquals(cursor, grid.move(cursor, KeyboardDirection.Right))
    }

    @Test
    fun `move Up at row 0 returns the cursor unchanged`() {
        val grid = KeyboardGrid.Default
        val cursor = KeyboardCursor(row = 0, col = 2)

        assertEquals(cursor, grid.move(cursor, KeyboardDirection.Up))
    }

    @Test
    fun `move Down at the last row returns the cursor unchanged`() {
        val grid = KeyboardGrid.Default
        val lastRow = grid.rows.lastIndex
        val cursor = KeyboardCursor(row = lastRow, col = 1)

        assertEquals(cursor, grid.move(cursor, KeyboardDirection.Down))
    }
}
