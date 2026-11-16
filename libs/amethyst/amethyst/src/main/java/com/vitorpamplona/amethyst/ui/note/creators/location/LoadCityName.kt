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
package com.vitorpamplona.amethyst.ui.note.creators.location

import android.content.Context
import android.location.Location
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.vitorpamplona.amethyst.service.location.CachedReversedGeoLocations
import com.vitorpamplona.amethyst.service.location.toLocation
import com.vitorpamplona.quartz.nip01Core.tags.geohash.toGeoHash
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val MAX_GEOLOCATE_RETRIES = 5

@Composable
fun LoadCityName(
    geohashStr: String,
    onLoading: (@Composable () -> Unit)? = null,
    content: @Composable (String) -> Unit,
) {
    var cityName by remember(geohashStr) { mutableStateOf(CachedReversedGeoLocations.cached(geohashStr)) }

    val resolved = cityName
    if (resolved != null) {
        content(resolved)
    } else if (!CachedReversedGeoLocations.isGeocoderAvailable()) {
        // Devices without a system Geocoder backend (e.g. AOSP / GrapheneOS
        // without Google Play Services or microG) cannot reverse-geocode at
        // all. Show the geohash directly instead of spinning forever.
        content(geohashStr)
    } else {
        onLoading?.invoke()

        val context = LocalContext.current

        LaunchedEffect(geohashStr, context) {
            val location = runCatching { geohashStr.toGeoHash() }.getOrNull()?.toLocation()
            if (location == null) {
                cityName = geohashStr
                return@LaunchedEffect
            }

            var delayMs = 1000L
            repeat(MAX_GEOLOCATE_RETRIES) {
                val result = geoLocateOnce(geohashStr, location, context)
                if (result != null) {
                    cityName = result
                    return@LaunchedEffect
                }
                delay(delayMs)
                delayMs *= 2L
            }
            // Geocoder kept returning errors — give up and render the geohash
            // so the loading indicator stops.
            cityName = geohashStr
        }
    }
}

private suspend fun geoLocateOnce(
    geohashStr: String,
    location: Location,
    context: Context,
): String? =
    suspendCancellableCoroutine { cont ->
        CachedReversedGeoLocations.geoLocate(geohashStr, location, context) { result ->
            if (cont.isActive) cont.resume(result)
        }
    }
