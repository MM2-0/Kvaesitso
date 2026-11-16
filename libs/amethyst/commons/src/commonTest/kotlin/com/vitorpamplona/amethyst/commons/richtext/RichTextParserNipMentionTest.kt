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

class RichTextParserNipMentionTest {
    private fun parse(content: String) = RichTextParser().parseText(content, EmptyTagList, null).paragraphs.flatMap { it.words }

    @Test
    fun mentionsBecomeNipSegments() {
        val nips = parse("Read NIP-44, then nip-7d. Not NIP-4 or NIPs.").filterIsInstance<NipMentionSegment>()
        assertEquals(listOf("44", "7D"), nips.map { it.number })
        assertEquals(listOf(",", "."), nips.map { it.extras })
    }

    @Test
    fun urlsWithNipsStayLinks() {
        val words = parse("https://github.com/nostr-protocol/nips/blob/master/44.md")
        assertTrue(words.none { it is NipMentionSegment })
    }
}
