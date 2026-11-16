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
package com.vitorpamplona.amethyst.commons.nipsOnNostr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NipLinksTest {
    @Test
    fun normalizesNumbers() {
        assertEquals("01", NipLinks.normalize("1"))
        assertEquals("7D", NipLinks.normalize("nip-7d"))
        assertEquals("46", NipLinks.normalize("NIP 46"))
        assertEquals("EE", NipLinks.normalize("ee"))
        assertNull(NipLinks.normalize("123"))
        assertNull(NipLinks.normalize("hello"))
    }

    @Test
    fun readsNipUrls() {
        assertEquals("44", NipLinks.numberFromUrl("https://github.com/nostr-protocol/nips/blob/master/44.md"))
        assertEquals("01", NipLinks.numberFromUrl("https://github.com/nostr-protocol/nips/blob/master/01.md#kinds"))
        assertEquals("7D", NipLinks.numberFromUrl("https://raw.githubusercontent.com/nostr-protocol/nips/master/7D.md"))
        assertEquals("19", NipLinks.numberFromUrl("https://nips.nostr.com/19"))
        assertNull(NipLinks.numberFromUrl("https://github.com/nostr-protocol/nips/blob/master/README.md"))
        assertNull(NipLinks.numberFromUrl("https://github.com/vitorpamplona/amethyst/blob/main/44.md"))
        // A proposal on another branch keeps opening on GitHub: the reader shows master.
        assertNull(NipLinks.numberFromUrl("https://github.com/nostr-protocol/nips/blob/maven-repos/A1.md"))
        assertNull(NipLinks.numberFromUrl("https://raw.githubusercontent.com/nostr-protocol/nips/feature/44.md"))
        assertEquals("44", NipLinks.numberFromUrl("https://raw.githubusercontent.com/nostr-protocol/nips/refs/heads/master/44.md"))
        assertEquals("kinds", NipLinks.anchorFromUrl("https://github.com/nostr-protocol/nips/blob/master/01.md#kinds"))
        assertNull(NipLinks.anchorFromUrl("https://nips.nostr.com/19"))
    }

    @Test
    fun readsMentionWords() {
        assertEquals("44" to "", NipLinks.mention("NIP-44"))
        assertEquals("17" to ",", NipLinks.mention("NIP-17,"))
        assertEquals("7D" to ").", NipLinks.mention("nip-7d)."))
        assertEquals("01" to "'s", NipLinks.mention("NIP-01's"))
        assertNull(NipLinks.mention("NIP-4"))
        assertNull(NipLinks.mention("NIP-444"))
        assertNull(NipLinks.mention("NIPS-44"))
        assertNull(NipLinks.mention("nope"))
    }
}
