package com.maik205.shoumeiplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerModelsTest {
    @Test
    fun `chapter marks are sorted clipped and addressable on the player timeline`() {
        val marks = chapterMarks(
            chapters = listOf(
                ChapterMark(120_000, "Act Two"),
                ChapterMark(0, "Opening"),
                ChapterMark(60_000, "Act One"),
                ChapterMark(180_000, "Credits"),
            ),
            durationMs = 180_000,
        )

        assertEquals(listOf(0L, 60_000L, 120_000L), marks.map(ChapterMark::positionMs))
        assertEquals("Act One", currentChapter(marks, 90_000)?.name)
        assertNull(currentChapter(marks, -1))
    }

    @Test
    fun `up next opens only in the final known playback window`() {
        val duration = 120_000L

        assertFalse(isUpNextDue(duration - UP_NEXT_WINDOW_MS - 1, duration))
        assertTrue(isUpNextDue(duration - UP_NEXT_WINDOW_MS, duration))
        assertTrue(isUpNextDue(duration, duration))
        assertFalse(isUpNextDue(duration + 1, duration))
        assertFalse(isUpNextDue(1, null))
    }
}
