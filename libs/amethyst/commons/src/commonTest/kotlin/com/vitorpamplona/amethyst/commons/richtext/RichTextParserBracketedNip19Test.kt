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

class RichTextParserBracketedNip19Test {
    private val npub = "npub1hgvtv4zn2l8l3ef34n87r4sf5s00xq3lhgr3mvwt7kn8gjxpjprqc89jnv"

    private fun words(content: String) = RichTextParser().parseText(content, EmptyTagList, null).paragraphs.flatMap { it.words }

    private fun assertSingleBech(
        content: String,
        expectedBech: String,
    ) {
        val bechs = words(content).filterIsInstance<BechSegment>()
        assertEquals(1, bechs.size, "no BechSegment found in `$content`: ${words(content).map { it::class.simpleName to it.segmentText }}")
        assertEquals(expectedBech, bechs.first().segmentText)
    }

    @Test
    fun parsesNpubWrappedInParenthesis() {
        // kind 1111 event add474916fe55bf90214ab00b7aa10df053dbdcb67fa8cc17e022e88692517b7
        assertSingleBech("(@$npub)", "@$npub)")
    }

    @Test
    fun parsesNpubWrappedInPunctuation() {
        assertSingleBech("($npub)", "$npub)")
        assertSingleBech("\"$npub\"", "$npub\"")
        assertSingleBech("[$npub]", "$npub]")
        assertSingleBech("{@$npub}", "@$npub}")
        assertSingleBech("Hello (@$npub), how are you?", "@$npub),")
    }

    @Test
    fun keepsTheOpeningPunctuationAsText() {
        val parsed = words("(@$npub)")
        assertEquals(2, parsed.size)
        assertTrue(parsed[0] is RegularTextSegment)
        assertEquals("(", parsed[0].segmentText)
    }

    @Test
    fun doesNotSplitWordsWithoutAnEntity() {
        val parsed = words("(just a comment)")
        assertTrue(parsed.all { it is RegularTextSegment })
        assertEquals("(just a comment)", parsed.joinToString(" ") { it.segmentText })
    }

    @Test
    fun stillParsesTheUnwrappedForms() {
        assertSingleBech(npub, npub)
        assertSingleBech("@$npub", "@$npub")
        assertSingleBech("nostr:$npub", "nostr:$npub")
    }
}
