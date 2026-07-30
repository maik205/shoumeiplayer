package com.maik205.shoumeiplayer.data.api

import com.maik205.shoumeiplayer.data.api.dto.DeviceProfileDto
import com.maik205.shoumeiplayer.data.api.dto.DirectPlayProfileDto
import com.maik205.shoumeiplayer.data.api.dto.SubtitleProfileDto
import com.maik205.shoumeiplayer.data.api.dto.TranscodingProfileDto

/**
 * Permissive mpv device profile: declares broad native video and audio support so Jellyfin hands
 * the original file to mpv, with a single video HLS fallback for an unsupported video source.
 */
object ShoumeiDeviceProfile {

    private val directPlayVideoContainers = listOf("mkv", "mp4", "webm", "avi", "ts", "mov", "flv", "m4v")
    private val directPlayAudioContainers = listOf(
        "aac",
        "ac3",
        "aif",
        "aiff",
        "alac",
        "amr",
        "ape",
        "dff",
        "dsf",
        "dts",
        "eac3",
        "flac",
        "m4a",
        "m4b",
        "mp3",
        "mpc",
        "mpp",
        "oga",
        "ogg",
        "opus",
        "spx",
        "tta",
        "wav",
        "webm",
        "webma",
        "wma",
    )
    private const val VIDEO_CODECS = "h264,hevc,av1,vp9,mpeg4,mpeg2video,vc1"
    private const val AUDIO_CODECS = "aac,ac3,eac3,opus,flac,mp3,dts,truehd,vorbis,pcm"

    fun build(): DeviceProfileDto = DeviceProfileDto(
        name = "Shoumei Player",
        maxStreamingBitrate = 400_000_000,
        directPlayProfiles = buildList {
            directPlayVideoContainers.forEach { container ->
                add(
                    DirectPlayProfileDto(
                        container = container,
                        type = "Video",
                        videoCodec = VIDEO_CODECS,
                        audioCodec = AUDIO_CODECS,
                    ),
                )
            }
            directPlayAudioContainers.forEach { container ->
                add(
                    DirectPlayProfileDto(
                        container = container,
                        type = "Audio",
                        // mpv owns audio decoding, so do not make Jellyfin reject an otherwise
                        // supported source merely because its codec is absent from a client-side
                        // allow-list. The container list is the direct-file capability boundary.
                        audioCodec = null,
                    ),
                )
            }
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
