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
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs zxing-cpp over the committed [QrCorpus] images and asserts it reads at least as many as
 * ZXing-Java did, category by category.
 *
 * This is the gate the plan's phase 0 asks for: the justification for replacing the decoder is
 * supposed to be a measurement, not an argument. The baseline it compares against was produced on
 * the JVM by `QrCorpusBaselineTest` from the same images.
 *
 * Regenerate both with:
 * ```
 * ./gradlew :amethyst:testFdroidDebugUnitTest --tests '*QrCorpusBaselineTest*' \\
 *     -Pamethyst.qr.corpus.export=true
 * ```
 */
@RunWith(AndroidJUnit4::class)
class QrDecodeCorpusTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val decoder = ZxingCppBarcodeDecoder()

    @Test
    fun readsEveryCleanCode() {
        fixtures().filter { it.category == "clean" }.forEach {
            assertTrue("clean fixture ${it.file} must decode", decode(it))
        }
    }

    @Test
    fun beatsTheOldDecoderInEveryCategory() {
        val baseline = baseline()
        assertTrue("baseline.tsv missing - regenerate the corpus", baseline.isNotEmpty())

        val results = fixtures().groupBy { it.category }.mapValues { (_, list) -> list.count { decode(it) } }
        val regressions = mutableListOf<String>()

        val report =
            buildString {
                appendLine()
                appendLine("category              zxing-cpp   ZXing-Java")
                baseline.keys.sorted().forEach { category ->
                    val old = baseline.getValue(category)
                    val new = results[category] ?: 0
                    appendLine("  %-20s %d/%d        %d/%d".format(category, new, old.total, old.passed, old.total))
                    if (new < old.passed) regressions += "$category: $new < ${old.passed}"
                }
            }
        println(report)

        if (regressions.isNotEmpty()) {
            fail("zxing-cpp read fewer codes than ZXing-Java in: ${regressions.joinToString("; ")}$report")
        }
    }

    private data class Fixture(
        val file: String,
        val category: String,
        val expected: String,
    )

    private data class Baseline(
        val passed: Int,
        val total: Int,
    )

    private fun decode(fixture: Fixture): Boolean {
        val bitmap =
            assets.open("$ASSET_DIR/${fixture.file}").use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })
            } ?: return false

        return try {
            decoder.decode(bitmap, DecodeEffort.Thorough).any { it.text == fixture.expected }
        } finally {
            bitmap.recycle()
        }
    }

    private fun fixtures(): List<Fixture> =
        assets.open("$ASSET_DIR/expected.tsv").bufferedReader().useLines { lines ->
            lines
                .filter { it.isNotBlank() }
                .map { line ->
                    val (file, category, expected) = line.split('\t', limit = 3)
                    Fixture(file, category, expected)
                }.toList()
        }

    private fun baseline(): Map<String, Baseline> =
        assets.open("$ASSET_DIR/baseline.tsv").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.associate { line ->
                val (category, passed, total) = line.split('\t', limit = 3)
                category to Baseline(passed.toInt(), total.toInt())
            }
        }

    companion object {
        private const val ASSET_DIR = "qr"
    }
}
