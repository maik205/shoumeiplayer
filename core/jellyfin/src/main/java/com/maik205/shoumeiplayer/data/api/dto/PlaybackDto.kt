package com.maik205.shoumeiplayer.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MediaStreamDto(
    @SerialName("Index") val index: Int = -1,
    @SerialName("Type") val type: String? = null, // Video|Audio|Subtitle|...
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("IsForced") val isForced: Boolean = false,
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("DeliveryMethod") val deliveryMethod: String? = null,
    @SerialName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("IsAudioDescription") val isAudioDescription: Boolean = false,
)

@Serializable
data class MediaSourceInfoDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Protocol") val protocol: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Bitrate") val bitrate: Long? = null,
    @SerialName("IsInfiniteStream") val isInfiniteStream: Boolean = false,
    @SerialName("RequiresOpening") val requiresOpening: Boolean = false,
    @SerialName("OpenToken") val openToken: String? = null,
    @SerialName("RequiresClosing") val requiresClosing: Boolean = false,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("RequiredHttpHeaders") val requiredHttpHeaders: Map<String, String?>? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @SerialName("DefaultAudioStreamIndex") val defaultAudioStreamIndex: Int? = null,
    @SerialName("DefaultSubtitleStreamIndex") val defaultSubtitleStreamIndex: Int? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
)

@Serializable
data class PlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<MediaSourceInfoDto> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("ErrorCode") val errorCode: String? = null,
)

@Serializable
data class PlaybackInfoDto(
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfileDto? = null,
    @SerialName("StartTimeTicks") val startTimeTicks: Long? = null,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long? = null,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("AutoOpenLiveStream") val autoOpenLiveStream: Boolean? = null,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean = true,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean = true,
    @SerialName("EnableTranscoding") val enableTranscoding: Boolean = true,
    @SerialName("AllowVideoStreamCopy") val allowVideoStreamCopy: Boolean = true,
    @SerialName("AllowAudioStreamCopy") val allowAudioStreamCopy: Boolean = true,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)

@Serializable
data class OpenLiveStreamDto(
    @SerialName("OpenToken") val openToken: String? = null,
    @SerialName("UserId") val userId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Int? = null,
    @SerialName("StartTimeTicks") val startTimeTicks: Long? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("MaxAudioChannels") val maxAudioChannels: Int? = null,
    @SerialName("ItemId") val itemId: String? = null,
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean? = null,
    @SerialName("EnableDirectStream") val enableDirectStream: Boolean? = null,
    @SerialName("AlwaysBurnInSubtitleWhenTranscoding") val alwaysBurnInSubtitleWhenTranscoding: Boolean? = null,
    @SerialName("DeviceProfile") val deviceProfile: DeviceProfileDto? = null,
    @SerialName("DirectPlayProtocols") val directPlayProtocols: List<String> = emptyList(),
)

@Serializable
data class LiveStreamResponse(
    @SerialName("MediaSource") val mediaSource: MediaSourceInfoDto? = null,
)

// Playback reporting bodies (one class serves Start and Progress).
@Serializable
data class PlaybackProgressBody(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("PlayMethod") val playMethod: String? = null,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("IsMuted") val isMuted: Boolean = false,
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)

@Serializable
data class PlaybackStopBody(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("PlaySessionId") val playSessionId: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("PositionTicks") val positionTicks: Long = 0,
    @SerialName("Failed") val failed: Boolean = false,
)
