package com.maik205.shoumeiplayer.ui.television.screens.settings

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.maik205.shoumeiplayer.R

internal enum class SettingsSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Playback(R.string.tv_settings_playback, Icons.Default.PlayArrow),
    Video(R.string.tv_settings_video, Icons.Default.Movie),
    Audio(R.string.tv_settings_audio, Icons.AutoMirrored.Filled.VolumeUp),
    Subtitles(R.string.tv_settings_subtitles, Icons.Default.ClosedCaption),
    Interface(R.string.tv_settings_interface, Icons.Default.Tv),
    Network(R.string.tv_settings_network, Icons.Default.Wifi),
    Screensaver(R.string.tv_settings_screensaver, Icons.Default.PhotoLibrary),
    Server(R.string.tv_settings_server, Icons.Default.Dns),
    Account(R.string.tv_settings_account, Icons.Default.Person),
    About(R.string.tv_settings_about, Icons.Default.Info),
}

internal enum class SettingControl {
    Value,
    Choice,
    Toggle,
}

internal enum class SettingCapability {
    RefreshRateSwitching,
    DolbyDigitalPassthrough,
    DolbyDigitalPlusPassthrough,
    DtsPassthrough,
}

internal data class SettingChoiceOption(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

internal data class SettingRowModel(
    val key: String,
    @StringRes val labelRes: Int,
    val value: String,
    val control: SettingControl,
    val checked: Boolean = false,
    val requiredCapability: SettingCapability? = null,
    val onClick: (() -> Unit)? = null,
    val choices: List<SettingChoiceOption> = emptyList(),
)
