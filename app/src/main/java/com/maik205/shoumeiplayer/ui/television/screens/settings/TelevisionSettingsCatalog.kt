package com.maik205.shoumeiplayer.ui.television.screens.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
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
import com.maik205.shoumeiplayer.domain.settings.StoredOption
import com.maik205.shoumeiplayer.domain.settings.SubtitleColor
import com.maik205.shoumeiplayer.domain.settings.SubtitleMode
import com.maik205.shoumeiplayer.domain.settings.SubtitleStroke
import com.maik205.shoumeiplayer.domain.settings.ToneMapping
import com.maik205.shoumeiplayer.domain.settings.TlsTrustSource
import com.maik205.shoumeiplayer.ui.i18n.resolve

@Composable
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
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(playbackBackend = it) } },
            choiceRow(
                key = "streaming-bitrate",
                labelRes = R.string.tv_settings_maximum_streaming_bitrate,
                current = settings.maxStreamingBitrateMbps,
                values = SettingsChoices.streamingBitratesMbps,
                display = {
                    it?.let { bitrate -> stringResource(R.string.tv_value_mbps, bitrate.toString()) }
                        ?: stringResource(R.string.tv_value_auto_mbps, "120")
                },
            ) { update { current -> current.copy(maxStreamingBitrateMbps = it) } },
            choiceRow(
                key = "refresh-rate",
                labelRes = R.string.tv_settings_refresh_rate_switching,
                current = settings.refreshRateSwitching,
                values = RefreshRateSwitching.entries,
                display = { it.localizedLabel() },
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
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(resumeBehavior = it) } },
            choiceRow(
                key = "seek",
                labelRes = R.string.tv_settings_seek_interval,
                current = settings.seekIntervalSeconds,
                values = SettingsChoices.seekIntervalsSeconds,
                display = { stringResource(R.string.tv_value_seconds, it) },
            ) { update { current -> current.copy(seekIntervalSeconds = it) } },
            toggleRow("remember-speed", R.string.tv_settings_remember_playback_speed, settings.rememberPlaybackSpeed) {
                update { it.copy(rememberPlaybackSpeed = !it.rememberPlaybackSpeed) }
            },
        )

        SettingsSection.Video -> listOf(
            valueRow(
                "player-core",
                R.string.tv_settings_player_core,
                stringResource(R.string.tv_settings_mpv) + " " + BuildConfig.MPV_VERSION,
            ),
            choiceRow(
                key = "rendering-profile",
                labelRes = R.string.tv_settings_rendering_profile,
                current = settings.renderingProfile,
                values = RenderingProfile.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(renderingProfile = it) } },
            valueRow("video-output", R.string.tv_settings_video_output, stringResource(R.string.tv_gpu)),
            choiceRow(
                key = "hardware-decoding",
                labelRes = R.string.tv_settings_hardware_decoding,
                current = settings.hardwareDecoding,
                values = HardwareDecoding.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(hardwareDecoding = it) } },
            choiceRow(
                key = "hardware-codecs",
                labelRes = R.string.tv_settings_hardware_codecs,
                current = settings.hardwareCodecs,
                values = HardwareCodecs.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(hardwareCodecs = it) } },
            choiceRow(
                key = "hdr",
                labelRes = R.string.tv_settings_hdr_handling,
                current = settings.hdrMode,
                values = HdrMode.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(hdrMode = it) } },
            choiceRow(
                key = "tone-map",
                labelRes = R.string.tv_settings_tone_mapping,
                current = settings.toneMapping,
                values = ToneMapping.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(toneMapping = it) } },
            choiceRow(
                key = "deinterlace",
                labelRes = R.string.tv_settings_deinterlacing,
                current = settings.deinterlaceMode,
                values = DeinterlaceMode.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(deinterlaceMode = it) } },
            toggleRow("interpolation", R.string.tv_settings_frame_interpolation, settings.frameInterpolation) {
                update { it.copy(frameInterpolation = !it.frameInterpolation) }
            },
        )

        SettingsSection.Audio -> listOf(
            valueRow(
                "audio-output",
                R.string.tv_settings_audio_output,
                stringResource(R.string.tv_android_audio_track),
            ),
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
            valueRow("subtitle-renderer", R.string.tv_settings_renderer, stringResource(R.string.tv_libass)),
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
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(subtitleMode = it) } },
            choiceRow(
                key = "burn-subtitles",
                labelRes = R.string.tv_settings_burn_subtitles,
                current = settings.burnSubtitles,
                values = BurnSubtitles.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(burnSubtitles = it) } },
            choiceRow(
                key = "subtitle-size",
                labelRes = R.string.tv_settings_text_size,
                current = settings.subtitleSizePercent,
                values = SettingsChoices.subtitleSizesPercent,
                display = { stringResource(R.string.tv_value_percent, it) },
            ) { update { current -> current.copy(subtitleSizePercent = it) } },
            choiceRow(
                key = "subtitle-color",
                labelRes = R.string.tv_settings_text_color,
                current = settings.subtitleColor,
                values = SubtitleColor.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(subtitleColor = it) } },
            choiceRow(
                key = "subtitle-stroke",
                labelRes = R.string.tv_settings_text_stroke,
                current = settings.subtitleStroke,
                values = SubtitleStroke.entries,
                display = { it.localizedLabel() },
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
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(assSsaDirectPlay = it) } },
        )

        SettingsSection.Interface -> listOf(
            choiceRow(
                key = "display-language",
                labelRes = R.string.tv_settings_display_language,
                current = settings.displayLanguage,
                values = DisplayLanguage.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(displayLanguage = it) } },
            choiceRow(
                key = "interface-scale",
                labelRes = R.string.tv_settings_interface_scale,
                current = settings.interfaceScale,
                values = InterfaceScale.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(interfaceScale = it) } },
            choiceRow(
                key = "theme",
                labelRes = R.string.tv_settings_theme,
                current = settings.theme,
                values = AppTheme.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(theme = it) } },
            toggleRow("backdrops", R.string.tv_settings_backdrop_images, settings.backdropImages) {
                update { it.copy(backdropImages = !it.backdropImages) }
            },
            choiceRow(
                key = "backdrop-rotation",
                labelRes = R.string.tv_settings_backdrop_rotation,
                current = settings.backdropRotationSeconds,
                values = SettingsChoices.backdropRotationSeconds,
                display = { stringResource(R.string.tv_value_seconds, it) },
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
                display = { stringResource(R.string.tv_value_auto_mbps, it.toString()) },
            ) { update { current -> current.copy(maxRemoteBitrateMbps = it) } },
            toggleRow("stream-cache", R.string.tv_settings_stream_cache, settings.networkCacheEnabled) {
                update { it.copy(networkCacheEnabled = !it.networkCacheEnabled) }
            },
            choiceRow(
                key = "cache-duration",
                labelRes = R.string.tv_settings_cache_duration,
                current = settings.cacheDurationSeconds,
                values = SettingsChoices.cacheDurationsSeconds,
                display = { stringResource(R.string.tv_value_seconds, it) },
            ) { update { current -> current.copy(cacheDurationSeconds = it) } },
            choiceRow(
                key = "read-ahead",
                labelRes = R.string.tv_settings_read_ahead,
                current = settings.readAheadSeconds,
                values = SettingsChoices.readAheadSeconds,
                display = { stringResource(R.string.tv_value_seconds, it) },
            ) { update { current -> current.copy(readAheadSeconds = it) } },
            choiceRow(
                key = "forward-cache",
                labelRes = R.string.tv_settings_forward_cache_limit,
                current = settings.forwardCacheMiB,
                values = SettingsChoices.forwardCacheMiB,
                display = { stringResource(R.string.tv_value_mib, it) },
            ) { update { current -> current.copy(forwardCacheMiB = it) } },
            choiceRow(
                key = "backward-cache",
                labelRes = R.string.tv_settings_backward_cache_limit,
                current = settings.backwardCacheMiB,
                values = SettingsChoices.backwardCacheMiB,
                display = { stringResource(R.string.tv_value_mib, it) },
            ) { update { current -> current.copy(backwardCacheMiB = it) } },
            choiceRow(
                key = "resume-buffer",
                labelRes = R.string.tv_settings_resume_buffer,
                current = settings.resumeBufferSeconds,
                values = SettingsChoices.resumeBufferSeconds,
                display = {
                    stringResource(
                        if (it == 1) R.string.tv_value_second else R.string.tv_value_seconds_plural,
                        it,
                    )
                },
            ) { update { current -> current.copy(resumeBufferSeconds = it) } },
            choiceRow(
                key = "network-timeout",
                labelRes = R.string.tv_settings_network_timeout,
                current = settings.networkTimeoutSeconds,
                values = SettingsChoices.networkTimeoutSeconds,
                display = { stringResource(R.string.tv_value_seconds, it) },
            ) { update { current -> current.copy(networkTimeoutSeconds = it) } },
            toggleRow("tls", R.string.tv_settings_verify_tls_certificates, settings.verifyTlsCertificates) {
                update { it.copy(verifyTlsCertificates = !it.verifyTlsCertificates) }
            },
            choiceRow(
                key = "tls-trust-source",
                labelRes = R.string.tv_settings_tls_trust_source,
                current = settings.tlsTrustSource,
                values = TlsTrustSource.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(tlsTrustSource = it) } },
            valueRow(
                "artwork-cache-limit",
                R.string.tv_settings_artwork_cache,
                stringResource(R.string.tv_value_auto_up_to_mib),
            ),
            actionRow(
                "clear-artwork-cache",
                R.string.tv_settings_clear_artwork_cache,
                if (state.clearingArtworkCache) {
                    stringResource(R.string.tv_value_clearing)
                } else {
                    stringResource(R.string.tv_cache_clear_value, state.artworkCacheSize.resolve())
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
                display = {
                    if (it == 0) stringResource(R.string.tv_value_never)
                    else stringResource(R.string.tv_value_after_minutes, it)
                },
            ) { update { current -> current.copy(screensaverTimeoutMinutes = it) } },
            choiceRow(
                key = "screensaver-content",
                labelRes = R.string.tv_settings_content,
                current = settings.screensaverContent,
                values = ScreensaverContent.entries,
                display = { it.localizedLabel() },
            ) { update { current -> current.copy(screensaverContent = it) } },
            choiceRow(
                key = "screensaver-duration",
                labelRes = R.string.tv_settings_image_duration,
                current = settings.screensaverImageDurationSeconds,
                values = SettingsChoices.screensaverImageDurationSeconds,
                display = { stringResource(R.string.tv_value_seconds, it) },
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
                value = when {
                    state.serverName == null -> stringResource(R.string.tv_settings_unavailable)
                    state.serverVersion == null -> state.serverName
                    else -> stringResource(
                        R.string.tv_server_name_version,
                        state.serverName,
                        state.serverVersion,
                    )
                },
            ),
            valueRow(
                key = "server-address",
                labelRes = R.string.tv_settings_address,
                value = if (state.serverUrl.isBlank()) {
                    stringResource(R.string.tv_settings_not_connected)
                } else {
                    state.serverUrl
                },
            ),
            valueRow(
                key = "connection",
                labelRes = R.string.tv_settings_connection,
                value = when {
                    state.serverUrl.isBlank() -> stringResource(R.string.tv_settings_offline)
                    state.serverUrl.startsWith("https://", ignoreCase = true) -> stringResource(R.string.tv_settings_secure)
                    else -> stringResource(R.string.tv_settings_local)
                },
            ),
            valueRow("device-name", R.string.tv_settings_device_name, stringResource(R.string.tv_android_tv)),
            actionRow(
                key = "test-connection",
                labelRes = R.string.tv_settings_test_connection,
                value = when {
                    state.testingConnection -> stringResource(R.string.tv_testing)
                    state.connectionMessage != null -> state.connectionMessage.resolve()
                    else -> stringResource(R.string.tv_test)
                },
                onClick = onTestConnection,
            ),
            actionRow("refresh-libraries", R.string.tv_settings_refresh_libraries, stringResource(R.string.tv_action_refresh), onRefreshLibraries),
            actionRow("change-server", R.string.tv_settings_change_server, stringResource(R.string.tv_action_choose), onChangeServer),
            actionRow("forget-server", R.string.tv_settings_forget_this_server, stringResource(R.string.tv_action_forget), onForgetServer),
        )

        SettingsSection.Account -> listOf(
            valueRow(
                key = "profile",
                labelRes = R.string.tv_settings_profile,
                value = if (state.userName.isBlank()) {
                    stringResource(R.string.tv_unknown)
                } else {
                    state.userName
                },
            ),
            actionRow("switch-profile", R.string.tv_settings_switch_profile, stringResource(R.string.tv_action_switch), onSwitchProfile),
            toggleRow("kids-mode", R.string.tv_settings_kids_mode, settings.kidsMode) {
                update { it.copy(kidsMode = !it.kidsMode) }
            },
            valueRow("login-method", R.string.tv_settings_login_method, stringResource(R.string.tv_password)),
            actionRow(
                key = "quick-connect",
                labelRes = R.string.tv_settings_replace_session,
                value = when {
                    state.quickConnectLoading -> stringResource(R.string.tv_generating)
                    state.quickConnectCode != null -> state.quickConnectCode.resolve()
                    else -> stringResource(R.string.tv_quick_connect)
                },
                onClick = onQuickConnect,
            ),
            actionRow("sign-out", R.string.tv_settings_sign_out, stringResource(R.string.tv_action_sign_out), onSignOut),
        )

        SettingsSection.About -> listOf(
            valueRow(
                key = "application",
                labelRes = R.string.tv_settings_application,
                value = stringResource(R.string.tv_shoumei_player),
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
                value = if (BuildConfig.RELEASE_TAG.isBlank()) {
                    stringResource(R.string.tv_development_build)
                } else {
                    BuildConfig.RELEASE_TAG
                },
            ),
            valueRow(
                key = "mpv-tag",
                labelRes = R.string.tv_settings_mpv_source_tag,
                value = stringResource(R.string.tv_version_prefix, BuildConfig.MPV_VERSION),
            ),
            valueRow(
                key = "platform",
                labelRes = R.string.tv_settings_platform,
                value = stringResource(R.string.tv_android_tv),
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

@Composable
private fun StoredOption.localizedLabel(): String = stringResource(localizedLabelRes())

@StringRes
private fun StoredOption.localizedLabelRes(): Int = when (this) {
    is PlaybackBackend -> when (this) {
        PlaybackBackend.Mpv -> R.string.tv_settings_mpv
        PlaybackBackend.System -> R.string.tv_settings_android_system
    }
    is TlsTrustSource -> when (this) {
        TlsTrustSource.Mpv -> R.string.tv_settings_mpv_ca_store
        TlsTrustSource.AndroidSystem -> R.string.tv_settings_android_system_ca_store
    }
    is RefreshRateSwitching -> when (this) {
        RefreshRateSwitching.Disabled -> R.string.tv_settings_disabled
        RefreshRateSwitching.MatchVideo -> R.string.tv_settings_match_video
        RefreshRateSwitching.Always -> R.string.tv_settings_always
    }
    is ResumeBehavior -> when (this) {
        ResumeBehavior.Ask -> R.string.tv_settings_ask
        ResumeBehavior.Resume -> R.string.tv_settings_resume
        ResumeBehavior.Restart -> R.string.tv_settings_restart
        ResumeBehavior.Always -> R.string.tv_settings_always
    }
    is RenderingProfile -> when (this) {
        RenderingProfile.Fast -> R.string.tv_settings_fast
        RenderingProfile.Balanced -> R.string.tv_settings_balanced
        RenderingProfile.Quality -> R.string.tv_settings_quality
    }
    is HardwareDecoding -> when (this) {
        HardwareDecoding.MediaCodecCopy -> R.string.tv_settings_mediacodec_copy
        HardwareDecoding.MediaCodec -> R.string.tv_settings_mediacodec
        HardwareDecoding.Software -> R.string.tv_settings_software
    }
    is HardwareCodecs -> when (this) {
        HardwareCodecs.Automatic -> R.string.tv_settings_automatic
        HardwareCodecs.H264Hevc -> R.string.tv_settings_h264_hevc
        HardwareCodecs.Av1 -> R.string.tv_settings_av1
        HardwareCodecs.Disabled -> R.string.tv_settings_disabled
    }
    is HdrMode -> when (this) {
        HdrMode.Automatic -> R.string.tv_settings_automatic
        HdrMode.Passthrough -> R.string.tv_settings_passthrough
        HdrMode.ToneMap -> R.string.tv_settings_tone_map
        HdrMode.ForceSdr -> R.string.tv_settings_force_sdr
        HdrMode.Off -> R.string.off
    }
    is ToneMapping -> when (this) {
        ToneMapping.Automatic -> R.string.tv_settings_automatic
        ToneMapping.Bt2390 -> R.string.tv_settings_bt2390
        ToneMapping.Reinhard -> R.string.tv_settings_reinhard
        ToneMapping.Mobius -> R.string.tv_settings_mobius
        ToneMapping.Off -> R.string.off
    }
    is DeinterlaceMode -> when (this) {
        DeinterlaceMode.Automatic -> R.string.tv_settings_automatic
        DeinterlaceMode.On -> R.string.on
        DeinterlaceMode.Off -> R.string.off
        DeinterlaceMode.Bob -> R.string.tv_settings_bob
    }
    is SubtitleColor -> when (this) {
        SubtitleColor.White -> R.string.tv_settings_white
        SubtitleColor.Yellow -> R.string.tv_settings_yellow
        SubtitleColor.Grey -> R.string.tv_settings_grey
    }
    is SubtitleStroke -> when (this) {
        SubtitleStroke.Off -> R.string.off
        SubtitleStroke.Light -> R.string.tv_settings_light
        SubtitleStroke.Medium -> R.string.tv_settings_medium
        SubtitleStroke.Heavy -> R.string.tv_settings_heavy
    }
    is SubtitleMode -> when (this) {
        SubtitleMode.Smart -> R.string.tv_settings_smart
        SubtitleMode.Always -> R.string.tv_settings_always
        SubtitleMode.OnlyForced -> R.string.tv_settings_only_forced
        SubtitleMode.None -> R.string.tv_settings_none
    }
    is BurnSubtitles -> when (this) {
        BurnSubtitles.Automatic -> R.string.tv_settings_automatic
        BurnSubtitles.ImageFormats -> R.string.tv_settings_only_image_formats
        BurnSubtitles.Always -> R.string.tv_settings_always
        BurnSubtitles.Never -> R.string.tv_settings_never
    }
    is AssSsaDirectPlay -> when (this) {
        AssSsaDirectPlay.Experimental -> R.string.tv_settings_experimental
        AssSsaDirectPlay.Enabled -> R.string.tv_settings_enabled
        AssSsaDirectPlay.Disabled -> R.string.tv_settings_disabled
    }
    is DisplayLanguage -> when (this) {
        DisplayLanguage.English -> R.string.tv_settings_english
        DisplayLanguage.Vietnamese -> R.string.tv_settings_vietnamese
        DisplayLanguage.Japanese -> R.string.tv_settings_japanese
        DisplayLanguage.French -> R.string.tv_settings_french
        DisplayLanguage.German -> R.string.tv_settings_german
    }
    is InterfaceScale -> when (this) {
        InterfaceScale.Compact -> R.string.tv_settings_compact
        InterfaceScale.Comfortable -> R.string.tv_settings_comfortable
        InterfaceScale.Large -> R.string.tv_settings_large
    }
    is AppTheme -> when (this) {
        AppTheme.Dark -> R.string.tv_settings_dark
        AppTheme.System -> R.string.tv_settings_system
    }
    is ScreensaverContent -> when (this) {
        ScreensaverContent.AllLibraries -> R.string.tv_settings_all_libraries
        ScreensaverContent.Movies -> R.string.tv_settings_movies
        ScreensaverContent.Shows -> R.string.tv_settings_shows
        ScreensaverContent.Music -> R.string.tv_settings_music
    }
    else -> R.string.tv_settings_unavailable
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

@Composable
private fun toggleRow(
    key: String,
    @StringRes labelRes: Int,
    value: Boolean,
    requiredCapability: SettingCapability? = null,
    onClick: () -> Unit,
) = SettingRowModel(
    key = key,
    labelRes = labelRes,
    value = stringResource(if (value) R.string.on else R.string.off),
    control = SettingControl.Toggle,
    checked = value,
    requiredCapability = requiredCapability,
    onClick = onClick,
)

@Composable
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
    display = { it ?: stringResource(R.string.settings_language_system_default) },
    onSelect = onSelect,
)

@Composable
private fun <T> choiceRow(
    key: String,
    @StringRes labelRes: Int,
    current: T,
    values: List<T>,
    display: @Composable (T) -> String = { it.toString() },
    requiredCapability: SettingCapability? = null,
    onSelect: (T) -> Unit,
): SettingRowModel {
    val choices = mutableListOf<SettingChoiceOption>()
    for ((index, value) in values.withIndex()) {
        choices += SettingChoiceOption(
            key = "$key-$index",
            label = display(value),
            selected = value == current,
            onSelect = { onSelect(value) },
        )
    }
    return SettingRowModel(
        key = key,
        labelRes = labelRes,
        value = display(current),
        control = SettingControl.Choice,
        requiredCapability = requiredCapability,
        choices = choices,
    )
}
