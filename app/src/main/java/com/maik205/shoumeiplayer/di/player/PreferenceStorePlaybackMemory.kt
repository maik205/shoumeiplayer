package com.maik205.shoumeiplayer.di.player

import com.maik205.shoumeiplayer.data.session.PreferenceStore
import com.maik205.shoumeiplayer.data.session.SessionStore
import com.maik205.shoumeiplayer.data.session.TrackDelays
import com.maik205.shoumeiplayer.data.session.UserScope
import com.maik205.shoumeiplayer.domain.settings.PreferredQuality
import com.maik205.shoumeiplayer.domain.settings.storedOption
import com.maik205.shoumeiplayer.feature.player.ItemTrackDelays
import com.maik205.shoumeiplayer.feature.player.PlaybackPreferenceMemory

/**
 * Binds `feature:player`'s [PlaybackPreferenceMemory] seam to the real [PreferenceStore].
 *
 * `feature:player` cannot depend on `core:data`, so it declares the interface and the app module
 * supplies the implementation — the same shape as [JellyfinPlaybackMetadataLoader]. Without this
 * class `PlayerViewModel.preferenceMemory` stays on its `null` default and every remembered-state
 * feature (#88 series audio and playback speed, #95 per-item delay and quality) silently does
 * nothing, because each call site is a no-op safe call.
 *
 * Every method resolves the signed-in [UserScope] per call rather than capturing it: the store
 * outlives any one playback, and a profile switch mid-session must not write the new viewer's
 * choices into the previous account's rows. A `null` scope (signed out) reads and writes nothing,
 * which the interface already documents as indistinguishable from "no memory".
 */
internal class PreferenceStorePlaybackMemory(
    private val preferenceStore: PreferenceStore,
    private val sessionStore: SessionStore,
) : PlaybackPreferenceMemory {

    private suspend fun scope(): UserScope? = UserScope.of(sessionStore.current())

    override suspend fun seriesAudioTrack(seriesId: String): Int? {
        val scope = scope() ?: return null
        // Stored as text, so a value written by a build with a different notion of "track" is
        // ignored rather than crashing the audio selection path.
        return preferenceStore.seriesAudioTrack(scope, seriesId)?.toIntOrNull()
    }

    override suspend fun setSeriesAudioTrack(seriesId: String, audioIndex: Int) {
        val scope = scope() ?: return
        preferenceStore.setSeriesAudioTrack(scope, seriesId, audioIndex.toString())
    }

    override suspend fun playbackSpeed(itemId: String): Float? {
        val scope = scope() ?: return null
        return preferenceStore.playbackSpeed(scope, itemId)
    }

    override suspend fun setPlaybackSpeed(itemId: String, speed: Float) {
        val scope = scope() ?: return
        preferenceStore.setPlaybackSpeed(scope, itemId, speed)
    }

    override suspend fun trackDelays(itemId: String): ItemTrackDelays? {
        val scope = scope() ?: return null
        return preferenceStore.trackDelays(scope, itemId)?.let { stored ->
            ItemTrackDelays(
                audioDelayMs = stored.audioDelayMs.toLong(),
                subtitleDelayMs = stored.subtitleDelayMs.toLong(),
            )
        }
    }

    override suspend fun setTrackDelays(itemId: String, delays: ItemTrackDelays?) {
        val scope = scope() ?: return
        preferenceStore.setTrackDelays(
            scope = scope,
            itemId = itemId,
            delays = delays?.let {
                TrackDelays(
                    audioDelayMs = it.audioDelayMs.toInt(),
                    subtitleDelayMs = it.subtitleDelayMs.toInt(),
                )
            },
        )
    }

    override suspend fun qualityCapLabel(itemId: String): String? {
        val scope = scope() ?: return null
        return preferenceStore.qualityCap(scope, itemId)?.label
    }

    override suspend fun setQualityCapLabel(itemId: String, label: String?) {
        val scope = scope() ?: return
        // `label` arrives as a VideoQuality label; storedOption matches on storageId, label and
        // legacy spellings, so the two enums line up on their shared "Auto"/"1080p"/… wording.
        // An unmappable label clears the cap instead of silently pinning Auto over the item.
        val quality = label?.let {
            PreferredQuality.entries.firstOrNull { option ->
                option.label.equals(it, ignoreCase = true) || option.storageId.equals(it, ignoreCase = true)
            }
        }
        preferenceStore.setQualityCap(scope, itemId, quality)
    }
}
