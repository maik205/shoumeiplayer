package com.maik205.shoumeiplayer.player

import java.util.Locale

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

    /**
     * English display names client settings historically persisted (see
     * `ClientSettings.preferredAudioLanguage`, whose dropdown offers exactly these five
     * languages). These aren't a language *tag* standard, so `Locale` can't resolve them; map
     * each to its ISO 639-1 code and let [canonicalLanguageCode] take it from there.
     */
    private val DISPLAY_NAME_TO_ISO1: Map<String, String> = mapOf(
        "english" to "en",
        "japanese" to "ja",
        "vietnamese" to "vi",
        "french" to "fr",
        "german" to "de",
    )

    /**
     * ISO 639-2 bibliographic (B) codes whose terminological (T) equivalent differs from the
     * code itself. `Locale.forLanguageTag(x).isO3Language` treats any syntactically valid
     * three-letter tag as already-canonical and returns it unchanged (verified: `ger` stays
     * `ger`, it does not become `deu`), so `Locale` alone can never unify a B-coded stream tag
     * with a 639-1 or T-coded preference. This table covers every B/T pair relevant to real
     * Jellyfin libraries; every other language has no B/T split and resolves through `Locale`.
     */
    private val ISO639_2B_TO_T: Map<String, String> = mapOf(
        "alb" to "sqi",
        "arm" to "hye",
        "baq" to "eus",
        "bur" to "mya",
        "chi" to "zho",
        "cze" to "ces",
        "dut" to "nld",
        "fre" to "fra",
        "geo" to "kat",
        "ger" to "deu",
        "gre" to "ell",
        "ice" to "isl",
        "mac" to "mkd",
        "mao" to "mri",
        "may" to "msa",
        "per" to "fas",
        "rum" to "ron",
        "slo" to "slk",
        "tib" to "bod",
        "wel" to "cym",
    )

    /**
     * Resolves an arbitrary language code/tag/display-name (already trimmed+lowercased) to its
     * canonical ISO 639-2/T code, or `null` if it can't be resolved so the caller falls back to
     * the prefix heuristic. Region/script subtags are stripped first ("de-de" -> "de") so a
     * region-tagged stream still resolves to the same canonical code as a bare-language
     * preference — without this, once one side canonicalized the other would never fall through
     * to a match, which is exactly the regression a previous fix introduced.
     */
    private fun canonicalLanguageCode(raw: String): String? {
        val base = raw.substringBefore('-').substringBefore('_')
        DISPLAY_NAME_TO_ISO1[base]?.let { iso1 -> return canonicalLanguageCode(iso1) ?: iso1 }
        ISO639_2B_TO_T[base]?.let { return it }
        return runCatching { Locale.forLanguageTag(base).isO3Language }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    internal fun languageMatches(streamLanguage: String?, preference: String?): Boolean {
        val stream = streamLanguage?.trim()?.lowercase().orEmpty()
        val preferred = preference?.trim()?.lowercase().orEmpty()
        if (stream.isEmpty() || preferred.isEmpty()) return false
        if (stream == preferred) return true
        val streamCanonical = canonicalLanguageCode(stream)
        val preferredCanonical = canonicalLanguageCode(preferred)
        if (streamCanonical != null || preferredCanonical != null) {
            // At least one side resolved to a canonical ISO 639-2/T code: compare both through
            // that resolution (falling back to the raw tag when only one side resolved) rather
            // than falling through to the prefix heuristic below, which is exactly what silently
            // mismatched "ja" against "jpn" — and every other cross-standard pair — before.
            return (streamCanonical ?: stream) == (preferredCanonical ?: preferred)
        }
        // Neither side resolved to a known language: keep the previous prefix heuristic so
        // uncurated codes/names (e.g. a longer display name that happens to start with a shorter
        // tag) still have a reasonable chance of matching.
        val shorter = minOf(stream.length, preferred.length)
        return shorter >= 2 && stream.take(shorter) == preferred.take(shorter)
    }

    private fun List<PlaybackMediaStream>.ofType(type: String) =
        filter { it.type.equals(type, ignoreCase = true) }

    fun selectAudioIndex(
        streams: List<PlaybackMediaStream>,
        configuration: PlaybackTrackPreferences?,
        defaultAudioStreamIndex: Int?,
        preferAudioDescription: Boolean = false,
    ): Int? {
        val audio = streams.ofType("Audio")
        if (audio.isEmpty()) return null
        if (preferAudioDescription) {
            audio.firstOrNull { it.isAudioDescription }?.index?.let { return it }
        }
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
