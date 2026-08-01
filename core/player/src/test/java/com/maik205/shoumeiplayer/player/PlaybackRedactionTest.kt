package com.maik205.shoumeiplayer.player

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test

class PlaybackRedactionTest {
    @Test
    fun `redacts signed URL query and sensitive headers`() {
        val url = redactPlaybackUrl("https://server.example/Items/a/stream?api_key=secret&Expires=123")
        val headers = redactPlaybackHeaders(
            mapOf("Authorization" to "MediaBrowser Token=secret", "Accept" to "video/mp4"),
        )

        assertEquals("https://server.example/Items/a/stream", url)
        assertEquals("<redacted>", headers["Authorization"])
        assertEquals("video/mp4", headers["Accept"])
        assertFalse(url.contains("secret"))
    }
}
