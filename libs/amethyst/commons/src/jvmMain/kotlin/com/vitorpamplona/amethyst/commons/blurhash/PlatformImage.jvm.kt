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

import java.awt.Image
import java.awt.image.BufferedImage

actual class PlatformImage(
    val image: BufferedImage,
) {
    actual val width: Int get() = image.width
    actual val height: Int get() = image.height

    actual fun getPixels(
        pixels: IntArray,
        offset: Int,
        stride: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        image.getRGB(x, y, width, height, pixels, offset, stride)
    }

    actual fun scale(
        width: Int,
        height: Int,
    ): PlatformImage {
        val scaled = image.getScaledInstance(width, height, Image.SCALE_FAST)
        val buffered = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        buffered.graphics.drawImage(scaled, 0, 0, null)
        return PlatformImage(buffered)
    }

    actual companion object {
        actual fun create(
            pixels: IntArray,
            width: Int,
            height: Int,
        ): PlatformImage {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            image.setRGB(0, 0, width, height, pixels, 0, width)
            return PlatformImage(image)
        }
    }
}

/**
 * Extension to convert BufferedImage to PlatformImage.
 */
fun BufferedImage.toPlatformImage(): PlatformImage = PlatformImage(this)

/**
 * Extension to get the underlying BufferedImage.
 */
fun PlatformImage.toBufferedImage(): BufferedImage = this.image
