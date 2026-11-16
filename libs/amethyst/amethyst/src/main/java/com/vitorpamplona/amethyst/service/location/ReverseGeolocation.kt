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
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import com.vitorpamplona.quartz.utils.Log
import java.io.IOException

@Suppress("DEPRECATION")
class ReverseGeolocation {
    companion object {
        fun execute(
            location: Location,
            context: Context,
            onReady: (List<Address>?) -> Unit,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                executeAsync(location, context, onReady)
            } else {
                onReady(executeSync(location, context))
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        fun executeAsync(
            location: Location,
            context: Context,
            onReady: (List<Address>?) -> Unit,
        ) {
            val locationCallback =
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: List<Address>) {
                        Log.d("ReverseGeoLocation") { "Found ${addresses.size} new addresses" }
                        onReady(addresses)
                    }

                    override fun onError(errorMessage: String?) {
                        super.onError(errorMessage)
                        Log.w("ReverseGeoLocation") { "Failure $errorMessage" }
                        onReady(null)
                    }
                }

            Log.d("ReverseGeoLocation") { "Execute Async $location" }
            Geocoder(context).getFromLocation(
                location.latitude,
                location.longitude,
                1,
                locationCallback,
            )
        }

        fun executeSync(
            location: Location,
            context: Context,
        ): List<Address>? {
            Log.d("ReverseGeoLocation") { "Execute Sync $location" }
            return try {
                Geocoder(context).getFromLocation(
                    location.latitude,
                    location.longitude,
                    1,
                )
            } catch (e: IOException) {
                Log.w("ReverseGeolocation", "IO Error", e)
                e.printStackTrace()
                return null
            }
        }
    }
}
