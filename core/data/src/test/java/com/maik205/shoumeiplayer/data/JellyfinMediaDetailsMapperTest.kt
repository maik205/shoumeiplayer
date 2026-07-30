package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.BaseItemPersonDto
import com.maik205.shoumeiplayer.data.api.dto.MediaStreamDto
import com.maik205.shoumeiplayer.data.api.dto.NameGuidPairDto
import com.maik205.shoumeiplayer.data.api.dto.UserItemDataDto
import com.maik205.shoumeiplayer.domain.result.mapping.toDetailItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinMediaDetailsMapperTest {
    private val images = ImageUrlBuilder { "http://server" }

    @Test
    fun `maps detail metadata people streams and user state without leaking transport types`() {
        val mapped = BaseItemDto(
            id = "episode-1",
            name = "Pilot",
            type = "Episode",
            seriesName = "Example Series",
            seasonName = "Season 1",
            parentIndexNumber = 1,
            indexNumber = 2,
            genres = listOf("Drama"),
            studios = listOf(NameGuidPairDto(id = "studio-1", name = "Studio")),
            people = listOf(
                BaseItemPersonDto(
                    id = "person-1",
                    name = "Performer",
                    role = "Lead",
                    type = "Actor",
                    primaryImageTag = "person-tag",
                ),
            ),
            mediaStreams = listOf(
                MediaStreamDto(
                    index = 3,
                    type = "Audio",
                    codec = "aac",
                    language = "eng",
                    channels = 6,
                    isDefault = true,
                ),
            ),
            userData = UserItemDataDto(
                played = true,
                playbackPositionTicks = 42L,
                isFavorite = true,
            ),
        ).toDetailItem(images)

        assertEquals("episode-1", mapped.id)
        assertEquals("Example Series", mapped.seriesName)
        assertEquals(listOf("Studio"), mapped.studios)
        assertEquals("Performer", mapped.people.single().name)
        assertTrue(mapped.people.single().imageUrl.orEmpty().contains("/Items/person-1/Images/Primary"))
        assertEquals(3, mapped.mediaStreams.single().index)
        assertTrue(mapped.mediaStreams.single().isDefault)
        assertTrue(mapped.favorite)
        assertTrue(mapped.played)
        assertEquals(42L, mapped.resumeTicks)
        assertEquals(mapped.id, mapped.media.id)
    }
}
