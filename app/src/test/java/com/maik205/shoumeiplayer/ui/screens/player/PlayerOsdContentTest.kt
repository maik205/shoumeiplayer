package com.maik205.shoumeiplayer.ui.screens.player

import com.maik205.shoumeiplayer.player.PlaybackSpeed
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.TrackType
import com.maik205.shoumeiplayer.player.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure content helpers behind the OSD v3 surfaces (docs/osd-v3.md §2 chip labels, §3 seek
 * lane arithmetic, §4 identity block, §5 panels and Up Next countdown).
 *
 * Everything tested here is a plain function on [PlayerUiState] or a primitive, deliberately: what a
 * chip *says* and what a panel *lists* are the parts of the OSD that can be wrong without looking
 * wrong, so they are the parts worth pinning down without a device.
 */
class PlayerOsdContentTest {

    private fun episode(
        title: String = "Immolation",
        seriesName: String? = "Rubicon",
        season: Int? = 3,
        number: Int? = 6,
        speed: Float = PlaybackSpeed.Normal,
    ) = PlayerUiState(
        title = title,
        isEpisode = true,
        seriesName = seriesName,
        seasonNumber = season,
        episodeNumber = number,
        speed = speed,
    )

    private fun movie(
        title: String = "Solaris",
        year: Int? = 1972,
        quality: VideoQuality = VideoQuality.AUTO,
        speed: Float = PlaybackSpeed.Normal,
    ) = PlayerUiState(title = title, isEpisode = false, year = year, quality = quality, speed = speed)

    // --- §4 identity block ----------------------------------------------------------------------

    @Test
    fun `identityTitle prefers the series name for an episode`() {
        // The logo an episode shows is the *series* logo, so the text that stands in for it has to
        // be the series name, not the episode's.
        assertEquals("Rubicon", identityTitle(episode()))
        assertEquals("Immolation", identityTitle(episode(seriesName = null)))
        assertEquals("Immolation", identityTitle(episode(seriesName = "   ")))
        assertEquals("Solaris", identityTitle(movie()))
    }

    @Test
    fun `episodeCode is the mono season-episode half, and only for episodes`() {
        assertEquals("S3 · E6", episodeCode(episode()))
        assertEquals("E6", episodeCode(episode(season = null)))
        assertNull(episodeCode(episode(season = null, number = null)))
        assertNull(episodeCode(movie()))
    }

    @Test
    fun `identityMetaLine is the episode name, or the movie's year and quality`() {
        assertEquals("Immolation", identityMetaLine(episode()))
        assertEquals("1972", identityMetaLine(movie()))
        assertEquals("1972 · 1080p", identityMetaLine(movie(quality = VideoQuality.FHD)))
        // Auto is not a resolution the app knows, so it is never printed as one.
        assertEquals("1972", identityMetaLine(movie(quality = VideoQuality.AUTO)))
        assertEquals("1080p", identityMetaLine(movie(year = null, quality = VideoQuality.FHD)))
        assertEquals("", identityMetaLine(movie(year = null)))
    }

    @Test
    fun `identityMetaLine appends a non-normal speed on the tab column, never a second dot`() {
        val line = identityMetaLine(movie(quality = VideoQuality.FHD, speed = 1.5f))
        assertEquals("1972 · 1080p    1.5×", line)
        // §3.2 — a metadata line carries at most one middle dot.
        assertEquals(1, line.count { it == '·' })
        assertEquals("Immolation    2×", identityMetaLine(episode(speed = 2f)))
        // At 1× the badge is absent: it would otherwise be on every title in the library.
        assertEquals("Immolation", identityMetaLine(episode(speed = 1f)))
    }

    @Test
    fun `speedBadge is silent at normal rate`() {
        assertNull(speedBadge(1.0f))
        assertNull(speedBadge(1.004f))
        assertEquals("0.5×", speedBadge(0.5f))
        assertEquals("1.25×", speedBadge(1.25f))
        assertEquals("2×", speedBadge(2f))
    }

    // --- §2 chip labels -------------------------------------------------------------------------

    @Test
    fun `chipLabel joins a control and its value on the one permitted dot`() {
        assertEquals("Subtitles · English", chipLabel("Subtitles", "English"))
        assertEquals("Quality · Auto", chipLabel("Quality", VideoQuality.AUTO.label))
        // A control with nothing to report is just its own name, never a dangling separator.
        assertEquals("Audio", chipLabel("Audio", null))
        assertEquals("Audio", chipLabel("Audio", "  "))
    }

    // --- §3 seek lane ---------------------------------------------------------------------------

    @Test
    fun `fractionOf clamps and treats an unknown duration as zero`() {
        assertEquals(0.5f, fractionOf(50, 100))
        assertEquals(0f, fractionOf(-10, 100))
        assertEquals(1f, fractionOf(500, 100))
        assertEquals(0f, fractionOf(50, 0))
        assertEquals(0f, fractionOf(50, -1))
    }

    @Test
    fun `formatSeekDelta is signed, mono and never uses a long dash`() {
        assertEquals("-00:30", formatSeekDelta(-30_000))
        assertEquals("+01:05", formatSeekDelta(65_000))
        assertEquals("+00:00", formatSeekDelta(0))
        assertFalse(formatSeekDelta(-30_000).contains('—'))
        assertFalse(formatSeekDelta(-30_000).contains('–'))
    }

    // --- §5 panels ------------------------------------------------------------------------------

    @Test
    fun `the speed panel lists the ladder and marks the running rung`() {
        val items = panelItems(PlayerPanel.Speed, movie(speed = 1.5f))
        assertEquals(listOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "2×"), items.map { it.label })
        assertEquals(listOf("1.5×"), items.filter { it.selected }.map { it.label })
        // Keys are stable and unique, or LazyColumn would recycle the wrong row.
        assertEquals(items.size, items.map { it.key }.toSet().size)
    }

    @Test
    fun `the speed panel snaps an off-ladder engine rate to its nearest rung`() {
        // mpv can report a rate the app never set; the panel still has to light exactly one row.
        val items = panelItems(PlayerPanel.Speed, movie(speed = 1.49f))
        assertEquals(listOf("1.5×"), items.filter { it.selected }.map { it.label })
    }

    @Test
    fun `the quality panel is the ladder, Auto first, with the cap spelled out`() {
        val items = panelItems(PlayerPanel.Quality, movie(quality = VideoQuality.HD))
        assertEquals(listOf("Auto", "4K", "1080p", "720p", "480p"), items.map { it.label })
        // Auto carries no cap of its own, so it has nothing to say in the detail column.
        assertNull(items.first().detail)
        assertEquals("20 Mbps", items[2].detail)
        assertEquals(listOf("720p"), items.filter { it.selected }.map { it.label })
    }

    @Test
    fun `the subtitle panel always offers an Off row`() {
        val state = PlayerUiState(
            subtitleTracks = listOf(
                PlayerTrack(id = 2, type = TrackType.SUBTITLE, label = "English", selected = true),
            ),
        )
        val items = panelItems(PlayerPanel.Subtitles, state)
        assertEquals(listOf("English", OffLabel), items.map { it.label })
        assertEquals(listOf("English"), items.filter { it.selected }.map { it.label })
    }

    @Test
    fun `the subtitle panel selects Off when nothing is on`() {
        val state = PlayerUiState(
            subtitleTracks = listOf(
                PlayerTrack(id = 2, type = TrackType.SUBTITLE, label = "English", selected = false),
            ),
        )
        val items = panelItems(PlayerPanel.Subtitles, state)
        assertEquals(listOf(OffLabel), items.filter { it.selected }.map { it.label })
    }

    @Test
    fun `a mid-playback stream swap keeps the OSD mounted and pulses the hairline instead`() {
        // §5 — a quality rung or an episode change swaps the stream *in place*. PlayerEngine.load
        // raises PlayerState.Loading on every load, so if the screen's bare-frame branch read the
        // engine state, the whole overlay — chips, shelf, panel, and every PlayerOsdFocus target —
        // would leave the tree mid-swap and drop focus while the reducer still believed it was on
        // layer 1. Only the initial resolve may take the bare frame.
        val swapping = PlayerUiState(title = "Immolation", loading = false, state = PlayerState.Loading, swapping = true)
        assertFalse(showsBareFrame(swapping))
        assertTrue(bufferingActive(swapping))

        val reopening = PlayerUiState(title = "Immolation", loading = false, state = PlayerState.Loading)
        assertFalse(showsBareFrame(reopening))
        assertTrue(bufferingActive(reopening))

        // The initial resolve, before there is anything to draw an OSD about, still gets the frame.
        assertTrue(showsBareFrame(PlayerUiState(loading = true)))

        // And a settled, playing stream pulses nothing.
        val playing = PlayerUiState(title = "Immolation", loading = false, state = PlayerState.Playing)
        assertFalse(showsBareFrame(playing))
        assertFalse(bufferingActive(playing))
    }

    @Test
    fun `subtitleTracksWithOff never adds a second Off row`() {
        val existing = listOf(
            PlayerTrack(id = -1, type = TrackType.SUBTITLE, label = "None", selected = true),
            PlayerTrack(id = 2, type = TrackType.SUBTITLE, label = "English"),
        )
        assertEquals(existing, subtitleTracksWithOff(existing, OffLabel))
    }

    @Test
    fun `panel keys round-trip back to the thing they select`() {
        // The panel hands back an OsdPanelItem, not a track, so the key has to be able to find its
        // way home again — this is the invariant applyPanelSelection leans on.
        val state = PlayerUiState(
            audioTracks = listOf(
                PlayerTrack(id = 1, type = TrackType.AUDIO, label = "English 5.1", selected = true),
                PlayerTrack(id = 2, type = TrackType.AUDIO, label = "Commentary"),
            ),
        )
        panelItems(PlayerPanel.Audio, state).forEach { item ->
            assertTrue(state.audioTracks.any { it.panelKey == item.key })
        }
        PlaybackSpeed.Steps.forEach { step ->
            assertEquals(step, PlaybackSpeed.Steps.first { speedKey(it) == speedKey(step) })
        }
        VideoQuality.Ladder.forEach { rung ->
            assertEquals(rung, VideoQuality.entries.first { it.name == rung.name })
        }
    }

    @Test
    fun `an empty panel lists nothing`() {
        assertTrue(panelItems(null, movie()).isEmpty())
    }

    // --- §5 Up Next -----------------------------------------------------------------------------

    @Test
    fun `countdownSeconds rounds up so the card never shows zero while the episode runs`() {
        assertEquals(30, countdownSeconds(30_000))
        assertEquals(2, countdownSeconds(1_200))
        assertEquals(1, countdownSeconds(1))
        assertEquals(0, countdownSeconds(0))
        assertEquals(0, countdownSeconds(-5_000))
    }
}
