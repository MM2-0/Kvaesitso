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
package com.vitorpamplona.amethyst.commons.podcasts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync

/**
 * Fetches the off-event side files a podcast episode references by URL — the Podcasting-2.0
 * `chapters.json` document and the `transcript` file — so the client can render them in-app.
 * A bounded read cap keeps a hostile/huge file from blowing up memory.
 */
object PodcastRemoteContent {
    /** Refuse bodies larger than this (chapters/transcripts are small text files). */
    private const val MAX_BYTES = 2_000_000L

    suspend fun fetchText(
        url: String,
        okHttpClient: OkHttpClient,
    ): String? =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .get()
                        .build()
                okHttpClient.newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body
                    // Reject an oversized declared length outright; cap the read for chunked bodies.
                    if (body.contentLength() > MAX_BYTES) return@use null
                    body.string().take(MAX_BYTES.toInt())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
}
