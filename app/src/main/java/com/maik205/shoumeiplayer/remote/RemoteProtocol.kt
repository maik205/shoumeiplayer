package com.maik205.shoumeiplayer.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val RemoteJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

const val PAIRING_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000L // 7 days

/**
 * Commands and events transferred between the Companion App and the TV.
 */
@Serializable
sealed interface RemoteMessage {

    // --- Handshake & Security ---

    @Serializable
    @SerialName("handshake_init")
    data class HandshakeInit(
        val clientPublicKeyBase64: String,
        val clientDeviceName: String = "Mobile Device",
        val pairingPin: String? = null,
        val pairingToken: String? = null,
    ) : RemoteMessage

    @Serializable
    @SerialName("handshake_challenge")
    data class HandshakeChallenge(
        val serverPublicKeyBase64: String,
        val saltBase64: String,
        val pin: String,
        val pairingValid: Boolean = false,
        val pairingExpired: Boolean = false,
    ) : RemoteMessage

    @Serializable
    @SerialName("pair_confirm")
    data class PairConfirm(
        val pin: String,
    ) : RemoteMessage

    @Serializable
    @SerialName("encrypted_credentials")
    data class EncryptedCredentials(
        val ivBase64: String,
        val cipherTextBase64: String,
    ) : RemoteMessage

    // --- Navigation & D-Pad ---

    @Serializable
    @SerialName("key_event")
    data class KeyCommand(
        val key: RemoteKey,
    ) : RemoteMessage

    @Serializable
    @SerialName("text_input")
    data class TextInput(
        val text: String,
    ) : RemoteMessage

    // --- Playback Controls ---

    @Serializable
    @SerialName("playback_command")
    data class PlaybackCommand(
        val action: RemotePlaybackAction,
        val positionMs: Long? = null,
        val deltaMs: Long? = null,
    ) : RemoteMessage

    @Serializable
    @SerialName("play_item")
    data class PlayItem(
        val itemId: String,
        val startPositionTicks: Long = 0L,
    ) : RemoteMessage

    // --- State Updates from TV ---

    @Serializable
    @SerialName("now_playing")
    data class NowPlaying(
        val state: RemoteNowPlayingState,
    ) : RemoteMessage

    @Serializable
    @SerialName("status_ack")
    data class StatusAck(
        val success: Boolean,
        val message: String? = null,
    ) : RemoteMessage

    @Serializable
    @SerialName("select_track")
    data class SelectTrack(
        val trackId: Int,
        val type: RemoteTrackType,
    ) : RemoteMessage

    @Serializable
    @SerialName("set_quality")
    data class SetQuality(
        val quality: String,
    ) : RemoteMessage

    @Serializable
    @SerialName("set_volume")
    data class SetVolume(
        val volume: Int,
    ) : RemoteMessage
}

@Serializable
enum class RemoteTrackType {
    VIDEO,
    AUDIO,
    SUBTITLE,
}

@Serializable
data class RemoteMediaTrack(
    val id: Int,
    val type: RemoteTrackType,
    val label: String,
    val language: String? = null,
    val title: String? = null,
    val codec: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isExternal: Boolean = false,
    val selected: Boolean = false,
)

@Serializable
enum class RemoteKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    SELECT,
    BACK,
    HOME,
    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MUTE,
    PLAY_PAUSE,
    FAST_FORWARD,
    REWIND,
    MENU,
    BACKSPACE,
    ENTER,
}

@Serializable
enum class RemotePlaybackAction {
    PLAY,
    PAUSE,
    TOGGLE_PLAY_PAUSE,
    SEEK_TO,
    SKIP_FORWARD,
    SKIP_BACKWARD,
    TOGGLE_SUBTITLES,
}

/**
 * Decrypted payload containing active Jellyfin server connection data.
 */
@Serializable
data class RemoteSessionData(
    val tvName: String,
    val serverUrl: String,
    val accessToken: String,
    val userId: String,
    val userName: String,
    val pairingToken: String? = null,
    val expiresAtMs: Long? = null,
)

/**
 * Real-time playback status broadcasted to the companion app.
 */
@Serializable
data class RemoteNowPlayingState(
    val itemId: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Int = 100,
    val isMuted: Boolean = false,
    val speed: Float = 1.0f,
    val audioTracks: List<RemoteMediaTrack> = emptyList(),
    val subtitleTracks: List<RemoteMediaTrack> = emptyList(),
    val videoTracks: List<RemoteMediaTrack> = emptyList(),
    val availableQualities: List<String> = emptyList(),
    val selectedQuality: String? = null,
)
