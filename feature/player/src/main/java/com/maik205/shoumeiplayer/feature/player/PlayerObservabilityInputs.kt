package com.maik205.shoumeiplayer.feature.player

import com.maik205.shoumeiplayer.player.PlaybackMetricsSink
import com.maik205.shoumeiplayer.player.PlaybackOwnershipCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The device/OS-level signals [PlayerViewModel] reflects into its UI state and metrics but does
 * not itself own or compute -- audio route, HDR mode, network reachability, and the process-wide
 * playback-ownership handoff. Grouped separately from the playback collaborators (engine, resolver,
 * track controller, ...) because every one of these is either read-only or optional, and bundling
 * them keeps the constructor from growing every time another ambient signal is wired in.
 */
data class PlayerObservabilityInputs(
    val audioRouteLabel: StateFlow<String> = MutableStateFlow("System default"),
    val effectiveHdrMode: StateFlow<String> = MutableStateFlow("Automatic"),
    val networkAvailable: StateFlow<Boolean> = MutableStateFlow(true),
    val networkTransport: StateFlow<String> = MutableStateFlow("unknown"),
    val metricsSink: PlaybackMetricsSink? = null,
    val backgroundAudio: Boolean = false,
    val playbackOwnershipCoordinator: PlaybackOwnershipCoordinator? = null,
)
