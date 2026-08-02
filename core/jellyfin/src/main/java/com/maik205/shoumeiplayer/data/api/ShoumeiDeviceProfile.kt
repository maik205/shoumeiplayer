package com.maik205.shoumeiplayer.data.api

import com.maik205.shoumeiplayer.data.api.dto.DeviceProfileDto
import com.maik205.shoumeiplayer.data.api.dto.DirectPlayProfileDto
import com.maik205.shoumeiplayer.data.api.dto.SubtitleProfileDto
import com.maik205.shoumeiplayer.data.api.dto.TranscodingProfileDto
import com.maik205.shoumeiplayer.domain.settings.AssSsaDirectPlay
import com.maik205.shoumeiplayer.domain.settings.BurnSubtitles
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities
import com.maik205.shoumeiplayer.domain.settings.HardwareCodecs

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

    /**
     * Ceiling used when [ClientSettings.maxStreamingBitrateMbps] is null ("Auto", displayed as up
     * to 120 Mbps): effectively unlimited so direct play/stream is never rejected on bitrate alone.
     */
    private const val AUTO_MAX_STREAMING_BITRATE = 400_000_000L

    /**
     * Builds the device profile Jellyfin uses to decide direct play vs. transcode. [capabilities]
     * comes from the device's actual MediaCodec enumeration; [settings] layers the user's explicit
     * ClientSettings choices on top per the client-wins-over-server-default precedence documented on
     * [ClientSettings] itself: [ClientSettings.maxStreamingBitrateMbps] caps the bitrate Jellyfin may
     * hand back, [ClientSettings.hardwareCodecs] narrows the advertised direct-play video codec
     * allow-list, and [ClientSettings.burnSubtitles]/[ClientSettings.pgsDirectPlay]/
     * [ClientSettings.assSsaDirectPlay] shape which subtitle formats are declared deliverable
     * (Embed/External) vs. left for the server to burn in.
     */
    fun build(
        capabilities: DevicePlaybackCapabilities = DevicePlaybackCapabilities(),
        settings: ClientSettings = ClientSettings(),
    ): DeviceProfileDto = DeviceProfileDto(
        name = "Shoumei Player",
        maxStreamingBitrate = settings.maxStreamingBitrateMbps
            ?.let { mbps -> mbps.toLong() * 1_000_000L }
            ?: AUTO_MAX_STREAMING_BITRATE,
        directPlayProfiles = buildList {
            val detectedVideoCodecs = capabilities.videoCodecs
                .mapNotNull { it.mimeType.toJellyfinVideoCodec() }
                .distinct()
                .takeIf(List<String>::isNotEmpty)
                ?: VIDEO_CODECS.split(",")
            // What this device can direct-play is what MediaCodec actually enumerated -- nothing
            // else. `ClientSettings.hardwareCodecs` deliberately does not narrow it: that setting
            // picks which codecs use the hardware decoder (mpv's `hwdec-codecs`, applied in
            // MpvEngine), and mpv software-decodes the rest perfectly well. Folding it in here made
            // choosing "AV1" advertise av1 alone, so an ordinary h264/HEVC library direct-played
            // one moment and became 100% server transcode the next.
            val videoCodec = detectedVideoCodecs.joinToString(",")
            directPlayVideoContainers.forEach { container ->
                add(
                    DirectPlayProfileDto(
                        container = container,
                        type = "Video",
                        videoCodec = videoCodec,
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
        subtitleProfiles = subtitleProfiles(settings),
        codecProfiles = emptyList(),
    )

    private fun String.toJellyfinVideoCodec(): String? = when (lowercase()) {
        "video/avc" -> "h264"
        "video/hevc" -> "hevc"
        "video/av01" -> "av1"
        "video/x-vnd.on2.vp9" -> "vp9"
        "video/x-vnd.on2.vp8" -> "vp8"
        "video/mp4v-es" -> "mpeg4"
        "video/mpeg2" -> "mpeg2video"
        else -> null
    }


    /**
     * Builds the subtitle profile list, which is how burn-in policy is actually expressed to
     * Jellyfin: a format left off this list (as Embed/External) has no non-burn delivery method the
     * server can match, so it falls back to encoding (burning) the subtitle into the transcoded
     * video. [ClientSettings.burnSubtitles] is the coarse control; [ClientSettings.pgsDirectPlay] and
     * [ClientSettings.assSsaDirectPlay] refine it per-format when burn policy is left at Automatic.
     */
    private fun subtitleProfiles(settings: ClientSettings): List<SubtitleProfileDto> {
        val forceAlwaysBurn = settings.burnSubtitles == BurnSubtitles.Always
        val forceImageBurn = forceAlwaysBurn || settings.burnSubtitles == BurnSubtitles.ImageFormats
        val forceNeverBurn = settings.burnSubtitles == BurnSubtitles.Never

        val textEmbeddable = forceNeverBurn || !forceAlwaysBurn
        val assSsaEmbeddable = forceNeverBurn ||
            (!forceAlwaysBurn && settings.assSsaDirectPlay != AssSsaDirectPlay.Disabled)
        val pgsEmbeddable = forceNeverBurn || (!forceImageBurn && settings.pgsDirectPlay)

        return buildList {
            if (textEmbeddable) add(SubtitleProfileDto(format = "subrip", method = "Embed"))
            if (assSsaEmbeddable) add(SubtitleProfileDto(format = "ass", method = "Embed"))
            if (assSsaEmbeddable) add(SubtitleProfileDto(format = "ssa", method = "Embed"))
            if (pgsEmbeddable) add(SubtitleProfileDto(format = "pgssub", method = "Embed"))
            if (textEmbeddable) add(SubtitleProfileDto(format = "vtt", method = "Embed"))
            if (textEmbeddable) {
                add(SubtitleProfileDto(format = "subrip", method = "External"))
                add(SubtitleProfileDto(format = "vtt", method = "External"))
            }
        }
    }
}
