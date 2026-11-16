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
package com.vitorpamplona.amethyst.commons.service.pdf

import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/** One page drawn by [PdfPageRenderer]: the image, and the page's own size in points (1/72"). */
class RenderedPdfPage(
    val image: ImageBitmap,
    val pageWidth: Int,
    val pageHeight: Int,
)

/**
 * A PDF file opened for drawing its pages: Android's `PdfRenderer` on Android, PDFBox on the
 * desktop. Not thread-safe; callers draw one page at a time and [close] it when done.
 */
expect class PdfPageRenderer(
    file: File,
) : AutoCloseable {
    val pageCount: Int

    /**
     * Draws page [pageIndex] on white, scaled so its longest side is [maxDim] pixels. Always
     * scales, never draws at native size: a page's native size is in points, far below any
     * useful display resolution, and a PDF is vector, so drawing it larger costs nothing.
     */
    fun renderPage(
        pageIndex: Int,
        maxDim: Int,
    ): RenderedPdfPage

    override fun close()
}

/**
 * The bitmap size to draw a [pageWidth] x [pageHeight] page at, scaled so its longest side is
 * [targetDim] while keeping its shape.
 */
fun cappedRenderSize(
    pageWidth: Int,
    pageHeight: Int,
    targetDim: Int,
): Pair<Int, Int> {
    if (pageWidth <= 0 || pageHeight <= 0) return 1 to 1
    val longest = maxOf(pageWidth, pageHeight)
    val scale = targetDim.toFloat() / longest
    val w = (pageWidth * scale).toInt().coerceAtLeast(1)
    val h = (pageHeight * scale).toInt().coerceAtLeast(1)
    return w to h
}
