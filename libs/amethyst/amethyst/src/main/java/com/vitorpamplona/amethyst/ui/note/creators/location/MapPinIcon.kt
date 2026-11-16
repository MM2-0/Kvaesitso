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
package com.vitorpamplona.amethyst.ui.note.creators.location

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.createBitmap
import kotlin.math.roundToInt

/**
 * Draws a Roadstr-style map pin: a colored teardrop in [colorArgb] with [emoji]
 * centered on its head, a white ring, and a soft drop shadow.
 *
 * The pin's tip sits at the bottom-center of the returned bitmap, so the
 * osmdroid marker should be anchored at (0.5, 1.0) — the geographic point then
 * lands exactly under the tip.
 *
 * Matches the visual language of the roadstr reference clients
 * (<https://github.com/jooray/roadstr>): the same per-category color is used so
 * a pin reads the same across implementations.
 */
fun roadEventPinBitmap(
    emoji: String,
    colorArgb: Int,
    density: Float,
): Bitmap {
    fun dp(value: Float) = value * density

    val head = dp(34f) // head diameter
    val radius = head / 2f
    val tail = dp(12f) // pointer height below the head
    val pad = dp(5f) // room for the drop shadow
    val ringWidth = dp(2.5f)

    val width = (head + pad * 2).roundToInt()
    val height = (head + tail + pad * 2).roundToInt()
    val cx = width / 2f
    val cy = pad + radius

    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)

    // Head circle + pointer drawn as a single path so they share one shadow
    // instead of seaming where the triangle meets the circle.
    val body =
        Path().apply {
            addCircle(cx, cy, radius, Path.Direction.CW)
            val mouth = radius * 0.7f
            moveTo(cx - mouth, cy + radius * 0.55f)
            lineTo(cx + mouth, cy + radius * 0.55f)
            lineTo(cx, pad + head + tail)
            close()
        }

    val fill =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            style = Paint.Style.FILL
            setShadowLayer(dp(3f), 0f, dp(2f), 0x80000000.toInt())
        }
    canvas.drawPath(body, fill)

    val ring =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = ringWidth
        }
    canvas.drawCircle(cx, cy, radius - ringWidth / 2f, ring)

    // Color emoji fonts ignore the paint color, so the glyph keeps its own hue.
    val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = head * 0.58f
        }
    val metrics = text.fontMetrics
    val baseline = cy - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(emoji, cx, baseline, text)

    return bitmap
}
