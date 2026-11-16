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
package com.vitorpamplona.amethyst.commons.preview

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The charset half of the meta scan: `<meta charset>` and `<meta http-equiv="content-type">` are
 * the tags that decide how the rest of the document -- including every og: value -- is decoded.
 * Get this wrong and a preview renders mojibake rather than nothing, so it fails quietly.
 */
class HtmlParserCharsetTest {
    private suspend fun firstContentOf(
        bytes: ByteArray,
        charsetName: String?,
    ): String =
        HtmlParser()
            .parseHtml(bytes, charsetName)
            .last()
            .attr("content")

    // -- HtmlCharsetParser --------------------------------------------------------------------

    @Test
    fun sniffsTheCharsetAttribute() {
        assertEquals("iso-8859-1", HtmlCharsetParser.detectCharset("""<head><meta charset="iso-8859-1">""".encodeToByteArray()))
    }

    @Test
    fun sniffsTheHttpEquivContentType() {
        val html = """<head><meta http-equiv="Content-Type" content="text/html; charset=shift_jis">"""

        assertEquals("shift_jis", HtmlCharsetParser.detectCharset(html.encodeToByteArray()))
    }

    @Test
    fun defaultsToUtf8WhenNothingIsDeclared() {
        assertEquals("UTF-8", HtmlCharsetParser.detectCharset("""<head><title>x</title>""".encodeToByteArray()))
    }

    @Test
    fun onlySniffsTheFirstKilobyte() {
        // The window is deliberate -- the declaration is required to be early -- but it means a
        // charset pushed past 1 KB by a banner comment is not found, and UTF-8 is assumed.
        val pushedOut = "<head>" + "<!-- " + "x".repeat(1100) + " -->" + """<meta charset="iso-8859-1">"""

        assertEquals("UTF-8", HtmlCharsetParser.detectCharset(pushedOut.encodeToByteArray()))
    }

    @Test
    fun aCommentedOutCharsetIsNotSniffed() {
        val html = """<head><!-- <meta charset="iso-8859-1"> --><meta charset="utf-8">"""

        assertEquals("utf-8", HtmlCharsetParser.detectCharset(html.encodeToByteArray()))
    }

    // -- HtmlParser: which charset wins --------------------------------------------------------

    @Test
    fun anExplicitCharsetWinsOverTheDocumentDeclaration() =
        runTest {
            // Content-Type said windows-1252; the document claims utf-8 and must not be believed.
            val bytes =
                byteArrayOf(0x3C) + // '<'
                    """head><meta charset="utf-8"><meta property="og:title" content="caf""".encodeToByteArray() +
                    byteArrayOf(0xE9.toByte()) + // 'é' in windows-1252
                    """"></head>""".encodeToByteArray()

            assertEquals("café", firstContentOf(bytes, "windows-1252"))
        }

    @Test
    fun aByteOrderMarkWinsOverTheDocumentDeclaration() =
        runTest {
            val utf16 = """<head><meta charset="iso-8859-1"><meta property="og:title" content="café"></head>"""
            val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + utf16.encodeToUtf16Be()

            assertEquals("café", firstContentOf(bytes, null))
        }

    @Test
    fun fallsBackToTheDocumentDeclarationWhenTheResponseHasNoCharset() =
        runTest {
            val bytes =
                """<head><meta charset="windows-1252"><meta property="og:title" content="caf""".encodeToByteArray() +
                    byteArrayOf(0xE9.toByte()) +
                    """"></head>""".encodeToByteArray()

            assertEquals("café", firstContentOf(bytes, null))
        }

    private fun String.encodeToUtf16Be(): ByteArray {
        val out = ByteArray(length * 2)
        forEachIndexed { i, c ->
            out[i * 2] = (c.code shr 8).toByte()
            out[i * 2 + 1] = (c.code and 0xFF).toByte()
        }
        return out
    }
}
