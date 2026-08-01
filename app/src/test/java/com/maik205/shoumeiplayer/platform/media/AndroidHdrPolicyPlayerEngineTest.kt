package com.maik205.shoumeiplayer.platform.media

import com.maik205.shoumeiplayer.domain.settings.HdrMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidHdrPolicyPlayerEngineTest {
    @Test
    fun `automatic and passthrough fall back to SDR on an SDR display`() {
        assertEquals(HdrMode.ForceSdr, effectiveHdrMode(HdrMode.Automatic, displaySupportsHdr = false))
        assertEquals(HdrMode.ForceSdr, effectiveHdrMode(HdrMode.Passthrough, displaySupportsHdr = false))
    }

    @Test
    fun `tone mapping and passthrough remain available on HDR displays`() {
        assertEquals(HdrMode.ToneMap, effectiveHdrMode(HdrMode.ToneMap, displaySupportsHdr = false))
        assertEquals(HdrMode.Passthrough, effectiveHdrMode(HdrMode.Passthrough, displaySupportsHdr = true))
    }
}
