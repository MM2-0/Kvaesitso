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
package com.vitorpamplona.amethyst.commons.cashu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MintDirectoryIndexTest {
    @Test
    fun `normalize lowercases, trims, strips trailing slash`() {
        assertEquals("https://mint.example.com", MintDirectoryIndex.normalize("  https://Mint.Example.com/  "))
        assertEquals("https://mint.example.com", MintDirectoryIndex.normalize("https://mint.example.com"))
        assertEquals("http://localhost:3338", MintDirectoryIndex.normalize("http://localhost:3338/"))
    }

    @Test
    fun `normalize rejects non-http urls`() {
        assertNull(MintDirectoryIndex.normalize("mint.example.com"))
        assertNull(MintDirectoryIndex.normalize("ftp://mint.example.com"))
        assertNull(MintDirectoryIndex.normalize(""))
        assertNull(MintDirectoryIndex.normalize("   "))
    }

    @Test
    fun `add dedupes case-insensitively`() {
        val idx = MintDirectoryIndex()
        idx.add("https://mint.example.com")
        idx.add("https://Mint.Example.com/")
        idx.add("HTTPS://MINT.EXAMPLE.COM")
        assertEquals(1, idx.size())
    }

    @Test
    fun `suggest ranks more-popular mints first`() {
        val idx = MintDirectoryIndex()
        // mint.b seen 3 times, mint.a once, mint.c twice
        repeat(3) { idx.add("https://mint.b.example.com") }
        idx.add("https://mint.a.example.com")
        repeat(2) { idx.add("https://mint.c.example.com") }

        val all = idx.suggest("")
        assertEquals(
            listOf(
                "https://mint.b.example.com",
                "https://mint.c.example.com",
                "https://mint.a.example.com",
            ),
            all,
        )
    }

    @Test
    fun `suggest filters by substring case-insensitively`() {
        val idx = MintDirectoryIndex()
        idx.add("https://mint.example.com")
        idx.add("https://nutmix.io")
        idx.add("https://my-mint.org")

        val hits = idx.suggest("MINT")
        assertEquals(setOf("https://mint.example.com", "https://my-mint.org"), hits.toSet())
    }

    @Test
    fun `suggest honors limit`() {
        val idx = MintDirectoryIndex()
        repeat(10) { i -> idx.add("https://mint-$i.example.com") }
        assertEquals(3, idx.suggest("", limit = 3).size)
    }

    @Test
    fun `empty query returns all mints sorted`() {
        val idx = MintDirectoryIndex()
        idx.add("https://b.example.com")
        idx.add("https://a.example.com")
        // Same count, so alphabetical fallback applies.
        assertEquals(
            listOf("https://a.example.com", "https://b.example.com"),
            idx.suggest(""),
        )
    }

    @Test
    fun `add drops malformed urls silently`() {
        val idx = MintDirectoryIndex()
        idx.add("not-a-url")
        idx.add("")
        idx.add("ftp://example.com")
        idx.add("https://valid.example.com")
        assertEquals(1, idx.size())
        assertTrue(idx.suggest("").contains("https://valid.example.com"))
    }
}
