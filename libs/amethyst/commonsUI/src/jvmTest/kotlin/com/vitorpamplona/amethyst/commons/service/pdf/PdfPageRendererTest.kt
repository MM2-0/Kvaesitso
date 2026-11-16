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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The desktop renderer, on PDFs written here: page count, page size, rotation and the drawn pixels. */
class PdfPageRendererTest {
    private val file = File.createTempFile("pdf-page-renderer", ".pdf")

    @AfterTest
    fun cleanUp() {
        file.delete()
    }

    /** A US Letter page with its top-left inch painted black, then a landscape page. */
    private fun writeTwoPages() {
        PDDocument().use { doc ->
            val letter = PDPage(PDRectangle.LETTER)
            doc.addPage(letter)
            PDPageContentStream(doc, letter).use { stream ->
                stream.setNonStrokingColor(0f, 0f, 0f)
                // PDF space starts at the bottom left: the top inch is y = 792 - 72.
                stream.addRect(0f, 792f - 72f, 72f, 72f)
                stream.fill()
            }
            doc.addPage(PDPage(PDRectangle(792f, 612f)))
            doc.save(file)
        }
    }

    @Test
    fun countsPagesAndReportsTheirSizeInPoints() {
        writeTwoPages()
        PdfPageRenderer(file).use { renderer ->
            assertEquals(2, renderer.pageCount)

            val portrait = renderer.renderPage(0, 792)
            assertEquals(612, portrait.pageWidth)
            assertEquals(792, portrait.pageHeight)

            val landscape = renderer.renderPage(1, 792)
            assertEquals(792, landscape.pageWidth)
            assertEquals(612, landscape.pageHeight)
        }
    }

    @Test
    fun scalesTheLongestSideToMaxDim() {
        writeTwoPages()
        PdfPageRenderer(file).use { renderer ->
            val page = renderer.renderPage(0, 1584)
            assertEquals(1224, page.image.width)
            assertEquals(1584, page.image.height)
        }
    }

    @Test
    fun drawsThePageOnWhite() {
        writeTwoPages()
        PdfPageRenderer(file).use { renderer ->
            val pixels = renderer.renderPage(0, 792).image.toPixelMap()
            // Inside the painted inch, and far away from it.
            assertEquals(Color.Black, pixels[36, 36])
            assertEquals(Color.White, pixels[400, 600])
        }
    }

    @Test
    fun aQuarterTurnedPageReportsItsTurnedSize() {
        PDDocument().use { doc ->
            doc.addPage(PDPage(PDRectangle.LETTER).apply { rotation = 90 })
            doc.save(file)
        }
        PdfPageRenderer(file).use { renderer ->
            val page = renderer.renderPage(0, 792)
            assertEquals(792, page.pageWidth)
            assertEquals(612, page.pageHeight)
            assertEquals(792, page.image.width)
            assertEquals(612, page.image.height)
        }
    }

    @Test
    fun notAPdfFailsToOpen() {
        file.writeText("not a pdf")
        assertFailsWith<Exception> { PdfPageRenderer(file) }
    }
}
