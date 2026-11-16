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
package com.vitorpamplona.amethyst.napplethost

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/**
 * Captures the selection loupe's magnified slice of an embedded page for the main process.
 *
 * Host-side `PixelCopy` can't read the sandbox surface, so the page is drawn here. That draw has to happen
 * on the main thread — the one thread every sandboxed surface in `:napplet` renders on — so everything else
 * is kept off it: the frame is capped at [MAX_SIDE_PX] a side (a large box at high zoom used to allocate
 * tens of MB and produce a frame too big for the binder, which was silently dropped), and the encode runs on
 * a background thread as lossy WebP (a quality-100 PNG, per drag frame, on the main thread, was the cost).
 */
@RequiresApi(Build.VERSION_CODES.R)
internal object MagnifierCapture {
    private const val MAX_SIDE_PX = 512
    private const val QUALITY = 85

    private val encoder = Executors.newSingleThreadExecutor { Thread(it, "napplet-magnifier").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    /**
     * Draws the [boxW]×[boxH] source rect centered on ([cx], [cy]) (view px) of [webView] at [zoom] over
     * [bgColor], then — on the main thread, once encoded — hands [deliver] the image bytes, their size, and
     * the capture time in ms. Main thread only.
     */
    fun capture(
        webView: WebView,
        bgColor: Int,
        cx: Float,
        cy: Float,
        boxW: Int,
        boxH: Int,
        zoom: Float,
        deliver: (bytes: ByteArray, width: Int, height: Int, captureMs: Double) -> Unit,
    ) {
        val t0 = SystemClock.elapsedRealtimeNanos()
        val scale = minOf(zoom, MAX_SIDE_PX.toFloat() / boxW, MAX_SIDE_PX.toFloat() / boxH)
        val outW = (boxW * scale).toInt().coerceAtLeast(1)
        val outH = (boxH * scale).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(outW, outH)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)
        // Map the source rect (centered on cx,cy in view px) into the scaled output bitmap.
        canvas.scale(scale, scale)
        canvas.translate(-(cx - boxW / 2f), -(cy - boxH / 2f))
        webView.draw(canvas)

        encoder.execute {
            val bytes =
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, out)
                    out.toByteArray()
                }
            bitmap.recycle()
            val captureMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
            main.post { deliver(bytes, outW, outH, captureMs) }
        }
    }
}
