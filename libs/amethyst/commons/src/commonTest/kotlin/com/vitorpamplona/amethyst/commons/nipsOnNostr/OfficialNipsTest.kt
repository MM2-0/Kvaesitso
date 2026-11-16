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
import kotlin.test.assertTrue

class OfficialNipsTest {
    // Lines copied from the nips repo's README.
    private val readme =
        """
        # NIPs

        - [List](#list)

        ## List

        - [NIP-01: Basic protocol flow description](01.md)
        - ~~[NIP-04: Encrypted Direct Message](04.md) --- **unrecommended**: deprecated in favor of [NIP-17](17.md)~~
        - [NIP-07: `window.nostr` capability for web browsers](07.md)
        - [NIP-7D: Threads](7D.md)

        ## Event Kinds

        - [NIP-99: Not in the list section](99.md)
        """.trimIndent()

    @Test
    fun parsesTheListSectionOnly() {
        val entries = OfficialNips.parseIndex(readme)
        assertEquals(listOf("01", "04", "07", "7D"), entries.map { it.number })
        assertEquals("Basic protocol flow description", entries[0].title)
        assertNull(entries[0].unrecommended)
        assertEquals("deprecated in favor of NIP-17", entries[1].unrecommended)
        assertEquals("window.nostr capability for web browsers", entries[2].title)
    }

    @Test
    fun officialNipFromRepoMarkdown() {
        val md = "NIP-01\n======\n\nBasic protocol flow description\n-------------------------------\n\n`draft` `mandatory` `relay`\n\nThis NIP defines the basic protocol. See NIP-10 and [NIP-19](19.md).\n\n## Events and signatures\n\nText."
        val view = NipTextView.ofMarkdown("01", md)
        assertEquals("01", view.number)
        assertEquals("Basic protocol flow description", view.title)
        assertEquals(listOf("draft", "mandatory", "relay"), view.flags)
        assertTrue(view.body.startsWith("This NIP defines"), view.body)
        assertTrue(view.body.contains("See [NIP-10](${NipLinks.REPO_BLOB}10.md) and [NIP-19](${NipLinks.REPO_BLOB}19.md)."), view.body)
        assertEquals(listOf("events-and-signatures"), view.sections.map { it.anchor })
    }
}
