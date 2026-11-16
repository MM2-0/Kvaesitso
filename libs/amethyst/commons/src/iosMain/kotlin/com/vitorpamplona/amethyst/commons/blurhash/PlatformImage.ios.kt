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
package com.vitorpamplona.amethyst.commons.blurhash

// Phase 2 compile-only iOS actual. Holds an ARGB pixel buffer + size. Phase 3
// will swap to a UIImage/CGImage-backed implementation when the iosApp module
// lands and BlurHash/ThumbHash output needs to render to UIKit.
actual class PlatformImage internal constructor(
    private val pixels: IntArray,
    actual val width: Int,
    actual val height: Int,
) {
    actual fun getPixels(
        pixels: IntArray,
        offset: Int,
        stride: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        for (row in 0 until height) {
            val srcStart = (y + row) * this.width + x
            val dstStart = offset + row * stride
            this.pixels.copyInto(
                destination = pixels,
                destinationOffset = dstStart,
                startIndex = srcStart,
                endIndex = srcStart + width,
            )
        }
    }

    actual fun scale(
        width: Int,
        height: Int,
    ): PlatformImage {
        // Nearest-neighbour resample. Adequate for the Phase 2 compile-only target;
        // Phase 3 should swap to CoreGraphics scaling for production quality.
        val out = IntArray(width * height)
        for (j in 0 until height) {
            val srcY = (j.toLong() * this.height / height).toInt().coerceAtMost(this.height - 1)
            for (i in 0 until width) {
                val srcX = (i.toLong() * this.width / width).toInt().coerceAtMost(this.width - 1)
                out[j * width + i] = pixels[srcY * this.width + srcX]
            }
        }
        return PlatformImage(out, width, height)
    }

    actual companion object {
        actual fun create(
            pixels: IntArray,
            width: Int,
            height: Int,
        ): PlatformImage = PlatformImage(pixels.copyOf(), width, height)
    }
}
