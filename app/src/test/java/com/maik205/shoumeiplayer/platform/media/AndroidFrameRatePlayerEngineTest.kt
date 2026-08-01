package com.maik205.shoumeiplayer.platform.media

import android.view.Surface
import com.maik205.shoumeiplayer.domain.settings.RefreshRateSwitching
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidFrameRatePlayerEngineTest {
    @Test
    fun `match video uses seamless fixed-source strategy`() {
        val request = resolveFrameRateRequest(RefreshRateSwitching.MatchVideo, 23.976, playbackActive = true)

        assertEquals(23.976f, request?.fps ?: 0f, 0.001f)
        assertEquals(Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE, request?.compatibility)
        assertEquals(Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS, request?.strategy)
    }

    @Test
    fun `disabled or inactive playback clears frame-rate ownership`() {
        assertNull(resolveFrameRateRequest(RefreshRateSwitching.Disabled, 24.0, playbackActive = true))
        assertNull(resolveFrameRateRequest(RefreshRateSwitching.Always, 24.0, playbackActive = false))
    }
}
