package com.maik205.shoumeiplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * docs/player-controls.md §3 — trickplay tile geometry and URL shape.
 *
 * The band below is the one `item_detail.json` ships: 320×180 thumbnails, 10×10 per sheet, one every
 * 10s, 414 of them (≈69 minutes), so a sheet covers 100 thumbnails = 1000s.
 */
class TrickplayTest {

    private val band = TrickplayInfo(
        width = 320,
        height = 180,
        tileWidth = 10,
        tileHeight = 10,
        thumbnailCount = 414,
        interval = 10_000,
    )

    @Test
    fun `position zero is the top-left thumbnail of the first sheet`() {
        val tile = TrickplayMath.tileAt(band, 0)!!
        assertEquals(0, tile.thumbnailIndex)
        assertEquals(0, tile.tileIndex)
        assertEquals(0, tile.row)
        assertEquals(0, tile.column)
        assertEquals(0, tile.left)
        assertEquals(0, tile.top)
        assertEquals(320, tile.width)
        assertEquals(180, tile.height)
    }

    @Test
    fun `thumbnails fill a sheet left to right then top to bottom`() {
        // 4th thumbnail (30s): still row 0, column 3.
        val fourth = TrickplayMath.tileAt(band, 30_000)!!
        assertEquals(3, fourth.thumbnailIndex)
        assertEquals(0, fourth.row)
        assertEquals(3, fourth.column)
        assertEquals(3 * 320, fourth.left)
        assertEquals(0, fourth.top)

        // 11th thumbnail (100s) wraps onto row 1, column 0.
        val eleventh = TrickplayMath.tileAt(band, 100_000)!!
        assertEquals(10, eleventh.thumbnailIndex)
        assertEquals(1, eleventh.row)
        assertEquals(0, eleventh.column)
        assertEquals(0, eleventh.left)
        assertEquals(180, eleventh.top)
    }

    @Test
    fun `a full sheet rolls over to the next tile index`() {
        // 100 thumbnails per sheet: index 99 (990s) is the last of sheet 0, bottom-right.
        val last = TrickplayMath.tileAt(band, 990_000)!!
        assertEquals(99, last.thumbnailIndex)
        assertEquals(0, last.tileIndex)
        assertEquals(9, last.row)
        assertEquals(9, last.column)
        assertEquals(9 * 320, last.left)
        assertEquals(9 * 180, last.top)

        // index 100 (1000s) opens sheet 1 back at the top-left.
        val rolled = TrickplayMath.tileAt(band, 1_000_000)!!
        assertEquals(100, rolled.thumbnailIndex)
        assertEquals(1, rolled.tileIndex)
        assertEquals(0, rolled.row)
        assertEquals(0, rolled.column)
    }

    @Test
    fun `a position between thumbnails floors onto the one before it`() {
        val tile = TrickplayMath.tileAt(band, 34_999)!!
        assertEquals(3, tile.thumbnailIndex)
    }

    @Test
    fun `a position past the last thumbnail clamps to it rather than blanking`() {
        val beyond = TrickplayMath.tileAt(band, 10_000_000)!!
        assertEquals(413, beyond.thumbnailIndex)
        assertEquals(4, beyond.tileIndex)
        assertEquals(1, beyond.row)
        assertEquals(3, beyond.column)
    }

    @Test
    fun `a negative position is treated as the start`() {
        assertEquals(0, TrickplayMath.tileAt(band, -5_000)!!.thumbnailIndex)
    }

    @Test
    fun `an unusable manifest yields no tile`() {
        assertNull(TrickplayMath.tileAt(band.copy(interval = 0), 5_000))
        assertNull(TrickplayMath.tileAt(band.copy(tileWidth = 0), 5_000))
        assertNull(TrickplayMath.tileAt(band.copy(tileHeight = 0), 5_000))
        assertNull(TrickplayMath.tileAt(band.copy(thumbnailCount = 0), 5_000))
        assertNull(TrickplayMath.tileAt(band.copy(width = 0), 5_000))
    }

    // --- band selection -----------------------------------------------------------------------

    private val bands = mapOf(
        "160" to band.copy(width = 160, height = 90),
        "320" to band,
        "640" to band.copy(width = 640, height = 360),
    )

    @Test
    fun `selectBand takes the widest band that still fits the preview`() {
        assertEquals(320, TrickplayMath.selectBand(bands, targetWidth = 320)!!.width)
        assertEquals(320, TrickplayMath.selectBand(bands, targetWidth = 400)!!.width)
        assertEquals(640, TrickplayMath.selectBand(bands, targetWidth = 1024)!!.width)
    }

    @Test
    fun `selectBand falls back to the narrowest band when every band overshoots`() {
        assertEquals(160, TrickplayMath.selectBand(bands, targetWidth = 100)!!.width)
    }

    @Test
    fun `selectBand ignores empty bands and empty manifests`() {
        assertNull(TrickplayMath.selectBand(emptyMap(), targetWidth = 320))
        val onlyEmpty = mapOf("320" to band.copy(thumbnailCount = 0))
        assertNull(TrickplayMath.selectBand(onlyEmpty, targetWidth = 320))
    }

    // --- URLs ---------------------------------------------------------------------------------

    @Test
    fun `tile url is the documented path with api_key and mediaSourceId`() {
        assertEquals(
            "http://myserver/Videos/ep-1/Trickplay/320/4.jpg?api_key=tok123&mediaSourceId=media-1",
            TrickplayUrl.tile(
                serverUrl = "http://myserver",
                itemId = "ep-1",
                thumbnailWidth = 320,
                tileIndex = 4,
                apiKey = "tok123",
                mediaSourceId = "media-1",
            ),
        )
    }

    @Test
    fun `tile url drops absent parameters and a trailing server slash`() {
        assertEquals(
            "http://myserver/Videos/ep-1/Trickplay/320/0.jpg",
            TrickplayUrl.tile("http://myserver/", "ep-1", 320, 0, apiKey = null, mediaSourceId = null),
        )
        assertEquals(
            "http://myserver/Videos/ep-1/Trickplay/320/0.jpg?api_key=tok123",
            TrickplayUrl.tile("http://myserver", "ep-1", 320, 0, apiKey = "tok123", mediaSourceId = ""),
        )
    }

    @Test
    fun `a source pairs the sheet url with the sub-rect for a position`() {
        val source = TrickplaySource(
            info = band,
            serverUrl = "http://myserver",
            itemId = "ep-1",
            apiKey = "tok123",
            mediaSourceId = "media-1",
        )

        val (url, tile) = source.tileAt(1_000_000)!!

        // The {width} path segment is the width of a single thumbnail, not of the composed sheet.
        assertEquals("http://myserver/Videos/ep-1/Trickplay/320/1.jpg?api_key=tok123&mediaSourceId=media-1", url)
        assertEquals(100, tile.thumbnailIndex)
        assertEquals(0, tile.left)
        assertEquals(0, tile.top)
    }
}
