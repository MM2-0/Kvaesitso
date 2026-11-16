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
package com.vitorpamplona.amethyst.commons.thumbhash

import com.vitorpamplona.amethyst.commons.blurhash.PlatformImage

/**
 * Encodes this [PlatformImage] to a base64 ThumbHash string (no padding).
 *
 * ThumbHash is specified at ≤100x100. Larger images are downscaled first.
 */
fun PlatformImage.toThumbhash(): String {
    val source =
        if (width > 100 || height > 100) {
            val aspect = width.toDouble() / height.toDouble()
            val scaled =
                if (width >= height) {
                    val w = 100
                    val h = (100.0 / aspect).toInt().coerceAtLeast(1)
                    this.scale(w, h)
                } else {
                    val h = 100
                    val w = (100.0 * aspect).toInt().coerceAtLeast(1)
                    this.scale(w, h)
                }
            scaled
        } else {
            this
        }

    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    return ThumbHashEncoder.encodeToBase64(pixels, source.width, source.height)
}
