package com.maik205.shoumeiplayer.domain.settings

interface StoredOption {
    val storageId: String
    val label: String
    val legacyValues: Set<String>
        get() = emptySet()
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

enum class SubtitleMode(
    override val storageId: String,
    override val label: String,
    override val legacyValues: Set<String> = emptySet(),
) : StoredOption {
    Smart("smart", "Smart"),
    Always("always", "Always"),
    OnlyForced("only_forced", "Only forced", setOf("OnlyForced")),
    None("none", "None"),
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
    English("en", "English"),
    Vietnamese("vi", "Vietnamese"),
    Japanese("ja", "Japanese"),
    French("fr", "French"),
    German("de", "German"),
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

enum class ScreensaverContent(
    override val storageId: String,
    override val label: String,
) : StoredOption {
    AllLibraries("all_libraries", "All libraries"),
    Movies("movies", "Movies"),
    Shows("shows", "Shows"),
    Music("music", "Music"),
}
