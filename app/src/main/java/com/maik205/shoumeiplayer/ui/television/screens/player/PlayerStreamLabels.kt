package com.maik205.shoumeiplayer.ui.television.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.settings.HdrMode
import com.maik205.shoumeiplayer.feature.player.PlayerAudioInfo
import com.maik205.shoumeiplayer.feature.player.PlayerVideoInfo
import com.maik205.shoumeiplayer.player.PLAY_METHOD_DIRECT
import com.maik205.shoumeiplayer.player.PLAY_METHOD_DIRECT_STREAM
import com.maik205.shoumeiplayer.player.PLAY_METHOD_TRANSCODE
import com.maik205.shoumeiplayer.player.VideoQuality

@Composable
internal fun PlayerVideoInfo?.descriptionLabel(): String? {
    val info = this ?: return null
    val codec = info.codec?.takeIf(String::isNotBlank)
    val resolution = streamResolutionLabel(info.width, info.height)
    return listOfNotNull(codec, resolution)
        .joinToString(stringResource(R.string.tv_metadata_separator))
        .takeIf(String::isNotBlank)
}

@Composable
internal fun PlayerAudioInfo?.descriptionLabel(): String? {
    val info = this ?: return null
    val codec = info.codec?.takeIf(String::isNotBlank)
    val channelCount = info.channels
    val channels = if (channelCount == null) {
        null
    } else {
        stringResource(R.string.tv_channels_count, channelCount)
    }
    val language = info.language?.takeIf(String::isNotBlank)
    return listOfNotNull(codec, channels, language)
        .joinToString(stringResource(R.string.tv_metadata_separator))
        .takeIf(String::isNotBlank)
}

@Composable
internal fun streamResolutionLabel(width: Int?, height: Int?): String? =
    if (width == null || height == null) {
        null
    } else {
        stringResource(R.string.tv_player_resolution, width, height)
    }

@Composable
internal fun HdrMode?.descriptionLabel(): String? = when (this) {
    HdrMode.Automatic -> stringResource(R.string.tv_settings_automatic)
    HdrMode.Passthrough -> stringResource(R.string.tv_settings_passthrough)
    HdrMode.ToneMap -> stringResource(R.string.tv_settings_tone_map)
    HdrMode.ForceSdr -> stringResource(R.string.tv_settings_force_sdr)
    HdrMode.Off -> stringResource(R.string.off)
    null -> null
}

@Composable
internal fun VideoQuality.descriptionLabel(): String = when (this) {
    VideoQuality.AUTO -> stringResource(R.string.tv_auto)
    VideoQuality.UHD -> stringResource(R.string.tv_resolution_4k)
    VideoQuality.FHD -> stringResource(R.string.tv_resolution_1080p)
    VideoQuality.HD -> stringResource(R.string.tv_resolution_720p)
    VideoQuality.SD -> stringResource(R.string.tv_resolution_480p)
}

@Composable
internal fun String?.playMethodLabel(): String? = when (this) {
    PLAY_METHOD_DIRECT -> stringResource(R.string.tv_player_direct_play)
    PLAY_METHOD_DIRECT_STREAM -> stringResource(R.string.tv_player_direct_stream)
    PLAY_METHOD_TRANSCODE -> stringResource(R.string.tv_player_transcoding)
    else -> this?.takeIf(String::isNotBlank)
}
