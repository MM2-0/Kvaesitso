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
package com.vitorpamplona.amethyst.commons.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The list showed `primal.net` twice; these are the pairs that produced that. */
class BrowserHistoryKeyTest {
    @Test
    fun `a trailing slash on the root is the same page`() {
        assertEquals(historyKey("https://primal.net"), historyKey("https://primal.net/"))
    }

    @Test
    fun `the host is compared without case`() {
        assertEquals(historyKey("https://Primal.NET/"), historyKey("https://primal.net/"))
    }

    @Test
    fun `a fragment is not a different page`() {
        assertEquals(historyKey("https://primal.net/home"), historyKey("https://primal.net/home#top"))
    }

    @Test
    fun `a path is still its own page`() {
        assertNotEquals(historyKey("https://primal.net/"), historyKey("https://primal.net/home"))
    }

    @Test
    fun `a query is still its own page`() {
        assertNotEquals(historyKey("https://x.com/search?q=a"), historyKey("https://x.com/search?q=b"))
    }

    @Test
    fun `a trailing slash deeper in the path is left alone`() {
        // Servers are free to treat these as different, so we do not decide for them.
        assertNotEquals(historyKey("https://primal.net/home"), historyKey("https://primal.net/home/"))
    }

    @Test
    fun `http and https are different origins`() {
        assertNotEquals(historyKey("http://primal.net/"), historyKey("https://primal.net/"))
    }

    @Test
    fun `something that is not a url is returned as itself`() {
        assertEquals("not a url", historyKey("not a url"))
    }
}
