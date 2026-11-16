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
package com.vitorpamplona.amethyst.commons.drawing.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.asPainter
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.vitorpamplona.amethyst.commons.drawing.DrawingBackground
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Asks Coil for a bitmap a software canvas can draw: on Android, not a hardware one. */
expect fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder

/**
 * Turns a picture the user picked into a drawing background: decoded by Coil
 * (which turns a phone photo upright on Android), cropped from the centre to a
 * shape between [DrawingBackground.Photo.MIN_ASPECT] and
 * [DrawingBackground.Photo.MAX_ASPECT], and scaled to [PHOTO_WIDTH] wide.
 * Null when the bytes are not a picture Coil can read.
 */
object PhotoBackgroundLoader {
    /** Twice the GIF's width, so the photo stays sharp on screen and scales down cleanly for export. */
    const val PHOTO_WIDTH = 1080

    suspend fun load(
        context: PlatformContext,
        bytes: ByteArray,
    ): DrawingBackground.Photo? {
        val request =
            ImageRequest
                .Builder(context)
                .data(bytes)
                .size(PHOTO_WIDTH * 2)
                .softwareBitmap()
                .build()
        val image =
            when (val result = SingletonImageLoader.get(context).execute(request)) {
                is SuccessResult -> {
                    result.image
                }

                is ErrorResult -> {
                    Log.w(TAG, "Could not decode the picked picture", result.throwable)
                    return null
                }
            }
        if (image.width <= 0 || image.height <= 0) return null

        return withContext(Dispatchers.Default) {
            val painter = image.asPainter(context)
            val aspect = (image.height.toFloat() / image.width).coerceIn(DrawingBackground.Photo.MIN_ASPECT, DrawingBackground.Photo.MAX_ASPECT)
            val width = PHOTO_WIDTH
            val height = (PHOTO_WIDTH * aspect).roundToInt()

            // Cover: scale until both sides are filled, then centre, cropping the overflow.
            val scale = max(width.toFloat() / image.width, height.toFloat() / image.height)
            val drawnW = image.width * scale
            val drawnH = image.height * scale

            val bitmap = ImageBitmap(width, height)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
                // A picture with see-through parts sits on white paper: the background must be opaque,
                // or the exporter's frames (drawn over the previous one) and the canvas show through it.
                drawRect(Color.White)
                translate((width - drawnW) / 2f, (height - drawnH) / 2f) {
                    with(painter) { draw(Size(drawnW, drawnH)) }
                }
            }
            val pixels = IntArray(width * height)
            bitmap.readPixels(pixels)
            DrawingBackground.Photo(pixels, width, height)
        }
    }

    private const val TAG = "PhotoBackgroundLoader"
}
