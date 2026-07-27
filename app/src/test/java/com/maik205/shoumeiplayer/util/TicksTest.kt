package com.maik205.shoumeiplayer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TicksTest {

    @Test
    fun `ticks round trip for multiples of 10000`() {
        val ms = 1_234_560L
        assertEquals(ms, Ticks.toMs(Ticks.fromMs(ms)))
    }

    @Test
    fun `formatDuration with hours`() {
        assertEquals("1:02:03", Ticks.formatDuration(3_723_000))
    }

    @Test
    fun `formatDuration without hours`() {
        assertEquals("2:23", Ticks.formatDuration(143_000))
    }
}
