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

import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.IOException

/**
 * PDFBox runs in the app's heap, unlike Android's renderer, so a hostile file must not be able to
 * take the app down. Embedded images are subsampled to the size being drawn instead of decoded
 * whole (a tiny compressed 25000x25000 image would otherwise need about 2 GB), and an
 * [OutOfMemoryError] or [StackOverflowError] from a file built to cause one becomes an
 * [IOException], so the card falls back to a link.
 */
actual class PdfPageRenderer actual constructor(
    file: File,
) : AutoCloseable {
    private val document: PDDocument = guarded { Loader.loadPDF(file) }
    private val renderer = PDFRenderer(document).apply { isSubsamplingAllowed = true }

    actual val pageCount: Int = document.numberOfPages

    actual fun renderPage(
        pageIndex: Int,
        maxDim: Int,
    ): RenderedPdfPage {
        val page = document.getPage(pageIndex)
        val box = page.cropBox
        // A page turned a quarter is drawn turned; its size has to follow.
        val quarterTurned = page.rotation % 180 != 0
        val pageWidth = (if (quarterTurned) box.height else box.width).toInt()
        val pageHeight = (if (quarterTurned) box.width else box.height).toInt()

        val longest = maxOf(pageWidth, pageHeight).coerceAtLeast(1)
        // RGB draws on white, as Android's renderer does after eraseColor(WHITE).
        val image = guarded { renderer.renderImage(pageIndex, maxDim.toFloat() / longest, ImageType.RGB).toComposeImageBitmap() }
        return RenderedPdfPage(image, pageWidth, pageHeight)
    }

    actual override fun close() {
        document.close()
    }
}

private inline fun <T> guarded(block: () -> T): T =
    try {
        block()
    } catch (e: OutOfMemoryError) {
        throw IOException("PDF too large to draw", e)
    } catch (e: StackOverflowError) {
        throw IOException("PDF nested too deeply to draw", e)
    }
