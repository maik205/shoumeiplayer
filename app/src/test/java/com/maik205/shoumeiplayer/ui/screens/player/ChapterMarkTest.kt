package com.maik205.shoumeiplayer.ui.screens.player

import com.maik205.shoumeiplayer.data.api.dto.ChapterInfoDto
import com.maik205.shoumeiplayer.util.Ticks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the pure chapter helpers behind the §5.3 seek-bar ticks (asset-audit P1–P5). */
class ChapterMarkTest {

    private fun chapter(ms: Long, name: String? = null) =
        ChapterInfoDto(startPositionTicks = Ticks.fromMs(ms), name = name)

    @Test
    fun `chapterMarks converts ticks to milliseconds`() {
        val marks = chapterMarks(listOf(chapter(0, "Cold Open"), chapter(90_000, "Titles")), 600_000)
        assertEquals(listOf(0L, 90_000L), marks.map { it.positionMs })
        assertEquals(listOf("Cold Open", "Titles"), marks.map { it.name })
    }

    @Test
    fun `chapterMarks drops chapters at or past the duration`() {
        val marks = chapterMarks(
            listOf(chapter(0), chapter(60_000), chapter(120_000), chapter(200_000)),
            durationMs = 120_000,
        )
        assertEquals(listOf(0L, 60_000L), marks.map { it.positionMs })
    }

    @Test
    fun `chapterMarks keeps everything when the duration is unknown`() {
        val marks = chapterMarks(listOf(chapter(0), chapter(999_000)), durationMs = null)
        assertEquals(2, marks.size)
        assertEquals(2, chapterMarks(listOf(chapter(0), chapter(999_000)), durationMs = 0).size)
    }

    @Test
    fun `chapterMarks sorts by position and blanks empty names`() {
        val marks = chapterMarks(listOf(chapter(60_000, "  "), chapter(10_000, "One")), 600_000)
        assertEquals(listOf(10_000L, 60_000L), marks.map { it.positionMs })
        assertEquals("One", marks[0].name)
        assertNull(marks[1].name)
    }

    @Test
    fun `currentChapter is the last mark at or before the playhead`() {
        val marks = chapterMarks(listOf(chapter(0, "A"), chapter(60_000, "B"), chapter(120_000, "C")), 600_000)
        assertEquals("A", currentChapter(marks, 0)?.name)
        assertEquals("A", currentChapter(marks, 59_999)?.name)
        assertEquals("B", currentChapter(marks, 60_000)?.name)
        assertEquals("C", currentChapter(marks, 500_000)?.name)
        assertNull(currentChapter(emptyList(), 1_000))
    }

    @Test
    fun `chapterFractions drops the opening chapter and maps the rest onto the lane`() {
        val marks = chapterMarks(listOf(chapter(0), chapter(50_000), chapter(75_000)), 100_000)
        assertEquals(listOf(0.5f, 0.75f), chapterFractions(marks, 100_000))
    }

    @Test
    fun `chapterFractions is empty without a duration`() {
        val marks = chapterMarks(listOf(chapter(50_000)), 100_000)
        assertTrue(chapterFractions(marks, 0).isEmpty())
        assertTrue(chapterFractions(marks, -1).isEmpty())
    }

    @Test
    fun `specLineWithChapter joins on a tab column and never adds a second middle dot`() {
        // §3.2 — the spec line already spends the one permitted `·`, so the chapter name joins it
        // on a tab column, and it is rendered as authored (no uppercase transform survives in v2).
        assertEquals("Direct Play · 1080p    Titles", specLineWithChapter("Direct Play · 1080p", "Titles"))
        assertEquals("1080p", specLineWithChapter("1080p", null))
        assertEquals("Titles", specLineWithChapter(null, "Titles"))
        assertEquals("", specLineWithChapter(null, "   "))
        assertEquals(1, specLineWithChapter("Direct Play · 1080p", "Titles").count { it == '·' })
    }

    @Test
    fun `nextChapterMs finds the boundary strictly ahead`() {
        val marks = chapterMarks(listOf(chapter(0), chapter(60_000), chapter(120_000)), 600_000)
        assertEquals(60_000L, nextChapterMs(marks, 0))
        assertEquals(120_000L, nextChapterMs(marks, 60_000))
        assertNull(nextChapterMs(marks, 120_000))
    }

    @Test
    fun `isChapterDoublePress requires same direction, the window, and chapters`() {
        // Second press, same direction, inside the window, chapters present → jump.
        assertTrue(isChapterDoublePress(sameDirection = true, elapsedMs = 200, chapterCount = 3))
        assertTrue(isChapterDoublePress(sameDirection = true, elapsedMs = ChapterDoublePressWindowMs, chapterCount = 1))
        // Direction changed mid-pair → both presses are plain seeks.
        assertFalse(isChapterDoublePress(sameDirection = false, elapsedMs = 200, chapterCount = 3))
        // Too slow → plain repeated seek.
        assertFalse(isChapterDoublePress(sameDirection = true, elapsedMs = ChapterDoublePressWindowMs + 1, chapterCount = 3))
        // Chapterless media: rapid seeking must never be hijacked.
        assertFalse(isChapterDoublePress(sameDirection = true, elapsedMs = 200, chapterCount = 0))
        // First-ever press (no prior timestamp) never counts as a double.
        assertFalse(isChapterDoublePress(sameDirection = true, elapsedMs = 0, chapterCount = 3))
    }

    @Test
    fun `previousChapterMs restarts the current chapter before stepping back`() {
        val marks = chapterMarks(listOf(chapter(0), chapter(60_000), chapter(120_000)), 600_000)
        // Deep inside chapter B → back to the start of B.
        assertEquals(60_000L, previousChapterMs(marks, 100_000))
        // Just after B's boundary (inside the grace window) → step back to A.
        assertEquals(0L, previousChapterMs(marks, 61_000))
        // Already at the very start → nowhere to go.
        assertNull(previousChapterMs(marks, 0))
    }

    // --- §5 Up Next window --------------------------------------------------------------------

    @Test
    fun `isUpNextDue opens only inside the last thirty seconds`() {
        val duration = 41_400_000L / 10 // any positive duration
        assertFalse(isUpNextDue(0, duration))
        assertFalse(isUpNextDue(duration - UP_NEXT_WINDOW_MS - 1, duration))
        // Exactly on the boundary counts: the card is meant to be up for the full window.
        assertTrue(isUpNextDue(duration - UP_NEXT_WINDOW_MS, duration))
        assertTrue(isUpNextDue(duration - 1_000, duration))
        assertTrue(isUpNextDue(duration, duration))
    }

    @Test
    fun `isUpNextDue stays shut without a known duration`() {
        // No end means no "near the end" - a live or still-loading stream must not offer Up Next.
        assertFalse(isUpNextDue(10_000, null))
        assertFalse(isUpNextDue(10_000, 0))
        assertFalse(isUpNextDue(10_000, -1))
    }

    @Test
    fun `isUpNextDue ignores a playhead past the end`() {
        // A position beyond the duration is a stale tick, not a reason to re-open a dismissed card.
        assertFalse(isUpNextDue(120_000, 60_000))
    }
}
