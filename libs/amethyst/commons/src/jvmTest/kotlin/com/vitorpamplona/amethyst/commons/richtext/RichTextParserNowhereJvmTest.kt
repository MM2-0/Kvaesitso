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

import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichTextParserNowhereJvmTest {
    @Test
    fun realWorldNowhereStoreLinkRoundtripsThroughTheParser() {
        // Verbatim from a real Nostr note ("Works, thanks!\n\nhttps://nowhr.xyz/s#..."): a
        // published nowhr.xyz store catalogue with a ~5KB base64url fragment. Kept as a
        // resource so the URL isn't subject to source-editor truncation.
        val url =
            this::class.java
                .classLoader!!
                .getResourceAsStream("richtext/realNowhereStoreUrl.txt")!!
                .bufferedReader()
                .readText()
                .trim()

        assertTrue(url.startsWith("https://nowhr.xyz/s#"), "resource lost its prefix")
        assertTrue(url.length > 5000, "resource shorter than expected: ${url.length}")

        val text = "Works, thanks!\n\n$url"

        val state = RichTextParser().parseText(text, EmptyTagList, null)
        val nowhere = state.paragraphs.flatMap { it.words }.firstOrNull { it is NowhereLinkSegment } as? NowhereLinkSegment

        assertEquals(url, nowhere?.segmentText)
        assertEquals("nowhr.xyz", nowhere?.host)
        assertEquals("s", nowhere?.tool)
        assertTrue(state.urlSet.withScheme.contains(url), "url not in detected withScheme set")
    }
}
