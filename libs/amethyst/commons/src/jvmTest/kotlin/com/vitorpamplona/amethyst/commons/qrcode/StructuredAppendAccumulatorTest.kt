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
package com.vitorpamplona.amethyst.commons.qrcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredAppendAccumulatorTest {
    private fun part(
        text: String,
        index: Int,
        size: Int,
        id: String = "seq",
    ) = ScanResult(text = text, bounds = null, sequenceId = id, sequenceIndex = index, sequenceSize = size)

    @Test
    fun `parts in order join once the last one lands`() {
        val acc = StructuredAppendAccumulator()

        assertNull(acc.add(part("abc", 0, 3), 0))
        assertNull(acc.add(part("def", 1, 3), 100))
        assertEquals("abcdefghi", acc.add(part("ghi", 2, 3), 200))
    }

    @Test
    fun `parts out of order still join in index order`() {
        val acc = StructuredAppendAccumulator()

        assertNull(acc.add(part("ghi", 2, 3), 0))
        assertNull(acc.add(part("abc", 0, 3), 10))
        assertEquals("abcdefghi", acc.add(part("def", 1, 3), 20))
    }

    @Test
    fun `a repeated part does not complete the sequence`() {
        val acc = StructuredAppendAccumulator()

        assertNull(acc.add(part("abc", 0, 3), 0))
        assertNull(acc.add(part("abc", 0, 3), 10))
        assertNull(acc.add(part("abc", 0, 3), 20))
        assertEquals(1, acc.captured)
    }

    @Test
    fun `progress reports captured against total`() {
        val acc = StructuredAppendAccumulator()

        acc.add(part("a", 0, 3), 0)
        assertEquals(1, acc.captured)
        assertEquals(3, acc.total)

        acc.add(part("b", 1, 3), 10)
        assertEquals(2, acc.captured)
    }

    @Test
    fun `a different sequence id restarts rather than splicing`() {
        val acc = StructuredAppendAccumulator()

        acc.add(part("abc", 0, 2, id = "one"), 0)
        assertNull(acc.add(part("xyz", 0, 2, id = "two"), 10))
        assertEquals(1, acc.captured)
        assertEquals("xyzuvw", acc.add(part("uvw", 1, 2, id = "two"), 20))
    }

    @Test
    fun `a part arriving after the timeout restarts the sequence`() {
        val acc = StructuredAppendAccumulator(timeoutMs = 1_000)

        acc.add(part("abc", 0, 2), 0)
        // The second part shows up two seconds late: treat it as the start of a new attempt
        // rather than half of an abandoned one.
        assertNull(acc.add(part("def", 1, 2), 3_000))
        assertEquals(1, acc.captured)
    }

    @Test
    fun `a result that is not part of a sequence is ignored`() {
        val acc = StructuredAppendAccumulator()

        assertNull(acc.add(ScanResult("plain", bounds = null), 0))
        assertEquals(0, acc.captured)
    }

    @Test
    fun `completing a sequence clears the accumulator for the next one`() {
        val acc = StructuredAppendAccumulator()

        acc.add(part("a", 0, 2), 0)
        assertEquals("ab", acc.add(part("b", 1, 2), 10))
        assertEquals(0, acc.captured)
        assertEquals(0, acc.total)
    }

    @Test
    fun `a different part at an index already held restarts rather than splicing`() {
        // Two posters whose sequences happen to share an id (for QR, the one-byte parity) and a
        // count. Taking "ZZZ" as index 1 of the sequence that "AAA" started would splice them.
        val acc = StructuredAppendAccumulator()

        acc.add(part("AAA", 0, 2), 0)
        acc.add(part("BBB", 0, 2), 10)
        assertEquals(1, acc.captured)
        assertEquals("BBBCCC", acc.add(part("CCC", 1, 2), 20))
    }

    @Test
    fun `a joined payload is returned whatever the id says`() {
        // The id is a parity byte some generators compute differently. A fully captured code is
        // never thrown away over it.
        val acc = StructuredAppendAccumulator()

        acc.add(part("abc", 0, 2, id = "0"), 0)
        assertEquals("abcdef", acc.add(part("def", 1, 2, id = "0"), 10))
    }

    @Test
    fun `only parts of the sequence being collected count as current`() {
        val acc = StructuredAppendAccumulator()
        assertFalse("nothing is current while idle", acc.isCurrentSequence(part("x", 0, 2, id = "a")))

        acc.add(part("x", 0, 2, id = "a"), 0)
        assertTrue(acc.isCurrentSequence(part("y", 1, 2, id = "a")))
        assertFalse(acc.isCurrentSequence(part("y", 1, 2, id = "b")))
        assertFalse(acc.isCurrentSequence(part("y", 1, 3, id = "a")))
    }

    @Test
    fun `an image with every part joins them and never returns a fragment`() {
        val parity = "7"
        val assembled =
            assembleStructuredAppend(
                listOf(
                    part("bbb", 1, 3, id = parity),
                    ScanResult("npub1plain", bounds = null),
                    part("ccc", 2, 3, id = parity),
                    part("nsec1aaa", 0, 3, id = parity),
                ),
            )

        assertEquals(listOf("npub1plain", "nsec1aaabbbccc"), assembled.texts)
        assertNull(assembled.incomplete)
    }

    @Test
    fun `an image with only some parts reports progress and returns no fragment`() {
        val assembled =
            assembleStructuredAppend(
                listOf(
                    part("nsec1aaa", 0, 3),
                    part("ccc", 2, 3),
                ),
            )

        assertTrue(assembled.texts.isEmpty())
        assertEquals(2 to 3, assembled.incomplete)
    }

    @Test
    fun `one image code alone is opened, anything more is a choice`() {
        assertEquals(ImageCodesOutcome.NothingFound, AssembledCodes(emptyList(), null).outcome())
        assertEquals(ImageCodesOutcome.OnlyPartial(2, 3), AssembledCodes(emptyList(), 2 to 3).outcome())
        assertEquals(ImageCodesOutcome.Open("npub1a"), AssembledCodes(listOf("npub1a"), null).outcome())
        assertEquals(ImageCodesOutcome.Choose(listOf("npub1a", "npub1b"), null), AssembledCodes(listOf("npub1a", "npub1b"), null).outcome())
    }

    @Test
    fun `one image code next to a partial sequence is a choice, not a silent open`() {
        assertEquals(ImageCodesOutcome.Choose(listOf("npub1a"), 2 to 3), AssembledCodes(listOf("npub1a"), 2 to 3).outcome())
    }
}
