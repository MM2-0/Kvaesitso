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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OmniboxInputTest {
    @Test
    fun blankIsNull() {
        assertNull(OmniboxInput.resolve(""))
        assertNull(OmniboxInput.resolve("   "))
    }

    @Test
    fun bareDomainGetsHttps() {
        assertEquals(OmniboxInput.Resolved("https://example.com", false), OmniboxInput.resolve("example.com"))
        assertEquals(OmniboxInput.Resolved("https://example.com/path?q=1", false), OmniboxInput.resolve("  example.com/path?q=1 "))
    }

    @Test
    fun explicitSchemeIsKept() {
        assertEquals(OmniboxInput.Resolved("http://example.com", false), OmniboxInput.resolve("http://example.com"))
        assertEquals(OmniboxInput.Resolved("nostr://npub1abc", false), OmniboxInput.resolve("nostr://npub1abc"))
    }

    @Test
    fun localhostAndIpAreHosts() {
        assertEquals("https://localhost", OmniboxInput.resolve("localhost")?.url)
        assertEquals("https://localhost:8080", OmniboxInput.resolve("localhost:8080")?.url)
        assertEquals("https://127.0.0.1", OmniboxInput.resolve("127.0.0.1")?.url)
    }

    @Test
    fun multiWordOrDotlessFallsBackToSearch() {
        assertEquals("https://duckduckgo.com/?q=how%20to%20tie%20a%20knot", OmniboxInput.resolve("how to tie a knot")?.url)
        assertEquals("https://duckduckgo.com/?q=cats", OmniboxInput.resolve("cats")?.url)
    }

    @Test
    fun textWithSpacesIsASearchEvenWithAScheme() {
        // Pasted prose that merely contains a link must not be loaded as a (broken) URL.
        val resolved = OmniboxInput.resolve("check this out https://example.com")!!
        assertEquals("https://duckduckgo.com/?q=check%20this%20out%20https%3A%2F%2Fexample.com", resolved.url)
        assertTrue(resolved.isSearch)
        assertTrue(!OmniboxInput.isAddress("check this out https://example.com"))
    }

    @Test
    fun isAddressMatchesResolve() {
        listOf("example.com", "http://example.com", "localhost:8080", "nostr://npub1abc").forEach {
            assertTrue(OmniboxInput.isAddress(it), it)
            assertTrue(!OmniboxInput.resolve(it)!!.isSearch, it)
        }
        listOf("cats", "how to tie a knot").forEach {
            assertTrue(!OmniboxInput.isAddress(it), it)
            assertTrue(OmniboxInput.resolve(it)!!.isSearch, it)
        }
    }

    @Test
    fun searchPrefixIsConfigurable() {
        assertEquals("https://search.example/?s=cats", OmniboxInput.resolve("cats", "https://search.example/?s=")?.url)
    }

    @Test
    fun onionForcesTor() {
        assertTrue(OmniboxInput.resolve("http://abcd.onion")!!.forceTor)
        assertTrue(OmniboxInput.resolve("abcd.onion")!!.forceTor)
        assertTrue(!OmniboxInput.resolve("example.com")!!.forceTor)
    }

    @Test
    fun hostOfStripsSchemePortPathUserInfo() {
        assertEquals("example.com", OmniboxInput.hostOf("https://example.com/a/b?c=d"))
        assertEquals("example.com", OmniboxInput.hostOf("https://user:pw@example.com:8443/x"))
        assertEquals("example.com", OmniboxInput.hostOf("example.com"))
    }
}
