package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.session.ServerConnectionCandidate
import com.maik205.shoumeiplayer.data.session.serverConnectionCandidates
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConnectionCandidatesTest {

    @Test
    fun `bare address probes https before insecure http fallback`() {
        assertEquals(
            listOf(
                ServerConnectionCandidate("https://myserver.local:8096"),
                ServerConnectionCandidate("http://myserver.local:8096", requiresInsecureWarning = true),
            ),
            serverConnectionCandidates("myserver.local:8096", prioritizeHttps = true),
        )
    }

    @Test
    fun `discovered http address is upgraded for the secure probe`() {
        assertEquals(
            listOf(
                ServerConnectionCandidate("https://192.168.1.20:8096"),
                ServerConnectionCandidate("http://192.168.1.20:8096", requiresInsecureWarning = true),
            ),
            serverConnectionCandidates("http://192.168.1.20:8096", prioritizeHttps = true),
        )
    }

    @Test
    fun `explicit http address remains an insecure warned choice`() {
        assertEquals(
            listOf(
                ServerConnectionCandidate("http://myserver.local:8096", requiresInsecureWarning = true),
            ),
            serverConnectionCandidates("http://myserver.local:8096", prioritizeHttps = false),
        )
    }

    @Test
    fun `explicit https address has no insecure fallback`() {
        assertEquals(
            listOf(ServerConnectionCandidate("https://myserver.local:8096")),
            serverConnectionCandidates("https://myserver.local:8096", prioritizeHttps = false),
        )
    }
}
