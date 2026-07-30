package com.maik205.shoumeiplayer.ui.television.components

import android.os.PowerManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalPolicyTest {
    @Test
    fun `optional work stops at moderate thermal pressure`() {
        assertTrue(thermalAllowsOptionalWork(PowerManager.THERMAL_STATUS_LIGHT))
        assertFalse(thermalAllowsOptionalWork(PowerManager.THERMAL_STATUS_MODERATE))
        assertFalse(thermalAllowsOptionalWork(PowerManager.THERMAL_STATUS_CRITICAL))
    }
}
