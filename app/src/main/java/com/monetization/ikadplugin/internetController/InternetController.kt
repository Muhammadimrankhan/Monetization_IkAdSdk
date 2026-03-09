package com.monetization.ikadplugin.internetController

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class InternetController(private val connectivityManager: ConnectivityManager) {
    val isInternetConnected: Boolean
        get() {
            try {
                val network = connectivityManager.activeNetwork
                if (network != null) {
                    return connectivityManager.getNetworkCapabilities(network)
                        .isNetworkCapabilitiesValid()
                }
            } catch (_: Exception) {
            }
            return false
        }

    private fun NetworkCapabilities?.isNetworkCapabilitiesValid(): Boolean = when {
        this == null -> false
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
                (hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) -> true

        else -> false
    }

    val isVPNConnected: Boolean
        get() {
            try {
                val networks = connectivityManager.allNetworks
                for (network in networks) {
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        return true
                    }
                }
                return false
            } catch (_: Exception) {
            }
            return false
        }

    fun observeVPN(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
//                Log.d(TAG, "onAvailable: VPN connected")
                trySend(true)
            }

            override fun onLost(network: Network) {
//                Log.d(TAG, "onLost: Disconnected")
                trySend(false)
            }
        }
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build(),
            callback
        )
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}