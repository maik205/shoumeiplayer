package com.maik205.shoumeiplayer.player

/** Pure default-track policy over provider-neutral playback metadata. */
object TrackSelection {
    object SubtitleMode {
        const val DEFAULT = "Default"
        const val ALWAYS = "Always"
        const val ONLY_FORCED = "OnlyForced"
        const val NONE = "None"
        const val SMART = "Smart"
    }

    const val NO_TRACK = -1

    internal fun languageMatches(streamLanguage: String?, preference: String?): Boolean {
        val stream = streamLanguage?.trim()?.lowercase().orEmpty()
        val preferred = preference?.trim()?.lowercase().orEmpty()
        if (stream.isEmpty() || preferred.isEmpty()) return false
        if (stream == preferred) return true
        val shorter = minOf(stream.length, preferred.length)
        return shorter >= 2 && stream.take(shorter) == preferred.take(shorter)
    }

    private fun List<PlaybackMediaStream>.ofType(type: String) =
        filter { it.type.equals(type, ignoreCase = true) }

    fun selectAudioIndex(
        streams: List<PlaybackMediaStream>,
        configuration: PlaybackTrackPreferences?,
        defaultAudioStreamIndex: Int?,
    ): Int? {
        val audio = streams.ofType("Audio")
        if (audio.isEmpty()) return null
        val serverDefault = defaultAudioStreamIndex?.takeIf { index -> audio.any { it.index == index } }
            ?: audio.firstOrNull { it.isDefault }?.index
            ?: audio.first().index
        val preferred = configuration ?: return serverDefault
        if (preferred.playDefaultAudioTrack) return serverDefault
        return audio.firstOrNull {
            languageMatches(it.language, preferred.audioLanguagePreference)
        }?.index ?: serverDefault
    }

    fun selectSubtitleIndex(
        streams: List<PlaybackMediaStream>,
        configuration: PlaybackTrackPreferences?,
        defaultSubtitleStreamIndex: Int?,
        selectedAudioIndex: Int?,
    ): Int {
        val subtitles = streams.ofType("Subtitle")
        if (subtitles.isEmpty()) return NO_TRACK
        val serverDefault = defaultSubtitleStreamIndex?.takeIf { index ->
            subtitles.any { it.index == index }
        }
        val preferred = configuration ?: return serverDefault ?: NO_TRACK
        val languagePreference = preferred.subtitleLanguagePreference

        fun preferredLanguage(candidates: List<PlaybackMediaStream>) =
            candidates.firstOrNull { languageMatches(it.language, languagePreference) }

        return when (preferred.subtitleMode) {
            SubtitleMode.NONE -> NO_TRACK
            SubtitleMode.ALWAYS -> (
                preferredLanguage(subtitles)
                    ?: subtitles.firstOrNull { !it.isForced }
                    ?: subtitles.first()
                ).index
            SubtitleMode.ONLY_FORCED -> {
                val forced = subtitles.filter { it.isForced }
                (preferredLanguage(forced) ?: forced.firstOrNull())?.index ?: NO_TRACK
            }
            SubtitleMode.SMART -> {
                val audioLanguage = streams.firstOrNull { it.index == selectedAudioIndex }?.language
                if (languageMatches(audioLanguage, languagePreference)) {
                    NO_TRACK
                } else {
                    preferredLanguage(subtitles)?.index ?: NO_TRACK
                }
            }
            else -> serverDefault ?: NO_TRACK
        }
    }
}
