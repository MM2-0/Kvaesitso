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
package com.vitorpamplona.amethyst.commons

import com.vitorpamplona.amethyst.commons.blurhash.Base83
import kotlin.test.Test
import kotlin.test.assertEquals

class Base83Test {
    @Test
    fun testEncodeDecode() {
        for (i in 0..820000) {
            assertEquals(i, Base83.decode(Base83.encode(i.toLong())), "$i encode decode")
        }
    }

    @Test
    fun testSingleDigits() {
        for (i in 0..82) {
            val expected: String = Base83.ALPHABET.concatToString(i, i + 1)
            assertEquals(expected, Base83.encode(i.toLong(), 1), "$i encodes")
        }
    }

    @Test
    fun test0000() {
        assertEquals("0000", Base83.encode(0, 4))
    }

    @Test
    fun test0001() {
        assertEquals("0001", Base83.encode(1, 4))
    }

    @Test
    fun test0010() {
        assertEquals("0010", Base83.encode(83, 4))
    }

    @Test
    fun test0011() {
        assertEquals("0011", Base83.encode(83 + 1, 4))
    }

    @Test
    fun test00X0() {
        assertEquals("00~0", Base83.encode(83 * 82, 4))
    }

    @Test
    fun test0100() {
        assertEquals("0100", Base83.encode(83 * 83, 4))
    }

    @Test
    fun test00XXEncode() {
        assertEquals("00~~", Base83.encode(83 * 82 + 82, 4))
    }

    @Test
    fun test0XXXDecode() {
        assertEquals(82 + 82 * 83 + 82 * 83 * 83, Base83.decode("0~~~"))
    }
}
