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
package com.vitorpamplona.amethyst.commons.qrcode

/** A point in the decoder's image space (origin top-left, after crop and rotation). */
data class ScanPoint(
    val x: Float,
    val y: Float,
)

/**
 * The four corners of a decoded symbol, in the decoder's image space.
 *
 * Kept because they drive three things the old scanner could not do: highlighting the code we
 * actually read, letting the user tap the right one when several are in frame, and measuring how
 * small the symbol is so auto-zoom knows whether to push in.
 */
data class ScanBounds(
    val topLeft: ScanPoint,
    val topRight: ScanPoint,
    val bottomRight: ScanPoint,
    val bottomLeft: ScanPoint,
) {
    val centerX: Float get() = (topLeft.x + topRight.x + bottomRight.x + bottomLeft.x) / 4f
    val centerY: Float get() = (topLeft.y + topRight.y + bottomRight.y + bottomLeft.y) / 4f

    /** The longest edge of the quad — a rotation-independent stand-in for "how big is it". */
    val longestSide: Float
        get() =
            maxOf(
                dist(topLeft, topRight),
                dist(topRight, bottomRight),
                dist(bottomRight, bottomLeft),
                dist(bottomLeft, topLeft),
            )

    private fun dist(
        a: ScanPoint,
        b: ScanPoint,
    ): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

/**
 * One decoded symbol.
 *
 * [sequenceSize] is greater than zero only for Structured Append codes — a payload split across
 * several physical QR codes. [StructuredAppendAccumulator] reassembles those.
 */
data class ScanResult(
    val text: String,
    val bounds: ScanBounds?,
    val sequenceId: String? = null,
    val sequenceIndex: Int = -1,
    val sequenceSize: Int = -1,
) {
    val isPartOfSequence: Boolean get() = sequenceSize > 0 && sequenceIndex >= 0
}

/** The size of the image the decoder was handed, so callers can map [ScanBounds] onto a view. */
data class ScanFrame(
    val width: Int,
    val height: Int,
)
