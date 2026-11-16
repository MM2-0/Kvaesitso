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

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Measures ZXing-Java — the decoder the old zxing-android-embedded scanner used — against
 * [QrCorpus], and records the result as the baseline the new decoder has to beat.
 *
 * This is the half of the plan's phase 0 that needs no device. The other half
 * (`QrDecodeCorpusTest`, instrumented) runs zxing-cpp over the exported images and asserts it
 * does at least as well, category by category.
 *
 * **What this is and is not.** It measures *decoder quality on a still image*, and it is
 * deliberately generous to the old decoder: it decodes the full uncropped image and retries
 * inverted, where the shipped scanner cropped to a viewfinder rect and alternated inversion
 * across frames. So a win here is a floor on the real-world improvement, not the whole of it.
 */
class QrCorpusBaselineTest {
    @Test
    fun cleanCodesAllDecode() {
        // The one hard assertion. If a pristine, generously-sized code fails, the corpus itself
        // is broken and every other number in this file is meaningless.
        val clean = QrCorpus.all().filter { it.category == "clean" }
        assertEquals("corpus should contain one clean fixture per payload", 3, clean.size)
        clean.forEach {
            assertEquals("clean fixture ${it.name} must decode", it.expected, decode(it.image))
        }
    }

    @Test
    fun reportsTheBaselinePerCategory() {
        val byCategory = QrCorpus.all().groupBy { it.category }

        val rows =
            byCategory.map { (category, fixtures) ->
                val passed = fixtures.count { decode(it.image) == it.expected }
                Row(category, passed, fixtures.size)
            }

        println(render(rows))

        // Recorded, not asserted: these numbers are the yardstick, and pinning them here would
        // just mean a ZXing bump breaks the build instead of informing it.
        if (System.getProperty(EXPORT_PROPERTY) == "true") {
            writeBaseline(rows)
        }
    }

    /**
     * Writes the corpus and its baseline into the instrumented test's assets.
     *
     * Opt-in via `-Pamethyst.qr.corpus.export=true` so an ordinary test run never dirties the
     * working tree. The generator is deterministic, so re-exporting an unchanged corpus is a
     * no-op. It is a Gradle property rather than a plain `-D` because the test runs in a forked
     * JVM that does not inherit the Gradle JVM's system properties.
     */
    @Test
    fun exportsTheCorpusForTheInstrumentedTest() {
        assumeTrue("pass -Pamethyst.qr.corpus.export=true to regenerate the corpus", System.getProperty(EXPORT_PROPERTY) == "true")

        val dir = File(ASSET_DIR)
        dir.mkdirs()

        QrCorpus.all().forEach { fixture ->
            // 8-bit grey, not RGB: every fixture is greyscale in content, and storing three
            // identical channels tripled the size of a corpus that has to live in the repo.
            ImageIO.write(toGrayscale(fixture.image), "png", File(dir, "${fixture.name}.png"))
        }

        File(dir, "expected.tsv").writeText(
            QrCorpus.all().joinToString("\n", postfix = "\n") { "${it.name}.png\t${it.category}\t${it.expected}" },
        )
    }

    private fun toGrayscale(source: BufferedImage): BufferedImage {
        val out = BufferedImage(source.width, source.height, BufferedImage.TYPE_BYTE_GRAY)
        val g = out.createGraphics()
        g.drawImage(source, 0, 0, null)
        g.dispose()
        return out
    }

    private data class Row(
        val category: String,
        val passed: Int,
        val total: Int,
    )

    private fun render(rows: List<Row>): String =
        buildString {
            appendLine()
            appendLine("ZXing-Java (the old scanner's decoder) over QrCorpus:")
            rows.sortedBy { it.category }.forEach {
                appendLine("  %-20s %d/%d".format(it.category, it.passed, it.total))
            }
            val passed = rows.sumOf { it.passed }
            val total = rows.sumOf { it.total }
            appendLine("  %-20s %d/%d".format("TOTAL", passed, total))
        }

    private fun writeBaseline(rows: List<Row>) {
        File(ASSET_DIR).mkdirs()
        File(ASSET_DIR, BASELINE_FILE).writeText(
            rows.sortedBy { it.category }.joinToString("\n", postfix = "\n") { "${it.category}\t${it.passed}\t${it.total}" },
        )
    }

    /** ZXing-Java, full frame, with an inverted retry — a deliberately generous baseline. */
    private fun decode(image: BufferedImage): String? {
        val source = GrayLuminanceSource(image)
        return tryDecode(source) ?: tryDecode(source.invert())
    }

    private fun tryDecode(source: LuminanceSource): String? =
        try {
            MultiFormatReader()
                .apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE))) }
                .decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                .text
        } catch (e: Exception) {
            null
        }

    /**
     * A [LuminanceSource] over a [BufferedImage].
     *
     * Hand-rolled rather than pulling in `com.google.zxing:javase` for twenty lines — a new
     * dependency, even a permissive one, is not worth it here.
     */
    private class GrayLuminanceSource(
        image: BufferedImage,
    ) : LuminanceSource(image.width, image.height) {
        private val luminances =
            ByteArray(image.width * image.height).also { out ->
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) {
                        val rgb = image.getRGB(x, y)
                        val r = (rgb shr 16) and 0xFF
                        val g = (rgb shr 8) and 0xFF
                        val b = rgb and 0xFF
                        // ITU-R BT.601 in integer arithmetic, matching ZXing's own conversion.
                        out[y * image.width + x] = ((r * 306 + g * 601 + b * 117) shr 10).toByte()
                    }
                }
            }

        override fun getRow(
            y: Int,
            row: ByteArray?,
        ): ByteArray {
            val out = if (row != null && row.size >= width) row else ByteArray(width)
            System.arraycopy(luminances, y * width, out, 0, width)
            return out
        }

        override fun getMatrix(): ByteArray = luminances

        override fun isRotateSupported(): Boolean = false

        override fun isCropSupported(): Boolean = false
    }

    companion object {
        private const val EXPORT_PROPERTY = "amethyst.qr.corpus.export"
        private const val ASSET_DIR = "src/androidTest/assets/qr"
        private const val BASELINE_FILE = "baseline.tsv"
    }
}
