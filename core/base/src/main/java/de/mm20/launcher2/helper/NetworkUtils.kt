package de.mm20.launcher2.helper

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object NetworkUtils {

    enum class NetworkType {
        Offline,
        Metered,
        NotMetered,
    }

    fun currentNetworkType(context: Context): Flow<NetworkType> = callbackFlow {
        val connectivityManager: ConnectivityManager = context.getSystemService(ConnectivityManager::class.java)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network : Network, networkCapabilities : NetworkCapabilities) {
                trySend(
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
                        NetworkType.NotMetered
                    } else {
                        NetworkType.Metered
                    }
                )
            }

            override fun onLost(network: Network) {
                trySend(NetworkType.Offline)
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}
