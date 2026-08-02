package com.maik205.shoumeiplayer.ui.television.screens.browse

import com.maik205.shoumeiplayer.domain.model.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #89: "Backdrop images" and "Watched indicators" were persisted `ClientSettings` fields nothing
 * consumed. [resolveBackdropUrl] and [MediaItem.withWatchedIndicatorPreference] are the seams
 * [TelevisionHomeScreen], [HomeShelf] and `TelevisionLibraryScreen` render through; each test below
 * proves the ON and OFF renderings actually differ, not merely that the helpers execute.
 */
class InterfaceTogglesTest {

    @Test
    fun `backdrop images on keeps the hero backdrop url`() {
        assertEquals("https://example.test/backdrop.jpg", resolveBackdropUrl("https://example.test/backdrop.jpg", backdropImagesEnabled = true))
    }

    @Test
    fun `backdrop images off drops the hero backdrop url`() {
        assertNull(resolveBackdropUrl("https://example.test/backdrop.jpg", backdropImagesEnabled = false))
    }

    @Test
    fun `backdrop images off on an already-absent url stays null`() {
        assertNull(resolveBackdropUrl(null, backdropImagesEnabled = true))
    }

    @Test
    fun `watched indicators on preserves a watched item untouched`() {
        val watchedItem = mediaItem(watched = true)

        val rendered = watchedItem.withWatchedIndicatorPreference(enabled = true)

        assertSame(watchedItem, rendered)
        assertTrue(rendered.watched)
    }

    @Test
    fun `watched indicators off hides the watched status a viewer actually set`() {
        val watchedItem = mediaItem(watched = true)

        val rendered = watchedItem.withWatchedIndicatorPreference(enabled = false)

        assertFalse(rendered.watched)
    }

    @Test
    fun `watched indicators off leaves an unwatched item unaffected`() {
        val unwatchedItem = mediaItem(watched = false)

        val rendered = unwatchedItem.withWatchedIndicatorPreference(enabled = false)

        assertSame(unwatchedItem, rendered)
    }

    private fun mediaItem(watched: Boolean) = MediaItem(
        id = "item-1",
        title = "Title",
        type = "Movie",
        watched = watched,
    )
}
