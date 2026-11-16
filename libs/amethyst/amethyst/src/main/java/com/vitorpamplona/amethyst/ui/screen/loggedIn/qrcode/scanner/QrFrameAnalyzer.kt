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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.vitorpamplona.amethyst.commons.qrcode.ScanFrame
import com.vitorpamplona.amethyst.commons.qrcode.ScanResult
import com.vitorpamplona.quartz.utils.Log
import kotlin.math.max

/** One analysed frame: what was decoded, how big the analysed image was, and how dark it is. */
data class FrameScan(
    val results: List<ScanResult>,
    val frame: ScanFrame,
    /** Mean luminance over a sparse sample of the Y plane, 0f (black) to 1f (white). */
    val brightness: Float,
    /**
     * Whether this frame got the expensive pass. Only those find a marginal code, so only they
     * can say a code is truly alone in view.
     */
    val thorough: Boolean = true,
)

/**
 * Decodes every camera frame and reports what it found.
 *
 * Two things happen per frame beyond the decode itself:
 *
 * 1. **Effort alternates.** Most frames get a fast pass; every [THOROUGH_EVERY]th gets
 *    `tryHarder` + `tryDenoise`. A stubborn code therefore still gets several expensive attempts
 *    a second, without the frame rate collapsing for codes that were never difficult.
 * 2. **Brightness is measured.** A sparse sample of the Y plane costs almost nothing and is what
 *    lets the UI offer the torch exactly when the scene is too dark, rather than parking a
 *    permanent button in the corner or firing the light at people unprompted.
 *
 * [isPaused] is polled from the analysis thread before each decode. While a sheet over the
 * camera is waiting on the user every result would be discarded, and a thorough pass is the most
 * expensive thing the scanner does, so frames are closed unread instead.
 */
class QrFrameAnalyzer(
    private val decoder: BarcodeDecoder,
    private val isPaused: () -> Boolean = { false },
    private val onFrame: (FrameScan) -> Unit,
) : ImageAnalysis.Analyzer {
    private var frameCount = 0L

    override fun analyze(image: ImageProxy) {
        image.use {
            if (isPaused()) return
            val effort =
                if (frameCount++ % THOROUGH_EVERY == 0L) DecodeEffort.Thorough else DecodeEffort.Fast

            val brightness = meanLuminance(image)

            val results =
                try {
                    decoder.decode(image, effort)
                } catch (e: Exception) {
                    // A frame we cannot read is not worth killing the camera over.
                    Log.w("QrScanner") { "Decode failed on one frame: ${e.message}" }
                    emptyList()
                }

            onFrame(FrameScan(results, rotatedFrameSize(image), brightness, thorough = effort == DecodeEffort.Thorough))
        }
    }

    /**
     * The size of the image the decoder actually saw.
     *
     * zxing-cpp is handed the crop rect and the rotation, and reports positions inside that
     * cropped, rotated space — so a quarter-turn swaps width and height. The overlay maps
     * [ScanBounds] onto the preview with this, so getting it wrong draws the highlight in the
     * wrong place.
     */
    private fun rotatedFrameSize(image: ImageProxy): ScanFrame {
        val crop = image.cropRect
        val quarterTurned = image.imageInfo.rotationDegrees % 180 != 0
        return if (quarterTurned) {
            ScanFrame(crop.height(), crop.width())
        } else {
            ScanFrame(crop.width(), crop.height())
        }
    }

    /**
     * Mean luminance over a grid of at most [LUMA_SAMPLES_PER_AXIS]² pixels.
     *
     * Uses absolute [java.nio.ByteBuffer.get] so it never disturbs the buffer position the
     * decoder is about to read from.
     */
    private fun meanLuminance(image: ImageProxy): Float {
        val plane = image.planes.firstOrNull() ?: return 1f
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val crop = image.cropRect

        val stepX = max(1, crop.width() / LUMA_SAMPLES_PER_AXIS)
        val stepY = max(1, crop.height() / LUMA_SAMPLES_PER_AXIS)

        var sum = 0L
        var count = 0
        var y = crop.top
        while (y < crop.bottom) {
            val row = y * rowStride
            var x = crop.left
            while (x < crop.right) {
                val index = row + x
                if (index in 0 until buffer.limit()) {
                    sum += buffer.get(index).toInt() and 0xFF
                    count++
                }
                x += stepX
            }
            y += stepY
        }

        return if (count == 0) 1f else sum.toFloat() / count / 255f
    }

    companion object {
        /**
         * At ~30fps this is roughly six thorough passes a second — enough that a hard code
         * resolves in well under a second, few enough that the analysis thread keeps up.
         */
        const val THOROUGH_EVERY = 5L

        private const val LUMA_SAMPLES_PER_AXIS = 24
    }
}
