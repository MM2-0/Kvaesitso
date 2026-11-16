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
package com.vitorpamplona.amethyst.commons.preview

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.coroutines.executeAsync
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8

class UrlPreview {
    suspend fun fetch(
        url: String,
        okHttpClient: (String) -> OkHttpClient,
        onComplete: suspend (urlInfo: UrlInfoItem) -> Unit,
        onFailed: suspend (t: Throwable) -> Unit,
    ) = try {
        onComplete(getDocument(url, okHttpClient))
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        onFailed(t)
    }

    suspend fun getDocument(
        url: String,
        okHttpClient: (String) -> OkHttpClient,
    ): UrlInfoItem =
        withContext(Dispatchers.IO) {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .build()

            val client = okHttpClient(url)

            client.newCall(request).executeAsync().use { response ->
                withContext(Dispatchers.IO) {
                    if (response.isSuccessful) {
                        val mimeType =
                            response.headers["Content-Type"]?.toMediaType()
                                ?: throw IllegalArgumentException("Website returned unknown mimetype: ${response.headers["Content-Type"]}")
                        when {
                            mimeType.type == "text" && mimeType.subtype == "html" -> {
                                val metaTags = HtmlParser().parseHtml(response.body.readPrefix(MAX_PREVIEW_BYTES), mimeType.charset()?.name())
                                val data = OpenGraphParser().extractUrlInfo(metaTags)
                                UrlInfoItem(
                                    url,
                                    data.title,
                                    data.description,
                                    data.image,
                                    mimeType.toString(),
                                    data.audio,
                                    data.audioType,
                                    data.video,
                                    data.videoType,
                                    data.type,
                                    data.icon,
                                    data.siteName,
                                    data.author,
                                )
                            }

                            mimeType.type == "image" -> {
                                UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                            }

                            mimeType.type == "video" -> {
                                UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                            }

                            // A URL with no audio extension that turns out to serve audio anyway
                            // (`/download?id=7`) used to land in the `else` below and throw, losing
                            // the note's link entirely. The renderer plays it off the MIME. `image`
                            // carries the URL for the same reason the video branch above does: it
                            // is what UrlInfoItem.fetchComplete reads to decide the fetch produced
                            // something renderable.
                            mimeType.type == "audio" -> {
                                UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                            }

                            mimeType.type == "application" && mimeType.subtype == "pdf" -> {
                                UrlInfoItem(url, image = url, mimeType = mimeType.toString())
                            }

                            else -> {
                                throw IllegalArgumentException("Website returned unknown encoding for previews: $mimeType")
                            }
                        }
                    } else {
                        throw IllegalArgumentException("Website returned: " + response.code)
                    }
                }
            }
        }
}

/**
 * Link previews only need the `<head>` (MetaTagsParser stops at `</head>`), and
 * the URL is attacker-controlled: any note can point at a host that streams
 * `text/html` forever. Read until the head closes, and never past [max] bytes,
 * instead of `bytes()`. Stopping at the head matters on slow links: a GitHub page
 * is ~500 KB but closes its head ~34 KB in, and the card's image only starts
 * loading once this returns.
 */
private fun ResponseBody.readPrefix(max: Long): ByteArray =
    source().use { src ->
        val buffer = src.buffer
        var searched = 0L
        while (buffer.size < max) {
            val more = src.request(minOf(buffer.size + READ_STEP_BYTES, max))
            // Back up by the marker's length, so one split across two reads is still found.
            val from = maxOf(0L, searched - HEAD_CLOSE.size)
            searched = buffer.size
            if (closesHead(buffer, from)) break
            if (!more) break
        }
        buffer.readByteArray(minOf(buffer.size, max))
    }

/**
 * Whether [buffer] has the head's end, looking for a new one from [from]. The bytes are only a cheap
 * first test: they also match a `</head>` inside a script or a comment, which the parser skips, and
 * stopping there would cut off the meta tags still to come. Tags are ASCII, so Latin-1 keeps them.
 */
private fun closesHead(
    buffer: Buffer,
    from: Long,
): Boolean {
    if (buffer.indexOf(HEAD_CLOSE, from) < 0 && buffer.indexOf(HEAD_CLOSE_UPPER, from) < 0) return false
    return MetaTagsParser.reachesHeadEnd(buffer.snapshot().string(Charsets.ISO_8859_1))
}

private val HEAD_CLOSE = "</head".encodeUtf8()
private val HEAD_CLOSE_UPPER = "</HEAD".encodeUtf8()

private const val READ_STEP_BYTES = 16L * 1024L

// Large enough for real `<head>`s, not just typical ones. YouTube's watch page front-loads its
// head with inline script: youtu.be/fwcKTYvupJw (2026-09) is 1.29 MB, and its og: block starts
// 708,776 bytes in, before `</head>` at 716,956. The earlier 512 KB cut it off, so every YouTube
// link lost its card. 2 MB leaves headroom for that head to grow while still bounding a body
// that never ends.
private const val MAX_PREVIEW_BYTES = 2L * 1024L * 1024L
