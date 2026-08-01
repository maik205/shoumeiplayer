package com.maik205.shoumeiplayer.data.api

import java.net.InetAddress
import java.net.URI

private val IPV4_LITERAL = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
private val LOCAL_HOST_SUFFIXES = listOf(".local", ".lan", ".home", ".internal")

/**
 * Jellyfin servers are almost always reached over a home LAN, so cleartext http:// has to stay
 * usable — but only for hosts that are actually private. This is a static, no-DNS check: it
 * recognizes IPv4 literals in RFC 1918 / loopback / link-local ranges, common local hostname
 * suffixes (`.local`, `.lan`, `.home`, `.internal`, bare `localhost`), and bare unqualified
 * hostnames (no dot at all, e.g. `myserver`, `nas`) -- a label with no dot can only ever resolve
 * via mDNS/NetBIOS/a local DNS search domain, never public DNS, so it is local by construction.
 * Anything else must use https://, matching the [android.R.attr.usesCleartextTraffic] policy this
 * backs.
 */
internal fun isPrivateOrLocalHost(host: String): Boolean {
    val normalized = host.trim().lowercase().removeSurrounding("[", "]")
    if (normalized == "localhost") return true
    if (LOCAL_HOST_SUFFIXES.any(normalized::endsWith)) return true
    if (!normalized.contains('.')) return true

    val ipv4 = IPV4_LITERAL.matchEntire(normalized) ?: return false
    val octets = ipv4.groupValues.drop(1).map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return runCatching {
        val address = InetAddress.getByAddress(octets.map { it.toByte() }.toByteArray())
        address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress
    }.getOrDefault(false)
}

/** Returns a message describing why [url] would be rejected, or null if it's allowed to proceed. */
internal fun cleartextRejectionReason(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (!uri.scheme.equals("http", ignoreCase = true)) return null
    val host = uri.host ?: return null
    if (isPrivateOrLocalHost(host)) return null
    return "Refusing plain http:// to a non-private host ($host); use https:// for servers reachable outside your LAN"
}
