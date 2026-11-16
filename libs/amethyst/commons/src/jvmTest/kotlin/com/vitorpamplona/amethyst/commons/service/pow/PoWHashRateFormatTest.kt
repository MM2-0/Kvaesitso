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
package com.vitorpamplona.amethyst.commons.service.pow

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PoWHashRateFormatTest {
    private lateinit var saved: Locale

    @BeforeTest
    fun pinLocale() {
        saved = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @AfterTest
    fun restoreLocale() = Locale.setDefault(saved)

    @Test
    fun picksTheUnit() {
        assertEquals("0 H/s", formatHashRate(0.0))
        assertEquals("850 H/s", formatHashRate(850.4))
        assertEquals("1.0 kH/s", formatHashRate(1_000.0))
        assertEquals("4.2 MH/s", formatHashRate(4_210_000.0))
        assertEquals("312 MH/s", formatHashRate(312_400_000.0))
        assertEquals("1.5 GH/s", formatHashRate(1.5e9))
        assertEquals("2,000 TH/s", formatHashRate(2e15))
    }

    @Test
    fun negativeReadsAsZero() = assertEquals("0 H/s", formatHashRate(-5.0))
}
