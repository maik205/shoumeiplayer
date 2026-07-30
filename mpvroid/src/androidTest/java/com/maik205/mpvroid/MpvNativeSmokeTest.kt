package com.maik205.mpvroid

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MpvNativeSmokeTest {
    @Test
    fun officialMpvCoreCreatesInitializesAndDestroys() {
        MpvNative.create(ApplicationProvider.getApplicationContext())
        try {
            assertEquals(0, MpvNative.setOptionString("config", "no"))
            assertEquals(0, MpvNative.setOptionString("vo", "null"))
            assertEquals(0, MpvNative.setOptionString("ao", "null"))
            assertEquals(0, MpvNative.setOptionString("idle", "yes"))
            MpvNative.init()
        } finally {
            MpvNative.destroy()
        }
    }
}
