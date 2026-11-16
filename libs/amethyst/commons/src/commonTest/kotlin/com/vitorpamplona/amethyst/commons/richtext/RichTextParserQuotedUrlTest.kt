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

/**
 * A quoted host name (`this bridge-relay "relay.momostr.pink" doesn't appear`) used to be
 * detected with the opening quote glued onto it, so the rendered link read
 * `"relay.momostr.pink` and pointed at a host that doesn't exist. Quotes are not host
 * characters, so they must be left in the surrounding text on both sides.
 */
class RichTextParserQuotedUrlTest {
    private fun segmentsOf(text: String) =
        RichTextParser()
            .parseText(text, EmptyTagList, null)
            .paragraphs
            .flatMap { it.words }

    @Test
    fun quotedSchemelessUrlKeepsQuotesOutOfTheLink() {
        val segments =
            segmentsOf(
                "It seems like this bridge-relay \"relay.momostr.pink\" doesn't appear in the feed",
            ).filterIsInstance<SchemelessUrlSegment>()

        assertEquals(listOf("relay.momostr.pink"), segments.map { it.segmentText })
    }

    @Test
    fun quotedUrlWithSchemeKeepsQuotesOutOfTheLink() {
        val segments =
            segmentsOf(
                "the docs are at \"https://example.com/some/page?a=b\" if you need them",
            ).filterIsInstance<LinkSegment>()

        assertEquals(listOf("https://example.com/some/page?a=b"), segments.map { it.segmentText })
    }

    @Test
    fun quotedRelayUrlKeepsQuotesOutOfTheLink() {
        val segments =
            segmentsOf("add \"wss://relay.momostr.pink\" to your list")
                .filterIsInstance<RelayUrlSegment>()

        assertEquals(listOf("wss://relay.momostr.pink"), segments.map { it.segmentText })
    }

    @Test
    fun apostrophesInProseDontCreateLinks() {
        val segments = segmentsOf("it doesn't appear until you go into the authors' accounts")

        assertEquals(emptyList(), segments.filterIsInstance<SchemelessUrlSegment>())
        assertEquals(emptyList(), segments.filterIsInstance<LinkSegment>())
    }
}
