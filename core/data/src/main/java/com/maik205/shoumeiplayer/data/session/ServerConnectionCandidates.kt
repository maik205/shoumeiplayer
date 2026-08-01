package com.maik205.shoumeiplayer.data.session

data class ServerConnectionCandidate(
    val url: String,
    val requiresInsecureWarning: Boolean = false,
)

/**
 * Builds the endpoints the connection screen may probe. A discovered HTTP endpoint is only a
 * discovery hint: the secure equivalent gets the first chance before the local HTTP fallback.
 */
fun serverConnectionCandidates(
    rawUrl: String,
    prioritizeHttps: Boolean,
): List<ServerConnectionCandidate> {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return emptyList()

    val normalized = normalizeServerUrl(trimmed)
    val hasExplicitScheme = trimmed.contains("://")
    val isHttp = normalized.startsWith("http://", ignoreCase = true)
    if (!prioritizeHttps || normalized.startsWith("https://", ignoreCase = true)) {
        return listOf(
            ServerConnectionCandidate(
                url = normalized,
                requiresInsecureWarning = isHttp,
            ),
        )
    }
    if (hasExplicitScheme && !isHttp) return listOf(ServerConnectionCandidate(normalized))

    val secure = if (isHttp) {
        "https://${normalized.substringAfter("://")}"
    } else {
        normalizeServerUrl(trimmed, defaultScheme = "https")
    }
    val insecure = if (isHttp) normalized else normalizeServerUrl(trimmed, defaultScheme = "http")
    return listOf(
        ServerConnectionCandidate(url = secure),
        ServerConnectionCandidate(url = insecure, requiresInsecureWarning = true),
    ).distinctBy(ServerConnectionCandidate::url)
}
