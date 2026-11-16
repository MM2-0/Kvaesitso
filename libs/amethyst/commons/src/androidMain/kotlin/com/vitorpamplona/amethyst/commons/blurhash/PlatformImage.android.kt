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

import android.graphics.Bitmap
import androidx.core.graphics.scale

actual class PlatformImage(
    val bitmap: Bitmap,
) {
    actual val width: Int get() = bitmap.width
    actual val height: Int get() = bitmap.height

    actual fun getPixels(
        pixels: IntArray,
        offset: Int,
        stride: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        bitmap.getPixels(pixels, offset, stride, x, y, width, height)
    }

    actual fun scale(
        width: Int,
        height: Int,
    ): PlatformImage = PlatformImage(bitmap.scale(width, height, false))

    actual companion object {
        actual fun create(
            pixels: IntArray,
            width: Int,
            height: Int,
        ): PlatformImage = PlatformImage(Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888))
    }
}

/**
 * Extension to convert Android Bitmap to PlatformImage.
 */
fun Bitmap.toPlatformImage(): PlatformImage = PlatformImage(this)

/**
 * Extension to get the underlying Android Bitmap.
 */
fun PlatformImage.toAndroidBitmap(): Bitmap = this.bitmap
