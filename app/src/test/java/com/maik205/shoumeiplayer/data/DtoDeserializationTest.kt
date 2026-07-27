package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.api.dto.AuthenticationResult
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.PlaybackInfoResponse
import com.maik205.shoumeiplayer.data.api.dto.PublicSystemInfo
import com.maik205.shoumeiplayer.data.api.dto.QueryResult
import com.maik205.shoumeiplayer.data.api.dto.blurHash
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the Json config installed on [com.maik205.shoumeiplayer.data.api.JellyfinClient]. */
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

class DtoDeserializationTest {

    @Test
    fun `PublicSystemInfo deserializes from PascalCase fixture`() {
        val info = json.decodeFromString<PublicSystemInfo>(FakeJellyfin.fixture("system_info_public.json"))
        assertEquals("Test Server", info.serverName)
        assertEquals("10.9.0", info.version)
        assertEquals("Jellyfin Server", info.productName)
        assertEquals(true, info.startupWizardCompleted)
    }

    @Test
    fun `AuthenticationResult deserializes nested user and token from PascalCase fixture`() {
        val result = json.decodeFromString<AuthenticationResult>(FakeJellyfin.fixture("auth_result.json"))
        assertEquals("test-access-token", result.accessToken)
        assertEquals("server-1", result.serverId)
        assertEquals("user-1", result.user?.id)
        assertEquals("testuser", result.user?.name)
    }

    @Test
    fun `QueryResult of BaseItemDto deserializes items list and paging fields`() {
        val result = json.decodeFromString<QueryResult<BaseItemDto>>(FakeJellyfin.fixture("items_query.json"))
        assertEquals(1, result.totalRecordCount)
        assertEquals(0, result.startIndex)
        assertEquals(1, result.items.size)

        val item = result.items.first()
        assertEquals("item-1", item.id)
        assertEquals("Test Movie", item.name)
        assertEquals("Movie", item.type)
        assertEquals(2020, item.productionYear)
        assertEquals(listOf("Action"), item.genres)
        assertEquals("tag1", item.imageTags["Primary"])
        assertEquals(false, item.userData?.played)
    }

    @Test
    fun `PlaybackInfoResponse deserializes media sources and streams`() {
        val result = json.decodeFromString<PlaybackInfoResponse>(FakeJellyfin.fixture("playback_info.json"))
        assertEquals("play-session-1", result.playSessionId)
        assertEquals(1, result.mediaSources.size)

        val source = result.mediaSources.first()
        assertEquals("media-1", source.id)
        assertTrue(source.supportsDirectPlay)
        assertEquals(3, source.mediaStreams.size)
        assertEquals(1, source.defaultAudioStreamIndex)

        val audio = source.mediaStreams.first { it.type == "Audio" }
        assertEquals("English (AAC 5.1)", audio.displayTitle)
        assertEquals("eng", audio.language)
    }

    @Test
    fun `detail fixture deserializes the asset tag and parent fallback fields`() {
        val item = detailItem()

        assertEquals("ep-1", item.id)
        assertEquals("The Winds of Winter", item.originalTitle)
        assertEquals(listOf("Winter is here."), item.taglines)
        assertEquals(88f, item.criticRating!!, 0.001f)
        assertEquals("Ended", item.status)
        assertEquals("2019-05-19T00:00:00.0000000Z", item.endDate)
        assertEquals(1.7777777777777777, item.primaryImageAspectRatio!!, 1e-9)
        assertEquals(listOf("HBO"), item.studios.map { it.name })
        assertEquals("studio-1", item.studios.first().id)

        assertEquals("series-1", item.parentBackdropItemId)
        assertEquals(listOf("parent-backdrop-tag", "parent-backdrop-tag-2"), item.parentBackdropImageTags)
        assertEquals("series-1", item.parentLogoItemId)
        assertEquals("parent-logo-tag", item.parentLogoImageTag)
        assertEquals("season-6", item.parentPrimaryImageItemId)
        assertEquals("parent-primary-tag", item.parentPrimaryImageTag)
        assertEquals("series-thumb-tag", item.seriesThumbImageTag)
        assertEquals(listOf("screenshot-tag"), item.screenshotImageTags)
    }

    @Test
    fun `detail fixture deserializes people with role and portrait tag`() {
        val people = detailItem().people

        assertEquals(2, people.size)
        val tyrion = people.first()
        assertEquals("person-1", tyrion.id)
        assertEquals("Peter Dinklage", tyrion.name)
        assertEquals("Tyrion Lannister", tyrion.role)
        assertEquals("Actor", tyrion.type)
        assertEquals("person-1-tag", tyrion.primaryImageTag)
        assertEquals("LEHV6nWB2yk8pyo0adR*.7kCMdnj", tyrion.imageBlurHashes.blurHash("Primary", "person-1-tag"))

        // A crew entry with no portrait must default cleanly rather than throw.
        val director = people[1]
        assertEquals("Director", director.type)
        assertNull(director.role)
        assertNull(director.primaryImageTag)
        assertTrue(director.imageBlurHashes.isEmpty())
    }

    @Test
    fun `detail fixture deserializes chapters with ticks and image tags`() {
        val chapters = detailItem().chapters

        assertEquals(2, chapters.size)
        assertEquals(0L, chapters[0].startPositionTicks)
        assertEquals("Cold Open", chapters[0].name)
        assertEquals("chapter-0-tag", chapters[0].imageTag)
        assertEquals(6_000_000_000L, chapters[1].startPositionTicks)
        assertEquals("The Trial", chapters[1].name)
        assertEquals("chapter-1-tag", chapters[1].imageTag)
    }

    @Test
    fun `detail fixture deserializes media streams and the trickplay manifest`() {
        val item = detailItem()

        assertEquals(2, item.mediaStreams.size)
        assertEquals("h264", item.mediaStreams.first { it.type == "Video" }.codec)
        assertEquals(6, item.mediaStreams.first { it.type == "Audio" }.channels)

        val band = item.trickplay["media-1"]?.get("320")
        assertEquals(320, band?.width)
        assertEquals(180, band?.height)
        assertEquals(10, band?.tileWidth)
        assertEquals(10, band?.tileHeight)
        assertEquals(414, band?.thumbnailCount)
        assertEquals(10_000, band?.interval)
        assertEquals(12_345, band?.bandwidth)
    }

    @Test
    fun `blurHash resolves by tag and falls back to the first hash of a type`() {
        val hashes = detailItem().imageBlurHashes

        assertEquals("LEHV6nWB2yk8pyo0adR*.7kCMdnj", hashes.blurHash("Primary", "primary-tag"))
        // Null tag: take whatever hash the type has.
        assertEquals("L6Pj0^jE.AyE_3t7t7R**0o#DgR4", hashes.blurHash("Backdrop", null))
        // Wrong tag for a known type, and an unknown type, both resolve to null rather than a wrong hash.
        assertNull(hashes.blurHash("Primary", "not-a-tag"))
        assertNull(hashes.blurHash("Logo", "logo-tag"))
    }

    @Test
    fun `items fixture leaves every new asset field at its default`() {
        val item = json.decodeFromString<QueryResult<BaseItemDto>>(FakeJellyfin.fixture("items_query.json"))
            .items.first()

        assertTrue(item.people.isEmpty())
        assertTrue(item.chapters.isEmpty())
        assertTrue(item.mediaStreams.isEmpty())
        assertTrue(item.trickplay.isEmpty())
        assertTrue(item.taglines.isEmpty())
        assertTrue(item.studios.isEmpty())
        assertTrue(item.imageBlurHashes.isEmpty())
        assertTrue(item.parentBackdropImageTags.isEmpty())
        assertNull(item.primaryImageAspectRatio)
        assertNull(item.criticRating)
    }

    private fun detailItem(): BaseItemDto =
        json.decodeFromString<QueryResult<BaseItemDto>>(FakeJellyfin.fixture("item_detail.json")).items.first()
}
