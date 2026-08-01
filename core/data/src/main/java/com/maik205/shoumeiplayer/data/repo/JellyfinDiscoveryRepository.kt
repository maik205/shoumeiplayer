package com.maik205.shoumeiplayer.data.repo

import com.maik205.shoumeiplayer.data.session.normalizeServerUrl
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val JELLYFIN_DISCOVERY_PORT = 7359
private const val JELLYFIN_DISCOVERY_MESSAGE = "Who is JellyfinServer?"
private const val RECEIVE_SLICE_MILLIS = 250
private const val MAX_DISCOVERY_RESPONSE_BYTES = 8 * 1024

data class DiscoveredJellyfinServer(
    val address: String,
    val id: String? = null,
    val name: String? = null,
    val endpointAddress: String? = null,
)

@Serializable
private data class DiscoveryResponseDto(
    @SerialName("Address") val address: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("EndpointAddress") val endpointAddress: String? = null,
)

/**
 * Jellyfin's UDP discovery protocol. A bounded receive loop ensures a device with
 * no route, no Wi-Fi, or no Jellyfin server never blocks the connection screen.
 */
class JellyfinDiscoveryRepository(
    private val port: Int = JELLYFIN_DISCOVERY_PORT,
    private val timeoutMillis: Long = 2_000,
) {
    suspend fun discover(): List<DiscoveredJellyfinServer> = withContext(Dispatchers.IO) {
        val discovered = mutableListOf<DiscoveredJellyfinServer>()
        val deadlineNanos = System.nanoTime() + timeoutMillis.coerceAtLeast(0) * 1_000_000

        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val requestBytes = JELLYFIN_DISCOVERY_MESSAGE.toByteArray(StandardCharsets.UTF_8)
                discoveryTargets().forEach { target ->
                    runCatching {
                        socket.send(DatagramPacket(requestBytes, requestBytes.size, target, port))
                    }
                }

                val buffer = ByteArray(MAX_DISCOVERY_RESPONSE_BYTES)
                while (System.nanoTime() < deadlineNanos) {
                    val remainingMillis = ((deadlineNanos - System.nanoTime()) / 1_000_000)
                        .coerceAtLeast(1)
                    socket.soTimeout = minOf(RECEIVE_SLICE_MILLIS.toLong(), remainingMillis).toInt()
                    val packet = DatagramPacket(buffer, buffer.size)
                    val response = runCatching {
                        socket.receive(packet)
                        String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                    }.getOrNull() ?: continue
                    parseDiscoveryResponse(response)?.let(discovered::add)
                }
            }
        } catch (_: Exception) {
            // Discovery is opportunistic. Manual server entry remains available.
        }

        Collections.unmodifiableList(normalizeDiscoveredServers(discovered))
    }

    private fun discoveryTargets(): Set<InetAddress> {
        val targets = linkedSetOf<InetAddress>()
        runCatching { targets += InetAddress.getByName("255.255.255.255") }
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                networkInterface.interfaceAddresses
                    .mapNotNull { it.broadcast }
                    .filterIsInstance<Inet4Address>()
                    .forEach(targets::add)
            }
        }
        return targets
    }
}

private val discoveryJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun parseDiscoveryResponse(payload: String): DiscoveredJellyfinServer? {
    val dto = runCatching {
        discoveryJson.decodeFromString<DiscoveryResponseDto>(payload.trim().trim('\u0000'))
    }.getOrNull() ?: return null
    val normalizedAddress = normalizeDiscoveredAddress(dto.address ?: dto.endpointAddress) ?: return null
    return DiscoveredJellyfinServer(
        address = normalizedAddress,
        id = dto.id?.takeIf(String::isNotBlank),
        name = dto.name?.takeIf(String::isNotBlank),
        endpointAddress = dto.endpointAddress?.takeIf(String::isNotBlank),
    )
}

internal fun normalizeDiscoveredServers(
    servers: Iterable<DiscoveredJellyfinServer>,
): List<DiscoveredJellyfinServer> {
    val byEndpoint = linkedMapOf<String, DiscoveredJellyfinServer>()
    servers.forEach { server ->
        val normalized = normalizeDiscoveredAddress(server.address) ?: return@forEach
        byEndpoint.putIfAbsent(normalized.lowercase(), server.copy(address = normalized))
    }
    return byEndpoint.values.sortedWith(
        compareBy<DiscoveredJellyfinServer> { it.name?.lowercase().orEmpty() }
            .thenBy { it.address.lowercase() },
    )
}

private fun normalizeDiscoveredAddress(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val normalized = normalizeServerUrl(raw, defaultScheme = "http")
    val uri = runCatching { URI(normalized) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
    return if (uri.scheme == scheme) {
        normalized
    } else {
        scheme + normalized.substring(uri.scheme.length)
    }
}
