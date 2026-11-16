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
import kotlin.test.assertEquals

/** Decimal units, a no-break space before the unit, whole KB, one decimal for MB/GB below 100. */
class FormatFileSizeTest {
    private val nbsp = "\u00A0"

    @Test
    fun underOneKb() {
        assertEquals("1${nbsp}KB", formatFileSize(0))
        assertEquals("1${nbsp}KB", formatFileSize(999))
    }

    @Test
    fun kilobytes() {
        assertEquals("850${nbsp}KB", formatFileSize(850_000))
        assertEquals("999${nbsp}KB", formatFileSize(999_499))
    }

    @Test
    fun roundingUpCrossesIntoMegabytes() {
        assertEquals("1.0${nbsp}MB", formatFileSize(999_950))
    }

    @Test
    fun megabytes() {
        assertEquals("1.0${nbsp}MB", formatFileSize(1_000_000))
        assertEquals("12.4${nbsp}MB", formatFileSize(12_400_000))
    }

    @Test
    fun hundredCutoff() {
        assertEquals("99.9${nbsp}MB", formatFileSize(99_940_000))
        assertEquals("140${nbsp}MB", formatFileSize(140_000_000))
    }

    @Test
    fun gigabytes() {
        assertEquals("1.2${nbsp}GB", formatFileSize(1_200_000_000))
        assertEquals("5.0${nbsp}GB", formatFileSize(5_000_000_000))
    }
}
