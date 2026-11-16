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

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import com.vitorpamplona.amethyst.commons.qrcode.ScanBounds
import com.vitorpamplona.amethyst.commons.qrcode.ScanPoint
import com.vitorpamplona.amethyst.commons.qrcode.ScanResult
import com.vitorpamplona.quartz.utils.Log
import zxingcpp.BarcodeReader

/**
 * How hard to work on one image.
 *
 * The live camera runs [Fast] on most frames and [Thorough] on every Nth, so a difficult code
 * still gets the expensive treatment a few times a second without dropping the frame rate for
 * the easy ones. Imported stills always get [Thorough] — there is only one image and the user is
 * waiting on it.
 */
enum class DecodeEffort {
    Fast,
    Thorough,
}

/**
 * Decodes QR codes out of camera frames and still images.
 *
 * An interface, not a class, because the engine is a choice we want to be able to re-make: the
 * camera plumbing, the overlay and every test above this line are engine-agnostic.
 */
interface BarcodeDecoder {
    /** Decodes straight from a CameraX analysis frame. Called on the analysis executor. */
    fun decode(
        image: ImageProxy,
        effort: DecodeEffort,
    ): List<ScanResult>

    /** Decodes an imported still (gallery, clipboard, share). Called off the main thread. */
    fun decode(
        bitmap: Bitmap,
        effort: DecodeEffort = DecodeEffort.Thorough,
    ): List<ScanResult>
}

/**
 * [BarcodeDecoder] on zxing-cpp (Apache-2.0).
 *
 * Every option here maps to a failure the previous zxing-android-embedded scanner had:
 *
 * - `tryInvert` replaces the old `MIXED_SCAN`, which inverted *alternate frames* and so threw
 *   away half of all decode attempts on ordinary dark-on-light codes. zxing-cpp does the
 *   inverted pass as a fallback inside one call, so nothing is wasted.
 * - `tryRotate` picks up codes held sideways.
 * - `tryDownscale` finds codes that fill most of the frame, which a fixed-scale detector misses.
 * - `tryHarder`/`tryDenoise` (Thorough only) are what actually rescue blurred, creased,
 *   low-contrast and photographed-off-a-screen codes.
 * - `maxNumberOfSymbols = MAX_SYMBOLS` so several codes in frame become a choice for the user
 *   rather than a coin flip.
 */
class ZxingCppBarcodeDecoder : BarcodeDecoder {
    private val fast = BarcodeReader(options(tryHarder = false))
    private val thorough = BarcodeReader(options(tryHarder = true))

    private fun reader(effort: DecodeEffort) = if (effort == DecodeEffort.Fast) fast else thorough

    override fun decode(
        image: ImageProxy,
        effort: DecodeEffort,
    ): List<ScanResult> =
        try {
            reader(effort).read(image).toScanResults()
        } catch (e: IllegalStateException) {
            // read() rejects any format that is not YUV. We always request YUV_420_888, so this
            // means the device handed us something else; log once per frame rather than crash.
            Log.w("QrScanner") { "Unsupported analysis image format ${image.format}: ${e.message}" }
            emptyList()
        }

    override fun decode(
        bitmap: Bitmap,
        effort: DecodeEffort,
    ): List<ScanResult> = reader(effort).read(bitmap, Rect(0, 0, bitmap.width, bitmap.height)).toScanResults()

    private fun List<BarcodeReader.Result>.toScanResults(): List<ScanResult> =
        mapNotNull { result ->
            val text = result.text
            if (result.error != null || text.isNullOrEmpty()) return@mapNotNull null

            ScanResult(
                text = text,
                bounds =
                    result.position.let {
                        ScanBounds(
                            topLeft = ScanPoint(it.topLeft.x.toFloat(), it.topLeft.y.toFloat()),
                            topRight = ScanPoint(it.topRight.x.toFloat(), it.topRight.y.toFloat()),
                            bottomRight = ScanPoint(it.bottomRight.x.toFloat(), it.bottomRight.y.toFloat()),
                            bottomLeft = ScanPoint(it.bottomLeft.x.toFloat(), it.bottomLeft.y.toFloat()),
                        )
                    },
                sequenceId = result.sequenceId,
                sequenceIndex = result.sequenceIndex,
                sequenceSize = result.sequenceSize,
            )
        }

    companion object {
        /**
         * Enough to disambiguate a poster with a few codes on it without letting a page of
         * barcodes turn every frame into a long detection run.
         */
        const val MAX_SYMBOLS = 5

        /**
         * Only the square formats. Nostr uses plain QR, but Micro and rMQR cost nothing extra to
         * accept and some hardware wallets and printed tags use them. Linear barcodes stay off:
         * they have no meaning here and each extra family slows every frame down.
         */
        private val FORMATS =
            setOf(
                BarcodeReader.Format.QR_CODE,
                BarcodeReader.Format.MICRO_QR_CODE,
                BarcodeReader.Format.RMQR_CODE,
            )

        private fun options(tryHarder: Boolean) =
            BarcodeReader.Options(
                formats = FORMATS,
                tryHarder = tryHarder,
                tryRotate = true,
                tryInvert = true,
                tryDownscale = true,
                tryDenoise = tryHarder,
                binarizer = BarcodeReader.Binarizer.LOCAL_AVERAGE,
                maxNumberOfSymbols = MAX_SYMBOLS,
                textMode = BarcodeReader.TextMode.PLAIN,
            )
    }
}
