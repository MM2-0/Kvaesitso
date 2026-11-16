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
package com.vitorpamplona.amethyst.service.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import com.vitorpamplona.amethyst.commons.util.deleteOrWarn
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk cache for pre-resized profile picture thumbnails.
 *
 * Stores small JPEG thumbnails (typically ~5-10KB each) keyed by URL hash.
 * This avoids re-decoding large original images from Coil's disk cache on
 * every recomposition when images are evicted from the memory cache.
 *
 * Thread safety:
 * - Writes use atomic temp-file-then-rename to prevent partial reads.
 * - [inFlight] tracks URLs currently being saved to prevent duplicate work
 *   when multiple composables request the same profile picture simultaneously.
 */
class ThumbnailDiskCache(
    private val cacheDir: File,
    private val maxEntries: Int = 10_000,
) {
    companion object {
        const val THUMBNAIL_SIZE_PX = 256
        private const val JPEG_QUALITY = 80
    }

    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    init {
        cacheDir.mkdirs()
    }

    private fun keyFor(url: String): String = sha256(url.toByteArray()).toHexKey()

    /**
     * Loads a cached thumbnail bitmap for the given URL, or null if not cached.
     */
    fun load(url: String): Bitmap? {
        val file = File(cacheDir, keyFor(url))
        if (!file.exists()) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.w("ThumbnailDiskCache", "Failed to decode cached thumbnail, deleting: ${file.absolutePath}", e)
            file.deleteOrWarn("ThumbnailDiskCache", "corrupt cache file")
            null
        }
    }

    /**
     * Generates a thumbnail from a source file and saves it to the cache.
     * Uses two-pass BitmapFactory decode (bounds then pixels) so the full
     * file is never loaded into a byte array.
     *
     * Returns true if saved, false if already in flight, already cached, or on error.
     */
    fun generateFromFile(
        url: String,
        sourceFile: File,
    ): Boolean {
        if (!inFlight.add(url)) return false

        if (isAnimatedAvif(sourceFile)) {
            // Skip caching — animated AVIF must always go through the AVIF decoder
            // so it can produce an AnimatedImageDrawable. A static JPEG thumbnail
            // would permanently freeze the avatar on the first frame.
            inFlight.remove(url)
            Log.d("ThumbnailDiskCache") { "Skipping animated AVIF thumbnail for $url" }
            return false
        }

        try {
            val key = keyFor(url)
            val finalFile = File(cacheDir, key)
            if (finalFile.exists()) return true

            val path = sourceFile.absolutePath
            val targetSize = THUMBNAIL_SIZE_PX

            // First pass: decode bounds only
            val boundsOptions =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
            BitmapFactory.decodeFile(path, boundsOptions)
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return false

            // Second pass: decode at reduced size
            val sampleSize = calculateInSampleSize(boundsOptions, targetSize)
            val decodeOptions =
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                }
            val decoded = BitmapFactory.decodeFile(path, decodeOptions) ?: return false

            // Fuse center-crop + scale into one allocation; matches CircleShape + ContentScale.Crop.
            val side = minOf(decoded.width, decoded.height)
            val scaled =
                if (decoded.width == targetSize && decoded.height == targetSize) {
                    decoded
                } else {
                    // try/finally ensures decoded is recycled even if createBitmap throws.
                    try {
                        val scale = targetSize.toFloat() / side
                        val matrix = Matrix().apply { setScale(scale, scale) }
                        Bitmap.createBitmap(
                            decoded,
                            (decoded.width - side) / 2,
                            (decoded.height - side) / 2,
                            side,
                            side,
                            matrix,
                            true,
                        )
                    } finally {
                        decoded.recycle()
                    }
                }

            val tempFile = File(cacheDir, "$key.tmp")
            // The cache dir can be wiped while the app runs (system cache trim,
            // user "Clear cache"); recreate it or the write fails with ENOENT.
            cacheDir.mkdirs()
            tempFile.outputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            scaled.recycle()
            if (!tempFile.renameTo(finalFile)) {
                Log.w("ThumbnailDiskCache") { "Failed to rename temp thumbnail to final: ${tempFile.absolutePath}" }
                tempFile.deleteOrWarn("ThumbnailDiskCache", "temp thumbnail")
                return false
            }

            evictIfNeeded()
            return true
        } catch (e: Exception) {
            Log.w("ThumbnailDiskCache", "Failed to generate thumbnail for $url", e)
            return false
        } finally {
            inFlight.remove(url)
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        targetSize: Int,
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > targetSize || width > targetSize) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= targetSize && halfWidth / inSampleSize >= targetSize) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun evictIfNeeded() {
        val files = cacheDir.listFiles() ?: return
        if (files.size > maxEntries) {
            files
                .sortedBy { it.lastModified() }
                .take(files.size - maxEntries)
                .forEach { it.deleteOrWarn("ThumbnailDiskCache", "thumbnail") }
        }
    }

    /**
     * Returns true if the source file is an AVIF Image Sequence (animated AVIF).
     *
     * Sniffs the ISOBMFF `ftyp` box brand at offset 8. Only `avis` is treated as
     * potentially animated; still AVIF (`avif`, `avo1`) is allowed to be cached
     * as a JPEG thumbnail because the visual result is the same.
     *
     * Returning true causes [generateFromFile] to skip thumbnail caching so the
     * full AVIF stays in Coil's normal disk cache and is decoded by the AVIF
     * decoder on every load — preserving animation.
     */
    private fun isAnimatedAvif(sourceFile: File): Boolean =
        runCatching {
            sourceFile.inputStream().use { stream ->
                val header = ByteArray(12)
                if (stream.read(header) != 12) return@use false
                // Offset 4..7 must be "ftyp" (the standard ISOBMFF box type)
                header[4] == 'f'.code.toByte() &&
                    header[5] == 't'.code.toByte() &&
                    header[6] == 'y'.code.toByte() &&
                    header[7] == 'p'.code.toByte() &&
                    // Offset 8..11 must be "avis" (AVIF Image Sequence brand)
                    header[8] == 'a'.code.toByte() &&
                    header[9] == 'v'.code.toByte() &&
                    header[10] == 'i'.code.toByte() &&
                    header[11] == 's'.code.toByte()
            }
        }.getOrDefault(false)
}
