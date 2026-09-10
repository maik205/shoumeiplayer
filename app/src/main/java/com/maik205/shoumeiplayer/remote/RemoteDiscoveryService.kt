package com.maik205.shoumeiplayer.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log

/**
 * Registers the Shoumei Remote service over Network Service Discovery (mDNS / DNS-SD)
 * so companion apps on the same Wi-Fi network discover the TV automatically.
 */
class RemoteDiscoveryService(
    private val context: Context,
) {
    companion object {
        const val SERVICE_TYPE = "_shoumei-remote._tcp."
        private const val TAG = "RemoteDiscoveryService"
    }

    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    @Volatile
    private var isRegistered = false

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            isRegistered = true
            Log.i(TAG, "Remote service registered: ${serviceInfo.serviceName} on port ${serviceInfo.port}")
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            isRegistered = false
            Log.e(TAG, "Remote service registration failed with error code $errorCode")
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            isRegistered = false
            Log.i(TAG, "Remote service unregistered: ${serviceInfo.serviceName}")
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Log.w(TAG, "Remote service unregistration failed with error code $errorCode")
        }
    }

    fun register(port: Int, tvName: String = "Shoumei TV (${Build.MODEL})") {
        if (isRegistered || port <= 0) return

        val serviceInfo = NsdServiceInfo().apply {
            serviceType = SERVICE_TYPE
            serviceName = tvName
            this.port = port
            setAttribute("version", "1")
            setAttribute("model", Build.MODEL)
            setAttribute("brand", Build.BRAND)
        }

        try {
            nsdManager?.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering NSD service", e)
        }
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            nsdManager?.unregisterService(registrationListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering NSD service", e)
        } finally {
            isRegistered = false
        }
    }
}
