package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlBuilderTest {

    private val builder = ImageUrlBuilder { "http://server" }

    @Test
    fun `null server url returns null`() {
        val offline = ImageUrlBuilder { null }
        assertNull(offline.primary("item1", "tag1"))
        assertNull(offline.backdrop("item1", "tag1"))
        assertNull(offline.thumb("item1", "tag1"))
        assertNull(offline.logo("item1", "tag1"))
        assertNull(offline.personPrimary("person1", "tag1"))
        assertNull(offline.userPrimary("user1", "tag1"))
        assertNull(offline.image("item1", "Primary", "tag1", 400))
        assertNull(offline.imageAtIndex("item1", "Backdrop", 2, "tag1", 1280))
        assertNull(offline.backdropAtIndex("item1", 2, "tag1"))
        assertNull(offline.chapterImage("item1", 3, "tag1"))
        // Fully-tagged item: the chains would resolve a link, but there is no server to build on.
        val tagged = episode.copy(
            imageTags = mapOf("Primary" to "p", "Thumb" to "t", "Logo" to "l"),
            backdropImageTags = listOf("b"),
        )
        assertNull(offline.thumbWithSeriesFallback(tagged))
        assertNull(offline.backdropWithParentFallback(tagged))
        assertNull(offline.primaryWithParentFallback(tagged))
        assertNull(offline.logoWithParentFallback(tagged))
    }

    @Test
    fun `tag present is appended`() {
        assertEquals(
            "http://server/Items/item1/Images/Primary?maxWidth=400&quality=90&tag=tag1",
            builder.primary("item1", "tag1"),
        )
    }

    @Test
    fun `tag absent is omitted`() {
        assertEquals(
            "http://server/Items/item1/Images/Primary?maxWidth=400&quality=90",
            builder.primary("item1", null),
        )
    }

    @Test
    fun `backdrop and thumb use expected defaults and type`() {
        assertEquals(
            "http://server/Items/item1/Images/Backdrop?maxWidth=1280&quality=90&tag=t",
            builder.backdrop("item1", "t"),
        )
        assertEquals(
            "http://server/Items/item1/Images/Thumb?maxWidth=640&quality=90&tag=t",
            builder.thumb("item1", "t"),
        )
    }

    @Test
    fun `logo and personPrimary use expected defaults and type`() {
        assertEquals(
            "http://server/Items/item1/Images/Logo?maxWidth=480&quality=90&tag=t",
            builder.logo("item1", "t"),
        )
        assertEquals(
            "http://server/Items/person1/Images/Primary?maxWidth=240&quality=90&tag=t",
            builder.personPrimary("person1", "t"),
        )
    }

    @Test
    fun `public profile portraits use the Jellyfin user image endpoint`() {
        assertEquals(
            "http://server/UserImage?userId=user1&tag=portrait",
            builder.userPrimary("user1", "portrait"),
        )
    }

    @Test
    fun `indexed images put the index in the path segment`() {
        assertEquals(
            "http://server/Items/item1/Images/Backdrop/2?maxWidth=1280&quality=90&tag=t",
            builder.backdropAtIndex("item1", 2, "t"),
        )
        assertEquals(
            "http://server/Items/item1/Images/Chapter/3?maxWidth=320&quality=90&tag=t",
            builder.chapterImage("item1", 3, "t"),
        )
        assertEquals(
            "http://server/Items/item1/Images/Backdrop/0?maxWidth=1280&quality=90",
            builder.backdropAtIndex("item1", 0, null),
        )
    }

    // --- fallback chains -------------------------------------------------------------------

    @Test
    fun `episode preview prefers the episode primary still over inherited artwork`() {
        val item = episode.copy(
            imageTags = mapOf("Primary" to "episode-still"),
            parentThumbItemId = "season-1",
            parentThumbImageTag = "season-thumb",
            seriesId = "series-1",
            seriesThumbImageTag = "series-thumb",
        )
        assertEquals(
            "http://server/Items/ep-1/Images/Primary?maxWidth=720&quality=90&tag=episode-still",
            builder.episodePreview(item),
        )
    }

    @Test
    fun `episode preview falls back to a paired parent thumb when no episode still exists`() {
        val item = episode.copy(
            parentThumbItemId = "season-1",
            parentThumbImageTag = "season-thumb",
        )
        assertEquals(
            "http://server/Items/season-1/Images/Thumb?maxWidth=720&quality=90&tag=season-thumb",
            builder.episodePreview(item),
        )
    }

    @Test
    fun `thumb chain prefers the items own thumb`() {
        val item = episode.copy(
            imageTags = mapOf("Thumb" to "own"),
            parentThumbItemId = "season-1",
            parentThumbImageTag = "parent",
            seriesThumbImageTag = "series",
        )
        assertEquals(
            "http://server/Items/ep-1/Images/Thumb?maxWidth=640&quality=90&tag=own",
            builder.thumbWithSeriesFallback(item),
        )
    }

    @Test
    fun `thumb chain falls back to the parent thumb paired with the parent id`() {
        val item = episode.copy(
            parentThumbItemId = "season-1",
            parentThumbImageTag = "parent",
            seriesThumbImageTag = "series",
        )
        assertEquals(
            "http://server/Items/season-1/Images/Thumb?maxWidth=640&quality=90&tag=parent",
            builder.thumbWithSeriesFallback(item),
        )
    }

    @Test
    fun `thumb chain falls back to the series thumb paired with the series id`() {
        val item = episode.copy(seriesId = "series-1", seriesThumbImageTag = "series")
        assertEquals(
            "http://server/Items/series-1/Images/Thumb?maxWidth=640&quality=90&tag=series",
            builder.thumbWithSeriesFallback(item),
        )
    }

    @Test
    fun `thumb chain never pairs a parent tag with the child id when the parent id is missing`() {
        val item = episode.copy(parentThumbItemId = null, parentThumbImageTag = "parent")
        assertNull(builder.thumbWithSeriesFallback(item))

        // …and with the parent id absent it skips straight to the series link, not the child.
        val withSeries = item.copy(seriesId = "series-1", seriesThumbImageTag = "series")
        assertEquals(
            "http://server/Items/series-1/Images/Thumb?maxWidth=640&quality=90&tag=series",
            builder.thumbWithSeriesFallback(withSeries),
        )

        // A series tag with no series id must not fall back to the episode id either.
        assertNull(builder.thumbWithSeriesFallback(episode.copy(seriesId = null, seriesThumbImageTag = "series")))
    }

    @Test
    fun `backdrop chain prefers the items own first backdrop tag`() {
        val item = episode.copy(
            backdropImageTags = listOf("own-0", "own-1"),
            parentBackdropItemId = "series-1",
            parentBackdropImageTags = listOf("parent-0"),
        )
        assertEquals(
            "http://server/Items/ep-1/Images/Backdrop?maxWidth=1280&quality=90&tag=own-0",
            builder.backdropWithParentFallback(item),
        )
    }

    @Test
    fun `backdrop chain falls back to the parent backdrop paired with the parent id`() {
        val item = episode.copy(
            parentBackdropItemId = "series-1",
            parentBackdropImageTags = listOf("parent-0", "parent-1"),
        )
        assertEquals(
            "http://server/Items/series-1/Images/Backdrop?maxWidth=1280&quality=90&tag=parent-0",
            builder.backdropWithParentFallback(item),
        )
    }

    @Test
    fun `backdrop chain yields null rather than an untagged guess when the parent id is missing`() {
        val item = episode.copy(parentBackdropItemId = null, parentBackdropImageTags = listOf("parent-0"))
        assertNull(builder.backdropWithParentFallback(item))
        assertNull(builder.backdropWithParentFallback(episode))
    }

    @Test
    fun `primary chain walks own then series then parent`() {
        val full = episode.copy(
            imageTags = mapOf("Primary" to "own"),
            seriesId = "series-1",
            seriesPrimaryImageTag = "series",
            parentPrimaryImageItemId = "season-1",
            parentPrimaryImageTag = "parent",
        )
        assertEquals(
            "http://server/Items/ep-1/Images/Primary?maxWidth=320&quality=90&tag=own",
            builder.primaryWithParentFallback(full),
        )
        assertEquals(
            "http://server/Items/series-1/Images/Primary?maxWidth=320&quality=90&tag=series",
            builder.primaryWithParentFallback(full.copy(imageTags = emptyMap())),
        )
        assertEquals(
            "http://server/Items/season-1/Images/Primary?maxWidth=320&quality=90&tag=parent",
            builder.primaryWithParentFallback(
                full.copy(imageTags = emptyMap(), seriesPrimaryImageTag = null),
            ),
        )
    }

    @Test
    fun `primary chain skips links whose id is missing and never reuses the child id`() {
        val noSeriesId = episode.copy(
            seriesId = null,
            seriesPrimaryImageTag = "series",
            parentPrimaryImageItemId = "season-1",
            parentPrimaryImageTag = "parent",
        )
        assertEquals(
            "http://server/Items/season-1/Images/Primary?maxWidth=320&quality=90&tag=parent",
            builder.primaryWithParentFallback(noSeriesId),
        )
        assertNull(
            builder.primaryWithParentFallback(
                episode.copy(seriesId = null, seriesPrimaryImageTag = "series", parentPrimaryImageItemId = null, parentPrimaryImageTag = "parent"),
            ),
        )
    }

    @Test
    fun `primary chain uses an audio tracks album cover`() {
        val track = BaseItemDto(
            id = "song-1",
            type = "Audio",
            albumId = "album-1",
            albumPrimaryImageTag = "cover",
        )
        assertEquals(
            "http://server/Items/album-1/Images/Primary?maxWidth=320&quality=90&tag=cover",
            builder.primaryWithParentFallback(track),
        )
    }

    @Test
    fun `episode logo uses its series owner for a stable shared cache key`() {
        val item = episode.copy(
            imageTags = mapOf("Logo" to "own"),
            parentLogoItemId = "series-1",
            parentLogoImageTag = "parent",
        )
        assertEquals(
            "http://server/Items/series-1/Images/Logo?maxWidth=480&quality=90&tag=parent",
            builder.logoWithParentFallback(item),
        )
        assertEquals(
            builder.logoWithParentFallback(item),
            builder.logoWithParentFallback(item.copy(id = "ep-2")),
        )
        assertEquals(
            "http://server/Items/ep-1/Images/Logo?maxWidth=480&quality=90&tag=own",
            builder.logoWithParentFallback(
                item.copy(parentLogoItemId = null, parentLogoImageTag = null),
            ),
        )
    }

    @Test
    fun `logo chain keeps own logo priority for non episode items`() {
        val item = episode.copy(
            type = "Movie",
            imageTags = mapOf("Logo" to "own"),
            parentLogoItemId = "collection-1",
            parentLogoImageTag = "parent",
        )
        assertEquals(
            "http://server/Items/ep-1/Images/Logo?maxWidth=480&quality=90&tag=own",
            builder.logoWithParentFallback(item),
        )
        assertEquals(
            "http://server/Items/collection-1/Images/Logo?maxWidth=480&quality=90&tag=parent",
            builder.logoWithParentFallback(item.copy(imageTags = emptyMap())),
        )
        assertNull(
            builder.logoWithParentFallback(item.copy(imageTags = emptyMap(), parentLogoItemId = null)),
        )
        assertNull(builder.logoWithParentFallback(episode))
    }

    @Test
    fun `fallback chains honour an explicit maxWidth`() {
        val item = episode.copy(imageTags = mapOf("Primary" to "own", "Thumb" to "t", "Logo" to "l"))
        assertEquals(
            "http://server/Items/ep-1/Images/Primary?maxWidth=1000&quality=90&tag=own",
            builder.primaryWithParentFallback(item, 1000),
        )
        assertEquals(
            "http://server/Items/ep-1/Images/Thumb?maxWidth=1000&quality=90&tag=t",
            builder.thumbWithSeriesFallback(item, 1000),
        )
        assertEquals(
            "http://server/Items/ep-1/Images/Logo?maxWidth=1000&quality=90&tag=l",
            builder.logoWithParentFallback(item, 1000),
        )
    }

    private val episode get() = BaseItemDto(id = "ep-1", name = "Episode", type = "Episode")
}
