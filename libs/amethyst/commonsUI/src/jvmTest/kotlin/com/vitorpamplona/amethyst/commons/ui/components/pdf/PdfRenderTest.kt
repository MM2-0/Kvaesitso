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
package com.vitorpamplona.amethyst.commons.ui.components.pdf

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import com.sun.net.httpserver.HttpServer
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import okio.Path.Companion.toOkioPath
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The shared PDF card and reader draw a real PDF on the desktop: fetched over HTTP into Coil's
 * disk cache, opened by PDFBox, and drawn through Compose, offscreen. Each page is one solid
 * colour, so the pixels say which page arrived.
 */
@OptIn(DelicateCoilApi::class, InternalComposeUiApi::class)
class PdfRenderTest {
    private lateinit var server: HttpServer
    private val cacheDir = Files.createTempDirectory("pdf-render-cache").toFile()
    private lateinit var url: String

    @BeforeTest
    fun startServer() {
        val pdf =
            ByteArrayOutputStream()
                .also { out ->
                    PDDocument().use { doc ->
                        // Red, green, blue: the colour on screen says which page is showing.
                        listOf(Triple(1f, 0f, 0f), Triple(0f, 1f, 0f), Triple(0f, 0f, 1f)).forEach { (r, g, b) ->
                            val page = PDPage(PDRectangle.LETTER)
                            doc.addPage(page)
                            PDPageContentStream(doc, page).use { stream ->
                                stream.setNonStrokingColor(r, g, b)
                                stream.addRect(0f, 0f, 612f, 792f)
                                stream.fill()
                            }
                        }
                        doc.save(out)
                    }
                }.toByteArray()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/doc.pdf") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/pdf")
            exchange.sendResponseHeaders(200, pdf.size.toLong())
            exchange.responseBody.use { it.write(pdf) }
        }
        server.start()
        url = "http://127.0.0.1:${server.address.port}/doc.pdf"

        // Replaced, not set-if-absent: Coil's singleton outlives a test class, and another render
        // test may have installed a loader without this disk cache.
        SingletonImageLoader.setUnsafe { ctx: PlatformContext ->
            ImageLoader
                .Builder(ctx)
                .diskCache { DiskCache.Builder().directory(cacheDir.toOkioPath()).build() }
                .build()
        }
    }

    @AfterTest
    fun stop() {
        SingletonImageLoader.reset()
        server.stop(0)
        cacheDir.deleteRecursively()
    }

    private fun scene(content: @Composable () -> Unit): ImageComposeScene =
        ImageComposeScene(width = 600, height = 900, density = Density(1f)).apply {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
        }

    /** Polls the frames until [color] covers a good part of the screen; the fetch and render run off the composition. */
    private fun ImageComposeScene.waitFor(color: (Color) -> Boolean): Int {
        var count = 0
        for (poll in 0 until 100) {
            // A real frame time, so animations such as the page turn advance between frames.
            count = render(System.nanoTime()).count(color)
            if (count > 10_000) break
            Thread.sleep(100)
        }
        return count
    }

    @Test
    fun theCardDrawsTheFirstPage() {
        val scene = scene { PdfPreviewCard(content = MediaUrlPdf(url), accountViewModel = mockAccountViewModel(), onOpen = {}) }
        try {
            val red = scene.waitFor(::isRed)
            assertTrue(red > 10_000, "the first page should have been drawn in the card; found $red red pixels")
        } finally {
            scene.close()
        }
    }

    @Test
    fun theReaderDrawsAPageAndTheArrowKeyTurnsIt() {
        val scene = scene { PdfViewerContent(content = MediaUrlPdf(url), accountViewModel = mockAccountViewModel(), onDismiss = {}) }
        try {
            val red = scene.waitFor(::isRed)
            assertTrue(red > 10_000, "the reader should have drawn the first page; found $red red pixels")

            val handled = scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyDown))
            scene.sendKeyEvent(KeyEvent(Key.DirectionRight, KeyEventType.KeyUp))
            assertTrue(handled, "the reader should have the focus and take the right arrow")

            val green = scene.waitFor(::isGreen)
            assertTrue(green > 10_000, "the right arrow should have turned to the second page; found $green green pixels")
        } finally {
            scene.close()
        }
    }

    private fun isRed(color: Color) = color.red > 0.9f && color.green < 0.1f && color.blue < 0.1f

    private fun isGreen(color: Color) = color.green > 0.9f && color.red < 0.1f && color.blue < 0.1f

    private fun Image.count(matches: (Color) -> Boolean): Int {
        val bitmap = Bitmap.makeFromImage(this)
        var count = 0
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) {
                if (matches(Color(bitmap.getColor(x, y)))) count++
            }
        }
        return count * 16
    }
}
