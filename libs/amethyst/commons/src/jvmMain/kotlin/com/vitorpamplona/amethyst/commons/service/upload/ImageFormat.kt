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

/**
 * Container image format produced by [ImageFormatSniffer]. The
 * sniffer's job is to decide whether a payload re-encodes through the
 * pipeline, passes through byte-identical, or refuses upload entirely.
 */
sealed class ImageFormat {
    /** MIME type for the upload payload. */
    abstract val mimeType: String

    object Jpeg : ImageFormat() {
        override val mimeType = "image/jpeg"
    }

    object Png : ImageFormat() {
        override val mimeType = "image/png"
    }

    object Bmp : ImageFormat() {
        override val mimeType = "image/bmp"
    }

    object Tiff : ImageFormat() {
        override val mimeType = "image/tiff"
    }

    /**
     * Animated GIFs must pass through byte-identical — ImageIO would
     * only encode the first frame.
     */
    data class Gif(
        val animated: Boolean,
    ) : ImageFormat() {
        override val mimeType = "image/gif"
    }

    /**
     * Animated WebPs must pass through byte-identical. Static WebPs
     * decode via stock ImageIO (limited) — for v1 they pass through
     * unchanged since there is no pure-Java WebP encoder.
     */
    data class WebP(
        val animated: Boolean,
    ) : ImageFormat() {
        override val mimeType = "image/webp"
    }

    /** Vector format — pass through unchanged. */
    object Svg : ImageFormat() {
        override val mimeType = "image/svg+xml"
    }

    /** Detected but refused — no pure-Java decoder exists in 2026. */
    object Avif : ImageFormat() {
        override val mimeType = "image/avif"
    }

    /** Detected but refused — no pure-Java decoder exists in 2026. */
    object Heic : ImageFormat() {
        override val mimeType = "image/heic"
    }

    /** Magic bytes did not match any known format. */
    data class Unknown(
        override val mimeType: String,
    ) : ImageFormat()
}
