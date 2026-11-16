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

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.BitmapParams
import android.os.Build
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag

fun MediaMetadataRetriever.getThumbnail(): Bitmap? {
    val raw: ByteArray? = embeddedPicture
    if (raw != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(raw))
    }

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val params = BitmapParams()
        params.preferredConfig = Bitmap.Config.ARGB_8888

        // Fall back to middle of video
        // Note: METADATA_KEY_DURATION unit is in ms, not us.
        val thumbnailTimeUs: Long =
            (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0) * 1000 / 2

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getFrameAtTime(thumbnailTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, params)
        } else {
            null
        }
    } else {
        null
    }
}

/**
 * The size the video displays at. The retriever reports the stored frame, so the stored width is
 * widened or narrowed by the [pixelAspectRatio] of an anamorphic video (see videoPixelAspectRatio)
 * before the rotation swaps the sides.
 */
fun MediaMetadataRetriever.prepareDimFromVideo(pixelAspectRatio: Float? = null): DimensionTag? {
    val rotation = prepareRotation()
    val width = displayWidth(prepareVideoWidth() ?: return null, pixelAspectRatio)
    val height = prepareVideoHeight() ?: return null

    return if (width > 0 && height > 0) {
        if (rotation == 90 || rotation == 270) {
            DimensionTag(height, width)
        } else {
            DimensionTag(width, height)
        }
    } else {
        null
    }
}

fun MediaMetadataRetriever.prepareRotation(): Int? {
    val rotation = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
    return if (rotation.isNullOrEmpty()) {
        null
    } else {
        rotation.toIntOrNull()
    }
}

fun MediaMetadataRetriever.prepareVideoWidth(): Int? {
    val widthData = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
    return if (widthData.isNullOrEmpty()) {
        null
    } else {
        widthData.toInt()
    }
}

fun MediaMetadataRetriever.prepareVideoHeight(): Int? {
    val heightData = extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
    return if (heightData.isNullOrEmpty()) {
        null
    } else {
        heightData.toInt()
    }
}
