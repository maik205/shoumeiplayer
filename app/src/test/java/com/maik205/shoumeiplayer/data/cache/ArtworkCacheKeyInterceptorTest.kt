package com.maik205.shoumeiplayer.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkCacheKeyInterceptorTest {

    @Test
    fun `session tokens do not change artwork cache identity`() {
        val firstSession =
            "https://media.test/Items/series-1/Images/Logo" +
                "?maxWidth=600&quality=90&tag=logo-tag&api_key=first"
        val replacementSession =
            "https://media.test/Items/series-1/Images/Logo" +
                "?maxWidth=600&quality=90&tag=logo-tag&api_key=replacement"

        assertEquals(
            canonicalArtworkCacheKey(firstSession),
            canonicalArtworkCacheKey(replacementSession),
        )
        assertEquals(
            "https://media.test/Items/series-1/Images/Logo" +
                "?maxWidth=600&quality=90&tag=logo-tag",
            canonicalArtworkCacheKey(firstSession),
        )
    }

    @Test
    fun `response defining artwork variants remain distinct`() {
        val base = "https://media.test/Items/series-1/Images/Backdrop"
        val seriesOne = canonicalArtworkCacheKey(
            "$base?maxWidth=1600&quality=90&tag=backdrop-1&api_key=token",
        )
        val seriesTwo = canonicalArtworkCacheKey(
            "https://media.test/Items/series-2/Images/Backdrop" +
                "?maxWidth=1600&quality=90&tag=backdrop-1&api_key=token",
        )
        val smaller = canonicalArtworkCacheKey(
            "$base?maxWidth=720&quality=90&tag=backdrop-1&api_key=token",
        )
        val newerArtwork = canonicalArtworkCacheKey(
            "$base?maxWidth=1600&quality=90&tag=backdrop-2&api_key=token",
        )

        assertNotEquals(seriesOne, seriesTwo)
        assertNotEquals(seriesOne, smaller)
        assertNotEquals(seriesOne, newerArtwork)
    }

    @Test
    fun `only authenticated network URLs receive an override`() {
        assertNull(
            canonicalArtworkCacheKey(
                "https://media.test/Items/item-1/Images/Primary?tag=image-tag",
            ),
        )
        assertNull(canonicalArtworkCacheKey("content://media/external/images/1?api_key=token"))
    }
}
