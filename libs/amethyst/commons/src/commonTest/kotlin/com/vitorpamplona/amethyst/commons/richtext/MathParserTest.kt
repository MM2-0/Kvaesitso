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

import com.vitorpamplona.amethyst.commons.richtext.MathParser.Token
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MathParserTest {
    // Kotlin uses `$` for string templates; using constants keeps the test
    // strings readable instead of peppering them with `\$`.
    private val d = "$"
    private val bs = "\\"

    private fun math(tokens: List<Token>) = tokens.filterIsInstance<Token.Math>()

    private fun joinWords(tokens: List<Token>) = tokens.filterIsInstance<Token.Word>().joinToString(" ") { it.text }

    @Test
    fun simpleInlineMath() {
        assertEquals(
            listOf(
                Token.Word("before"),
                Token.Math("${d}x_1$d", "x_1", false),
                Token.Word("after"),
            ),
            MathParser.split("before ${d}x_1$d after"),
        )
    }

    @Test
    fun displayMath() {
        val m = math(MathParser.split("eq $d${d}E = mc^2$d$d end")).single()
        assertEquals("E = mc^2", m.latex)
        assertTrue(m.displayMode)
        assertEquals("$d${d}E = mc^2$d$d", m.raw)
    }

    @Test
    fun mathWithInternalSpacesStaysWhole() {
        // The span keeps its internal spaces instead of being split into words.
        val tokens = MathParser.split("Vectors ${d}A_1, ${bs}ldots, A_n$d are")
        assertEquals(
            listOf("Vectors", "are"),
            tokens.filterIsInstance<Token.Word>().map { it.text },
        )
        val m = math(tokens).single()
        assertEquals("A_1, ${bs}ldots, A_n", m.latex)
        assertFalse(m.displayMode)
    }

    @Test
    fun theLinearIndependencePost() {
        val content =
            "Vectors ${d}A_1, ${bs}ldots, A_n$d are independent if the only choice of scalars for which " +
                "${d}x_1 A_1 + ${bs}cdots + x_n A_n = 0$d is the trivial one ${d}x_1 = ${bs}cdots = x_n = 0$d."
        val m = math(MathParser.split(content))
        assertEquals(3, m.size)
        assertEquals("A_1, ${bs}ldots, A_n", m[0].latex)
        assertEquals("x_1 A_1 + ${bs}cdots + x_n A_n = 0", m[1].latex)
        assertEquals("x_1 = ${bs}cdots = x_n = 0", m[2].latex)
        // The closing span ends the sentence, so the period rides along as trailing.
        assertEquals("", m[0].trailing)
        assertEquals(".", m[2].trailing)
    }

    @Test
    fun trailingPunctuationRidesWithMath() {
        val m = math(MathParser.split("the value ${d}x$d, computed")).single()
        assertEquals("x", m.latex)
        assertEquals(",", m.trailing)
    }

    @Test
    fun currencyIsNotMath() {
        // Opening `$` of `$5` is followed by a digit (fine), but the closing `$`
        // of `$10` is preceded by a space, so no valid span is formed.
        val line = "It costs ${d}5 and ${d}10 total"
        val tokens = MathParser.split(line)
        assertTrue(math(tokens).isEmpty())
        assertEquals(line, joinWords(tokens))
    }

    @Test
    fun openingDollarFollowedBySpaceIsNotMath() {
        assertTrue(math(MathParser.split("a $d x + y $d b")).isEmpty())
    }

    @Test
    fun escapedDollarIsLiteral() {
        assertTrue(math(MathParser.split("price is $bs${d}5 to $bs${d}9")).isEmpty())
    }

    @Test
    fun escapedDollarInsideMathDoesNotClose() {
        val m = math(MathParser.split("cost ${d}a + $bs$d + b$d ok")).single()
        assertEquals("a + $bs$d + b", m.latex)
    }

    @Test
    fun emptyMathIsIgnored() {
        assertTrue(math(MathParser.split("empty $d$d here")).isEmpty())
    }

    @Test
    fun collapsesOverEscapedBackslashes() {
        // `\\ldots` (over-escaped) -> `\ldots`; otherwise JLaTeXMath sees a line
        // break plus the literal letters "ldots".
        assertEquals("A_1, ${bs}ldots, A_n", MathParser.collapseDoubledBackslashes("A_1, $bs$bs" + "ldots, A_n"))
        assertEquals("x_1 = ${bs}cdots = x_n", MathParser.collapseDoubledBackslashes("x_1 = $bs$bs" + "cdots = x_n"))
    }

    @Test
    fun singleBackslashLatexIsLeftAlone() {
        assertEquals("${bs}frac{1}{2} + ${bs}sqrt{x}", MathParser.collapseDoubledBackslashes("${bs}frac{1}{2} + ${bs}sqrt{x}"))
    }

    @Test
    fun gluedMathStaysPlainWord() {
        // Math without a separating space is not whitespace-delimited, so it
        // remains a single literal word rather than three rendered pieces.
        val tokens = MathParser.split("a${d}x$d" + "b")
        assertTrue(math(tokens).isEmpty())
        assertEquals(listOf(Token.Word("a${d}x$d" + "b")), tokens)
    }

    @Test
    fun noDollarsBehavesLikeSpaceSplit() {
        assertFalse(MathParser.mightContainMath("just regular text"))
        assertEquals(
            "just regular text".split(' ').map { Token.Word(it) },
            MathParser.split("just regular text"),
        )
    }

    @Test
    fun doubleSpacesArePreservedAsEmptyWords() {
        // RichTextParser relies on split(' ') semantics to keep double-spaces:
        // each run of N spaces yields N-1 empty words, on both sides of math.
        assertEquals(
            listOf(
                Token.Word("a"),
                Token.Word(""),
                Token.Word("b"),
                Token.Math("${d}x$d", "x", false),
                Token.Word(""),
                Token.Word("c"),
            ),
            MathParser.split("a  b ${d}x$d  c"),
        )
    }

    @Test
    fun adjacentInlineSpans() {
        val m = math(MathParser.split("${d}a$d ${d}b$d"))
        assertEquals(2, m.size)
        assertEquals("a", m[0].latex)
        assertEquals("b", m[1].latex)
    }

    @Test
    fun parenWrappedMathIsDetected() {
        // `($r = 1$)` is the common prose case: the opening paren rides along as
        // leading, the closing paren as trailing, and the inner span is math.
        val m = math(MathParser.split("translations (${d}r = 1$d) is cyclic")).single()
        assertEquals("r = 1", m.latex)
        assertEquals("(", m.leading)
        assertEquals(")", m.trailing)
    }

    @Test
    fun bracketAndQuoteWrappedMathAreDetected() {
        val tokens = MathParser.split("see [${d}x$d] and \"${d}y$d\" here")
        val m = math(tokens)
        assertEquals(2, m.size)
        assertEquals("x", m[0].latex)
        assertEquals("[", m[0].leading)
        assertEquals("]", m[0].trailing)
        assertEquals("y", m[1].latex)
        assertEquals("\"", m[1].leading)
        assertEquals("\"", m[1].trailing)
    }

    @Test
    fun alphanumericGluedPrefixStaysPlainWord() {
        // A letter before `$` is not opening punctuation, so the existing
        // glued-word behaviour is preserved (no false-firing on `a$x$`).
        val tokens = MathParser.split("a${d}x$d" + " end")
        assertTrue(math(tokens).isEmpty())
        assertEquals(listOf("a${d}x$d", "end"), tokens.filterIsInstance<Token.Word>().map { it.text })
    }
}
