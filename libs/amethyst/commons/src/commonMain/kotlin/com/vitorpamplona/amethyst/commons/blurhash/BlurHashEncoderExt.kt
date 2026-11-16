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

import kotlin.math.roundToInt

/**
 * Encodes this PlatformImage to a blurhash string.
 * The image will be scaled down if larger than 100x100 for performance.
 */
fun PlatformImage.toBlurhash(): String {
    val aspectRatio = this.width.toFloat() / this.height.toFloat()

    if (this.width > 100 && this.height > 100) {
        return this.scale(100, (100 / aspectRatio).toInt()).toBlurhash()
    }

    val intArray = IntArray(width * height)
    this.getPixels(intArray, 0, width, 0, 0, width, height)

    val numX =
        if (aspectRatio > 1) {
            9
        } else if (aspectRatio < 1) {
            (9 * aspectRatio).roundToInt()
        } else {
            4
        }

    val numY =
        if (aspectRatio > 1) {
            (9 * (1 / aspectRatio)).roundToInt()
        } else if (aspectRatio < 1) {
            9
        } else {
            4
        }

    return BlurHashEncoder().encode(
        intArray,
        width,
        height,
        numX,
        numY,
    )
}
