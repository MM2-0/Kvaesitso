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
package com.vitorpamplona.amethyst.service.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.vitorpamplona.quartz.utils.Log
import java.io.IOException

/**
 * Forward geocoding: turn a free-text place query (a city, address, or landmark)
 * into a list of candidate [Address]es so the user can jump the map there.
 *
 * The mirror image of [ReverseGeolocation]: on TIRAMISU+ it uses the async
 * listener overload of [Geocoder.getFromLocationName] (the blocking one is
 * deprecated there), and falls back to the synchronous call on older devices.
 * Both branches funnel through [onReady]; a null result means "no backend, an
 * error, or nothing matched" and the caller should degrade gracefully.
 */
@Suppress("DEPRECATION")
class ForwardGeolocation {
    companion object {
        const val MAX_RESULTS = 5

        fun execute(
            query: String,
            context: Context,
            onReady: (List<Address>?) -> Unit,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                executeAsync(query, context, onReady)
            } else {
                onReady(executeSync(query, context))
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun executeAsync(
            query: String,
            context: Context,
            onReady: (List<Address>?) -> Unit,
        ) {
            val listener =
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: List<Address>) {
                        Log.d("ForwardGeoLocation") { "Found ${addresses.size} addresses for $query" }
                        onReady(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        super.onError(errorMessage)
                        Log.w("ForwardGeoLocation") { "Failure $errorMessage" }
                        onReady(null)
                    }
                }

            Log.d("ForwardGeoLocation") { "Execute Async $query" }
            Geocoder(context).getFromLocationName(query, MAX_RESULTS, listener)
        }

        fun executeSync(
            query: String,
            context: Context,
        ): List<Address>? {
            Log.d("ForwardGeoLocation") { "Execute Sync $query" }
            return try {
                Geocoder(context).getFromLocationName(query, MAX_RESULTS)
            } catch (e: IOException) {
                Log.w("ForwardGeolocation", "IO Error", e)
                null
            }
        }
    }
}
