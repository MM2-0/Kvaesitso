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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.qrcode

import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * Error correction Q first, for codes photographed off a screen; a payload too long for Q at the
 * largest version still fits at M or L, and a slightly less forgiving code beats the empty square
 * that used to be drawn in its place.
 */
private val ERROR_CORRECTION_FALLBACK = listOf(ErrorCorrectionLevel.Q, ErrorCorrectionLevel.M, ErrorCorrectionLevel.L)

actual fun encodeQrMatrix(contents: String): QrMatrix? {
    if (contents.isEmpty()) return null

    val code =
        ERROR_CORRECTION_FALLBACK.firstNotNullOfOrNull { level ->
            try {
                Encoder.encode(
                    contents,
                    level,
                    mapOf(
                        EncodeHintType.CHARACTER_SET to "UTF-8",
                        EncodeHintType.MARGIN to QR_QUIET_ZONE_MODULES,
                        EncodeHintType.ERROR_CORRECTION to level,
                    ),
                )
            } catch (e: WriterException) {
                null
            }
        } ?: return null

    val matrix = code.matrix
    return QrMatrix(
        width = matrix.width,
        height = matrix.height,
        modules = BooleanArray(matrix.width * matrix.height) { matrix[it % matrix.width, it / matrix.width] == 1.toByte() },
    )
}
