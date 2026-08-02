package com.maik205.shoumeiplayer.feature.player

import com.maik205.shoumeiplayer.player.PlaybackTrackPreferenceProvider
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.ResolvedPlayback
import com.maik205.shoumeiplayer.player.TrackSelection
import com.maik205.shoumeiplayer.player.TrackType

data class SelectedTracks(
    val audioIndex: Int?,
    val subtitleIndex: Int?,
)

class TrackController(
    private val preferenceProvider: PlaybackTrackPreferenceProvider,
    private val initialAudioStreamIndex: Int?,
    private val initialSubtitleStreamIndex: Int?,
    private val preferAudioDescription: Boolean = false,
) {
    private var initialSelectionPending =
        initialAudioStreamIndex != null || initialSubtitleStreamIndex != null

    var selectedAudioIndex: Int? = null
        private set

    var selectedSubtitleIndex: Int? = null
        private set

    val requestedAudioIndex: Int?
        get() = if (initialSelectionPending) initialAudioStreamIndex else selectedAudioIndex

    val requestedSubtitleIndex: Int?
        get() = if (initialSelectionPending) initialSubtitleStreamIndex else selectedSubtitleIndex

    fun select(track: PlayerTrack): SelectedTracks {
        val previous = snapshot()
        when (track.type) {
            TrackType.AUDIO -> selectedAudioIndex = track.id
            TrackType.SUBTITLE -> selectedSubtitleIndex = track.id
            TrackType.VIDEO -> Unit
        }
        return previous
    }

    fun restore(selection: SelectedTracks) {
        selectedAudioIndex = selection.audioIndex
        selectedSubtitleIndex = selection.subtitleIndex
    }

    /**
     * @param rememberedAudioIndex §88 `rememberSeriesAudio` — the audio stream a returning viewer
     * previously picked for this item's series, if the setting is on and one is on record (see
     * [PlayerViewModel.rememberedAudioIndexFor]). Only ever consulted for the *default* pick:
     * [initialAudioStreamIndex] (while [initialSelectionPending] is still true) is an explicit
     * per-item request carried in from Detail and always wins over it, and [TrackSelection]'s own
     * server-preference default is still the fallback whenever there is no remembered index or it no
     * longer exists on this stream (a re-muxed source, a removed track). This precedence is what
     * keeps the two mechanisms from fighting: [TrackSelection] owns "what should a fresh account
     * default to"; [rememberedAudioIndex] only ever overrides the specific default a *returning*
     * viewer already chose for this series.
     */
    suspend fun prepare(
        resolved: ResolvedPlayback,
        keepCurrent: Boolean,
        rememberedAudioIndex: Int? = null,
    ) {
        if (keepCurrent) return
        val preferences = preferenceProvider.preferences()
        val defaultAudio = TrackSelection.selectAudioIndex(
            streams = resolved.mediaStreams,
            configuration = preferences,
            defaultAudioStreamIndex = resolved.defaultAudioIndex,
            preferAudioDescription = preferAudioDescription,
        )
        val remembered = rememberedAudioIndex?.takeIf { index ->
            resolved.mediaStreams.any { it.type.equals("Audio", ignoreCase = true) && it.index == index }
        }
        selectedAudioIndex = if (initialSelectionPending) {
            initialAudioStreamIndex ?: defaultAudio
        } else {
            remembered ?: defaultAudio
        }
        val defaultSubtitle = TrackSelection.selectSubtitleIndex(
            streams = resolved.mediaStreams,
            configuration = preferences,
            defaultSubtitleStreamIndex = resolved.defaultSubtitleIndex,
            selectedAudioIndex = selectedAudioIndex,
        )
        selectedSubtitleIndex = if (initialSelectionPending) {
            initialSubtitleStreamIndex ?: defaultSubtitle
        } else {
            defaultSubtitle
        }
        initialSelectionPending = false
    }

    fun snapshot(): SelectedTracks =
        SelectedTracks(selectedAudioIndex, selectedSubtitleIndex)
}
