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
package com.vitorpamplona.amethyst.commons.ui.markdown

import com.halilibo.richtext.commonmark.CommonMarkdownParseOptions
import com.halilibo.richtext.commonmark.CommonmarkAstNodeParser
import com.halilibo.richtext.markdown.node.AstCode
import com.halilibo.richtext.markdown.node.AstEmphasis
import com.halilibo.richtext.markdown.node.AstFencedCodeBlock
import com.halilibo.richtext.markdown.node.AstImage
import com.halilibo.richtext.markdown.node.AstLink
import com.halilibo.richtext.markdown.node.AstNode
import com.halilibo.richtext.markdown.node.AstStrikethrough
import com.halilibo.richtext.markdown.node.AstStrongEmphasis
import com.halilibo.richtext.markdown.node.AstText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime

/**
 * A bare URL in a markdown note renders as one whole link. commonmark linked bare URLs only after
 * it had parsed emphasis, so the `_`, `*` and `~` in a URL's path were read as markers first:
 * Amazon's `…/71zvSJXs2KL._SL1499_.jpg` became a link to `…/71zvSJXs2KL` and an italic `SL1499`.
 */
class BareUrlExtensionTest {
    private val amazon = "https://m.media-amazon.com/images/I/71zvSJXs2KL._SL1499_.jpg"

    private fun parse(content: String) = CommonmarkAstNodeParser(AmethystMarkdownParseOptions).parse(content)

    private fun AstNode.all(): List<AstNode> {
        val nodes = mutableListOf(this)
        var child = links.firstChild
        while (child != null) {
            nodes += child.all()
            child = child.links.next
        }
        return nodes
    }

    private fun links(content: String) = parse(content).all().mapNotNull { (it.type as? AstLink)?.destination }

    private fun texts(content: String) = parse(content).all().mapNotNull { (it.type as? AstText)?.literal }

    private fun has(
        content: String,
        type: Class<*>,
    ) = parse(content).all().any { type.isInstance(it.type) }

    @Test
    fun theUnextendedParserSplitsTheUrl() {
        // The bug this guards against, so the tests below can't pass by accident.
        val plain = CommonmarkAstNodeParser(CommonMarkdownParseOptions.MarkdownWithLinks).parse("**b** $amazon")
        assertEquals(listOf("https://m.media-amazon.com/images/I/71zvSJXs2KL"), plain.all().mapNotNull { (it.type as? AstLink)?.destination })
    }

    @Test
    fun anUnderscoredImageUrlStaysOneLink() {
        val content = "**Look** at this\n\n$amazon"
        assertEquals(listOf(amazon), links(content))
        assertFalse(has(content, AstEmphasis::class.java))
        // The link's text is its URL, which is what turns it into an inline image or a preview.
        assertTrue(amazon in texts(content))
    }

    @Test
    fun starsAndTildesInAUrlStayInTheLink() {
        assertEquals(listOf("https://example.com/a*b*c"), links("**bold** https://example.com/a*b*c end"))
        assertEquals(listOf("https://x.com/~~y~~z"), links("**bold** https://x.com/~~y~~z end"))
        assertEquals(listOf("https://x.com/__init__.py"), links("# Title\nhttps://x.com/__init__.py"))
    }

    @Test
    fun trailingPunctuationIsLeftOut() {
        assertEquals(listOf("https://example.com/a_b_.jpg"), links("**bold** see (https://example.com/a_b_.jpg)."))
    }

    @Test
    fun aTrailingMarkerNothingClosesStaysInTheLink() {
        assertEquals(listOf("https://x.com/a_b_"), links("**b** https://x.com/a_b_"))
        assertEquals(listOf("https://x.com/a_b*"), links("**b** https://x.com/a_b* end"))
    }

    @Test
    fun emphasisAroundAUrlStillWorks() {
        assertEquals(listOf("https://x.com/a_b"), links("_see https://x.com/a_b_"))
        assertTrue(has("_see https://x.com/a_b_", AstEmphasis::class.java))

        assertEquals(listOf("https://x.com/a_b"), links("**https://x.com/a_b**"))
        assertTrue(has("**https://x.com/a_b**", AstStrongEmphasis::class.java))

        assertEquals(listOf("https://x.com/a"), links("~~https://x.com/a~~"))
        assertTrue(has("~~https://x.com/a~~", AstStrikethrough::class.java))
    }

    @Test
    fun escapedMarkersStillLinkTheUnescapedUrl() {
        // Writing `\_` was the workaround for the bug; it must keep working.
        assertEquals(listOf("https://x.com/a_b_c"), links("**b** https://x.com/a\\_b\\_c"))
    }

    @Test
    fun linksAndAutolinksAreLeftAlone() {
        assertEquals(listOf("https://x.com/a_b_"), links("**b** [https://x.com/a_b_](https://x.com/a_b_)"))
        assertEquals(listOf("https://x.com/a_b_"), links("**b** <https://x.com/a_b_>"))
        assertEquals(listOf("https://x.com/a_b_"), links("**b** [docs][1]\n\n[1]: https://x.com/a_b_"))
        assertEquals(listOf("https://x.com/i_m_.png"), parse("![https://x.com/a_b_](https://x.com/i_m_.png)").all().mapNotNull { (it.type as? AstImage)?.destination })
    }

    @Test
    fun codeIsLeftAlone() {
        for (content in listOf("**b** `https://x.com/a_b_`", "**b**\n```\nhttps://x.com/a_b_\n```", "**b**\n\n    https://x.com/a_b_", "> ```\n> https://x.com/a_b_\n> ```", "**b** `see\nhttps://x.com/a_b_` end")) {
            assertEquals(emptyList(), links(content), content)
        }
        assertTrue(has("**b** `see\nhttps://x.com/a_b_` end", AstCode::class.java))
        assertTrue(has("> ```\n> https://x.com/a_b_\n> ```", AstFencedCodeBlock::class.java))
    }

    @Test
    fun onlyHttpUrlsAreTaken() {
        // Other schemes, and words that merely start with h, go the old way.
        assertEquals(listOf("https://x.com/plain"), links("**b** https://x.com/plain"))
        assertFalse(links("**b** hello_world_ and x://a_b_c").any { it.startsWith("x:") && it != "x://a_b_c" })
    }

    @Test
    fun aLongRunOfTextIsLinear() {
        val nasty = "**b** " + "h".repeat(200_000) + " " + "-h".repeat(100_000) + " " + "[".repeat(50_000) + " https://x.com/a_b_c"
        val took = measureTime { parse(nasty) }
        assertTrue(took.inWholeSeconds < 5, "took $took")
    }
}
