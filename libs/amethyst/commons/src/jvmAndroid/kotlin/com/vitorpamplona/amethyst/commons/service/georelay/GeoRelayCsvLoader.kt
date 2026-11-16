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
package com.vitorpamplona.amethyst.commons.service.georelay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

/**
 * Fetches and parses the live georelays CSV so the [GeoRelayDirectory] can route
 * geohash channel traffic to the same relays Bitchat uses.
 *
 * The [okHttpClient] provider is supplied by the host app so the fetch runs over
 * whatever transport the user configured (including Tor). Failures return an
 * empty list; callers keep the previous/fallback directory in that case.
 */
class GeoRelayCsvLoader(
    val okHttpClient: (String) -> OkHttpClient,
) {
    suspend fun fetch(url: String = GeoRelayDirectory.CSV_URL): List<GeoRelay> =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()
            okHttpClient(url).newCall(request).executeAsync().use { response ->
                if (response.isSuccessful) {
                    GeoRelayDirectory.parseCsv(response.body.string())
                } else {
                    emptyList()
                }
            }
        }

    /** Fetches the CSV and, if non-empty, applies it to [directory]. Returns the number of relays loaded. */
    suspend fun refresh(
        directory: GeoRelayDirectory,
        url: String = GeoRelayDirectory.CSV_URL,
    ): Int {
        val relays = runCatching { fetch(url) }.getOrDefault(emptyList())
        directory.setRelays(relays)
        return relays.size
    }
}
