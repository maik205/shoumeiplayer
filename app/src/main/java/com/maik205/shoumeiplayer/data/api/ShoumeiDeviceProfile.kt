package com.maik205.shoumeiplayer.data.api

import com.maik205.shoumeiplayer.data.api.dto.DeviceProfileDto
import com.maik205.shoumeiplayer.data.api.dto.DirectPlayProfileDto
import com.maik205.shoumeiplayer.data.api.dto.SubtitleProfileDto
import com.maik205.shoumeiplayer.data.api.dto.TranscodingProfileDto

/**
 * Permissive device profile: mostly declares broad direct-play support so the
 * server hands back a stream URL rather than transcoding, with a single HLS
 * fallback profile for anything unsupported.
 */
object ShoumeiDeviceProfile {

    private val directPlayContainers = listOf("mkv", "mp4", "webm", "avi", "ts", "mov", "flv", "m4v")
    private const val VIDEO_CODECS = "h264,hevc,av1,vp9,mpeg4,mpeg2video,vc1"
    private const val AUDIO_CODECS = "aac,ac3,eac3,opus,flac,mp3,dts,truehd,vorbis,pcm"

    fun build(): DeviceProfileDto = DeviceProfileDto(
        name = "Shoumei Player",
        maxStreamingBitrate = 400_000_000,
        directPlayProfiles = directPlayContainers.map { container ->
            DirectPlayProfileDto(
                container = container,
                type = "Video",
                videoCodec = VIDEO_CODECS,
                audioCodec = AUDIO_CODECS,
            )
        },
        transcodingProfiles = listOf(
            TranscodingProfileDto(
                container = "ts",
                type = "Video",
                videoCodec = "h264",
                audioCodec = "aac,ac3",
                protocol = "hls",
                context = "Streaming",
                maxAudioChannels = "6",
                minSegments = 1,
                breakOnNonKeyFrames = true,
            ),
        ),
        subtitleProfiles = listOf(
            SubtitleProfileDto(format = "subrip", method = "Embed"),
            SubtitleProfileDto(format = "ass", method = "Embed"),
            SubtitleProfileDto(format = "ssa", method = "Embed"),
            SubtitleProfileDto(format = "pgssub", method = "Embed"),
            SubtitleProfileDto(format = "vtt", method = "Embed"),
            SubtitleProfileDto(format = "subrip", method = "External"),
            SubtitleProfileDto(format = "vtt", method = "External"),
        ),
        codecProfiles = emptyList(),
    )
}
