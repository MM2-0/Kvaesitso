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
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.davotoula.lightcompressor.config.VideoResizer
import com.vitorpamplona.quartz.utils.Log
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LOG_TAG = "VideoPixelAspectRatio"

// MediaFormat.KEY_PIXEL_ASPECT_RATIO_WIDTH/HEIGHT name these from API 30, but the extractor has
// filled them in (from an MP4's `pasp` box) for much longer.
private const val SAR_WIDTH = "sar-width"
private const val SAR_HEIGHT = "sar-height"

/**
 * The pixel aspect ratio the first video track of [uri] declares, or null for square pixels or
 * when it does not say.
 *
 * An anamorphic video stores its frames at one shape and is shown at another: 1080x720 with 4:9
 * pixels is a 480x720 portrait video. MediaMetadataRetriever only reports the stored size, so a
 * `dim` built from it alone describes a landscape video that no player draws.
 */
fun videoPixelAspectRatio(
    context: Context,
    uri: Uri,
): Float? = readPixelAspectRatio { it.setDataSource(context, uri, null) }

/** [videoPixelAspectRatio] for a video held in memory. */
fun videoPixelAspectRatio(source: MediaDataSource): Float? = readPixelAspectRatio { it.setDataSource(source) }

private fun readPixelAspectRatio(open: (MediaExtractor) -> Unit): Float? {
    val extractor = MediaExtractor()
    return try {
        open(extractor)
        val video =
            (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: return null
        if (!video.containsKey(SAR_WIDTH) || !video.containsKey(SAR_HEIGHT)) return null
        val width = video.getInteger(SAR_WIDTH)
        val height = video.getInteger(SAR_HEIGHT)
        if (width <= 0 || height <= 0) return null
        (width.toFloat() / height).takeIf { abs(it - 1f) > 0.01f }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not read the pixel aspect ratio", e)
        null
    } finally {
        extractor.release()
    }
}

/** [width] (stored pixels) in square pixels of the same height, for a [pixelAspectRatio] of null or not. */
fun displayWidth(
    width: Int,
    pixelAspectRatio: Float?,
): Int = if (pixelAspectRatio == null) width else (width * pixelAspectRatio).roundToInt().coerceAtLeast(1)

/**
 * Sizes the compressed video in square pixels. LightCompressor stretches each decoded frame to the
 * size its resizer returns and writes no pixel ratio, so handing it the stored size of an
 * anamorphic video bakes the squash into the upload. This widens or narrows the stored size to the
 * display shape first, then lets [inner] limit it, keeping both sides even for the encoder.
 */
class SquarePixelsResizer(
    private val pixelAspectRatio: Float,
    private val inner: VideoResizer?,
) : VideoResizer {
    override fun resize(
        width: Double,
        height: Double,
    ): Pair<Double, Double> {
        val squareWidth = width * pixelAspectRatio
        val (w, h) = inner?.resize(squareWidth, height) ?: Pair(squareWidth, height)
        return Pair(even(w), even(h))
    }

    private fun even(side: Double) = (side.roundToInt() / 2 * 2).coerceAtLeast(2).toDouble()
}
