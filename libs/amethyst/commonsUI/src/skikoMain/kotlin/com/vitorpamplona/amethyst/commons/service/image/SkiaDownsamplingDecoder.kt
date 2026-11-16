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
package com.vitorpamplona.amethyst.commons.service.image

import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Dimension
import coil3.size.Precision
import coil3.size.Scale
import coil3.size.isOriginal
import okio.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.impl.use // with okio.use: skia and okio types are AutoCloseable only on JVM, so kotlin.use breaks iOS
import kotlin.math.roundToInt

/**
 * Coil's own Skia decoder (desktop and iOS), with a downscale that keeps the picture.
 *
 * Coil's `SkiaImageDecoder` shrinks each image to the size it is drawn at, as Android's decoder
 * does, but with Skia's default sampling: nearest neighbour, no mipmaps. That keeps one source
 * pixel in every n and drops the rest, so a 400 px avatar drawn at 36 px comes out blocky and a
 * link preview's 1200 px card comes out jagged. This decoder makes the same size choice and
 * filters through mipmaps instead, which averages every source pixel into the result.
 *
 * A JPEG much larger than its slot is first decoded at 1/2, 1/4 or 1/8 of its size, which libjpeg
 * does natively: a 6000 x 4000 phone photo in a feed card decodes in a quarter of the time,
 * through a 1.5 MB bitmap instead of a 96 MB one.
 */
class SkiaDownsamplingDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val bytes = source.source().use { it.readByteArray() }
        if (isJpeg(bytes)) shrinkJpeg(bytes, options)?.let { return it }
        val image = Image.makeFromEncoded(bytes)
        try {
            return downsample(image, options)
        } finally {
            image.close()
        }
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder = SkiaDownsamplingDecoder(result.source, options)
    }
}

/**
 * Draws [image] into a bitmap of the size [options] asks for ([decodedSize]). Only the sampling
 * differs from Coil's. [image] stays open: the caller owns it.
 */
internal fun downsample(
    image: Image,
    options: Options,
): DecodeResult {
    val srcWidth = image.width
    val srcHeight = image.height
    val (width, height) = decodedSize(srcWidth, srcHeight, options)

    val bitmap =
        if (width == srcWidth && height == srcHeight) {
            Bitmap().apply {
                allocN32Pixels(width, height)
                image.readPixels(this)
                setImmutable()
            }
        } else {
            drawScaled(image, width, height, EncodedOrigin.TOP_LEFT)
        }

    return DecodeResult(
        image = bitmap.asImage(),
        isSampled = width < srcWidth || height < srcHeight,
    )
}

/**
 * [bytes] decoded at the largest 1/2, 1/4 or 1/8 of its size that still covers the size [options]
 * asks for, then filtered down to it; null when the full size is needed, so the caller decodes as
 * usual. The JPEG's EXIF orientation is applied here, as `Image.makeFromEncoded` does.
 */
internal fun shrinkJpeg(
    bytes: ByteArray,
    options: Options,
): DecodeResult? =
    Data.makeFromBytes(bytes).use { data ->
        Codec.makeFromData(data).use { codec ->
            val origin = codec.encodedOrigin
            val swaps = origin.swapsWidthHeight()
            // The size asked for is the upright picture's; the codec decodes it as stored.
            val (width, height) = if (swaps) decodedSize(codec.height, codec.width, options) else decodedSize(codec.width, codec.height, options)
            val storedWidth = if (swaps) height else width
            val storedHeight = if (swaps) width else height

            var denominator = 8
            while (denominator > 1 && (ceilDiv(codec.width, denominator) < storedWidth || ceilDiv(codec.height, denominator) < storedHeight)) denominator /= 2
            if (denominator == 1) return@use null

            val scaled = Bitmap()
            try {
                // libjpeg's scaled sizes round up; Skia decodes natively only at exactly those.
                scaled.allocN32Pixels(ceilDiv(codec.width, denominator), ceilDiv(codec.height, denominator))
                codec.readPixels(scaled, 0)
                scaled.setImmutable()
                Image.makeFromBitmap(scaled).use { image ->
                    DecodeResult(image = drawScaled(image, width, height, origin).asImage(), isSampled = true)
                }
            } catch (e: Exception) {
                // A JPEG Skia can't decode at that scale (a broken or exotic file): the usual path.
                null
            } finally {
                scaled.close()
            }
        }
    }

/** [image] filtered through mipmaps into a [width] x [height] bitmap, turned upright from [origin]. */
private fun drawScaled(
    image: Image,
    width: Int,
    height: Int,
    origin: EncodedOrigin,
): Bitmap {
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(width, height)
    Canvas(bitmap).use { canvas ->
        // Drawn over what is there, so a transparent picture needs a clear start.
        canvas.clear(Color.TRANSPARENT)
        if (origin != EncodedOrigin.TOP_LEFT) canvas.concat(origin.toMatrix(width, height))
        // Under a rotation the picture is drawn as stored, sideways, and the matrix stands it up.
        val drawWidth = if (origin.swapsWidthHeight()) height else width
        val drawHeight = if (origin.swapsWidthHeight()) width else height
        canvas.drawImageRect(
            image,
            0f,
            0f,
            image.width.toFloat(),
            image.height.toFloat(),
            0f,
            0f,
            drawWidth.toFloat(),
            drawHeight.toFloat(),
            MIPMAP_SAMPLING,
            null,
            true,
        )
    }
    bitmap.setImmutable()
    return bitmap
}

private fun ceilDiv(
    value: Int,
    divisor: Int,
) = (value + divisor - 1) / divisor

/** JPEG's start-of-image marker and the first segment marker after it. */
private fun isJpeg(bytes: ByteArray) = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

/**
 * The size [options] asks a [srcWidth] x [srcHeight] picture to be decoded at: Coil's math
 * (`makeFromImage` in coil-core's non-Android utils), never larger than the picture when inexact.
 */
internal fun decodedSize(
    srcWidth: Int,
    srcHeight: Int,
    options: Options,
): Pair<Int, Int> {
    val dstWidth: Int
    val dstHeight: Int
    if (options.size.isOriginal) {
        dstWidth = srcWidth
        dstHeight = srcHeight
    } else {
        dstWidth = options.size.width.toPx(options.scale)
        dstHeight = options.size.height.toPx(options.scale)
    }

    var multiplier =
        DecodeUtils.computeSizeMultiplier(
            srcWidth = srcWidth,
            srcHeight = srcHeight,
            dstWidth = dstWidth,
            dstHeight = dstHeight,
            scale = options.scale,
            maxSize = options.maxBitmapSize,
        )
    if (options.precision == Precision.INEXACT) multiplier = multiplier.coerceAtMost(1.0)

    val width = (multiplier * srcWidth).roundToInt().coerceAtLeast(1)
    val height = (multiplier * srcHeight).roundToInt().coerceAtLeast(1)
    return width to height
}

/** Trilinear: bilinear inside the two nearest mip levels, blended. */
private val MIPMAP_SAMPLING = FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR)

/** An undefined side doesn't limit the size: it loses to the other side under either [Scale]. */
private fun Dimension.toPx(scale: Scale): Int =
    when (this) {
        is Dimension.Pixels -> px
        Dimension.Undefined ->
            when (scale) {
                Scale.FILL -> Int.MIN_VALUE
                Scale.FIT -> Int.MAX_VALUE
            }
    }
