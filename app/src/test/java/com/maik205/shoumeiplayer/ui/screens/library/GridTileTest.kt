package com.maik205.shoumeiplayer.ui.screens.library

import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.UserItemDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers `BaseItemDto.toGridTile` — the pure mapping behind the §5.4 / §5.6 contact sheet
 * (asset-audit L1, L2, L4).
 *
 * The three things worth pinning are the ones that silently 404 or letterbox when they regress:
 * which art link wins, which blurhash is paired with it, and when the reported aspect ratio is
 * allowed to reshape the tile.
 */
class GridTileTest {

    private val images = ImageUrlBuilder { "http://nas:8096" }

    private fun movie(
        id: String = "m1",
        name: String? = "Solaris",
        imageTags: Map<String, String> = mapOf("Primary" to "ptag"),
        blurHashes: Map<String, Map<String, String>> = emptyMap(),
        aspectRatio: Double? = null,
        userData: UserItemDataDto? = null,
    ) = BaseItemDto(
        id = id,
        name = name,
        type = "Movie",
        productionYear = 1972,
        imageTags = imageTags,
        imageBlurHashes = blurHashes,
        primaryImageAspectRatio = aspectRatio,
        userData = userData,
    )

    private fun episode(
        imageTags: Map<String, String> = emptyMap(),
        parentThumbItemId: String? = null,
        parentThumbImageTag: String? = null,
        seriesId: String? = null,
        seriesThumbImageTag: String? = null,
        seriesPrimaryImageTag: String? = null,
        blurHashes: Map<String, Map<String, String>> = emptyMap(),
        aspectRatio: Double? = null,
    ) = BaseItemDto(
        id = "e1",
        name = "The Bicameral Mind",
        type = "Episode",
        indexNumber = 10,
        parentIndexNumber = 1,
        seriesId = seriesId,
        imageTags = imageTags,
        parentThumbItemId = parentThumbItemId,
        parentThumbImageTag = parentThumbImageTag,
        seriesThumbImageTag = seriesThumbImageTag,
        seriesPrimaryImageTag = seriesPrimaryImageTag,
        imageBlurHashes = blurHashes,
        primaryImageAspectRatio = aspectRatio,
    )

    // --- L2: the poster chain replaces the old hand-rolled tag soup ---

    @Test
    fun `a movie tile draws its own poster at the 320 poster budget`() {
        val tile = movie().toGridTile(images)
        assertEquals("http://nas:8096/Items/m1/Images/Primary?maxWidth=320&quality=90&tag=ptag", tile.imageUrl)
        assertEquals("Solaris", tile.title)
        assertEquals("1972", tile.subtitle)
        assertNull(tile.typeLabel)
    }

    @Test
    fun `an episode with no poster of its own falls back to the series poster, never its own id`() {
        val tile = episode(seriesId = "s9", seriesPrimaryImageTag = "stag").toGridTile(images)
        // The regression this guards: pairing the *series* tag with the *episode* id, which 404s.
        assertEquals("http://nas:8096/Items/s9/Images/Primary?maxWidth=320&quality=90&tag=stag", tile.imageUrl)
    }

    @Test
    fun `a parent tag with no parent id is skipped rather than pinned to the child`() {
        // seriesPrimaryImageTag present but seriesId null — the chain must yield nothing at all.
        val tile = episode(seriesPrimaryImageTag = "stag").toGridTile(images)
        assertNull(tile.imageUrl)
    }

    @Test
    fun `no art anywhere leaves a null url so the tile draws the empty frame`() {
        val tile = movie(imageTags = emptyMap()).toGridTile(images)
        assertNull(tile.imageUrl)
        assertNull(tile.blurHash)
    }

    // --- L4: Search prefers an episode's thumb, and only for episodes ---

    @Test
    fun `search prefers an episode thumb over the series poster`() {
        val tile = episode(
            imageTags = mapOf("Thumb" to "ttag"),
            seriesId = "s9",
            seriesPrimaryImageTag = "stag",
        ).toGridTile(images, typeLabel = "Episode", preferEpisodeThumb = true)
        assertEquals("http://nas:8096/Items/e1/Images/Thumb?maxWidth=320&quality=90&tag=ttag", tile.imageUrl)
        assertEquals("Episode", tile.typeLabel)
        assertEquals("S1:E10", tile.subtitle)
    }

    @Test
    fun `the episode thumb chain walks season then series before giving up on the poster`() {
        val seasonThumb = episode(
            parentThumbItemId = "se4",
            parentThumbImageTag = "pttag",
            seriesId = "s9",
            seriesThumbImageTag = "sttag",
        ).toGridTile(images, preferEpisodeThumb = true)
        assertEquals(
            "http://nas:8096/Items/se4/Images/Thumb?maxWidth=320&quality=90&tag=pttag",
            seasonThumb.imageUrl,
        )

        val seriesThumb = episode(seriesId = "s9", seriesThumbImageTag = "sttag")
            .toGridTile(images, preferEpisodeThumb = true)
        assertEquals(
            "http://nas:8096/Items/s9/Images/Thumb?maxWidth=320&quality=90&tag=sttag",
            seriesThumb.imageUrl,
        )
    }

    @Test
    fun `an episode with no thumb anywhere still falls back to the poster chain`() {
        val tile = episode(seriesId = "s9", seriesPrimaryImageTag = "stag")
            .toGridTile(images, preferEpisodeThumb = true)
        assertEquals("http://nas:8096/Items/s9/Images/Primary?maxWidth=320&quality=90&tag=stag", tile.imageUrl)
    }

    @Test
    fun `library never takes the thumb branch even for an episode`() {
        val tile = episode(imageTags = mapOf("Thumb" to "ttag", "Primary" to "ptag")).toGridTile(images)
        assertTrue(tile.imageUrl!!.contains("/Images/Primary"))
    }

    // --- L1: blurhash is paired with the link that won ---

    @Test
    fun `the poster blurhash is looked up under Primary and keyed by our own tag`() {
        val tile = movie(
            blurHashes = mapOf(
                "Primary" to mapOf("ptag" to "LEHV6nWB", "other" to "WRONG"),
                "Thumb" to mapOf("ttag" to "ALSOWRONG"),
            ),
        ).toGridTile(images)
        assertEquals("LEHV6nWB", tile.blurHash)
    }

    @Test
    fun `the thumb blurhash is used when the thumb wins`() {
        val tile = episode(
            imageTags = mapOf("Thumb" to "ttag"),
            blurHashes = mapOf(
                "Thumb" to mapOf("ttag" to "THUMBHASH"),
                "Primary" to mapOf("ptag" to "POSTERHASH"),
            ),
        ).toGridTile(images, preferEpisodeThumb = true)
        assertEquals("THUMBHASH", tile.blurHash)
    }

    @Test
    fun `a parent-supplied hash resolves even though we hold no tag of our own`() {
        // imageTypeLimit=1 on every grid call means one tag per type, so the sole entry is the
        // parent's — this is what lets a series poster still fade up from colour.
        val tile = episode(
            seriesId = "s9",
            seriesPrimaryImageTag = "stag",
            blurHashes = mapOf("Primary" to mapOf("stag" to "SERIESHASH")),
        ).toGridTile(images)
        assertEquals("SERIESHASH", tile.blurHash)
    }

    // --- L1: the aspect ratio only reshapes the tile when our own poster won ---

    @Test
    fun `a movie honours its own reported aspect ratio`() {
        assertEquals(0.75f, movie(aspectRatio = 0.75).toGridTile(images).aspect)
    }

    @Test
    fun `an absent ratio leaves the tile on the default 2 to 3 frame`() {
        assertNull(movie(aspectRatio = null).toGridTile(images).aspect)
    }

    @Test
    fun `an out-of-band ratio is clamped rather than allowed to break the row height`() {
        assertEquals(2.0f, movie(aspectRatio = 9.0).toGridTile(images).aspect)
        assertEquals(0.5f, movie(aspectRatio = 0.01).toGridTile(images).aspect)
    }

    @Test
    fun `a nonsense ratio is dropped, not clamped into a plausible-looking lie`() {
        assertNull(movie(aspectRatio = Double.NaN).toGridTile(images).aspect)
        assertNull(movie(aspectRatio = Double.POSITIVE_INFINITY).toGridTile(images).aspect)
        assertNull(movie(aspectRatio = 0.0).toGridTile(images).aspect)
        assertNull(movie(aspectRatio = -1.5).toGridTile(images).aspect)
    }

    @Test
    fun `a thumb tile carries no aspect because it crops into the 2 to 3 frame`() {
        val tile = episode(imageTags = mapOf("Thumb" to "ttag"), aspectRatio = 1.777)
            .toGridTile(images, preferEpisodeThumb = true)
        assertNull(tile.aspect)
    }

    @Test
    fun `an episode still never letterboxes the tile even when the poster branch wins`() {
        // 16:9 honoured here would leave a 160x90 strip floating inside the 160x240 tile.
        val tile = episode(imageTags = mapOf("Primary" to "ptag"), aspectRatio = 1.777)
            .toGridTile(images, preferEpisodeThumb = true)
        assertTrue(tile.imageUrl!!.contains("/Images/Primary"))
        assertNull(tile.aspect)
    }

    @Test
    fun `a ratio belonging to us is not applied to a parent's artwork`() {
        val tile = BaseItemDto(
            id = "s2",
            name = "Season 2",
            type = "Season",
            seriesId = "s9",
            seriesPrimaryImageTag = "stag",
            primaryImageAspectRatio = 1.5,
        ).toGridTile(images)
        assertTrue(tile.imageUrl!!.contains("/Items/s9/"))
        assertNull(tile.aspect)
    }

    // --- L3: the enableUserData fix has to actually surface on the tile ---

    @Test
    fun `progress becomes a fraction only while genuinely part-watched`() {
        fun pct(p: Double?) = movie(userData = UserItemDataDto(playedPercentage = p))
            .toGridTile(images).progressFraction

        assertEquals(0.42f, pct(42.0))
        assertNull("0% is not resumable", pct(0.0))
        assertNull("100% is watched, not in progress", pct(100.0))
        assertNull(pct(null))
        assertNull("no UserData at all", movie().toGridTile(images).progressFraction)
    }

    @Test
    fun `watched comes straight off UserData Played`() {
        assertTrue(movie(userData = UserItemDataDto(played = true)).toGridTile(images).watched)
        assertFalse(movie(userData = UserItemDataDto(played = false)).toGridTile(images).watched)
        assertFalse(movie().toGridTile(images).watched)
    }

    // --- misc mapping ---

    @Test
    fun `a missing name maps to an empty title rather than null`() {
        assertEquals("", movie(name = null).toGridTile(images).title)
    }

    @Test
    fun `an episode with no numbering still produces a stable subtitle`() {
        val tile = BaseItemDto(id = "e2", type = "Episode").toGridTile(images)
        assertEquals("S0:E0", tile.subtitle)
    }

    @Test
    fun `no server url yields no art rather than a relative url`() {
        val offline = ImageUrlBuilder { null }
        assertNull(movie().toGridTile(offline).imageUrl)
    }
}
