package com.maik205.shoumeiplayer.ui.television.screens.settings

import androidx.annotation.StringRes
import com.maik205.shoumeiplayer.BuildConfig
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.AppTheme
import com.maik205.shoumeiplayer.domain.settings.AssSsaDirectPlay
import com.maik205.shoumeiplayer.domain.settings.BurnSubtitles
import com.maik205.shoumeiplayer.domain.settings.DeinterlaceMode
import com.maik205.shoumeiplayer.domain.settings.DisplayLanguage
import com.maik205.shoumeiplayer.domain.settings.HardwareCodecs
import com.maik205.shoumeiplayer.domain.settings.HardwareDecoding
import com.maik205.shoumeiplayer.domain.settings.HdrMode
import com.maik205.shoumeiplayer.domain.settings.InterfaceScale
import com.maik205.shoumeiplayer.domain.settings.PlaybackBackend
import com.maik205.shoumeiplayer.domain.settings.RefreshRateSwitching
import com.maik205.shoumeiplayer.domain.settings.RenderingProfile
import com.maik205.shoumeiplayer.domain.settings.ResumeBehavior
import com.maik205.shoumeiplayer.domain.settings.ScreensaverContent
import com.maik205.shoumeiplayer.domain.settings.SettingsChoices
import com.maik205.shoumeiplayer.domain.settings.SubtitleColor
import com.maik205.shoumeiplayer.domain.settings.SubtitleMode
import com.maik205.shoumeiplayer.domain.settings.SubtitleStroke
import com.maik205.shoumeiplayer.domain.settings.ToneMapping
import com.maik205.shoumeiplayer.domain.settings.TlsTrustSource

internal fun settingsRows(
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
    val rows = when (section) {
        SettingsSection.Playback -> listOf(
            choiceRow(
                key = "playback-backend",
                labelRes = R.string.tv_settings_playback_backend,
                current = settings.playbackBackend,
                values = PlaybackBackend.entries,
                display = PlaybackBackend::label,
            ) { update { current -> current.copy(playbackBackend = it) } },
            choiceRow(
                key = "streaming-bitrate",
                labelRes = R.string.tv_settings_maximum_streaming_bitrate,
                current = settings.maxStreamingBitrateMbps,
                values = SettingsChoices.streamingBitratesMbps,
                display = { it?.let { bitrate -> "$bitrate Mbps" } ?: "Auto · 120 Mbps" },
            ) { update { current -> current.copy(maxStreamingBitrateMbps = it) } },
            choiceRow(
                key = "refresh-rate",
                labelRes = R.string.tv_settings_refresh_rate_switching,
                current = settings.refreshRateSwitching,
                values = RefreshRateSwitching.entries,
                display = RefreshRateSwitching::label,
                requiredCapability = SettingCapability.RefreshRateSwitching,
            ) { update { current -> current.copy(refreshRateSwitching = it) } },
            toggleRow("autoplay", R.string.tv_settings_auto_play_next_episode, settings.autoplayNextEpisode) {
                update { it.copy(autoplayNextEpisode = !it.autoplayNextEpisode) }
            },
            toggleRow("skip-intro", R.string.tv_settings_skip_intro_prompt, settings.skipIntroPrompt) {
                update { it.copy(skipIntroPrompt = !it.skipIntroPrompt) }
            },
            choiceRow(
                key = "resume",
                labelRes = R.string.tv_settings_resume_behavior,
                current = settings.resumeBehavior,
                values = ResumeBehavior.entries.filterNot { it == ResumeBehavior.Always },
                display = ResumeBehavior::label,
            ) { update { current -> current.copy(resumeBehavior = it) } },
            choiceRow(
                key = "seek",
                labelRes = R.string.tv_settings_seek_interval,
                current = settings.seekIntervalSeconds,
                values = SettingsChoices.seekIntervalsSeconds,
                display = { "$it seconds" },
            ) { update { current -> current.copy(seekIntervalSeconds = it) } },
            toggleRow("remember-speed", R.string.tv_settings_remember_playback_speed, settings.rememberPlaybackSpeed) {
                update { it.copy(rememberPlaybackSpeed = !it.rememberPlaybackSpeed) }
            },
        )

        SettingsSection.Video -> listOf(
            valueRow("player-core", R.string.tv_settings_player_core, "mpv ${BuildConfig.MPV_VERSION}"),
            choiceRow(
                key = "rendering-profile",
                labelRes = R.string.tv_settings_rendering_profile,
                current = settings.renderingProfile,
                values = RenderingProfile.entries,
                display = RenderingProfile::label,
            ) { update { current -> current.copy(renderingProfile = it) } },
            valueRow("video-output", R.string.tv_settings_video_output, "GPU"),
            choiceRow(
                key = "hardware-decoding",
                labelRes = R.string.tv_settings_hardware_decoding,
                current = settings.hardwareDecoding,
                values = HardwareDecoding.entries,
                display = HardwareDecoding::label,
            ) { update { current -> current.copy(hardwareDecoding = it) } },
            choiceRow(
                key = "hardware-codecs",
                labelRes = R.string.tv_settings_hardware_codecs,
                current = settings.hardwareCodecs,
                values = HardwareCodecs.entries,
                display = HardwareCodecs::label,
            ) { update { current -> current.copy(hardwareCodecs = it) } },
            choiceRow(
                key = "hdr",
                labelRes = R.string.tv_settings_hdr_handling,
                current = settings.hdrMode,
                values = HdrMode.entries,
                display = HdrMode::label,
            ) { update { current -> current.copy(hdrMode = it) } },
            choiceRow(
                key = "tone-map",
                labelRes = R.string.tv_settings_tone_mapping,
                current = settings.toneMapping,
                values = ToneMapping.entries,
                display = ToneMapping::label,
            ) { update { current -> current.copy(toneMapping = it) } },
            choiceRow(
                key = "deinterlace",
                labelRes = R.string.tv_settings_deinterlacing,
                current = settings.deinterlaceMode,
                values = DeinterlaceMode.entries,
                display = DeinterlaceMode::label,
            ) { update { current -> current.copy(deinterlaceMode = it) } },
            toggleRow("interpolation", R.string.tv_settings_frame_interpolation, settings.frameInterpolation) {
                update { it.copy(frameInterpolation = !it.frameInterpolation) }
            },
        )

        SettingsSection.Audio -> listOf(
            valueRow("audio-output", R.string.tv_settings_audio_output, "Android AudioTrack"),
            languageRow(
                key = "audio-language",
                labelRes = R.string.tv_settings_preferred_audio_language,
                current = settings.preferredAudioLanguage,
            ) { update { current -> current.copy(preferredAudioLanguage = it) } },
            toggleRow("remember-series-audio", R.string.tv_settings_keep_audio_language_for_series, settings.rememberSeriesAudio) {
                update { it.copy(rememberSeriesAudio = !it.rememberSeriesAudio) }
            },
            toggleRow("pitch", R.string.tv_settings_pitch_correction, settings.pitchCorrection) {
                update { it.copy(pitchCorrection = !it.pitchCorrection) }
            },
            toggleRow("downmix", R.string.tv_settings_downmix_to_stereo, settings.downmixStereo) {
                update { it.copy(downmixStereo = !it.downmixStereo) }
            },
            toggleRow(
                "ac3",
                R.string.tv_settings_dolby_digital_bitstream,
                settings.dolbyDigitalPassthrough,
                SettingCapability.DolbyDigitalPassthrough,
            ) {
                update { it.copy(dolbyDigitalPassthrough = !it.dolbyDigitalPassthrough) }
            },
            toggleRow(
                "eac3",
                R.string.tv_settings_dolby_digital_plus_bitstream,
                settings.dolbyDigitalPlusPassthrough,
                SettingCapability.DolbyDigitalPlusPassthrough,
            ) {
                update { it.copy(dolbyDigitalPlusPassthrough = !it.dolbyDigitalPlusPassthrough) }
            },
            toggleRow(
                "dts",
                R.string.tv_settings_dts_bitstream,
                settings.dtsPassthrough,
                SettingCapability.DtsPassthrough,
            ) {
                update { it.copy(dtsPassthrough = !it.dtsPassthrough) }
            },
        )

        SettingsSection.Subtitles -> listOf(
            valueRow("subtitle-renderer", R.string.tv_settings_renderer, "libass"),
            languageRow(
                key = "subtitle-language",
                labelRes = R.string.tv_settings_preferred_subtitle_language,
                current = settings.preferredSubtitleLanguage,
            ) { update { current -> current.copy(preferredSubtitleLanguage = it) } },
            choiceRow(
                key = "subtitle-mode",
                labelRes = R.string.tv_settings_subtitle_mode,
                current = settings.subtitleMode,
                values = SubtitleMode.entries,
                display = SubtitleMode::label,
            ) { update { current -> current.copy(subtitleMode = it) } },
            choiceRow(
                key = "burn-subtitles",
                labelRes = R.string.tv_settings_burn_subtitles,
                current = settings.burnSubtitles,
                values = BurnSubtitles.entries,
                display = BurnSubtitles::label,
            ) { update { current -> current.copy(burnSubtitles = it) } },
            choiceRow(
                key = "subtitle-size",
                labelRes = R.string.tv_settings_text_size,
                current = settings.subtitleSizePercent,
                values = SettingsChoices.subtitleSizesPercent,
                display = { "$it%" },
            ) { update { current -> current.copy(subtitleSizePercent = it) } },
            choiceRow(
                key = "subtitle-color",
                labelRes = R.string.tv_settings_text_color,
                current = settings.subtitleColor,
                values = SubtitleColor.entries,
                display = SubtitleColor::label,
            ) { update { current -> current.copy(subtitleColor = it) } },
            choiceRow(
                key = "subtitle-stroke",
                labelRes = R.string.tv_settings_text_stroke,
                current = settings.subtitleStroke,
                values = SubtitleStroke.entries,
                display = SubtitleStroke::label,
            ) { update { current -> current.copy(subtitleStroke = it) } },
            toggleRow("bold", R.string.tv_settings_bold_text, settings.boldSubtitles) {
                update { it.copy(boldSubtitles = !it.boldSubtitles) }
            },
            toggleRow("subtitle-scale", R.string.tv_settings_scale_with_window, settings.scaleSubtitlesWithWindow) {
                update { it.copy(scaleSubtitlesWithWindow = !it.scaleSubtitlesWithWindow) }
            },
            toggleRow("video-margins", R.string.tv_settings_use_video_margins, settings.useVideoMargins) {
                update { it.copy(useVideoMargins = !it.useVideoMargins) }
            },
            toggleRow("pgs", R.string.tv_settings_pgs_direct_play, settings.pgsDirectPlay) {
                update { it.copy(pgsDirectPlay = !it.pgsDirectPlay) }
            },
            choiceRow(
                key = "ass-ssa",
                labelRes = R.string.tv_settings_ass_and_ssa_direct_play,
                current = settings.assSsaDirectPlay,
                values = AssSsaDirectPlay.entries,
                display = AssSsaDirectPlay::label,
            ) { update { current -> current.copy(assSsaDirectPlay = it) } },
        )

        SettingsSection.Interface -> listOf(
            choiceRow(
                key = "display-language",
                labelRes = R.string.tv_settings_display_language,
                current = settings.displayLanguage,
                values = DisplayLanguage.entries,
                display = DisplayLanguage::label,
            ) { update { current -> current.copy(displayLanguage = it) } },
            choiceRow(
                key = "interface-scale",
                labelRes = R.string.tv_settings_interface_scale,
                current = settings.interfaceScale,
                values = InterfaceScale.entries,
                display = InterfaceScale::label,
            ) { update { current -> current.copy(interfaceScale = it) } },
            choiceRow(
                key = "theme",
                labelRes = R.string.tv_settings_theme,
                current = settings.theme,
                values = AppTheme.entries,
                display = AppTheme::label,
            ) { update { current -> current.copy(theme = it) } },
            toggleRow("backdrops", R.string.tv_settings_backdrop_images, settings.backdropImages) {
                update { it.copy(backdropImages = !it.backdropImages) }
            },
            choiceRow(
                key = "backdrop-rotation",
                labelRes = R.string.tv_settings_backdrop_rotation,
                current = settings.backdropRotationSeconds,
                values = SettingsChoices.backdropRotationSeconds,
                display = { "$it seconds" },
            ) { update { current -> current.copy(backdropRotationSeconds = it) } },
            toggleRow("watched", R.string.tv_settings_watched_indicators, settings.watchedIndicators) {
                update { it.copy(watchedIndicators = !it.watchedIndicators) }
            },
            toggleRow("clock", R.string.tv_settings_show_clock, settings.clockInOsd) {
                update { it.copy(clockInOsd = !it.clockInOsd) }
            },
            toggleRow("last-library", R.string.tv_settings_remember_last_library, settings.rememberLastLibrary) {
                update { it.copy(rememberLastLibrary = !it.rememberLastLibrary) }
            },
            toggleRow("cache-home", R.string.tv_settings_cache_home_content, settings.cacheHomeContent) {
                update { it.copy(cacheHomeContent = !it.cacheHomeContent) }
            },
        )

        SettingsSection.Network -> listOf(
            choiceRow(
                key = "remote-bitrate",
                labelRes = R.string.tv_settings_maximum_remote_bitrate,
                current = settings.maxRemoteBitrateMbps,
                values = SettingsChoices.remoteBitratesMbps,
                display = { "Auto · $it Mbps" },
            ) { update { current -> current.copy(maxRemoteBitrateMbps = it) } },
            toggleRow("stream-cache", R.string.tv_settings_stream_cache, settings.networkCacheEnabled) {
                update { it.copy(networkCacheEnabled = !it.networkCacheEnabled) }
            },
            choiceRow(
                key = "cache-duration",
                labelRes = R.string.tv_settings_cache_duration,
                current = settings.cacheDurationSeconds,
                values = SettingsChoices.cacheDurationsSeconds,
                display = { "$it seconds" },
            ) { update { current -> current.copy(cacheDurationSeconds = it) } },
            choiceRow(
                key = "read-ahead",
                labelRes = R.string.tv_settings_read_ahead,
                current = settings.readAheadSeconds,
                values = SettingsChoices.readAheadSeconds,
                display = { "$it seconds" },
            ) { update { current -> current.copy(readAheadSeconds = it) } },
            choiceRow(
                key = "forward-cache",
                labelRes = R.string.tv_settings_forward_cache_limit,
                current = settings.forwardCacheMiB,
                values = SettingsChoices.forwardCacheMiB,
                display = { "$it MiB" },
            ) { update { current -> current.copy(forwardCacheMiB = it) } },
            choiceRow(
                key = "backward-cache",
                labelRes = R.string.tv_settings_backward_cache_limit,
                current = settings.backwardCacheMiB,
                values = SettingsChoices.backwardCacheMiB,
                display = { "$it MiB" },
            ) { update { current -> current.copy(backwardCacheMiB = it) } },
            choiceRow(
                key = "resume-buffer",
                labelRes = R.string.tv_settings_resume_buffer,
                current = settings.resumeBufferSeconds,
                values = SettingsChoices.resumeBufferSeconds,
                display = { "$it ${if (it == 1) "second" else "seconds"}" },
            ) { update { current -> current.copy(resumeBufferSeconds = it) } },
            choiceRow(
                key = "network-timeout",
                labelRes = R.string.tv_settings_network_timeout,
                current = settings.networkTimeoutSeconds,
                values = SettingsChoices.networkTimeoutSeconds,
                display = { "$it seconds" },
            ) { update { current -> current.copy(networkTimeoutSeconds = it) } },
            toggleRow("tls", R.string.tv_settings_verify_tls_certificates, settings.verifyTlsCertificates) {
                update { it.copy(verifyTlsCertificates = !it.verifyTlsCertificates) }
            },
            choiceRow(
                key = "tls-trust-source",
                labelRes = R.string.tv_settings_tls_trust_source,
                current = settings.tlsTrustSource,
                values = TlsTrustSource.entries,
                display = TlsTrustSource::label,
            ) { update { current -> current.copy(tlsTrustSource = it) } },
            valueRow("artwork-cache-limit", R.string.tv_settings_artwork_cache, "Automatic · up to 250 MiB"),
            actionRow(
                "clear-artwork-cache",
                R.string.tv_settings_clear_artwork_cache,
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
                labelRes = R.string.tv_settings_start_screensaver,
                current = settings.screensaverTimeoutMinutes,
                values = SettingsChoices.screensaverTimeoutMinutes,
                display = { if (it == 0) "Never" else "After $it minutes" },
            ) { update { current -> current.copy(screensaverTimeoutMinutes = it) } },
            choiceRow(
                key = "screensaver-content",
                labelRes = R.string.tv_settings_content,
                current = settings.screensaverContent,
                values = ScreensaverContent.entries,
                display = ScreensaverContent::label,
            ) { update { current -> current.copy(screensaverContent = it) } },
            choiceRow(
                key = "screensaver-duration",
                labelRes = R.string.tv_settings_image_duration,
                current = settings.screensaverImageDurationSeconds,
                values = SettingsChoices.screensaverImageDurationSeconds,
                display = { "$it seconds" },
            ) { update { current -> current.copy(screensaverImageDurationSeconds = it) } },
            toggleRow("screensaver-shuffle", R.string.tv_settings_shuffle_images, settings.screensaverShuffle) {
                update { it.copy(screensaverShuffle = !it.screensaverShuffle) }
            },
            toggleRow("screensaver-repeats", R.string.tv_settings_avoid_repeats, settings.screensaverAvoidRepeats) {
                update { it.copy(screensaverAvoidRepeats = !it.screensaverAvoidRepeats) }
            },
            toggleRow("screensaver-clock", R.string.tv_settings_show_clock, settings.screensaverClock) {
                update { it.copy(screensaverClock = !it.screensaverClock) }
            },
        )

        SettingsSection.Server -> listOf(
            valueRow(
                key = "server-name",
                labelRes = R.string.tv_settings_server,
                value = state.serverName?.let { name ->
                    state.serverVersion?.let { "$name · $it" } ?: name
                } ?: "Unavailable",
            ),
            valueRow(
                key = "server-address",
                labelRes = R.string.tv_settings_address,
                value = state.serverUrl.ifBlank { "Not connected" },
            ),
            valueRow(
                key = "connection",
                labelRes = R.string.tv_settings_connection,
                value = when {
                    state.serverUrl.isBlank() -> "Offline"
                    state.serverUrl.startsWith("https://", ignoreCase = true) -> "Secure"
                    else -> "Local"
                },
            ),
            valueRow("device-name", R.string.tv_settings_device_name, "Android TV"),
            actionRow(
                key = "test-connection",
                labelRes = R.string.tv_settings_test_connection,
                value = when {
                    state.testingConnection -> "Testing…"
                    state.connectionMessage != null -> state.connectionMessage
                    else -> "Test"
                },
                onClick = onTestConnection,
            ),
            actionRow("refresh-libraries", R.string.tv_settings_refresh_libraries, "Refresh", onRefreshLibraries),
            actionRow("change-server", R.string.tv_settings_change_server, "Choose", onChangeServer),
            actionRow("forget-server", R.string.tv_settings_forget_this_server, "Forget", onForgetServer),
        )

        SettingsSection.Account -> listOf(
            valueRow(
                key = "profile",
                labelRes = R.string.tv_settings_profile,
                value = state.userName.ifBlank { "Unknown" },
            ),
            actionRow("switch-profile", R.string.tv_settings_switch_profile, "Switch", onSwitchProfile),
            toggleRow("kids-mode", R.string.tv_settings_kids_mode, settings.kidsMode) {
                update { it.copy(kidsMode = !it.kidsMode) }
            },
            valueRow("login-method", R.string.tv_settings_login_method, "Password"),
            actionRow(
                key = "quick-connect",
                labelRes = R.string.tv_settings_replace_session,
                value = when {
                    state.quickConnectLoading -> "Generating…"
                    state.quickConnectCode != null -> state.quickConnectCode
                    else -> "Quick Connect"
                },
                onClick = onQuickConnect,
            ),
            actionRow("sign-out", R.string.tv_settings_sign_out, "Sign out", onSignOut),
        )

        SettingsSection.About -> listOf(
            valueRow(
                key = "application",
                labelRes = R.string.tv_settings_application,
                value = "Shoumei Player",
            ),
            valueRow(
                key = "application-version",
                labelRes = R.string.tv_settings_version,
                value = BuildConfig.VERSION_NAME,
            ),
            valueRow(
                key = "application-build",
                labelRes = R.string.tv_settings_build,
                value = BuildConfig.VERSION_CODE.toString(),
            ),
            valueRow(
                key = "release-tag",
                labelRes = R.string.tv_settings_release_tag,
                value = BuildConfig.RELEASE_TAG.ifBlank { "Development build" },
            ),
            valueRow(
                key = "mpv-tag",
                labelRes = R.string.tv_settings_mpv_source_tag,
                value = "v${BuildConfig.MPV_VERSION}",
            ),
            valueRow(
                key = "platform",
                labelRes = R.string.tv_settings_platform,
                value = "Android TV",
            ),
        )
    }
    return rows.filter { row -> state.capabilities.supports(row.requiredCapability) }
}

private fun com.maik205.shoumeiplayer.domain.settings.DevicePlaybackCapabilities.supports(
    capability: SettingCapability?,
): Boolean = when (capability) {
    null -> true
    SettingCapability.RefreshRateSwitching -> supportsRefreshRateSwitching
    SettingCapability.DolbyDigitalPassthrough -> supportsDolbyDigitalPassthrough
    SettingCapability.DolbyDigitalPlusPassthrough -> supportsDolbyDigitalPlusPassthrough
    SettingCapability.DtsPassthrough -> supportsDtsPassthrough
}

private fun valueRow(
    key: String,
    @StringRes labelRes: Int,
    value: String,
) = SettingRowModel(
    key = key,
    labelRes = labelRes,
    value = value,
    control = SettingControl.Value,
)

private fun actionRow(
    key: String,
    @StringRes labelRes: Int,
    value: String,
    onClick: () -> Unit,
) = SettingRowModel(
    key = key,
    labelRes = labelRes,
    value = value,
    control = SettingControl.Choice,
    onClick = onClick,
)

private fun toggleRow(
    key: String,
    @StringRes labelRes: Int,
    value: Boolean,
    requiredCapability: SettingCapability? = null,
    onClick: () -> Unit,
) = SettingRowModel(
    key = key,
    labelRes = labelRes,
    value = if (value) "On" else "Off",
    control = SettingControl.Toggle,
    checked = value,
    requiredCapability = requiredCapability,
    onClick = onClick,
)

private fun languageRow(
    key: String,
    @StringRes labelRes: Int,
    current: String?,
    onSelect: (String?) -> Unit,
) = choiceRow(
    key = key,
    labelRes = labelRes,
    current = current,
    values = SettingsChoices.preferredLanguages,
    display = { it ?: "Server default" },
    onSelect = onSelect,
)

private fun <T> choiceRow(
    key: String,
    @StringRes labelRes: Int,
    current: T,
    values: List<T>,
    display: (T) -> String = { it.toString() },
    requiredCapability: SettingCapability? = null,
    onSelect: (T) -> Unit,
): SettingRowModel {
    return SettingRowModel(
        key = key,
        labelRes = labelRes,
        value = display(current),
        control = SettingControl.Choice,
        requiredCapability = requiredCapability,
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
