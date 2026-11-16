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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.vitorpamplona.amethyst.commons.blurhash.toBlurhash
import com.vitorpamplona.amethyst.commons.service.image.BlurhashWrapper
import com.vitorpamplona.amethyst.commons.service.image.ThumbhashWrapper
import com.vitorpamplona.amethyst.commons.service.uploads.PreviewHashes
import com.vitorpamplona.amethyst.commons.service.uploads.isAvif
import com.vitorpamplona.amethyst.commons.thumbhash.toThumbhash
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import com.vitorpamplona.quartz.utils.Log
import java.nio.ByteBuffer

object PreviewMetadataCalculator {
    private const val LOG_TAG = "PreviewMetadataCalc"

    // Twice the 100 pixels blurhash and thumbhash scale down to.
    private const val HASH_SOURCE_MIN_SIDE = 200

    private fun isImage(mimeType: String?) = mimeType?.startsWith("image/", ignoreCase = true) == true

    private fun isVideo(mimeType: String?) = mimeType?.startsWith("video/", ignoreCase = true) == true

    fun shouldAttempt(mimeType: String?): Boolean = isImage(mimeType) || isVideo(mimeType)

    private fun createBitmapOptions() =
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

    /** The largest power-of-two step that keeps the short side at [HASH_SOURCE_MIN_SIDE] or more. */
    private fun sampleSizeForHashes(
        width: Int,
        height: Int,
    ): Int {
        var sample = 1
        while (minOf(width, height) / (sample * 2) >= HASH_SOURCE_MIN_SIDE) sample *= 2
        return sample
    }

    fun computeFromBytes(
        data: ByteArray,
        mimeType: String?,
        dimPrecomputed: DimensionTag?,
    ): PreviewHashes =
        when {
            isAvif(mimeType) -> {
                val bitmap = decodeAvifBytes(data)
                processImage(bitmap, dimPrecomputed)
            }

            isImage(mimeType) -> {
                processSampledImage(dimPrecomputed) { options -> BitmapFactory.decodeByteArray(data, 0, data.size, options) }
            }

            isVideo(mimeType) -> {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(ByteArrayMediaDataSource(data))
                    processRetriever(retriever, videoPixelAspectRatio(ByteArrayMediaDataSource(data)), dimPrecomputed)
                } finally {
                    retriever.release()
                }
            }

            else -> {
                PreviewHashes(dim = dimPrecomputed)
            }
        }

    fun computeFromUri(
        context: Context,
        uri: Uri,
        mimeType: String?,
        dimPrecomputed: DimensionTag? = null,
    ): PreviewHashes? {
        if (!shouldAttempt(mimeType)) return null

        return try {
            when {
                isAvif(mimeType) -> {
                    val bitmap = decodeAvifFromUri(context, uri)
                    processImage(bitmap, dimPrecomputed)
                }

                isImage(mimeType) -> {
                    processSampledImage(dimPrecomputed) { options ->
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    }
                }

                isVideo(mimeType) -> {
                    val retriever = MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, uri)
                        processRetriever(retriever, videoPixelAspectRatio(context, uri), dimPrecomputed)
                    } finally {
                        retriever.release()
                    }
                }

                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Failed to compute metadata from uri", e)
            null
        }
    }

    /**
     * The real size comes from the header; the pixels are decoded at a fraction of it, since the
     * preview hashes only look at about 100 of them. A full decode of a 50 MP photo would need a
     * 200 MB bitmap. [decode] runs twice: once for the bounds, once for the sampled pixels.
     */
    private fun processSampledImage(
        dimPrecomputed: DimensionTag?,
        decode: (BitmapFactory.Options) -> Bitmap?,
    ): PreviewHashes {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        val options = createBitmapOptions().apply { inSampleSize = sampleSizeForHashes(bounds.outWidth, bounds.outHeight) }
        val bitmap = decode(options)
        val hashes = processBitmap(bitmap)
        val realDim = if (bitmap != null && bounds.outWidth > 0 && bounds.outHeight > 0) DimensionTag(bounds.outWidth, bounds.outHeight) else null
        return hashes.copy(dim = realDim ?: hashes.dim ?: dimPrecomputed)
    }

    private fun processImage(
        bitmap: Bitmap?,
        dimPrecomputed: DimensionTag?,
    ): PreviewHashes {
        val hashes = processBitmap(bitmap)
        return hashes.copy(dim = hashes.dim ?: dimPrecomputed)
    }

    private fun processBitmap(bitmap: Bitmap?): PreviewHashes =
        if (bitmap != null) {
            try {
                val blurhash =
                    runCatching { BlurhashWrapper(bitmap.toBlurhash()) }
                        .onFailure { Log.w(LOG_TAG, "blurhash generation failed", it) }
                        .getOrNull()
                val thumbhash =
                    runCatching { ThumbhashWrapper(bitmap.toThumbhash()) }
                        .onFailure { Log.w(LOG_TAG, "thumbhash generation failed", it) }
                        .getOrNull()
                PreviewHashes(
                    blurhash = blurhash,
                    thumbhash = thumbhash,
                    dim = DimensionTag(bitmap.width, bitmap.height),
                )
            } finally {
                bitmap.recycle()
            }
        } else {
            PreviewHashes.EMPTY
        }

    private fun processRetriever(
        retriever: MediaMetadataRetriever,
        pixelAspectRatio: Float?,
        dimPrecomputed: DimensionTag?,
    ): PreviewHashes {
        val dim = retriever.prepareDimFromVideo(pixelAspectRatio) ?: dimPrecomputed
        val thumb = retriever.getThumbnail()
        if (thumb == null) {
            Log.w(LOG_TAG) { "video frame extraction returned null; no blurhash/thumbhash will be generated" }
        }
        val hashes = processBitmap(thumb)
        val finalDim = if (dim?.hasSize() == true) dim else hashes.dim
        return hashes.copy(dim = finalDim)
    }

    /**
     * Decode an AVIF [ImageDecoder.Source] into a bitmap. Uses [ImageDecoder.ALLOCATOR_SOFTWARE]
     * because hardware bitmaps reject `getPixels()`, which is required for blurhash computation.
     */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeAvif(source: ImageDecoder.Source): Bitmap? =
        try {
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "AVIF decode failed: ${e.message}", e)
            null
        }

    private fun decodeAvifBytes(data: ByteArray): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d(LOG_TAG, "AVIF preview metadata skipped on API < 28")
            return null
        }
        if (data.isEmpty()) return null
        return decodeAvif(ImageDecoder.createSource(ByteBuffer.wrap(data)))
    }

    private fun decodeAvifFromUri(
        context: Context,
        uri: Uri,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.d(LOG_TAG, "AVIF preview metadata skipped on API < 28")
            return null
        }
        return decodeAvif(ImageDecoder.createSource(context.contentResolver, uri))
    }
}
