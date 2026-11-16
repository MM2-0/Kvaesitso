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

/**
 * Platform-agnostic image wrapper for blurhash encoding/decoding.
 *
 * On Android: wraps android.graphics.Bitmap
 * On Desktop JVM: wraps java.awt.image.BufferedImage
 */
expect class PlatformImage {
    val width: Int
    val height: Int

    /**
     * Read ARGB pixels into the provided array.
     * Pixels are in ARGB format (0xAARRGGBB).
     */
    fun getPixels(
        pixels: IntArray,
        offset: Int,
        stride: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    )

    /**
     * Create a scaled copy of this image.
     */
    fun scale(
        width: Int,
        height: Int,
    ): PlatformImage

    companion object {
        /**
         * Create a new image from ARGB pixel array.
         */
        fun create(
            pixels: IntArray,
            width: Int,
            height: Int,
        ): PlatformImage
    }
}
