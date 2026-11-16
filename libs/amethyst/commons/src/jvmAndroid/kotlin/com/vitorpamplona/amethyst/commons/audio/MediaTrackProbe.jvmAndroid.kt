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
package com.vitorpamplona.amethyst.commons.audio

import com.vitorpamplona.amethyst.commons.service.MediaSizeCache
import com.vitorpamplona.amethyst.commons.service.fileLength
import com.vitorpamplona.amethyst.commons.service.http.IRoleBasedHttpClientBuilder
import com.vitorpamplona.amethyst.commons.service.parseContentRangeTotal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

internal actual suspend fun IRoleBasedHttpClientBuilder.readMp4AudioOnly(url: String): Boolean? {
    val client = okHttpClientForVideo(url)
    return probeMp4AudioOnly { offset, length ->
        withContext(Dispatchers.IO) {
            try {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("Range", "bytes=$offset-${offset + length - 1}")
                        .build()
                client.newCall(request).execute().use { response ->
                    when {
                        // Past the end of the file: an empty read, which ends the walk.
                        response.code == 416 -> ByteArray(0)
                        response.code == 206 -> {
                            // The first answer says how big the whole file is: a held-back placeholder shows it.
                            if (offset == 0L) parseContentRangeTotal(response.header("Content-Range"))?.let { MediaSizeCache.put(url, it) }
                            response.body.source().use { it.readAtMost(length) }
                        }
                        // A server that ignores Range sends the whole file from the start. That still
                        // answers the first block; any later block would mean downloading the file.
                        response.code == 200 && offset == 0L -> {
                            response.fileLength()?.let { MediaSizeCache.put(url, it) }
                            response.body.source().use { it.readAtMost(length) }
                        }
                        else -> null
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
    }
}

private fun okio.BufferedSource.readAtMost(length: Int): ByteArray {
    request(length.toLong())
    return readByteArray(minOf(length.toLong(), buffer.size))
}
