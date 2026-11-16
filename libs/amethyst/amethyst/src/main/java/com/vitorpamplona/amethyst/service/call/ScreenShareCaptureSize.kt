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
package com.vitorpamplona.amethyst.service.call

import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ScreenShareCaptureSize(
    val width: Int,
    val height: Int,
)

internal fun screenShareCaptureSize(
    widthPixels: Int,
    heightPixels: Int,
    maxDimension: Int = 1920,
): ScreenShareCaptureSize {
    val width = widthPixels.coerceAtLeast(2)
    val height = heightPixels.coerceAtLeast(2)
    val scale = min(1f, maxDimension.toFloat() / maxOf(width, height))

    fun even(value: Int): Int = value.coerceAtLeast(2).let { it - it % 2 }

    return ScreenShareCaptureSize(
        width = even((width * scale).roundToInt()),
        height = even((height * scale).roundToInt()),
    )
}

data class ScreenShareResources(
    val track: VideoTrack,
    val source: VideoSource,
)
