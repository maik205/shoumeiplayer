package com.maik205.shoumeiplayer.data.api

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PrivateNetworkGuardTest {

    @Test
    fun `public http is rejected`() {
        assertNotNull(cleartextRejectionReason("http://media.example.com/System/Info/Public"))
    }

    @Test
    fun `private and local http are allowed to reach the explicit confirmation flow`() {
        assertNull(cleartextRejectionReason("http://192.168.1.20:8096/System/Info/Public"))
        assertNull(cleartextRejectionReason("http://jellyfin.local:8096/System/Info/Public"))
    }

    @Test
    fun `https is never rejected by the cleartext guard`() {
        assertNull(cleartextRejectionReason("https://media.example.com/System/Info/Public"))
    }
}
