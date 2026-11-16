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
package com.vitorpamplona.amethyst.commons.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A relative reference against a base with no path resolves as if the path were `/` (RFC 3986).
 * Android's `java.net.URI` got this wrong — `y18.svg` against `https://news.ycombinator.com` became
 * `https://news.ycombinator.comy18.svg` — so the base is normalized before resolving.
 */
class HttpUrlResolutionTest {
    @Test
    fun relativeReferenceAgainstAPathlessBase() {
        assertEquals("https://news.ycombinator.com/y18.svg", resolveHttpUrl("https://news.ycombinator.com", "y18.svg"))
        assertEquals("https://news.ycombinator.com/favicon.ico", resolveHttpUrl("https://news.ycombinator.com", "/favicon.ico"))
    }

    @Test
    fun pathlessBaseKeepsItsQueryAndEncoding() {
        assertEquals("https://a.example/icon.png", resolveHttpUrl("https://a.example?q=a%20b", "icon.png"))
        // Encoded characters are carried over as they are, not encoded a second time.
        assertEquals("https://a.example/icons/a%20b.png", resolveHttpUrl("https://a.example?q=a%20b", "icons/a%20b.png"))
    }

    @Test
    fun basesWithAPathAreUnchanged() {
        assertEquals("https://a.example/dir/icon.png", resolveHttpUrl("https://a.example/dir/page.html", "icon.png"))
        assertEquals("https://cdn.example/x.png", resolveHttpUrl("https://a.example/dir/", "//cdn.example/x.png"))
    }

    @Test
    fun nonHttpStaysRefused() {
        assertNull(resolveHttpUrl("https://a.example", "file:///etc/passwd"))
    }
}
