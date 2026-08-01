package com.maik205.shoumeiplayer.player

import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.TlsTrustSource
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class TlsTrustConfigurationTest {
    @Test
    fun `Android trust source selects the generated bundle while verification stays enabled`() {
        val options = tlsMpvOptions(
            ClientSettings(tlsTrustSource = TlsTrustSource.AndroidSystem),
            androidBundlePath = "/cache/android-ca.pem",
        )

        assertEquals("yes", options.verify)
        assertEquals("/cache/android-ca.pem", options.caFile)
    }

    @Test
    fun `mpv trust source does not inherit the Android bundle`() {
        val options = tlsMpvOptions(
            ClientSettings(tlsTrustSource = TlsTrustSource.Mpv),
            androidBundlePath = "/cache/android-ca.pem",
        )

        assertEquals("yes", options.verify)
        assertNull(options.caFile)
    }

    @Test
    fun `disabling verification is explicit and independent of trust source`() {
        val options = tlsMpvOptions(
            ClientSettings(verifyTlsCertificates = false, tlsTrustSource = TlsTrustSource.AndroidSystem),
            androidBundlePath = "/cache/android-ca.pem",
        )

        assertEquals("no", options.verify)
        assertNull(options.caFile)
    }
}
