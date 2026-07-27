package com.maik205.shoumeiplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mpv id ↔ Jellyfin stream index translation. This is the seam where a mistake silently plays
 * the wrong audio track, so it is pinned against real `track-list` shapes.
 *
 * Note the namespaces deliberately disagree in the fixture: mpv calls the only audio track `id: 1`
 * while its container index (`ff-index`) is 2.
 */
class MpvTrackListTest {

    private val trackListJson = """
        [
          {"id":1,"type":"video","src-id":0,"ff-index":0,"codec":"h264","default":true,"selected":true},
          {"id":1,"type":"audio","src-id":1,"ff-index":2,"lang":"jpn","title":"Japanese 5.1",
           "codec":"flac","default":true,"selected":true},
          {"id":2,"type":"audio","src-id":2,"ff-index":3,"lang":"eng","title":"English Dub",
           "codec":"aac","default":false,"selected":false},
          {"id":1,"type":"sub","src-id":3,"ff-index":4,"lang":"eng","title":"Full Subtitles",
           "codec":"ass","default":false,"forced":false,"selected":false},
          {"id":2,"type":"sub","src-id":4,"ff-index":5,"lang":"eng","title":"Signs & Songs",
           "codec":"ass","default":false,"forced":true,"selected":false}
        ]
    """.trimIndent()

    @Test
    fun `parse keeps audio and subtitle tracks and drops video`() {
        val tracks = MpvTrackList.parse(trackListJson)

        // The video track (ff-index 0) is not representable as a PlayerTrack and is skipped.
        assertEquals(4, tracks.size)
        assertTrue(tracks.none { it.ffIndex == 0 })
        val audio = tracks.filter { it.type == TrackType.AUDIO }
        assertEquals(listOf(1, 2), audio.map { it.mpvId })
        assertEquals(listOf(2, 3), audio.map { it.ffIndex })
        assertEquals("Japanese 5.1", audio.first().title)
        assertTrue(audio.first().selected)
        assertTrue(tracks.single { it.type == TrackType.SUBTITLE && it.mpvId == 2 }.isForced)
    }

    @Test
    fun `stream index prefers ff-index over the mpv id`() {
        val tracks = MpvTrackList.parse(trackListJson)
        val secondAudio = tracks.single { it.type == TrackType.AUDIO && it.mpvId == 2 }

        assertEquals(3, MpvTrackList.streamIndexOf(secondAudio, emptyMap()))
    }

    @Test
    fun `player tracks are addressed by Jellyfin stream index and gain an Off entry`() {
        val tracks = MpvTrackList.parse(trackListJson)

        val playerTracks = MpvTrackList.toPlayerTracks(tracks, emptyMap())

        assertEquals(listOf(2, 3), playerTracks.filter { it.type == TrackType.AUDIO }.map { it.id })
        val subs = playerTracks.filter { it.type == TrackType.SUBTITLE }
        assertEquals(listOf(4, 5, MpvTrackList.NO_TRACK), subs.map { it.id })
        // Nothing is selected in the fixture, so "Off" is the active subtitle entry.
        assertTrue(subs.single { it.id == MpvTrackList.NO_TRACK }.selected)
        assertEquals("Signs & Songs (Forced)", subs.single { it.id == 5 }.label)
    }

    @Test
    fun `no Off entry when the source has no subtitles at all`() {
        val audioOnly = MpvTrackList.parse(
            """[{"id":1,"type":"audio","ff-index":1,"lang":"eng"}]""",
        )

        val playerTracks = MpvTrackList.toPlayerTracks(audioOnly, emptyMap())

        assertEquals(1, playerTracks.size)
        assertTrue(playerTracks.none { it.id == MpvTrackList.NO_TRACK })
    }

    @Test
    fun `mpvIdFor translates a Jellyfin index back into an aid or sid`() {
        val tracks = MpvTrackList.parse(trackListJson)

        assertEquals(2, MpvTrackList.mpvIdFor(tracks, emptyMap(), TrackType.AUDIO, 3))
        assertEquals(1, MpvTrackList.mpvIdFor(tracks, emptyMap(), TrackType.SUBTITLE, 4))
        // A stream the transcode dropped resolves to nothing rather than to a wrong track.
        assertNull(MpvTrackList.mpvIdFor(tracks, emptyMap(), TrackType.AUDIO, 99))
        // Types do not bleed into each other even though both namespaces start at 1.
        assertNull(MpvTrackList.mpvIdFor(tracks, emptyMap(), TrackType.AUDIO, 4))
    }

    @Test
    fun `sideloaded subtitles map onto their Jellyfin indices in add order`() {
        val withExternals = MpvTrackList.parse(
            """
            [
              {"id":1,"type":"audio","ff-index":1,"lang":"eng"},
              {"id":1,"type":"sub","external":true,"lang":"eng","title":"English"},
              {"id":2,"type":"sub","external":true,"lang":"spa","title":"Spanish"}
            ]
            """.trimIndent(),
        )
        val requested = listOf(
            ExternalSubtitle(streamIndex = 7, url = "http://s/7.srt", title = "English", language = "eng"),
            ExternalSubtitle(streamIndex = 9, url = "http://s/9.srt", title = "Spanish", language = "spa"),
        )

        val map = MpvTrackList.externalIndexByMpvId(withExternals, requested)

        assertEquals(mapOf(1 to 7, 2 to 9), map)
        // With the map in hand the external subs are addressable by Jellyfin index like any other:
        // Jellyfin stream 9 is mpv sid 2.
        assertEquals(2, MpvTrackList.mpvIdFor(withExternals, map, TrackType.SUBTITLE, 9))
        assertEquals(1, MpvTrackList.mpvIdFor(withExternals, map, TrackType.SUBTITLE, 7))
        assertEquals(
            listOf(7, 9, MpvTrackList.NO_TRACK),
            MpvTrackList.toPlayerTracks(withExternals, map)
                .filter { it.type == TrackType.SUBTITLE }
                .map { it.id },
        )
    }

    @Test
    fun `tracks without ff-index fall back to the mpv id`() {
        // Transcoded HLS output: mpv reports no ff-index, so the id is all there is.
        val transcoded = MpvTrackList.parse("""[{"id":1,"type":"audio","lang":"eng","selected":true}]""")

        assertNull(transcoded.single().ffIndex)
        assertEquals(1, MpvTrackList.streamIndexOf(transcoded.single(), emptyMap()))
        assertEquals(1, MpvTrackList.mpvIdFor(transcoded, emptyMap(), TrackType.AUDIO, 1))
    }

    @Test
    fun `labels fall back through title language and codec`() {
        val tracks = MpvTrackList.parse(
            """
            [
              {"id":1,"type":"audio","ff-index":1,"lang":"eng","codec":"aac"},
              {"id":2,"type":"audio","ff-index":2,"codec":"ac3"},
              {"id":3,"type":"audio","ff-index":3}
            ]
            """.trimIndent(),
        )

        val labels = MpvTrackList.toPlayerTracks(tracks, emptyMap()).map { it.label }

        assertEquals(listOf("eng", "ac3", "Track 3"), labels)
    }
}
