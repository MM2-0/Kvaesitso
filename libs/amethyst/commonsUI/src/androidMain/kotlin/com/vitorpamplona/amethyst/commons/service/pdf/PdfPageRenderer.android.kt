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

import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import java.io.File

actual class PdfPageRenderer actual constructor(
    file: File,
) : AutoCloseable {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer =
        try {
            PdfRenderer(pfd)
        } catch (t: Throwable) {
            pfd.close()
            throw t
        }

    // Read once: Compose's saveable PagerState can ask for the count during teardown, after
    // close(), when the renderer would throw "Document already closed".
    actual val pageCount: Int = renderer.pageCount

    actual fun renderPage(
        pageIndex: Int,
        maxDim: Int,
    ): RenderedPdfPage =
        renderer.openPage(pageIndex).use { page ->
            val (width, height) = cappedRenderSize(page.width, page.height, maxDim)
            // PdfRenderer requires ARGB_8888; RGB_565 silently produces blank output.
            val bitmap = createBitmap(width, height)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            RenderedPdfPage(bitmap.asImageBitmap(), page.width, page.height)
        }

    actual override fun close() {
        renderer.close()
        pfd.close()
    }
}
