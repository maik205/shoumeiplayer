package com.maik205.shoumeiplayer.ui.television.screens.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailFocusPolicyTest {
    @Test
    fun `first content target follows rendered section order`() {
        assertEquals(
            DetailContentFocusTarget.Series,
            firstDetailContentFocusTarget(true, true, true, true, true, true),
        )
        assertEquals(
            DetailContentFocusTarget.Tracks,
            firstDetailContentFocusTarget(false, true, true, true, true, true),
        )
        assertEquals(
            DetailContentFocusTarget.People,
            firstDetailContentFocusTarget(false, false, false, false, true, true),
        )
        assertNull(firstDetailContentFocusTarget(false, false, false, false, false, false))
    }

    @Test
    fun `retry fallback only runs after focused error disappears`() {
        assertTrue(shouldRestoreDetailRetryFocus(setOf("section:Tracks"), retryHadFocus = true))
        assertFalse(shouldRestoreDetailRetryFocus(emptySet(), retryHadFocus = true))
        assertFalse(shouldRestoreDetailRetryFocus(setOf("action"), retryHadFocus = false))
    }
}
