package com.maik205.shoumeiplayer.ui.screens.detail

import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.BaseItemPersonDto
import com.maik205.shoumeiplayer.data.api.dto.MediaStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §5.2 — the Detail copy column, spec rail and cast row, exercised without Compose. */
class DetailMetadataTest {

    private val images = ImageUrlBuilder { "https://jf.example" }

    // --- metadata line (§3.2: columns, never a dot chain) --------------------------------------

    @Test
    fun `metadata line carries year and rating as separate columns`() {
        assertEquals(
            listOf("2014", "PG-13"),
            metadataFields(years = "2014", rating = "PG-13"),
        )
    }

    @Test
    fun `metadata line drops blank and absent parts`() {
        assertEquals(listOf("2014"), metadataFields(years = "2014", rating = "  "))
    }

    @Test
    fun `metadata line carries a series year range and run status`() {
        val series = BaseItemDto(type = "Series", productionYear = 2016, status = "Ended")
        assertEquals(
            listOf("2016", "Ended", "TV-MA"),
            metadataFields(
                years = yearRange(series),
                status = runStatus(series),
                rating = "TV-MA",
            ),
        )
    }

    @Test
    fun `genres take their own comma line and never nest in another join`() {
        assertEquals(
            "Adventure, Drama, Science Fiction, Thriller",
            genreLine(listOf("Adventure", "Drama", "Science Fiction", "Thriller", "Ignored")),
        )
        assertEquals("", genreLine(emptyList()))
    }

    @Test
    fun `runtime reads as a sentence-case duration`() {
        assertEquals("2h 49m", formatRuntime(169 * 600_000_000L))
        assertEquals("48m", formatRuntime(48 * 600_000_000L))
    }

    // --- year range / status (audit #26) -------------------------------------------------------

    @Test
    fun `year range closes on end date and stays open while continuing`() {
        assertEquals(
            "2016-2022",
            yearRange(BaseItemDto(productionYear = 2016, endDate = "2022-07-01T00:00:00Z")),
        )
        assertEquals(
            "2016-",
            yearRange(BaseItemDto(productionYear = 2016, status = "Continuing")),
        )
        assertEquals("2016", yearRange(BaseItemDto(productionYear = 2016)))
        assertNull(yearRange(BaseItemDto()))
    }

    @Test
    fun `run status only speaks when no closing year did`() {
        assertEquals("Ended", runStatus(BaseItemDto(status = "Ended")))
        assertNull(runStatus(BaseItemDto(status = "Ended", endDate = "2022-07-01T00:00:00Z")))
        assertNull(runStatus(BaseItemDto(status = "Continuing")))
    }

    // --- spec block (E2: MediaStreams are no longer dead code) ----------------------------------

    @Test
    fun `spec block renders audio subtitles and codec from media streams`() {
        val rows = buildSpecRows(
            item = BaseItemDto(
                runTimeTicks = 164 * 600_000_000L,
                officialRating = "TV-MA",
            ),
            streams = listOf(
                MediaStreamDto(type = "Video", codec = "hevc"),
                MediaStreamDto(type = "Audio", codec = "eac3", channels = 6, isDefault = true),
                MediaStreamDto(type = "Subtitle", codec = "subrip"),
                MediaStreamDto(type = "Subtitle", codec = "subrip"),
            ),
        )
        assertEquals(
            listOf(
                SpecRow("Runtime", "2h 44m", mono = true),
                SpecRow("Rating", "TV-MA"),
                SpecRow("Audio", "5.1 EAC3"),
                SpecRow("Subtitles", "2"),
                SpecRow("Codec", "HEVC"),
            ),
            rows,
        )
    }

    @Test
    fun `spec block keeps one rating scale and never exceeds five rows`() {
        val rows = buildSpecRows(
            item = BaseItemDto(
                runTimeTicks = 600_000_000L * 60,
                officialRating = "R",
                communityRating = 8.25f,
                criticRating = 87.6f,
                productionYear = 2014,
            ),
        )
        // The score's decimal separator is locale-owned; only its presence is contractual.
        assertTrue(rows.any { it.label == "Score" })
        // One rating scale only: the critic percentage and the repeated year are dropped.
        assertTrue(rows.none { it.label == "Critic" })
        assertTrue(rows.none { it.label == "Year" })
        assertTrue(rows.size <= 5)
    }

    // --- cast row (E6) ---------------------------------------------------------------------------

    @Test
    fun `cast keeps actors and guest stars with portraits addressed by the person id`() {
        val item = BaseItemDto(
            id = "item-1",
            people = listOf(
                BaseItemPersonDto(
                    id = "p1",
                    name = "Matthew McConaughey",
                    role = "Cooper",
                    type = "Actor",
                    primaryImageTag = "tag1",
                    imageBlurHashes = mapOf("Primary" to mapOf("tag1" to "LEHV6nWB")),
                ),
                BaseItemPersonDto(id = "p2", name = "Some Director", type = "Director"),
                BaseItemPersonDto(id = "p3", name = "A Guest", type = "GuestStar"),
                BaseItemPersonDto(id = "p4", name = "   ", type = "Actor"),
            ),
        )

        val cast = item.toCastUi(images)

        assertEquals(listOf("p1", "p3"), cast.map { it.id })
        assertEquals("Cooper", cast[0].role)
        assertEquals("LEHV6nWB", cast[0].blurHash)
        assertEquals(
            "https://jf.example/Items/p1/Images/Primary?maxWidth=240&quality=90&tag=tag1",
            cast[0].imageUrl,
        )
        // No portrait tag → no URL at all, rather than an untagged 404.
        assertNull(cast[1].imageUrl)
        assertNull(cast[1].role)
    }

    @Test
    fun `cast is capped at twelve tiles`() {
        val item = BaseItemDto(
            people = List(20) { BaseItemPersonDto(id = "p$it", name = "Person $it", type = "Actor") },
        )
        assertEquals(12, item.toCastUi(images).size)
    }
}
