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
package com.vitorpamplona.amethyst.commons.richtext

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime

class UrlWithoutSchemeTest {
    @Test
    fun recognisesSchemelessUrls() {
        assertTrue(RichTextParser.isUrlWithoutScheme("example.com"))
        assertTrue(RichTextParser.isUrlWithoutScheme("example.com/path?x=1"))
        assertTrue(RichTextParser.isUrlWithoutScheme("user@example.com"))
        assertTrue(RichTextParser.isUrlWithoutScheme("sub.example.com:8080/a-b_c"))
        assertFalse(RichTextParser.isUrlWithoutScheme("just words"))
    }

    @Test
    fun pathologicalInputDoesNotBacktrackExponentially() {
        // A trailing line terminator forces the regex to fail; with an optional
        // separator inside the repeated group that failure took 2^n steps
        // (13 s at n = 26). The composer runs this on every keystroke.
        val input = "a.b" + "c".repeat(40) + "\r"
        val elapsed = measureTime { RichTextParser.isUrlWithoutScheme(input) }
        assertTrue(elapsed.inWholeMilliseconds < 1_000, "took $elapsed")
    }
}
