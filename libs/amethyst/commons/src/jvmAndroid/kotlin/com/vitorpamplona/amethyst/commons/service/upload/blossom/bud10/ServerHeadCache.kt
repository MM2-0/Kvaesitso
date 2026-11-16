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
package com.vitorpamplona.amethyst.commons.service.upload.blossom.bud10

import androidx.collection.LruCache
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

class ServerHeadCache {
    val cache = LruCache<String, HasFile>(200)

    sealed interface HasFile {
        object NoFile : HasFile

        class TypeAndSize(
            val mimeType: String,
            val size: Long,
        ) : HasFile
    }

    suspend fun getFileSizeBytes(
        url: String,
        client: (url: String) -> OkHttpClient,
    ): HasFile {
        cache[url]?.let { return it }

        try {
            // Build a HEAD request instead of GET
            val request =
                Request
                    .Builder()
                    .url(url)
                    .head() // Specifies the HEAD method
                    .build()

            client(url).newCall(request).executeAsync().use { response ->
                if (!response.isSuccessful) {
                    // Only a definitive answer is remembered. A 5xx, 429 or auth challenge is
                    // transient: caching it would hide this server for the app's lifetime.
                    if (response.code == 404 || response.code == 410) {
                        cache.put(url, HasFile.NoFile)
                    }
                    return HasFile.NoFile
                }

                // Retrieve the "Content-Length" header
                val contentLength = response.header("Content-Length")?.toLongOrNull()
                // type/subtype only: a `; charset=binary` parameter must not fail the match below.
                val mimeType = response.header("Content-Type")?.toMediaTypeOrNull()?.let { "${it.type}/${it.subtype}" }

                return if (contentLength != null && mimeType != null) {
                    val result = HasFile.TypeAndSize(mimeType, contentLength)
                    cache.put(url, result)
                    result
                } else {
                    cache.put(url, HasFile.NoFile)
                    HasFile.NoFile
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline, DNS failure, timeout: says nothing about the server having the file,
            // so it is not cached — a brief outage must not hide the blob until eviction.
            return HasFile.NoFile
        }
    }

    suspend fun urlIfServerHasFile(
        server: String,
        filename: String,
        expectedMimeType: String?,
        expectedSize: Long?,
        client: (url: String) -> OkHttpClient,
    ): String? {
        val url =
            if (server.startsWith("http")) {
                server.removeSuffix("/") + "/" + filename
            } else {
                "https://" + server.removeSuffix("/") + "/" + filename
            }

        val result = getFileSizeBytes(url, client)

        if (result is HasFile.TypeAndSize) {
            if (expectedSize == null && expectedMimeType == null) {
                // any match goes
                return url
            } else {
                if (result.size == expectedSize) {
                    return url
                }
                if (expectedSize == null && result.size > 0 && result.mimeType.equals(expectedMimeType, ignoreCase = true)) {
                    return url
                }
            }
        }
        return null
    }
}
