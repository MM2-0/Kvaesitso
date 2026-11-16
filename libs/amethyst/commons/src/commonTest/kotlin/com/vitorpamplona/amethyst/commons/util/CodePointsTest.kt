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
package com.vitorpamplona.amethyst.commons.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CodePointsTest {
    // ---- codePointCharCount ----

    @Test
    fun charCountIsOneForBmp() {
        assertEquals(1, codePointCharCount(0x0041)) // 'A'
        assertEquals(1, codePointCharCount(0xFFFF)) // last BMP code point
    }

    @Test
    fun charCountIsTwoForSupplementary() {
        assertEquals(2, codePointCharCount(0x10000)) // first supplementary code point
        assertEquals(2, codePointCharCount(0x1F600)) // 😀 grinning face
        assertEquals(2, codePointCharCount(0x10FFFF)) // last valid code point
    }

    // ---- codePointToChars ----

    @Test
    fun toCharsRoundTripsAscii() {
        assertContentEquals(charArrayOf('A'), codePointToChars(0x0041))
    }

    @Test
    fun toCharsRoundTripsLastBmp() {
        assertContentEquals(charArrayOf('￿'), codePointToChars(0xFFFF))
    }

    @Test
    fun toCharsProducesSurrogatePairForGrinningFace() {
        // U+1F600 (😀) is encoded as the surrogate pair (0xD83D, 0xDE00).
        assertContentEquals(charArrayOf('\uD83D', '\uDE00'), codePointToChars(0x1F600))
    }

    @Test
    fun toCharsProducesSurrogatePairForFirstSupplementary() {
        // U+10000 -> (0xD800, 0xDC00).
        assertContentEquals(charArrayOf('\uD800', '\uDC00'), codePointToChars(0x10000))
    }

    @Test
    fun toCharsProducesSurrogatePairForLastCodePoint() {
        // U+10FFFF -> (0xDBFF, 0xDFFF).
        assertContentEquals(charArrayOf('\uDBFF', '\uDFFF'), codePointToChars(0x10FFFF))
    }

    // ---- String.codePointAtKmp ----

    @Test
    fun codePointAtReturnsAsciiCodeForBmp() {
        assertEquals(0x0041, "Aa".codePointAtKmp(0))
        assertEquals(0x0061, "Aa".codePointAtKmp(1))
    }

    @Test
    fun codePointAtDecodesSurrogatePair() {
        // "😀X" — surrogate pair followed by 'X'.
        val s = "😀X"
        assertEquals(0x1F600, s.codePointAtKmp(0))
    }

    @Test
    fun codePointAtReturnsLowSurrogateWhenIndexIsOnLowHalf() {
        // Indexing into the middle of a surrogate pair returns the low surrogate's raw code unit.
        val s = "😀"
        assertEquals(0xDE00, s.codePointAtKmp(1))
    }

    @Test
    fun codePointAtReturnsHighSurrogateWhenStringEndsOnLoneHighSurrogate() {
        // Lone high surrogate at end of string: `index + 1 < length` is false, so we
        // fall back to returning the surrogate's raw code unit. This matches
        // java.lang.Character.codePointAt(CharSequence, int).
        val s = "\uD83D"
        assertEquals(0xD83D, s.codePointAtKmp(0))
    }

    @Test
    fun codePointAtReturnsHighSurrogateWhenFollowedByNonSurrogate() {
        // High surrogate followed by a non-low-surrogate char: return the high surrogate raw.
        val s = "\uD83DA"
        assertEquals(0xD83D, s.codePointAtKmp(0))
        assertEquals(0x0041, s.codePointAtKmp(1))
    }

    // ---- String.offsetByCodePointsKmp ----

    @Test
    fun offsetByOneStepsOverBmp() {
        // Two ASCII chars; offset 1 from index 0 lands on index 1.
        assertEquals(1, "Aa".offsetByCodePointsKmp(0, 1))
    }

    @Test
    fun offsetByOneStepsOverSupplementaryPair() {
        // 😀 is 2 UTF-16 code units; offset 1 from index 0 lands on index 2.
        val s = "😀X"
        assertEquals(2, s.offsetByCodePointsKmp(0, 1))
        // Then a single step lands on the end of the string.
        assertEquals(3, s.offsetByCodePointsKmp(2, 1))
    }

    @Test
    fun offsetByZeroIsIdentity() {
        assertEquals(0, "Aa".offsetByCodePointsKmp(0, 0))
        assertEquals(2, "Aa".offsetByCodePointsKmp(2, 0))
    }

    // ---- Round-trip ----

    @Test
    fun encodeDecodeRoundTripCoversFullCodePointRange() {
        // Spot-check a handful of code points across BMP and supplementary planes.
        val samples = intArrayOf(0x0001, 0x0041, 0x00FF, 0x4F60, 0xFFFF, 0x10000, 0x1F600, 0x10FFFF)
        for (cp in samples) {
            val chars = codePointToChars(cp)
            val asString = chars.concatToString()
            assertEquals(cp, asString.codePointAtKmp(0), "round-trip code point U+${cp.toString(16).uppercase()}")
            assertEquals(
                chars.size,
                codePointCharCount(cp),
                "char count for U+${cp.toString(16).uppercase()}",
            )
        }
    }
}
