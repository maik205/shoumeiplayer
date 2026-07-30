package com.maik205.shoumeiplayer.platform.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class NetworkSnapshot(
    val validated: Boolean,
    val metered: Boolean,
    val transport: String,
)

internal class AndroidNetworkMonitor(context: Context) {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)
    private val _snapshot = MutableStateFlow(currentSnapshot())
    val snapshot: StateFlow<NetworkSnapshot> = _snapshot.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh(network)
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            refresh(network, capabilities)
        }
        override fun onLost(network: Network) {
            if (connectivityManager?.activeNetwork == network) _snapshot.value = NetworkSnapshot(false, false, "none")
        }
    }

    init {
        connectivityManager?.registerDefaultNetworkCallback(callback)
    }

    private fun refresh(network: Network, capabilities: NetworkCapabilities? = null) {
        if (connectivityManager?.activeNetwork != network) return
        val current = capabilities ?: connectivityManager.getNetworkCapabilities(network) ?: return
        _snapshot.value = current.toNetworkSnapshot()
    }

    private fun currentSnapshot(): NetworkSnapshot =
        connectivityManager?.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it)?.toNetworkSnapshot() }
            ?: NetworkSnapshot(false, false, "none")
}

private fun NetworkCapabilities.toNetworkSnapshot(): NetworkSnapshot = NetworkSnapshot(
    validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
    metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    transport = when {
        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
        else -> "other"
    },
)
