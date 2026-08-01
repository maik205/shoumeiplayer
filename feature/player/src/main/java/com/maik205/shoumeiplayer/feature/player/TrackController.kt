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

    suspend fun prepare(resolved: ResolvedPlayback, keepCurrent: Boolean) {
        if (keepCurrent) return
        val preferences = preferenceProvider.preferences()
        val defaultAudio = TrackSelection.selectAudioIndex(
            streams = resolved.mediaStreams,
            configuration = preferences,
            defaultAudioStreamIndex = resolved.defaultAudioIndex,
            preferAudioDescription = preferAudioDescription,
        )
        selectedAudioIndex = if (initialSelectionPending) {
            initialAudioStreamIndex ?: defaultAudio
        } else {
            defaultAudio
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
