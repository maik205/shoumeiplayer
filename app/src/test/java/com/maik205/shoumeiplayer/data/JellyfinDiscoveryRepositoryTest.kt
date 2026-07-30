package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.repo.DiscoveredJellyfinServer
import com.maik205.shoumeiplayer.data.repo.normalizeDiscoveredServers
import com.maik205.shoumeiplayer.data.repo.parseDiscoveryResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinDiscoveryRepositoryTest {

    @Test
    fun `discovery response parsing normalizes Jellyfin addresses`() {
        val server = parseDiscoveryResponse(
            """
            {
              "Address":"192.168.1.20:8096/web/index.html",
              "Id":"server-1",
              "Name":"Living Room",
              "EndpointAddress":"192.168.1.20"
            }
            """.trimIndent(),
        )

        assertEquals("http://192.168.1.20:8096", server?.address)
        assertEquals("server-1", server?.id)
        assertEquals("Living Room", server?.name)
    }

    @Test
    fun `discovery ignores malformed payloads and unsupported schemes`() {
        assertNull(parseDiscoveryResponse("not-json"))
        assertNull(parseDiscoveryResponse("""{"Address":"ftp://media.local"}"""))
    }

    @Test
    fun `normalization deduplicates endpoints and returns deterministic immutable values`() {
        val normalized = normalizeDiscoveredServers(
            listOf(
                DiscoveredJellyfinServer("HTTP://server.local:8096/", name = "Second"),
                DiscoveredJellyfinServer("http://server.local:8096", name = "First"),
                DiscoveredJellyfinServer("https://other.local", name = "Other"),
            ),
        )

        assertEquals(2, normalized.size)
        assertEquals(
            setOf("http://server.local:8096", "https://other.local"),
            normalized.map { it.address }.toSet(),
        )
    }
}
