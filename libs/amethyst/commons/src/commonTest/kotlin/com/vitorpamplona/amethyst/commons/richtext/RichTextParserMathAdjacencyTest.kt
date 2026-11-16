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

/**
 * Guards that math extraction stays inside its `$`-delimiters and doesn't disturb
 * neighbouring links / images / hashtags when separated by a space (the normal
 * case), and documents the glued (no-space) edge behaviour.
 */
class RichTextParserMathAdjacencyTest {
    // Kotlin uses `$` for string templates; a constant keeps the test strings readable.
    private val d = "$"

    private fun parse(content: String) = RichTextParser().parseText(content, EmptyTagList, null).paragraphs.flatMap { it.words }

    @Test
    fun mathSegmentIsJustTheSpanNotTheParagraph() {
        val content = "before ${d}x_1 + y$d after more words here"
        val math = parse(content).filterIsInstance<MathSegment>().single()
        assertEquals("${d}x_1 + y$d", math.segmentText)
        assertEquals("x_1 + y", math.latex)
    }

    @Test
    fun spaceSeparatedHashtagUrlAndMathAreAllDetected() {
        val content = "#physics the law ${d}E=mc^2$d see https://nostr.com today"
        val words = parse(content)
        assertEquals(1, words.filterIsInstance<HashTagSegment>().size)
        assertEquals(1, words.filterIsInstance<MathSegment>().size)
        assertEquals(1, words.filterIsInstance<LinkSegment>().size)
        assertEquals("physics", words.filterIsInstance<HashTagSegment>().single().hashtag)
        assertEquals("E=mc^2", words.filterIsInstance<MathSegment>().single().latex)
        assertEquals("https://nostr.com", words.filterIsInstance<LinkSegment>().single().segmentText)
    }

    @Test
    fun imageNextToMathStaysAnImage() {
        val content = "${d}a^2$d https://i.imgur.com/abc.jpg done"
        val words = parse(content)
        assertEquals(1, words.filterIsInstance<MathSegment>().size)
        assertEquals(1, words.filterIsInstance<ImageSegment>().size)
        assertEquals("https://i.imgur.com/abc.jpg", words.filterIsInstance<ImageSegment>().single().segmentText)
    }

    @Test
    fun mathEndingSentenceThenHashtag() {
        // `$x$.` keeps the period as trailing; the hashtag after the space is its own segment.
        val content = "result ${d}x=0$d. #done"
        val words = parse(content)
        val math = words.filterIsInstance<MathSegment>().single()
        assertEquals("x=0", math.latex)
        assertEquals(".", math.trailing)
        assertEquals("done", words.filterIsInstance<HashTagSegment>().single().hashtag)
    }

    // ---- glued (no-space) adjacency: documents the known limitation ----

    @Test
    fun hashtagGluedAfterMathBecomesTrailingText() {
        // `$x$#tag` with no space: the hashtag is absorbed as trailing punctuation
        // and is NOT a clickable hashtag. Acceptable edge case.
        val content = "eq ${d}x$d#tag"
        val words = parse(content)
        val math = words.filterIsInstance<MathSegment>().single()
        assertEquals("x", math.latex)
        assertEquals("#tag", math.trailing)
        assertTrue(words.none { it is HashTagSegment })
    }

    @Test
    fun mathGluedAfterHashtagDoesNotRenderAsMath() {
        // `#tag$x$` with no space: the `#` token wins; the math stays literal text.
        val content = "see #tag${d}x$d here"
        val words = parse(content)
        assertTrue(words.none { it is MathSegment })
        assertEquals("tag", words.filterIsInstance<HashTagSegment>().single().hashtag)
    }

    @Test
    fun currencyBeforeRealEquationDoesNotSwallow() {
        // `$5` must not pair with the later equation's `$`.
        val content = "costs ${d}5 but the formula ${d}x=1$d holds"
        assertEquals("x=1", parse(content).filterIsInstance<MathSegment>().single().latex)
    }
}
