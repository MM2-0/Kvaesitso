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

import android.os.Build.VERSION.SDK_INT
import androidx.annotation.RequiresApi
import coil3.ImageLoader
import coil3.decode.DecodeUtils
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.gif.AnimatedImageDecoder
import coil3.gif.isAnimatedHeif
import coil3.gif.isAnimatedWebP
import coil3.gif.isGif
import coil3.request.Options
import com.vitorpamplona.amethyst.commons.service.images.hasSubThresholdGifFrameDelay
import okio.BufferedSource
import okio.buffer

/**
 * Drop-in replacement for Coil's `AnimatedImageDecoder.Factory` that stops asking for the GIF
 * frame-delay rewrite when the file does not need it.
 *
 * The sniffing is identical to Coil's. The one difference: below API 34 Coil wraps every GIF in a
 * stream-backed `FrameDelayRewritingSource` to clamp sub-threshold frame delays, and a
 * stream-backed source has no file for `ImageDecoder` to read, so the decode falls back to
 * squashing the whole encoded animation into RAM (see [SystemFileSystemFetcher] for what that
 * costs). Nearly every GIF comes out of that rewriter byte-for-byte identical, so
 * [hasSubThresholdGifFrameDelay] checks first and the rewrite is requested only for the files
 * that would actually change.
 */
@RequiresApi(28)
class AnimatedImageDecoderFactory : Decoder.Factory {
    override fun create(
        result: SourceFetchResult,
        options: Options,
        imageLoader: ImageLoader,
    ): Decoder? {
        val source = result.source.source()
        val isGif = DecodeUtils.isGif(source)
        if (!isGif && !isAnimatedNonGif(source)) return null

        return newAnimatedImageDecoder(result.source, options, isGif)
    }

    private fun isAnimatedNonGif(source: BufferedSource): Boolean = DecodeUtils.isAnimatedWebP(source) || (SDK_INT >= 30 && DecodeUtils.isAnimatedHeif(source))
}

/**
 * Builds Coil's [AnimatedImageDecoder], asking for the frame-delay rewrite only when it is both
 * possible and needed.
 *
 * [mayNeedFrameDelayRewrite] should be true only for GIFs: Coil's rewriter no-ops on every other
 * format, so scanning one would be pure IO for a decision already made.
 */
@RequiresApi(28)
fun newAnimatedImageDecoder(
    source: ImageSource,
    options: Options,
    mayNeedFrameDelayRewrite: Boolean,
): Decoder = AnimatedImageDecoder(source, options, enforceMinimumFrameDelay(source, mayNeedFrameDelayRewrite))

/**
 * Whether to ask Coil to clamp this GIF's sub-threshold frame delays. From API 34 the platform
 * decoder does it itself, which is why Coil's own default turns the rewrite off there; below that
 * we pay for it only when the file really has a delay to clamp.
 */
private fun enforceMinimumFrameDelay(
    source: ImageSource,
    mayNeedFrameDelayRewrite: Boolean,
): Boolean {
    if (!mayNeedFrameDelayRewrite || SDK_INT >= 34) return false

    // Not file-backed: the decode squashes the stream into RAM either way, so there is no fast
    // path to protect and no reason to deviate from Coil's default.
    val file = source.fileOrNull() ?: return true

    return source.fileSystem
        .source(file)
        .buffer()
        .use { it.hasSubThresholdGifFrameDelay() }
}
