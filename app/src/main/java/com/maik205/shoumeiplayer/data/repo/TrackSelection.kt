package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.data.api.dto.MediaStreamDto
import com.maik205.shoumeiplayer.data.api.dto.UserConfigurationDto

/**
 * Default audio/subtitle track selection, ported from Jellyfin's own server-side rules so a
 * Shoumei session starts on the same tracks the web client would pick.
 *
 * Everything here is pure: it takes the `MediaSourceInfo` streams plus the user's
 * `UserConfiguration` and returns stream indices. No IO, no engine, no Android.
 */
object TrackSelection {

    /** `SubtitlePlaybackMode` values as the server spells them (verified against the OpenAPI enum). */
    object SubtitleMode {
        const val DEFAULT = "Default"
        const val ALWAYS = "Always"
        const val ONLY_FORCED = "OnlyForced"
        const val NONE = "None"
        const val SMART = "Smart"
    }

    /** "Subtitles off" — the value Jellyfin and mpv both treat as no subtitle stream. */
    const val NO_TRACK = -1

    /**
     * Language codes come back as ISO 639-2/B ("eng") from most containers but 639-1 ("en") from
     * some, and the user preference is whatever the server stored. Compare case-insensitively and
     * accept a common prefix of at least two characters so "en" matches "eng"/"eng-US".
     */
    internal fun languageMatches(streamLanguage: String?, preference: String?): Boolean {
        val stream = streamLanguage?.trim()?.lowercase().orEmpty()
        val preferred = preference?.trim()?.lowercase().orEmpty()
        if (stream.isEmpty() || preferred.isEmpty()) return false
        if (stream == preferred) return true
        val shorter = minOf(stream.length, preferred.length)
        return shorter >= 2 && stream.take(shorter) == preferred.take(shorter)
    }

    private fun List<MediaStreamDto>.ofType(type: String) = filter { it.type.equals(type, ignoreCase = true) }

    /**
     * The audio stream index to start on, or null when the source has no audio at all.
     *
     * - `PlayDefaultAudioTrack = true` (the Jellyfin default): honour the server's
     *   `DefaultAudioStreamIndex` and ignore the language preference entirely.
     * - otherwise: first stream whose language matches `AudioLanguagePreference`, falling back to
     *   the server default, then to the first audio stream.
     */
    fun selectAudioIndex(
        streams: List<MediaStreamDto>,
        configuration: UserConfigurationDto?,
        defaultAudioStreamIndex: Int?,
    ): Int? {
        val audio = streams.ofType("Audio")
        if (audio.isEmpty()) return null
        val serverDefault = defaultAudioStreamIndex?.takeIf { index -> audio.any { it.index == index } }
            ?: audio.firstOrNull { it.isDefault }?.index
            ?: audio.first().index
        val config = configuration ?: return serverDefault
        if (config.playDefaultAudioTrack) return serverDefault
        val preferred = audio.firstOrNull { languageMatches(it.language, config.audioLanguagePreference) }
        return preferred?.index ?: serverDefault
    }

    /**
     * The subtitle stream index to start on, or [NO_TRACK] for "off".
     *
     * [selectedAudioIndex] only matters for `Smart`, which turns subtitles on exactly when the
     * audio the user is about to hear is not in their preferred subtitle language.
     */
    fun selectSubtitleIndex(
        streams: List<MediaStreamDto>,
        configuration: UserConfigurationDto?,
        defaultSubtitleStreamIndex: Int?,
        selectedAudioIndex: Int?,
    ): Int {
        val subtitles = streams.ofType("Subtitle")
        if (subtitles.isEmpty()) return NO_TRACK
        val serverDefault = defaultSubtitleStreamIndex?.takeIf { index -> subtitles.any { it.index == index } }
        val config = configuration ?: return serverDefault ?: NO_TRACK
        val languagePreference = config.subtitleLanguagePreference

        fun preferredLanguage(candidates: List<MediaStreamDto>) =
            candidates.firstOrNull { languageMatches(it.language, languagePreference) }

        return when (config.subtitleMode) {
            SubtitleMode.NONE -> NO_TRACK

            // Always: a subtitle track, preferring the user's language, otherwise any non-forced
            // track (a forced track alone would only caption foreign dialogue).
            SubtitleMode.ALWAYS -> (
                preferredLanguage(subtitles)
                    ?: subtitles.firstOrNull { !it.isForced }
                    ?: subtitles.first()
                ).index

            // OnlyForced: forced tracks only, preferring the user's language.
            SubtitleMode.ONLY_FORCED -> {
                val forced = subtitles.filter { it.isForced }
                (preferredLanguage(forced) ?: forced.firstOrNull())?.index ?: NO_TRACK
            }

            // Smart: subtitles only when the audio the user will hear is in a different language
            // than their subtitle preference.
            SubtitleMode.SMART -> {
                val audioLanguage = streams.firstOrNull { it.index == selectedAudioIndex }?.language
                if (languageMatches(audioLanguage, languagePreference)) {
                    NO_TRACK
                } else {
                    preferredLanguage(subtitles)?.index ?: NO_TRACK
                }
            }

            // Default (and any unknown/absent mode): whatever the server marked default, else off.
            else -> serverDefault ?: NO_TRACK
        }
    }
}
