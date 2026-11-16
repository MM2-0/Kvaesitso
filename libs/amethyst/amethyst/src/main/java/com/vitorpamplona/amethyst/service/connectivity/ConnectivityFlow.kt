/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.vitorpamplona.amethyst.commons.service.connectivity.ConnectivityStatus
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

class ConnectivityFlow(
    context: Context,
) {
    @OptIn(FlowPreview::class)
    val status =
        callbackFlow {
            trySend(ConnectivityStatus.StartingService)

            val connectivityManager = context.getConnectivityManager()

            Log.d("ConnectivityFlow", "Starting Connectivity Flow")
            val networkCallback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        Log.d("ConnectivityFlow") { "onAvailable ${network.networkHandle}" }
                        connectivityManager.getNetworkCapabilities(network)?.let {
                            trySend(ConnectivityStatus.Active(network.networkHandle, it.isMeteredOrMobileData()))
                        }
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        super.onCapabilitiesChanged(network, networkCapabilities)
                        val isMobile = networkCapabilities.isMeteredOrMobileData()
                        Log.d("ConnectivityFlow") { "onCapabilitiesChanged ${network.networkHandle} $isMobile" }
                        trySend(ConnectivityStatus.Active(network.networkHandle, isMobile))
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        Log.d("ConnectivityFlow") { "onLost ${network.networkHandle} " }
                        trySend(ConnectivityStatus.Off)
                    }
                }

            connectivityManager.registerDefaultNetworkCallback(networkCallback)

            connectivityManager.activeNetwork?.let { network ->
                connectivityManager.getNetworkCapabilities(network)?.let {
                    trySend(ConnectivityStatus.Active(network.networkHandle, it.isMeteredOrMobileData()))
                }
            }

            awaitClose {
                Log.d("ConnectivityFlow", "Stopping Connectivity Flow")
                connectivityManager.unregisterNetworkCallback(networkCallback)
                trySend(ConnectivityStatus.Off)
            }
        }.distinctUntilChanged().debounce(200).flowOn(Dispatchers.IO)
}

fun NetworkCapabilities.isMeteredOrMobileData(): Boolean {
    val metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    val mobileData = hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    return metered || mobileData
}

fun Context.getConnectivityManager() = getSystemService(ConnectivityManager::class.java) as ConnectivityManager
