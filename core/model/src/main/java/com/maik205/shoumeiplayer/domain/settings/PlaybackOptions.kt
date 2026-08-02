package com.maik205.shoumeiplayer.domain.settings

import java.util.Locale

interface StoredOption {
    val storageId: String
    val label: String
    val legacyValues: Set<String>
        get() = emptySet()
}

enum class PlaybackBackend(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Mpv("mpv", "mpv"),
    System("system", "Android system"),
}

enum class TlsTrustSource(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Mpv("mpv", "mpv CA store"),
    AndroidSystem("android_system", "Android system CA store"),
}

enum class PreferredQuality(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Auto("auto", "Auto"),
    Uhd4k("4k", "4K"),
    FullHd("1080p", "1080p"),
    Hd("720p", "720p"),
    Sd("480p", "480p"),
}

enum class RefreshRateSwitching(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Disabled("disabled", "Disabled"),
    MatchVideo("match_video", "Match video"),
    Always("always", "Always"),
}

enum class ResumeBehavior(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Ask("ask", "Ask"),
    Resume("resume", "Resume"),
    Restart("restart", "Restart"),
    Always("always", "Always"),
}

fun <T> storedOption(
    raw: String?,
    default: T,
    values: Array<T>,
): T where T : Enum<T>, T : StoredOption {
    if (raw.isNullOrBlank()) return default
    return values.firstOrNull { option ->
        option.storageId.equals(raw, ignoreCase = true) ||
            option.label.equals(raw, ignoreCase = true) ||
            option.legacyValues.any { it.equals(raw, ignoreCase = true) }
    } ?: default
}

enum class RenderingProfile(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Fast("fast", "Fast"),
    Balanced("balanced", "Balanced"),
    Quality("quality", "Quality"),
}

enum class HardwareDecoding(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    MediaCodecCopy("mediacodec_copy", "MediaCodec copy"),
    MediaCodec("mediacodec", "MediaCodec"),
    Software("software", "Software"),
}

enum class HardwareCodecs(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Automatic("automatic", "Automatic"),
    H264Hevc("h264_hevc", "H.264 / HEVC"),
    Av1("av1", "AV1"),
    Disabled("disabled", "Disabled"),
}

enum class HdrMode(
    override val storageId: String,
    override val label: String,
    override val legacyValues: Set<String> = emptySet(),
) : StoredOption {
    Automatic("automatic", "Automatic"),
    Passthrough("passthrough", "Passthrough"),
    ToneMap("tone_map", "Tone map"),
    ForceSdr("force_sdr", "Force SDR"),
    Off("off", "Off"),
}

enum class ToneMapping(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Automatic("automatic", "Automatic"),
    Bt2390("bt_2390", "BT.2390"),
    Reinhard("reinhard", "Reinhard"),
    Mobius("mobius", "Mobius"),
    Off("off", "Off"),
}

enum class DeinterlaceMode(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Automatic("automatic", "Automatic"),
    On("on", "On"),
    Off("off", "Off"),
    Bob("bob", "Bob"),
}

enum class SubtitleColor(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    White("white", "White"),
    Yellow("yellow", "Yellow"),
    Grey("grey", "Grey"),
}

enum class SubtitleStroke(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Off("off", "Off"),
    Light("light", "Light"),
    Medium("medium", "Medium"),
    Heavy("heavy", "Heavy"),
}

/**
 * Typed mirror of Jellyfin's account-level `UserConfiguration.SubtitleMode`
 * (`SubtitlePlaybackMode`), **not** a client setting.
 *
 * Subtitle track selection is driven entirely by the server value through
 * `TrackSelection.selectSubtitleIndex`, so [storageId] is the wire value the server round-trips
 * rather than an app-private id -- the enum can be converted straight back into the string posted
 * to `/Users/Configuration`. [legacyValues] keeps the snake_case ids the retired client-side copy
 * of this enum persisted readable, so an upgrading install still resolves its old value to a mode
 * instead of silently falling back.
 */
enum class SubtitleMode(
    override val storageId: String,
    override val label: String,
    override val legacyValues: Set<String> = emptySet(),
) : StoredOption {
    Default("Default", "Default", setOf("default")),
    Smart("Smart", "Smart", setOf("smart")),
    Always("Always", "Always", setOf("always")),
    OnlyForced("OnlyForced", "Only forced", setOf("only_forced")),
    None("None", "None", setOf("none")),
}

/**
 * The account-level audio/subtitle language values this app offers.
 *
 * [storageId] is the ISO 639-2 code Jellyfin's own clients write into
 * `UserConfiguration.AudioLanguagePreference` / `SubtitleLanguagePreference`, so the value posted
 * back is one another client recognises. [legacyValues] absorbs the 639-1 tag and the alternate
 * 639-2 form, plus (via [StoredOption.label]) the English display names the retired client-side
 * `ClientSettings.preferredAudioLanguage` used to persist -- an upgrading account whose server
 * value came from either source still resolves to a real option instead of "no preference".
 *
 * A `null` [ServerLanguage] is a real state, not an absence of data: it is Jellyfin's "no
 * preference", which lets the server-marked default track win.
 */
enum class ServerLanguage(
    override val storageId: String,
    override val label: String,
    override val legacyValues: Set<String> = emptySet(),
) : StoredOption {
    // storageId is ISO 639-2/B, which is what Jellyfin's own clients write. legacyValues carries the
    // 639-1 tag and the 639-2/T form where they differ, so an account configured by any other client
    // resolves here instead of falling through to "no preference".
    Arabic("ara", "Arabic", setOf("ar")),
    Chinese("chi", "Chinese", setOf("zh", "zho")),
    Czech("cze", "Czech", setOf("cs", "ces")),
    Danish("dan", "Danish", setOf("da")),
    Dutch("dut", "Dutch", setOf("nl", "nld")),
    English("eng", "English", setOf("en")),
    Finnish("fin", "Finnish", setOf("fi")),
    French("fre", "French", setOf("fr", "fra")),
    German("ger", "German", setOf("de", "deu")),
    Greek("gre", "Greek", setOf("el", "ell")),
    Hebrew("heb", "Hebrew", setOf("he")),
    Hindi("hin", "Hindi", setOf("hi")),
    Hungarian("hun", "Hungarian", setOf("hu")),
    Indonesian("ind", "Indonesian", setOf("id")),
    Italian("ita", "Italian", setOf("it")),
    Japanese("jpn", "Japanese", setOf("ja")),
    Korean("kor", "Korean", setOf("ko")),
    Norwegian("nor", "Norwegian", setOf("no", "nob", "nb")),
    Polish("pol", "Polish", setOf("pl")),
    Portuguese("por", "Portuguese", setOf("pt")),
    Romanian("rum", "Romanian", setOf("ro", "ron")),
    Russian("rus", "Russian", setOf("ru")),
    Spanish("spa", "Spanish", setOf("es")),
    Swedish("swe", "Swedish", setOf("sv")),
    Thai("tha", "Thai", setOf("th")),
    Turkish("tur", "Turkish", setOf("tr")),
    Ukrainian("ukr", "Ukrainian", setOf("uk")),
    Vietnamese("vie", "Vietnamese", setOf("vi")),
    ;

    /**
     * The language's name in [locale]'s own language — "Spanish", "Spanisch", "スペイン語" — resolved
     * from the ICU data already on the device.
     *
     * Deliberately not a string resource. Routing these through `R.string` would mean a new key for
     * every language times every UI locale, so each added language taxed every translation and each
     * added locale taxed every language. [label] stays as the English fallback for the rare device
     * whose ICU data does not know a code.
     */
    fun displayName(locale: Locale = Locale.getDefault()): String =
        Locale.forLanguageTag(legacyValues.firstOrNull { it.length == 2 } ?: storageId)
            .getDisplayLanguage(locale)
            .takeIf(String::isNotBlank)
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
            ?: label

    companion object {
        /** Resolves a server/legacy value, or null when the account expresses no preference. */
        fun fromStored(raw: String?): ServerLanguage? {
            if (raw.isNullOrBlank()) return null
            return ServerLanguage.entries.firstOrNull { option ->
                option.storageId.equals(raw, ignoreCase = true) ||
                    option.label.equals(raw, ignoreCase = true) ||
                    option.legacyValues.any { it.equals(raw, ignoreCase = true) }
            }
        }
    }
}

enum class BurnSubtitles(
    override val storageId: String,
    override val label: String,
    override val legacyValues: Set<String> = emptySet(),
) : StoredOption {
    Automatic("automatic", "Automatic"),
    ImageFormats("image_formats", "Only image formats"),
    Always("always", "Always", setOf("All")),
    Never("never", "Never"),
}

enum class AssSsaDirectPlay(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Experimental("experimental", "Experimental"),
    Enabled("enabled", "Enabled"),
    Disabled("disabled", "Disabled"),
}

enum class DisplayLanguage(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    // storageId doubles as a BCP 47 tag for AppLocaleManager. "system_default" is not a valid
    // BCP 47 language subtag, so it can never collide with a real locale tag chosen below, and it
    // sorts before the fixed languages so a fresh install (no persisted value) follows the device
    // locale instead of forcing English.
    SystemDefault("system_default", "System default"),
    English("en", "English"),
    German("de", "German"),
    Spanish("es", "Spanish"),
    French("fr", "French"),
    Japanese("ja", "Japanese"),
    Korean("ko", "Korean"),
    Portuguese("pt-BR", "Portuguese (Brazil)"),
    Vietnamese("vi", "Vietnamese"),
    Chinese("zh-CN", "Chinese (Simplified)"),
    ;

    /**
     * The language's own name — "Deutsch", "日本語", "Português (Brasil)".
     *
     * A language picker is the one list that should NOT follow the current UI language: someone who
     * has landed in a language they cannot read needs to recognise their own entry to escape. Uses
     * the locale as its own display locale for exactly that reason, unlike [ServerLanguage.displayName]
     * which is read in the surrounding UI's language.
     *
     * [SystemDefault] has no locale of its own and keeps a translated string resource instead.
     */
    fun endonym(): String {
        val locale = Locale.forLanguageTag(storageId)
        val language = locale.getDisplayLanguage(locale).takeIf(String::isNotBlank) ?: return label
        val region = locale.getDisplayCountry(locale).takeIf(String::isNotBlank)
        return (if (region == null) language else "$language ($region)")
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}

enum class InterfaceScale(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Compact("compact", "Compact"),
    Comfortable("comfortable", "Comfortable"),
    Large("large", "Large"),
}

enum class AppTheme(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Dark("dark", "Dark"),
    System("system", "System"),
}

/**
 * The colour palette the television UI is painted with (#101).
 *
 * Each entry is a complete palette rather than an accent hue, and each has a counterpart in the
 * other light/dark mode ([Midnight]/[Daylight] achromatic, [Ember]/[Sunrise] accented) so
 * [AppTheme.System] has something real to switch between. [Midnight] is the look every install has
 * today and stays the default: an upgrade must not repaint anybody's television.
 */
enum class ColorPalette(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    Midnight("midnight", "Midnight"),
    Ember("ember", "Ember"),
    Daylight("daylight", "Daylight"),
    Sunrise("sunrise", "Sunrise"),
}

enum class ScreensaverContent(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    AllLibraries("all_libraries", "All libraries"),
    Movies("movies", "Movies"),
    Shows("shows", "Shows"),
    Music("music", "Music"),
}
