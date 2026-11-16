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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownChunksTest {
    @Test
    fun plainMarkdownIsNotSplit() {
        assertNull(MarkdownChunks.split("# Title\n\nSome **text**.\n\n```kotlin\nval x = 1\n```", atHeadings = false))
    }

    @Test
    fun jsonBlocksSplitOut() {
        val md = "Before.\n\n```json\n{\"kind\": 1}\n```\n\nAfter."
        val chunks = MarkdownChunks.split(md, atHeadings = false)!!
        assertEquals(3, chunks.size)
        assertEquals("Before.", (chunks[0] as MarkdownChunk.Prose).markdown)
        val code = assertIs<MarkdownChunk.Code>(chunks[1])
        assertEquals("json", code.language)
        assertEquals("{\"kind\": 1}", code.code)
        assertEquals("After.", (chunks[2] as MarkdownChunk.Prose).markdown)
    }

    @Test
    fun unlabelledJsonAndYamlCount() {
        assertTrue(MarkdownChunks.isHighlightable(null, "  {\"a\": 1}"))
        assertTrue(MarkdownChunks.isHighlightable("yaml", "a: b"))
        assertTrue(!MarkdownChunks.isHighlightable("kotlin", "{"))
        assertTrue(!MarkdownChunks.isHighlightable(null, "plain"))
    }

    @Test
    fun indentedFenceInAListStaysInProse() {
        val md = "- item\n\n  ```json\n  {\"a\": 1}\n  ```\n"
        assertNull(MarkdownChunks.split(md, atHeadings = false))
    }

    @Test
    fun splitsAtHeadingsWithGithubAnchors() {
        val md = "Intro.\n\n## Events and signatures\n\nA.\n\n### `window.nostr` capability\n\nB.\n\n## Events and signatures\n\nC."
        val chunks = MarkdownChunks.split(md, atHeadings = true)!!.map { it as MarkdownChunk.Prose }
        assertEquals(listOf(null, "events-and-signatures", "windownostr-capability", "events-and-signatures-1"), chunks.map { it.anchor })
        assertTrue(chunks[1].markdown.startsWith("## Events and signatures"))
        assertEquals(listOf(2, 3, 2), MarkdownChunks.headings(md).map { it.level })
    }

    @Test
    fun setextHeadingsAndHeadingsInCode() {
        val md = "Encryption\n----------\n\nText.\n\n```\n# not a heading\n```"
        val headings = MarkdownChunks.headings(md)
        assertEquals(listOf("Encryption"), headings.map { it.text })
        assertEquals("encryption", headings[0].anchor)
    }

    @Test
    fun referenceDefinitionsFollowEveryPiece() {
        val md = "Uses [Blossom][].\n\n## Next\n\nAlso [Blossom][].\n\n[Blossom]: https://github.com/hzrd149/blossom"
        val chunks = MarkdownChunks.split(md, atHeadings = true)!!.map { it as MarkdownChunk.Prose }
        assertEquals(2, chunks.size)
        chunks.forEach { assertTrue(it.markdown.endsWith("[Blossom]: https://github.com/hzrd149/blossom"), it.markdown) }
    }

    @Test
    fun footnotesKeepTheDocumentWhole() {
        assertNull(MarkdownChunks.split("Text[^1].\n\n## H\n\n[^1]: Note.", atHeadings = true))
    }

    @Test
    fun anchorsMatchGithub() {
        assertEquals("encryption", MarkdownChunks.anchorOf("Encryption"))
        assertEquals("nip-44-v2-encryption", MarkdownChunks.anchorOf("NIP-44 v2: Encryption"))
        assertEquals("kinds", MarkdownChunks.anchorOf("Kinds"))
    }

    @Test
    fun cachedSplitMatchesSplitAndKeysOnAnchors() {
        val md = "Intro.\n\n## Part\n\nText."
        assertNull(MarkdownChunks.cachedSplit(md, atHeadings = false))
        assertEquals(MarkdownChunks.split(md, atHeadings = true), MarkdownChunks.cachedSplit(md, atHeadings = true))
        assertEquals(2, MarkdownChunks.cachedSplit(md, atHeadings = true)?.size)
    }
}
