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
package com.vitorpamplona.amethyst.commons.model.highlights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HighlightQuoteTest {
    @Test
    fun marksTheQuoteInsideItsContext() {
        val quote = HighlightQuote.of("the merge happens slowly", "We think the merge happens slowly. It does.")

        assertEquals("We think the merge happens slowly. It does.", quote.text)
        assertEquals(9 until 33, quote.marked)
        assertEquals("the merge happens slowly", quote.text.substring(quote.marked!!))
    }

    @Test
    fun marksEverythingWhenThereIsNoContext() {
        val quote = HighlightQuote.of("a bare quote", null)

        assertEquals("a bare quote", quote.text)
        assertNull(quote.marked)
    }

    @Test
    fun dropsContextThatDoesNotContainTheQuote() {
        val quote = HighlightQuote.of("not in here", "some entirely different sentence")

        assertEquals("not in here", quote.text)
        assertNull(quote.marked)
    }

    /** The old renderer used String.replace, which marked every occurrence at once. */
    @Test
    fun marksOnlyOneOccurrenceOfARepeatedQuote() {
        val quote = HighlightQuote.of("freedom", "freedom begets freedom")

        assertEquals(0 until 7, quote.marked)
    }

    @Test
    fun usesTheSelectorPrefixToPickTheRightOccurrence() {
        val quote = HighlightQuote.of("freedom", "freedom begets freedom", prefix = "freedom begets ")

        assertEquals(15 until 22, quote.marked)
        assertEquals("freedom", quote.text.substring(quote.marked!!))
    }

    @Test
    fun fallsBackToTheFirstOccurrenceWhenThePrefixMatchesNothing() {
        val quote = HighlightQuote.of("freedom", "freedom begets freedom", prefix = "nowhere in the text")

        assertEquals(0 until 7, quote.marked)
    }

    @Test
    fun handlesAQuoteSpanningNewlines() {
        val context = "First line here.\nSecond line here."
        val quote = HighlightQuote.of("here.\nSecond", context)

        assertEquals(context, quote.text)
        assertEquals("here.\nSecond", quote.text.substring(quote.marked!!))
    }

    @Test
    fun handlesAnEmptyHighlight() {
        val quote = HighlightQuote.of("", "some context")

        assertEquals("some context", quote.text)
        assertNull(quote.marked)
    }

    @Test
    fun keepsShortContextWholeWithoutEllipsis() {
        val quote = HighlightQuote.of("the merge happens slowly", "We think the merge happens slowly. It does.")

        assertEquals("We think the merge happens slowly. It does.", quote.text)
        assertTrue('…' !in quote.text)
    }

    @Test
    fun trimsLongContextToAWindowAroundTheQuote() {
        val filler = "word ".repeat(200).trim() // ~1000 chars of context on each side
        val context = "$filler the marked quote $filler"
        val quote = HighlightQuote.of("the marked quote", context)

        // The whole quote survives and stays marked...
        assertEquals("the marked quote", quote.text.substring(quote.marked!!))
        // ...but the surrounding text is trimmed with an ellipsis on each side...
        assertTrue(quote.text.startsWith("… "))
        assertTrue(quote.text.endsWith(" …"))
        // ...and the result is a small fraction of the original two-paragraph context.
        assertTrue(quote.text.length < context.length / 2, "expected windowing, got ${quote.text.length} of ${context.length}")
    }

    @Test
    fun dropsBlankLinesAtTheEdgesOfAShortContext() {
        // A context whose paragraph boundaries left blank lines at its very start and end would
        // otherwise render as empty space above and below the quote.
        val quote = HighlightQuote.of("Forward Secrecy", "\n\nForward Secrecy is nice.\n\n")

        assertEquals("Forward Secrecy is nice.", quote.text)
        assertEquals(0 until 15, quote.marked)
        assertEquals("Forward Secrecy", quote.text.substring(quote.marked!!))
    }

    @Test
    fun trimmingSnapsToWholeWordsSoNoWordIsCutInHalf() {
        val lead = "alpha bravo charlie delta echo foxtrot ".repeat(20) // long, space-separated
        val context = "${lead}QUOTE"
        val quote = HighlightQuote.of("QUOTE", context)

        // The kept lead-in starts right after the ellipsis with a whole word, never a fragment.
        val keptLead = quote.text.removePrefix("… ").removeSuffix("QUOTE")
        assertTrue(keptLead.split(" ").first() in setOf("alpha", "bravo", "charlie", "delta", "echo", "foxtrot"))
    }
}
