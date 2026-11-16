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

import android.os.Build
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.decode.Decoder
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.request.Options
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8

/**
 * Coil [Decoder.Factory] for AVIF (still and animated).
 *
 * Coil's bundled [AnimatedImageDecoder.Factory] only accepts HEIF brands (msf1/hevc/hevx)
 * at offset 8, so it misses AVIF, whose major brand at offset 8 is "avis" (animated AVIF
 * Image Sequence), "avif" (still AVIF), or "avo1" (older single-image AVIF). Without this
 * factory, animated AVIFs fall through to Coil's default static decoder and render as
 * the first frame only.
 *
 * On API 31+, the platform [android.graphics.ImageDecoder] produces an
 * [android.graphics.drawable.AnimatedImageDrawable] for animated AVIF. We delegate the
 * actual decode to Coil's [AnimatedImageDecoder] — only the brand sniff is custom. It is built
 * through [newAnimatedImageDecoder] so an AVIF gets the same disk-cache file fast path a GIF does.
 *
 * Below API 31 the platform decoder cannot handle AVIF at all, so we decline and let
 * other decoders try (they will also fail, and Coil falls through to its error slot,
 * which is the documented behavior for unsupported formats on older API levels).
 */
class AvifAnimatedDecoderFactory : Decoder.Factory {
    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null // AVIF requires API 31+
        if (!isAvif(result.source.source())) return null
        return createAnimatedImageDecoder(result, options)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createAnimatedImageDecoder(
        result: SourceFetchResult,
        options: Options,
    ): Decoder = newAnimatedImageDecoder(result.source, options, mayNeedFrameDelayRewrite = false)

    private fun isAvif(source: BufferedSource): Boolean =
        source.rangeEquals(4, FTYP) &&
            AVIF_BRANDS.any { source.rangeEquals(8, it) }

    companion object {
        private val FTYP = "ftyp".encodeUtf8()
        private val AVIF_BRANDS =
            listOf(
                "avis".encodeUtf8(), // animated AVIF Image Sequence
                "avif".encodeUtf8(), // still AVIF
                "avo1".encodeUtf8(), // older single-image AVIF brand
            )
    }
}
