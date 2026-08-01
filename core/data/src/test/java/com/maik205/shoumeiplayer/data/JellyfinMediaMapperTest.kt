package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.UserItemDataDto
import com.maik205.shoumeiplayer.domain.result.mapping.toMediaItem
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinMediaMapperTest {
    private val images = ImageUrlBuilder { "http://server" }

    @Test
    fun `maps Jellyfin transport data into a presentation neutral media item`() {
        val item = BaseItemDto(
            id = "movie-1",
            name = "Example",
            type = "Movie",
            overview = "Overview",
            productionYear = 2026,
            runTimeTicks = 5_400_000_000L,
            imageTags = mapOf("Primary" to "primary-tag"),
            backdropImageTags = listOf("backdrop-tag"),
            genres = listOf("Drama"),
            userData = UserItemDataDto(
                playedPercentage = 42.0,
                playbackPositionTicks = 1_000L,
                isFavorite = true,
            ),
        )

        val mapped = item.toMediaItem(images)

        assertEquals("movie-1", mapped.id)
        assertEquals("Example", mapped.title)
        assertEquals(ArtworkShape.Poster, mapped.shape)
        assertEquals("2026", mapped.subtitle)
        assertEquals(listOf("2026", "9m", "Drama"), mapped.metadata)
        assertEquals(0.42f, mapped.progress)
        assertTrue(mapped.favorite)
        assertEquals(
            "http://server/Items/movie-1/Images/Primary?maxWidth=480&quality=90&tag=primary-tag",
            mapped.imageUrl,
        )
    }

    @Test
    fun `maps episode identity and artwork shape without exposing its DTO`() {
        val item = BaseItemDto(
            id = "episode-1",
            name = "Pilot",
            type = "Episode",
            seriesName = "Series",
            parentIndexNumber = 1,
            indexNumber = 2,
        )

        val mapped = item.toMediaItem(images)

        assertEquals(ArtworkShape.Landscape, mapped.shape)
        assertEquals("Series · S1 E2", mapped.subtitle)
        assertEquals(1, mapped.seasonNumber)
        assertEquals(2, mapped.episodeNumber)
    }
}
