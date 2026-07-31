package com.maik205.shoumeiplayer.player

import java.net.URI

fun redactPlaybackUrl(url: String): String = runCatching {
    val parsed = URI(url)
    URI(parsed.scheme, parsed.authority, parsed.path, null, null).toString()
}.getOrDefault("<redacted>")

fun redactPlaybackHeaders(headers: Map<String, String>): Map<String, String> = headers.mapValues { (name, value) ->
    if (name.equals("Authorization", ignoreCase = true) ||
        name.equals("Cookie", ignoreCase = true) ||
        name.equals("Proxy-Authorization", ignoreCase = true) ||
        name.contains("token", ignoreCase = true) ||
        name.contains("key", ignoreCase = true)
    ) {
        "<redacted>"
    } else {
        value
    }
}
