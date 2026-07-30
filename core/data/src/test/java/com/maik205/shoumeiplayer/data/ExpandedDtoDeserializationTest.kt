package com.maik205.shoumeiplayer.data

import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.LyricDto
import com.maik205.shoumeiplayer.data.api.dto.UserDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpandedDtoDeserializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `music live-tv and current-program fields deserialize`() {
        val item = json.decodeFromString<BaseItemDto>(
            """
            {
              "Id":"channel-1",
              "Name":"Music TV",
              "Type":"LiveTvChannel",
              "ChannelNumber":"42",
              "Album":"Album One",
              "AlbumArtist":"Artist One",
              "AlbumId":"album-1",
              "Artists":["Artist One"],
              "ArtistItems":[{"Id":"artist-1","Name":"Artist One"}],
              "SongCount":12,
              "AlbumCount":4,
              "HasLyrics":true,
              "IsLive":true,
              "CurrentProgram":{
                "Id":"program-1",
                "Name":"Now Playing",
                "Type":"LiveTvProgram",
                "StartDate":"2026-07-28T10:00:00Z",
                "EndDate":"2026-07-28T11:00:00Z"
              }
            }
            """.trimIndent(),
        )

        assertEquals("Album One", item.album)
        assertEquals("Artist One", item.albumArtist)
        assertEquals("artist-1", item.artistItems.single().id)
        assertEquals("42", item.channelNumber)
        assertEquals(12, item.songCount)
        assertEquals(4, item.albumCount)
        assertTrue(item.hasLyrics)
        assertTrue(item.isLive)
        assertEquals("program-1", item.currentProgram?.id)
    }

    @Test
    fun `public user policy fields deserialize`() {
        val user = json.decodeFromString<UserDto>(
            """
            {
              "Id":"user-1",
              "Name":"Alice",
              "PrimaryImageTag":"portrait",
              "HasPassword":true,
              "Policy":{
                "IsAdministrator":true,
                "EnableLiveTvAccess":true,
                "EnableMediaPlayback":true,
                "AuthenticationProviderId":"Jellyfin.Server.Implementations.Users.DefaultAuthenticationProvider",
                "PasswordResetProviderId":"Jellyfin.Server.Implementations.Users.DefaultPasswordResetProvider"
              }
            }
            """.trimIndent(),
        )

        assertEquals("portrait", user.primaryImageTag)
        assertTrue(user.hasPassword)
        assertTrue(user.policy?.isAdministrator == true)
        assertTrue(user.policy?.enableLiveTvAccess == true)
    }

    @Test
    fun `synced lyrics and word cues deserialize`() {
        val lyrics = json.decodeFromString<LyricDto>(
            """
            {
              "Metadata":{"Artist":"Artist","Title":"Song","IsSynced":true,"Offset":10000},
              "Lyrics":[{
                "Text":"Hello world",
                "Start":20000,
                "Cues":[{"Position":0,"EndPosition":5,"Start":20000,"End":25000}]
              }]
            }
            """.trimIndent(),
        )

        assertEquals("Song", lyrics.metadata?.title)
        assertTrue(lyrics.metadata?.isSynced == true)
        assertEquals(20_000L, lyrics.lyrics.single().start)
        assertEquals(5, lyrics.lyrics.single().cues?.single()?.endPosition)
    }
}
