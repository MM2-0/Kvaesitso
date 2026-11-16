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
package com.vitorpamplona.amethyst.commons.service.uploads

import com.vitorpamplona.crypto.sha2.sha256StreamWithCount
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync

/**
 * Downloads files back from media servers. [maxAttempts] and [retryDelayMs] set how long the
 * `wait*` calls give a server that has not started serving a fresh upload yet.
 */
class ImageDownloader(
    private val maxAttempts: Int = 15,
    private val retryDelayMs: Long = 1000,
) {
    class Blob(
        val bytes: ByteArray,
        val contentType: String?,
    ) {
        /** The server's Content-Type without parameters (`image/jpeg; charset=…` → `image/jpeg`). */
        fun mimeType(): String? =
            contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                ?.ifEmpty { null }

        /**
         * The type of the bytes the server sent when it names an image: what an imeta's `m` should
         * say, even when a CDN serves WebP behind a `.jpg` URL.
         */
        fun imageMimeType(): String? = mimeType()?.takeIf { it.startsWith("image/") }
    }

    /**
     * Result of streaming verification - hash and metadata without storing full file
     */
    class StreamVerification(
        val hash: HexKey,
        val size: Long,
        val contentType: String?,
    )

    /**
     * Runs [operation] until it returns a value, [maxAttempts] times at most, [retryDelayMs] apart:
     * a server that just accepted a file often does not serve it yet.
     */
    private suspend fun <T> retryWithDelay(operation: suspend () -> T?): T? {
        repeat(maxAttempts) { attempt ->
            val result =
                try {
                    operation()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    null
                }
            if (result != null) return result
            if (attempt < maxAttempts - 1) delay(retryDelayMs)
        }
        return null
    }

    /**
     * Downloads [imageUrl] and hashes it as it streams, without holding the file in memory.
     * Null when the server never served it.
     */
    suspend fun waitAndVerifyStream(
        imageUrl: String,
        okHttpClient: (url: String) -> OkHttpClient,
    ): StreamVerification? =
        retryWithDelay {
            get(imageUrl, okHttpClient(imageUrl)) { response ->
                val (hash, totalBytes) = response.body.byteStream().use { sha256StreamWithCount(it) }
                StreamVerification(hash.toHexKey(), totalBytes, response.header("Content-Type"))
            }
        }

    /** What a `HEAD` request says about a file: [ServedCheck.Served], or that the server has no `HEAD`. */
    sealed interface ServedCheck {
        /** [size] is null when the server did not say, or said it for an encoded (compressed) body. */
        class Served(
            val size: Long?,
            val contentType: String?,
        ) : ServedCheck

        data object HeadNotSupported : ServedCheck
    }

    /**
     * Waits for [url] to be served, asking with `HEAD` so nothing is downloaded: enough when the
     * caller already knows the bytes, as after a Blossom upload whose descriptor names the hash
     * that was sent. Null when the server never served it.
     */
    suspend fun waitUntilServed(
        url: String,
        okHttpClient: (url: String) -> OkHttpClient,
    ): ServedCheck? =
        retryWithDelay {
            okHttpClient(url)
                .newCall(
                    Request
                        .Builder()
                        .url(url)
                        .head()
                        .build(),
                ).executeAsync()
                .use { response ->
                    when {
                        response.isSuccessful -> {
                            val size = response.header("Content-Length")?.toLongOrNull()?.takeIf { response.header("Content-Encoding") == null }
                            ServedCheck.Served(size, response.header("Content-Type"))
                        }

                        response.code == 405 || response.code == 501 -> ServedCheck.HeadNotSupported

                        else -> null
                    }
                }
        }

    suspend fun waitAndGetImage(
        imageUrl: String,
        okHttpClient: (url: String) -> OkHttpClient,
    ): Blob? =
        retryWithDelay {
            get(imageUrl, okHttpClient(imageUrl)) { response ->
                Blob(response.body.bytes(), response.header("Content-Type"))
            }
        }

    /**
     * GETs [url] and hands a successful response to [read], or returns null for any other status.
     * The body is read on [Dispatchers.IO]; cancelling the caller cancels the call, which closes
     * the socket a blocked read is waiting on instead of leaving it to the read timeout.
     */
    private suspend fun <T> get(
        url: String,
        client: OkHttpClient,
        read: (Response) -> T,
    ): T? =
        coroutineScope {
            val call = client.newCall(Request.Builder().url(url).build())
            call.executeAsync().use { response ->
                if (!response.isSuccessful) return@coroutineScope null
                // runCatching keeps a read error from cancelling this scope: it comes back as the
                // IOException it is, which the retry loop handles, not as a cancellation.
                val reader = async(Dispatchers.IO) { runCatching { read(response) } }
                try {
                    reader.await().getOrThrow()
                } catch (e: CancellationException) {
                    call.cancel()
                    throw e
                }
            }
        }

    /**
     * One GET of a link someone pasted, through [client] so it carries the app's user agent, DNS
     * and Tor routing. Unlike [waitAndGetImage], which waits for a server still publishing a file
     * we just uploaded, it does not retry: a dead link fails at once. Null on any failure, or when
     * the body is larger than [maxBytes].
     */
    suspend fun fetch(
        url: String,
        client: OkHttpClient,
        maxBytes: Long = MAX_LINKED_FILE_BYTES,
    ): Blob? =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(url).build()).executeAsync().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body.contentLength() > maxBytes) return@use null
                    val source = body.source()
                    // Fills the buffer up to one byte past the limit: true means the file is too big.
                    if (source.request(maxBytes + 1)) return@use null
                    Blob(source.buffer.readByteArray(), response.header("Content-Type"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }

    companion object {
        const val MAX_LINKED_FILE_BYTES = 20L * 1024 * 1024
    }
}
