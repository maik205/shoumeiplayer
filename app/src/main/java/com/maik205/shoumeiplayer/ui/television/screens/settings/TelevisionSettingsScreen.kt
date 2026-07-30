package com.maik205.shoumeiplayer.ui.television.screens.settings

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.BuildConfig
import com.maik205.shoumeiplayer.data.session.ClientSettings
import com.maik205.shoumeiplayer.ui.television.components.TelevisionAppTopNavigation
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusScale
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.model.LibraryDestinationUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions

private enum class SettingsSection(
    val label: String,
    val icon: ImageVector,
) {
    Playback("Playback", Icons.Default.PlayArrow),
    Video("Video", Icons.Default.Movie),
    Audio("Audio", Icons.AutoMirrored.Filled.VolumeUp),
    Subtitles("Subtitles", Icons.Default.ClosedCaption),
    Interface("Interface", Icons.Default.Tv),
    Network("Network", Icons.Default.Wifi),
    Screensaver("Screensaver", Icons.Default.PhotoLibrary),
    Server("Server", Icons.Default.Dns),
    Account("Account", Icons.Default.Person),
    About("About", Icons.Default.Info),
}

private enum class SettingControl {
    Value,
    Choice,
    Toggle,
}

private data class SettingChoiceOption(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onSelect: () -> Unit,
)

private data class SettingRowModel(
    val key: String,
    val label: String,
    val value: String,
    val control: SettingControl,
    val checked: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val choices: List<SettingChoiceOption> = emptyList(),
)

@Composable
fun TelevisionSettingsScreen(
    state: TelevisionSettingsState,
    libraries: List<LibraryDestinationUi>,
    avatarUrl: String?,
    onUpdate: ((ClientSettings) -> ClientSettings) -> Unit,
    onSignOut: () -> Unit,
    onChangeServer: () -> Unit,
    onForgetServer: () -> Unit,
    onSwitchProfile: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshLibraries: () -> Unit,
    onQuickConnect: () -> Unit,
    onClearArtworkCache: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: (LibraryDestinationUi) -> Unit,
    onNavigateProfile: () -> Unit,
    navigationState: LazyListState,
) {
    var section by remember { mutableStateOf(SettingsSection.Playback) }
    var activeChoice by remember { mutableStateOf<SettingRowModel?>(null) }
    var choiceReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val settingsTopFocus = remember { FocusRequester() }
    val sectionFocus = remember {
        SettingsSection.entries.associateWith { FocusRequester() }
    }
    val sectionRailFocus = SettingsSection.entries.map(sectionFocus::getValue)
    val sectionRailState = rememberLazyListState()

    LaunchedEffect(activeChoice) {
        if (activeChoice == null) {
            choiceReturnFocus?.let { requester ->
                runCatching { requester.requestFocus() }
            }
            choiceReturnFocus = null
        }
    }

    BackHandler(enabled = activeChoice != null) {
        activeChoice = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(TelevisionColors.LibraryBackground)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            TelevisionColors.Paper.copy(alpha = 0.07f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        center = Offset(size.width * 0.16f, 0f),
                        radius = size.width * 0.58f,
                    ),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    end = TelevisionDimensions.SafeHorizontal,
                    top = 50.dp,
                ),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                state = sectionRailState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .focusGroup()
                    .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                itemsIndexed(
                    SettingsSection.entries,
                    key = { _, destination -> destination.name },
                ) { index, destination ->
                    TelevisionFocusSurface(
                        onClick = { section = destination },
                        restingAlpha = if (section == destination) 0.82f else 0.4f,
                        scaleTo = TelevisionFocusScale.Navigation,
                        focusRequester = sectionFocus.getValue(destination),
                        onFocusChanged = { focused ->
                            if (focused) {
                                section = destination
                            }
                        },
                        modifier = Modifier
                            .height(30.dp)
                            .televisionHorizontalWrap(index, sectionRailFocus, sectionRailState)
                            .focusProperties { up = settingsTopFocus }
                            .televisionBringIntoViewOnFocus(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = destination.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingsSectionContent(
                section = section,
                state = state,
                onUpdate = onUpdate,
                onSignOut = onSignOut,
                onChangeServer = onChangeServer,
                onForgetServer = onForgetServer,
                onSwitchProfile = onSwitchProfile,
                onTestConnection = onTestConnection,
                onRefreshLibraries = onRefreshLibraries,
                onQuickConnect = onQuickConnect,
                onClearArtworkCache = onClearArtworkCache,
                selectedSectionFocus = sectionFocus.getValue(section),
                onOpenChoice = { row, returnFocus ->
                    choiceReturnFocus = returnFocus
                    activeChoice = row
                },
                onMoveSection = { offset ->
                    val destination = SettingsSection.entries.getOrNull(section.ordinal + offset)
                    destination?.let { target ->
                        runCatching { sectionFocus.getValue(target).requestFocus() }
                    }
                },
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            )
        }

        TelevisionAppTopNavigation(
            libraries = libraries,
            selectedKey = "settings",
            userName = state.userName,
            avatarUrl = avatarUrl,
            onNavigateHome = onNavigateHome,
            onNavigateSearch = onNavigateSearch,
            onNavigateLibrary = onNavigateLibrary,
            onNavigateSettings = {},
            onNavigateProfile = onNavigateProfile,
            settingsFocusRequester = settingsTopFocus,
            contentFocusRequester = sectionFocus.getValue(section),
            navigationState = navigationState,
        )

        activeChoice?.let { row ->
            SettingsChoiceDrawer(
                row = row,
                onSelect = { selectedOption ->
                    activeChoice = activeChoice?.copy(
                        value = selectedOption.label,
                        choices = activeChoice?.choices.orEmpty().map { option ->
                            option.copy(selected = option.key == selectedOption.key)
                        },
                    )
                    selectedOption.onSelect()
                },
                onDismiss = { activeChoice = null },
            )
        }
    }
}

@Composable
private fun SettingsSectionContent(
    section: SettingsSection,
    state: TelevisionSettingsState,
    onUpdate: ((ClientSettings) -> ClientSettings) -> Unit,
    onSignOut: () -> Unit,
    onChangeServer: () -> Unit,
    onForgetServer: () -> Unit,
    onSwitchProfile: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshLibraries: () -> Unit,
    onQuickConnect: () -> Unit,
    onClearArtworkCache: () -> Unit,
    selectedSectionFocus: FocusRequester,
    onOpenChoice: (SettingRowModel, FocusRequester) -> Unit,
    onMoveSection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = settingsRows(
        section = section,
        state = state,
        update = onUpdate,
        onSignOut = onSignOut,
        onChangeServer = onChangeServer,
        onForgetServer = onForgetServer,
        onSwitchProfile = onSwitchProfile,
        onTestConnection = onTestConnection,
        onRefreshLibraries = onRefreshLibraries,
        onQuickConnect = onQuickConnect,
        onClearArtworkCache = onClearArtworkCache,
    )
    var focusedKey by remember(section) { mutableStateOf<String?>(null) }
    val rowFocus = remember(section) {
        rows
            .filter { row -> row.onClick != null || row.choices.isNotEmpty() }
            .associate { row -> row.key to FocusRequester() }
    }

    LazyColumn(
        modifier = modifier
            .focusGroup()
            .focusRestorer()
            .focusProperties { up = selectedSectionFocus },
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        items(rows, key = SettingRowModel::key) { row ->
            val restingAlpha = if (focusedKey == null || focusedKey == row.key) 0.74f else 0.38f
            val focusRequester = rowFocus[row.key]
            if (focusRequester == null) {
                SettingsValueRow(
                    row = row,
                    rowAlpha = restingAlpha,
                )
            } else {
                SettingsInteractiveRow(
                    row = row,
                    restingAlpha = restingAlpha,
                    focusRequester = focusRequester,
                    onClick = if (row.choices.isNotEmpty()) {
                        { onOpenChoice(row, focusRequester) }
                    } else {
                        row.onClick ?: {}
                    },
                    onMoveSection = onMoveSection,
                    onFocusChanged = { focused ->
                        if (focused) {
                            focusedKey = row.key
                        } else if (focusedKey == row.key) {
                            focusedKey = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsValueRow(
    row: SettingRowModel,
    rowAlpha: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(37.dp)
            .padding(horizontal = 6.dp)
            .graphicsLayer { alpha = rowAlpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            color = TelevisionColors.PaperMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsInteractiveRow(
    row: SettingRowModel,
    restingAlpha: Float,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onMoveSection: (Int) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        restingAlpha = restingAlpha,
        focusedAlpha = 1f,
        scaleTo = 1f,
        focusRequester = focusRequester,
        onFocusChanged = onFocusChanged,
        modifier = Modifier
            .fillMaxWidth()
            .height(37.dp)
            .televisionBringIntoViewOnFocus()
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                if (nativeEvent.action != KeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onMoveSection(-1)
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onMoveSection(1)
                            true
                        }

                        else -> false
                    }
                }
            },
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            when (row.control) {
                SettingControl.Toggle -> SettingsToggle(
                    checked = row.checked,
                    focused = focused,
                )

                SettingControl.Choice -> {
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (focused) TelevisionColors.Paper else TelevisionColors.PaperMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }

                SettingControl.Value -> Unit
            }
        }
    }
}

@Composable
private fun SettingsChoiceDrawer(
    row: SettingRowModel,
    onSelect: (SettingChoiceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val backFocus = remember(row.key) { FocusRequester() }
    val selectedFocus = remember(row.key) { FocusRequester() }
    val initialIndex = row.choices.indexOfFirst(SettingChoiceOption::selected)
        .takeIf { it >= 0 }
        ?: 0

    LaunchedEffect(row.key) {
        runCatching { selectedFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TelevisionColors.Black.copy(alpha = 0.54f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(390.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        0.18f to TelevisionColors.Black.copy(alpha = 0.18f),
                        0.5f to TelevisionColors.Black.copy(alpha = 0.68f),
                        0.78f to TelevisionColors.Black.copy(alpha = 0.94f),
                        1f to TelevisionColors.Black.copy(alpha = 0.99f),
                    ),
                )
                .padding(
                    start = 40.dp,
                    end = TelevisionDimensions.SafeHorizontal,
                    top = TelevisionDimensions.SafeTop,
                    bottom = TelevisionDimensions.SafeBottom,
                )
                .focusGroup()
                .focusRestorer()
                .onPreviewKeyEvent { event ->
                    val nativeEvent = event.nativeKeyEvent
                    if (
                        nativeEvent.action == KeyEvent.ACTION_DOWN &&
                        nativeEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    ) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
        ) {
            TelevisionFocusSurface(
                onClick = onDismiss,
                focusRequester = backFocus,
                scaleTo = 1.12f,
                restingAlpha = 0.46f,
                modifier = Modifier
                    .size(48.dp)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = selectedFocus
                    },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close ${row.label}",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(22.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = row.label,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(
                    items = row.choices,
                    key = SettingChoiceOption::key,
                ) { option ->
                    val optionIndex = row.choices.indexOf(option)
                    TelevisionFocusSurface(
                        onClick = { onSelect(option) },
                        focusRequester = if (optionIndex == initialIndex) selectedFocus else null,
                        scaleTo = 1f,
                        restingAlpha = if (option.selected) 0.78f else 0.46f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                                if (optionIndex == 0) {
                                    up = backFocus
                                }
                                if (optionIndex == row.choices.lastIndex) {
                                    down = FocusRequester.Cancel
                                }
                            },
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = if (focused) 8.dp.toPx() else 0f
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (option.selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    checked: Boolean,
    focused: Boolean,
) {
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(15.dp)
            .clip(CircleShape)
            .background(
                if (checked) {
                    TelevisionColors.Paper
                } else {
                    TelevisionColors.Paper.copy(alpha = if (focused) 0.28f else 0.18f)
                },
            )
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(if (checked) TelevisionColors.Black else TelevisionColors.Paper),
        )
    }
}

private fun settingsRows(
    section: SettingsSection,
    state: TelevisionSettingsState,
    update: ((ClientSettings) -> ClientSettings) -> Unit,
    onSignOut: () -> Unit,
    onChangeServer: () -> Unit,
    onForgetServer: () -> Unit,
    onSwitchProfile: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshLibraries: () -> Unit,
    onQuickConnect: () -> Unit,
    onClearArtworkCache: () -> Unit,
): List<SettingRowModel> {
    val settings = state.settings
    return when (section) {
        SettingsSection.Playback -> listOf(
            choiceRow(
                key = "streaming-bitrate",
                label = "Maximum streaming bitrate",
                current = settings.maxStreamingBitrateMbps,
                values = listOf<Int?>(null, 5, 10, 20, 40, 80, 120),
                display = { it?.let { bitrate -> "$bitrate Mbps" } ?: "Auto · 120 Mbps" },
            ) { update { current -> current.copy(maxStreamingBitrateMbps = it) } },
            choiceRow(
                key = "refresh-rate",
                label = "Refresh rate switching",
                current = settings.refreshRateSwitching,
                values = listOf("Disabled", "Match video", "Always"),
            ) { update { current -> current.copy(refreshRateSwitching = it) } },
            toggleRow("autoplay", "Auto-play next episode", settings.autoplayNextEpisode) {
                update { it.copy(autoplayNextEpisode = !it.autoplayNextEpisode) }
            },
            toggleRow("skip-intro", "Skip intro prompt", settings.skipIntroPrompt) {
                update { it.copy(skipIntroPrompt = !it.skipIntroPrompt) }
            },
            choiceRow(
                key = "resume",
                label = "Resume behavior",
                current = settings.resumeBehavior,
                values = listOf("Ask", "Resume", "Restart"),
            ) { update { current -> current.copy(resumeBehavior = it) } },
            choiceRow(
                key = "seek",
                label = "Seek interval",
                current = settings.seekIntervalSeconds,
                values = listOf(5, 10, 15, 30, 60),
                display = { "$it seconds" },
            ) { update { current -> current.copy(seekIntervalSeconds = it) } },
            toggleRow("remember-speed", "Remember playback speed", settings.rememberPlaybackSpeed) {
                update { it.copy(rememberPlaybackSpeed = !it.rememberPlaybackSpeed) }
            },
        )

        SettingsSection.Video -> listOf(
            valueRow("player-core", "Player core", "mpv ${BuildConfig.MPV_VERSION}"),
            choiceRow(
                key = "rendering-profile",
                label = "Rendering profile",
                current = settings.renderingProfile,
                values = listOf("Fast", "Balanced", "Quality"),
            ) { update { current -> current.copy(renderingProfile = it) } },
            valueRow("video-output", "Video output", "GPU"),
            choiceRow(
                key = "hardware-decoding",
                label = "Hardware decoding",
                current = settings.hardwareDecoding,
                values = listOf("MediaCodec copy", "MediaCodec", "Software"),
            ) { update { current -> current.copy(hardwareDecoding = it) } },
            choiceRow(
                key = "hardware-codecs",
                label = "Hardware codecs",
                current = settings.hardwareCodecs,
                values = listOf("Automatic", "H.264 / HEVC", "AV1", "Disabled"),
            ) { update { current -> current.copy(hardwareCodecs = it) } },
            choiceRow(
                key = "hdr",
                label = "HDR handling",
                current = settings.hdrMode,
                values = listOf("Automatic", "Passthrough", "Tone map", "Off"),
            ) { update { current -> current.copy(hdrMode = it) } },
            choiceRow(
                key = "tone-map",
                label = "Tone mapping",
                current = settings.toneMapping,
                values = listOf("Automatic", "BT.2390", "Reinhard", "Mobius", "Off"),
            ) { update { current -> current.copy(toneMapping = it) } },
            choiceRow(
                key = "deinterlace",
                label = "Deinterlacing",
                current = settings.deinterlaceMode,
                values = listOf("Automatic", "On", "Off"),
            ) { update { current -> current.copy(deinterlaceMode = it) } },
            toggleRow("interpolation", "Frame interpolation", settings.frameInterpolation) {
                update { it.copy(frameInterpolation = !it.frameInterpolation) }
            },
        )

        SettingsSection.Audio -> listOf(
            valueRow("audio-output", "Audio output", "Android AudioTrack"),
            languageRow(
                key = "audio-language",
                label = "Preferred audio language",
                current = settings.preferredAudioLanguage,
            ) { update { current -> current.copy(preferredAudioLanguage = it) } },
            toggleRow("remember-series-audio", "Keep audio language for series", settings.rememberSeriesAudio) {
                update { it.copy(rememberSeriesAudio = !it.rememberSeriesAudio) }
            },
            toggleRow("pitch", "Pitch correction", settings.pitchCorrection) {
                update { it.copy(pitchCorrection = !it.pitchCorrection) }
            },
            toggleRow("downmix", "Downmix to stereo", settings.downmixStereo) {
                update { it.copy(downmixStereo = !it.downmixStereo) }
            },
            toggleRow("ac3", "Dolby Digital bitstream", settings.dolbyDigitalPassthrough) {
                update { it.copy(dolbyDigitalPassthrough = !it.dolbyDigitalPassthrough) }
            },
            toggleRow("eac3", "Dolby Digital Plus bitstream", settings.dolbyDigitalPlusPassthrough) {
                update { it.copy(dolbyDigitalPlusPassthrough = !it.dolbyDigitalPlusPassthrough) }
            },
            toggleRow("dts", "DTS bitstream", settings.dtsPassthrough) {
                update { it.copy(dtsPassthrough = !it.dtsPassthrough) }
            },
        )

        SettingsSection.Subtitles -> listOf(
            valueRow("subtitle-renderer", "Renderer", "libass"),
            languageRow(
                key = "subtitle-language",
                label = "Preferred subtitle language",
                current = settings.preferredSubtitleLanguage,
            ) { update { current -> current.copy(preferredSubtitleLanguage = it) } },
            choiceRow(
                key = "subtitle-mode",
                label = "Subtitle mode",
                current = settings.subtitleMode,
                values = listOf("Smart", "Always", "Only forced", "None"),
            ) { update { current -> current.copy(subtitleMode = it) } },
            choiceRow(
                key = "burn-subtitles",
                label = "Burn subtitles",
                current = settings.burnSubtitles,
                values = listOf("Automatic", "Only image formats", "Always", "Never"),
            ) { update { current -> current.copy(burnSubtitles = it) } },
            choiceRow(
                key = "subtitle-size",
                label = "Text size",
                current = settings.subtitleSizePercent,
                values = listOf(75, 90, 100, 115, 130, 150),
                display = { "$it%" },
            ) { update { current -> current.copy(subtitleSizePercent = it) } },
            choiceRow(
                key = "subtitle-color",
                label = "Text color",
                current = settings.subtitleColor,
                values = listOf("White", "Yellow", "Grey"),
            ) { update { current -> current.copy(subtitleColor = it) } },
            choiceRow(
                key = "subtitle-stroke",
                label = "Text stroke",
                current = settings.subtitleStroke,
                values = listOf("Off", "Light", "Medium", "Heavy"),
            ) { update { current -> current.copy(subtitleStroke = it) } },
            toggleRow("bold", "Bold text", settings.boldSubtitles) {
                update { it.copy(boldSubtitles = !it.boldSubtitles) }
            },
            toggleRow("subtitle-scale", "Scale with window", settings.scaleSubtitlesWithWindow) {
                update { it.copy(scaleSubtitlesWithWindow = !it.scaleSubtitlesWithWindow) }
            },
            toggleRow("video-margins", "Use video margins", settings.useVideoMargins) {
                update { it.copy(useVideoMargins = !it.useVideoMargins) }
            },
            toggleRow("pgs", "PGS direct play", settings.pgsDirectPlay) {
                update { it.copy(pgsDirectPlay = !it.pgsDirectPlay) }
            },
            choiceRow(
                key = "ass-ssa",
                label = "ASS and SSA direct play",
                current = settings.assSsaDirectPlay,
                values = listOf("Experimental", "Enabled", "Disabled"),
            ) { update { current -> current.copy(assSsaDirectPlay = it) } },
        )

        SettingsSection.Interface -> listOf(
            choiceRow(
                key = "display-language",
                label = "Display language",
                current = settings.displayLanguage,
                values = listOf("English", "Vietnamese", "Japanese", "French", "German"),
            ) { update { current -> current.copy(displayLanguage = it) } },
            choiceRow(
                key = "interface-scale",
                label = "Interface scale",
                current = settings.interfaceScale,
                values = listOf("Compact", "Comfortable", "Large"),
            ) { update { current -> current.copy(interfaceScale = it) } },
            choiceRow(
                key = "theme",
                label = "Theme",
                current = settings.theme,
                values = listOf("Dark", "System"),
            ) { update { current -> current.copy(theme = it) } },
            toggleRow("backdrops", "Backdrop images", settings.backdropImages) {
                update { it.copy(backdropImages = !it.backdropImages) }
            },
            choiceRow(
                key = "backdrop-rotation",
                label = "Backdrop rotation",
                current = settings.backdropRotationSeconds,
                values = listOf(10, 20, 30, 45, 60),
                display = { "$it seconds" },
            ) { update { current -> current.copy(backdropRotationSeconds = it) } },
            toggleRow("watched", "Watched indicators", settings.watchedIndicators) {
                update { it.copy(watchedIndicators = !it.watchedIndicators) }
            },
            toggleRow("clock", "Show clock", settings.clockInOsd) {
                update { it.copy(clockInOsd = !it.clockInOsd) }
            },
            toggleRow("last-library", "Remember last library", settings.rememberLastLibrary) {
                update { it.copy(rememberLastLibrary = !it.rememberLastLibrary) }
            },
            toggleRow("cache-home", "Cache home content", settings.cacheHomeContent) {
                update { it.copy(cacheHomeContent = !it.cacheHomeContent) }
            },
        )

        SettingsSection.Network -> listOf(
            choiceRow(
                key = "remote-bitrate",
                label = "Maximum remote bitrate",
                current = settings.maxRemoteBitrateMbps,
                values = listOf(5, 10, 20, 40, 80, 120),
                display = { "Auto · $it Mbps" },
            ) { update { current -> current.copy(maxRemoteBitrateMbps = it) } },
            toggleRow("stream-cache", "Stream cache", settings.networkCacheEnabled) {
                update { it.copy(networkCacheEnabled = !it.networkCacheEnabled) }
            },
            choiceRow(
                key = "cache-duration",
                label = "Cache duration",
                current = settings.cacheDurationSeconds,
                values = listOf(10, 20, 30, 60, 120),
                display = { "$it seconds" },
            ) { update { current -> current.copy(cacheDurationSeconds = it) } },
            choiceRow(
                key = "read-ahead",
                label = "Read ahead",
                current = settings.readAheadSeconds,
                values = listOf(5, 10, 20, 30, 60),
                display = { "$it seconds" },
            ) { update { current -> current.copy(readAheadSeconds = it) } },
            choiceRow(
                key = "forward-cache",
                label = "Forward cache limit",
                current = settings.forwardCacheMiB,
                values = listOf(32, 64, 128, 256, 512),
                display = { "$it MiB" },
            ) { update { current -> current.copy(forwardCacheMiB = it) } },
            choiceRow(
                key = "backward-cache",
                label = "Backward cache limit",
                current = settings.backwardCacheMiB,
                values = listOf(16, 32, 64, 128, 256),
                display = { "$it MiB" },
            ) { update { current -> current.copy(backwardCacheMiB = it) } },
            choiceRow(
                key = "resume-buffer",
                label = "Resume buffer",
                current = settings.resumeBufferSeconds,
                values = listOf(0, 1, 2, 3, 5),
                display = { "$it ${if (it == 1) "second" else "seconds"}" },
            ) { update { current -> current.copy(resumeBufferSeconds = it) } },
            choiceRow(
                key = "network-timeout",
                label = "Network timeout",
                current = settings.networkTimeoutSeconds,
                values = listOf(5, 10, 15, 30, 60),
                display = { "$it seconds" },
            ) { update { current -> current.copy(networkTimeoutSeconds = it) } },
            toggleRow("tls", "Verify TLS certificates", settings.verifyTlsCertificates) {
                update { it.copy(verifyTlsCertificates = !it.verifyTlsCertificates) }
            },
            valueRow("artwork-cache-limit", "Artwork cache", "Automatic · up to 250 MiB"),
            actionRow(
                "clear-artwork-cache",
                "Clear artwork cache",
                if (state.clearingArtworkCache) {
                    "Clearing…"
                } else {
                    "Clear · ${state.artworkCacheSize}"
                },
                onClearArtworkCache,
            ),
        )

        SettingsSection.Screensaver -> listOf(
            choiceRow(
                key = "screensaver-timeout",
                label = "Start screensaver",
                current = settings.screensaverTimeoutMinutes,
                values = listOf(0, 5, 10, 20, 30),
                display = { if (it == 0) "Never" else "After $it minutes" },
            ) { update { current -> current.copy(screensaverTimeoutMinutes = it) } },
            choiceRow(
                key = "screensaver-content",
                label = "Content",
                current = settings.screensaverContent,
                values = listOf("All libraries", "Movies", "Shows", "Music"),
            ) { update { current -> current.copy(screensaverContent = it) } },
            choiceRow(
                key = "screensaver-duration",
                label = "Image duration",
                current = settings.screensaverImageDurationSeconds,
                values = listOf(10, 20, 30, 45, 60),
                display = { "$it seconds" },
            ) { update { current -> current.copy(screensaverImageDurationSeconds = it) } },
            toggleRow("screensaver-shuffle", "Shuffle images", settings.screensaverShuffle) {
                update { it.copy(screensaverShuffle = !it.screensaverShuffle) }
            },
            toggleRow("screensaver-repeats", "Avoid repeats", settings.screensaverAvoidRepeats) {
                update { it.copy(screensaverAvoidRepeats = !it.screensaverAvoidRepeats) }
            },
            toggleRow("screensaver-clock", "Show clock", settings.screensaverClock) {
                update { it.copy(screensaverClock = !it.screensaverClock) }
            },
        )

        SettingsSection.Server -> listOf(
            valueRow(
                key = "server-name",
                label = "Server",
                value = state.serverName?.let { name ->
                    state.serverVersion?.let { "$name · $it" } ?: name
                } ?: "Unavailable",
            ),
            valueRow(
                key = "server-address",
                label = "Address",
                value = state.serverUrl.ifBlank { "Not connected" },
            ),
            valueRow(
                key = "connection",
                label = "Connection",
                value = when {
                    state.serverUrl.isBlank() -> "Offline"
                    state.serverUrl.startsWith("https://", ignoreCase = true) -> "Secure"
                    else -> "Local"
                },
            ),
            valueRow("device-name", "Device name", "Android TV"),
            actionRow(
                key = "test-connection",
                label = "Test connection",
                value = when {
                    state.testingConnection -> "Testing…"
                    state.connectionMessage != null -> state.connectionMessage
                    else -> "Test"
                },
                onClick = onTestConnection,
            ),
            actionRow("refresh-libraries", "Refresh libraries", "Refresh", onRefreshLibraries),
            actionRow("change-server", "Change server", "Choose", onChangeServer),
            actionRow("forget-server", "Forget this server", "Forget", onForgetServer),
        )

        SettingsSection.Account -> listOf(
            valueRow(
                key = "profile",
                label = "Profile",
                value = state.userName.ifBlank { "Unknown" },
            ),
            actionRow("switch-profile", "Switch profile", "Switch", onSwitchProfile),
            toggleRow("kids-mode", "Kids mode", settings.kidsMode) {
                update { it.copy(kidsMode = !it.kidsMode) }
            },
            valueRow("login-method", "Login method", "Password"),
            actionRow(
                key = "quick-connect",
                label = "Replace session",
                value = when {
                    state.quickConnectLoading -> "Generating…"
                    state.quickConnectCode != null -> state.quickConnectCode
                    else -> "Quick Connect"
                },
                onClick = onQuickConnect,
            ),
            actionRow("sign-out", "Sign out", "Sign out", onSignOut),
        )

        SettingsSection.About -> listOf(
            valueRow(
                key = "application",
                label = "Application",
                value = "Shoumei Player",
            ),
            valueRow(
                key = "application-version",
                label = "Version",
                value = BuildConfig.VERSION_NAME,
            ),
            valueRow(
                key = "application-build",
                label = "Build",
                value = BuildConfig.VERSION_CODE.toString(),
            ),
            valueRow(
                key = "release-tag",
                label = "Release tag",
                value = BuildConfig.RELEASE_TAG.ifBlank { "Development build" },
            ),
            valueRow(
                key = "mpv-tag",
                label = "mpv source tag",
                value = "v${BuildConfig.MPV_VERSION}",
            ),
            valueRow(
                key = "platform",
                label = "Platform",
                value = "Android TV",
            ),
        )
    }
}

private fun valueRow(
    key: String,
    label: String,
    value: String,
) = SettingRowModel(
    key = key,
    label = label,
    value = value,
    control = SettingControl.Value,
)

private fun actionRow(
    key: String,
    label: String,
    value: String,
    onClick: () -> Unit,
) = SettingRowModel(
    key = key,
    label = label,
    value = value,
    control = SettingControl.Choice,
    onClick = onClick,
)

private fun toggleRow(
    key: String,
    label: String,
    value: Boolean,
    onClick: () -> Unit,
) = SettingRowModel(
    key = key,
    label = label,
    value = if (value) "On" else "Off",
    control = SettingControl.Toggle,
    checked = value,
    onClick = onClick,
)

private fun languageRow(
    key: String,
    label: String,
    current: String?,
    onSelect: (String?) -> Unit,
) = choiceRow(
    key = key,
    label = label,
    current = current,
    values = listOf(null, "English", "Japanese", "Vietnamese", "French", "German"),
    display = { it ?: "Server default" },
    onSelect = onSelect,
)

private fun <T> choiceRow(
    key: String,
    label: String,
    current: T,
    values: List<T>,
    display: (T) -> String = { it.toString() },
    onSelect: (T) -> Unit,
): SettingRowModel {
    return SettingRowModel(
        key = key,
        label = label,
        value = display(current),
        control = SettingControl.Choice,
        choices = values.mapIndexed { index, value ->
            SettingChoiceOption(
                key = "$key-$index",
                label = display(value),
                selected = value == current,
                onSelect = { onSelect(value) },
            )
        },
    )
}
