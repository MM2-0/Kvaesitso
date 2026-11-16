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
package com.vitorpamplona.amethyst.commons.service.upload

import com.vitorpamplona.amethyst.commons.blurhash.toBlurhash
import com.vitorpamplona.amethyst.commons.blurhash.toPlatformImage
import com.vitorpamplona.amethyst.commons.thumbhash.toThumbhash
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

data class MediaMetadata(
    val sha256: String,
    val size: Long,
    val mimeType: String,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    val thumbhash: String? = null,
)

/**
 * Reads media metadata (sha256, size, mime-type, dimensions, blurhash, thumbhash)
 * from a JVM [File]. Named to avoid colliding with the [MediaMetadata] data class
 * in the same package.
 */
object MediaMetadataReader {
    fun compute(file: File): MediaMetadata = compute(file.readBytes(), guessMimeType(file))

    /**
     * Same as [compute] for a file already in memory, such as one downloaded from a link.
     * [fallbackDecoder] reads the images ImageIO cannot (WebP, through Skia on desktop).
     */
    fun compute(
        bytes: ByteArray,
        mimeType: String,
        fallbackDecoder: ((ByteArray) -> BufferedImage?)? = null,
    ): MediaMetadata {
        val hash = sha256(bytes).toHexKey()
        var width: Int? = null
        var height: Int? = null
        var blurhash: String? = null
        var thumbhash: String? = null

        if (mimeType.startsWith("image/")) {
            try {
                val decoded = runCatching { readForHashes(bytes) }.getOrNull()
                // ImageIO reads the pixels as stored; a camera's orientation tag says how they
                // display, which is what the dimensions and the hashes must describe. The fallback
                // (Skia) applies the tag itself.
                val orientation = if (decoded != null) ExifOrientation.read(bytes) else ExifOrientation.NORMAL
                val swap = ExifOrientation.swapsAxes(orientation)
                val image = decoded?.image?.let { ExifOrientation.apply(it, orientation) } ?: fallbackDecoder?.invoke(bytes)
                if (image != null) {
                    width = decoded?.let { if (swap) it.height else it.width } ?: image.width
                    height = decoded?.let { if (swap) it.width else it.height } ?: image.height
                    val platformImage = image.toPlatformImage()
                    blurhash = runCatching { platformImage.toBlurhash() }.getOrNull()
                    thumbhash = runCatching { platformImage.toThumbhash() }.getOrNull()
                }
            } catch (_: Exception) {
            }
        }

        return MediaMetadata(
            sha256 = hash,
            size = bytes.size.toLong(),
            mimeType = mimeType,
            width = width,
            height = height,
            blurhash = blurhash,
            thumbhash = thumbhash,
        )
    }

    private class Decoded(
        val width: Int,
        val height: Int,
        val image: BufferedImage,
    )

    /**
     * The image's real size from its header, plus a copy decoded at a fraction of it: the preview
     * hashes only look at about 100 pixels, so a 5000x3333 photo need not fill 66 MB to make them.
     */
    private fun readForHashes(bytes: ByteArray): Decoded? =
        ImageIO.createImageInputStream(ByteArrayInputStream(bytes))?.use { input ->
            val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
            try {
                reader.input = input
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                val step = (minOf(width, height) / HASH_SOURCE_MIN_SIDE).coerceAtLeast(1)
                val param = reader.defaultReadParam.apply { if (step > 1) setSourceSubsampling(step, step, 0, 0) }
                Decoded(width, height, reader.read(0, param))
            } finally {
                reader.dispose()
            }
        }

    // Twice the 100 pixels blurhash and thumbhash scale down to.
    private const val HASH_SOURCE_MIN_SIDE = 200

    fun guessMimeType(file: File): String = guessMimeTypeFromExtension(file.extension)

    fun guessMimeTypeFromExtension(extension: String): String =
        when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "avif" -> "image/avif"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            else -> "application/octet-stream"
        }
}
