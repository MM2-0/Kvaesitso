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

/**
 * Characters inside a URL are not markdown. Amazon's image CDN names its files
 * `71zvSJXs2KL._SL1499_.jpg`: the `_` after the `.` passed the emphasis check, so a note holding
 * nothing but that link went to the markdown renderer, which italicised `SL1499`, cut the link in
 * two and never showed the image.
 */
class MarkdownDetectionUrlTest {
    private fun isMarkdown(content: String) = CachedRichTextParser.isMarkdown(content)

    @Test
    fun underscoresInsideAnImageUrlAreNotEmphasis() {
        assertFalse(isMarkdown("https://m.media-amazon.com/images/I/71zvSJXs2KL._SL1499_.jpg"))
    }

    @Test
    fun underscoresInsideAUrlInProseAreNotEmphasis() {
        assertFalse(isMarkdown("look at this\nhttps://m.media-amazon.com/images/I/71zvSJXs2KL._SL1499_.jpg nice"))
    }

    @Test
    fun otherMarkersInsideAUrlAreNotMarkdown() {
        assertFalse(isMarkdown("https://example.com/a/*star*/b"))
        assertFalse(isMarkdown("https://example.com/wiki/A|B"))
        assertFalse(isMarkdown("https://example.com/~~x~~"))
        assertFalse(isMarkdown("https://example.com/__init__.py"))
    }

    @Test
    fun markdownAfterAUrlIsStillMarkdown() {
        assertTrue(isMarkdown("https://example.com/a_b_.jpg and **bold**"))
        assertTrue(isMarkdown("https://example.com/x\n# Heading"))
    }

    @Test
    fun anOpenerBeforeAUrlDoesNotCloseInsideIt() {
        assertFalse(isMarkdown("so tired -_- https://m.media-amazon.com/images/I/71zvSJXs2KL._SL1499_.jpg"))
        assertFalse(isMarkdown("5 *x https://example.com/a*b.jpg"))
    }

    @Test
    fun aUrlEndsAtUnicodeSpaceAndPunctuation() {
        assertTrue(isMarkdown("https://example.com/a\u3001**\u91cd\u70b9**"))
        assertTrue(isMarkdown("https://example.com/a\u00a0**bold**"))
    }

    @Test
    fun emphasisAroundAUrlIsStillMarkdown() {
        assertTrue(isMarkdown("_see https://example.com/x_"))
        assertTrue(isMarkdown("*see https://example.com/x*."))
        assertTrue(isMarkdown("_see https://example.com/x_"))
        assertTrue(isMarkdown("see [the docs](https://example.com/a_b_.html)"))
    }
}
