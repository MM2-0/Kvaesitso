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
package com.vitorpamplona.amethyst.service.uploads

import android.media.MediaDataSource
import com.vitorpamplona.amethyst.commons.service.upload.FileHeader
import com.vitorpamplona.amethyst.commons.service.uploads.ImageDownloader
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import java.io.IOException

/** Downloads [fileUrl] and computes its [FileHeader] with [prepare]. */
suspend fun FileHeader.Companion.prepare(
    fileUrl: String,
    mimeType: String?,
    dimPrecomputed: DimensionTag?,
    okHttpClient: (String) -> OkHttpClient,
): Result<FileHeader> =
    try {
        val imageData: ImageDownloader.Blob? = ImageDownloader().waitAndGetImage(fileUrl, okHttpClient)

        if (imageData != null) {
            prepare(imageData.bytes, mimeType ?: imageData.contentType, dimPrecomputed)
        } else {
            Result.failure(FileHeader.UnableToDownload(fileUrl))
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e("ImageDownload") { "Couldn't download image from server: ${e.message}" }
        Result.failure(e)
    }

/** Hashes [data] and decodes its dimensions and preview hashes (Android media APIs). */
fun FileHeader.Companion.prepare(
    data: ByteArray,
    mimeType: String?,
    dimPrecomputed: DimensionTag?,
): Result<FileHeader> =
    try {
        val hash = sha256(data).toHexKey()
        val size = data.size

        val preview = PreviewMetadataCalculator.computeFromBytes(data, mimeType, dimPrecomputed)

        Result.success(FileHeader(mimeType, hash, size, preview.dim, preview.blurhash, preview.thumbhash))
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e("ImageDownload") { "Couldn't convert image in to File Header: ${e.message}" }
        Result.failure(e)
    }

class ByteArrayMediaDataSource(
    var imageData: ByteArray,
) : MediaDataSource() {
    override fun getSize(): Long = imageData.size.toLong()

    @Throws(IOException::class)
    override fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int,
    ): Int {
        if (position >= imageData.size) {
            return -1
        }
        val newSize =
            if (position + size > imageData.size) {
                size - ((position.toInt() + size) - imageData.size)
            } else {
                size
            }

        imageData.copyInto(buffer, offset, position.toInt(), position.toInt() + newSize)

        return newSize
    }

    @Throws(IOException::class)
    override fun close() {
        // No resources to release; data is an in-memory byte array
    }
}
