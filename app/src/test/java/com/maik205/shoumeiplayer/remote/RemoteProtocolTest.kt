package com.maik205.shoumeiplayer.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteProtocolTest {

    @Test
    fun serializeAndDeserialize_handshakeMessages() {
        val init = RemoteMessage.HandshakeInit(
            clientPublicKeyBase64 = "BM25+clientKey==",
            clientDeviceName = "Pixel 9 Pro",
        )
        val jsonInit = RemoteJson.encodeToString(RemoteMessage.serializer(), init)
        val decodedInit = RemoteJson.decodeFromString<RemoteMessage>(jsonInit)
        assertEquals(init, decodedInit)

        val challenge = RemoteMessage.HandshakeChallenge(
            serverPublicKeyBase64 = "BM25+serverKey==",
            saltBase64 = "salt1234==",
            pin = "4821",
        )
        val jsonChallenge = RemoteJson.encodeToString(RemoteMessage.serializer(), challenge)
        val decodedChallenge = RemoteJson.decodeFromString<RemoteMessage>(jsonChallenge)
        assertEquals(challenge, decodedChallenge)

        val confirm = RemoteMessage.PairConfirm(pin = "4821")
        val jsonConfirm = RemoteJson.encodeToString(RemoteMessage.serializer(), confirm)
        val decodedConfirm = RemoteJson.decodeFromString<RemoteMessage>(jsonConfirm)
        assertEquals(confirm, decodedConfirm)
    }

    @Test
    fun serializeAndDeserialize_keyCommands() {
        RemoteKey.entries.forEach { key ->
            val cmd = RemoteMessage.KeyCommand(key = key)
            val json = RemoteJson.encodeToString(RemoteMessage.serializer(), cmd)
            val decoded = RemoteJson.decodeFromString<RemoteMessage>(json)
            assertEquals(cmd, decoded)
        }
    }

    @Test
    fun serializeAndDeserialize_playbackCommands() {
        val play = RemoteMessage.PlaybackCommand(action = RemotePlaybackAction.PLAY)
        val pause = RemoteMessage.PlaybackCommand(action = RemotePlaybackAction.PAUSE)
        val seek = RemoteMessage.PlaybackCommand(action = RemotePlaybackAction.SEEK_TO, positionMs = 45000L)
        val skip = RemoteMessage.PlaybackCommand(action = RemotePlaybackAction.SKIP_FORWARD, deltaMs = 10000L)

        listOf(play, pause, seek, skip).forEach { cmd ->
            val json = RemoteJson.encodeToString(RemoteMessage.serializer(), cmd)
            val decoded = RemoteJson.decodeFromString<RemoteMessage>(json)
            assertEquals(cmd, decoded)
        }
    }

    @Test
    fun serializeAndDeserialize_playItemAndTextInput() {
        val playItem = RemoteMessage.PlayItem(itemId = "item-12345", startPositionTicks = 300000000L)
        val jsonPlay = RemoteJson.encodeToString(RemoteMessage.serializer(), playItem)
        val decodedPlay = RemoteJson.decodeFromString<RemoteMessage>(jsonPlay)
        assertEquals(playItem, decodedPlay)

        val textInput = RemoteMessage.TextInput(text = "Dune: Part Two")
        val jsonText = RemoteJson.encodeToString(RemoteMessage.serializer(), textInput)
        val decodedText = RemoteJson.decodeFromString<RemoteMessage>(jsonText)
        assertEquals(textInput, decodedText)

        val setVol = RemoteMessage.SetVolume(volume = 65)
        val jsonVol = RemoteJson.encodeToString(RemoteMessage.serializer(), setVol)
        val decodedVol = RemoteJson.decodeFromString<RemoteMessage>(jsonVol)
        assertEquals(setVol, decodedVol)
    }

    @Test
    fun serializeAndDeserialize_nowPlayingState() {
        val state = RemoteNowPlayingState(
            itemId = "item-abc",
            title = "Oppenheimer",
            subtitle = "Christopher Nolan",
            posterUrl = "http://jellyfin/img",
            isPlaying = true,
            positionMs = 120000L,
            durationMs = 18000000L,
            volume = 85,
            isMuted = false,
            speed = 1.0f,
        )
        val msg = RemoteMessage.NowPlaying(state)
        val json = RemoteJson.encodeToString(RemoteMessage.serializer(), msg)
        val decoded = RemoteJson.decodeFromString<RemoteMessage>(json)
        assertEquals(msg, decoded)
    }

    @Test
    fun serializeAndDeserialize_remoteSessionData() {
        val session = RemoteSessionData(
            tvName = "Living Room TV",
            serverUrl = "http://192.168.1.10:8096",
            accessToken = "secret-token-xyz",
            userId = "user-123",
            userName = "Maik",
        )
        val json = RemoteJson.encodeToString(RemoteSessionData.serializer(), session)
        val decoded = RemoteJson.decodeFromString<RemoteSessionData>(json)
        assertEquals(session, decoded)
    }
}
