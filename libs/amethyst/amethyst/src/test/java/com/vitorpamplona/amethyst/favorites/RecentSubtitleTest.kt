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
package com.vitorpamplona.amethyst.favorites

import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.browser.recentSubtitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The two rows that both read "Primal / primal.net" and could not be told apart. */
class RecentSubtitleTest {
    @Test
    fun `two pages of one site no longer read the same`() {
        assertNotEquals(
            recentSubtitle("https://primal.net/", "primal.net"),
            recentSubtitle("https://primal.net/home", "primal.net"),
        )
    }

    @Test
    fun `the root shows the bare host`() {
        assertEquals("primal.net", recentSubtitle("https://primal.net/", "primal.net"))
    }

    @Test
    fun `a path is shown after the host`() {
        assertEquals("primal.net/home", recentSubtitle("https://primal.net/home", "primal.net"))
    }

    @Test
    fun `a port is kept, since it is part of where you are going`() {
        assertEquals("localhost:8000/t.html", recentSubtitle("http://localhost:8000/t.html", "localhost"))
    }

    @Test
    fun `a query is kept`() {
        assertEquals("x.com/search?q=nostr", recentSubtitle("https://x.com/search?q=nostr", "x.com"))
    }

    @Test
    fun `a url with nothing to add falls back to the host`() {
        assertEquals("primal.net", recentSubtitle("", "primal.net"))
    }
}
