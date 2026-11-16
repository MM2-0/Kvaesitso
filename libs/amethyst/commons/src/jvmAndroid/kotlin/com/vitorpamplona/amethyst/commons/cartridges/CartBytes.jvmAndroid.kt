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
package com.vitorpamplona.amethyst.commons.cartridges

import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.BufferedSource

private const val TAG = "CartBytes"

actual suspend fun IRoleBasedHttpClientBuilder.fetchMediaPrefix(
    url: String,
    count: Int,
): ByteArray? =
    get(url, mapOf("Range" to "bytes=0-${count - 1}")) { code, source ->
        // A server that ignores Range sends the whole file; the first bytes still answer.
        if (code == 206 || code == 200) source.readAtMost(count) else null
    }

actual suspend fun IRoleBasedHttpClientBuilder.fetchMedia(
    url: String,
    maxBytes: Int,
): ByteArray? =
    get(url, emptyMap()) { code, source ->
        if (code != 200) {
            Log.w(TAG) { "The cart at $url answered HTTP $code" }
            return@get null
        }
        val bytes = source.readAtMost(maxBytes + 1)
        if (bytes.size > maxBytes) {
            Log.w(TAG) { "The cart at $url is over $maxBytes bytes" }
            null
        } else {
            bytes
        }
    }

private suspend fun IRoleBasedHttpClientBuilder.get(
    url: String,
    headers: Map<String, String>,
    read: (code: Int, source: BufferedSource) -> ByteArray?,
): ByteArray? {
    if (!url.startsWith("https://") && !url.startsWith("http://")) return null
    return withContext(Dispatchers.IO) {
        try {
            val client = okHttpClientForImage(url)
            val request =
                Request
                    .Builder()
                    .url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
            client.newCall(request).execute().use { response -> response.body.source().use { read(response.code, it) } }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Could not download the cart at $url", e)
            null
        }
    }
}

private fun BufferedSource.readAtMost(length: Int): ByteArray {
    request(length.toLong())
    return readByteArray(minOf(length.toLong(), buffer.size))
}
