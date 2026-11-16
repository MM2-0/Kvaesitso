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

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class ZapFormatterTest {
    private fun <T> inEnglish(block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            return block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun halvesRoundUpNotToEven() =
        inEnglish {
            // Kotlin's BigDecimal `div` rounds HALF_EVEN, so 12,500 used to read "12k".
            assertEquals("13k", showAmountInteger(12_500))
            assertEquals("14k", showAmountInteger(13_500))
            assertEquals("3M", showAmountInteger(2_500_000))
            assertEquals("13k", showAmount(BigDecimal(12_500)))
        }

    @Test
    fun roundingUpToTheNextUnitSwitchesUnit() =
        inEnglish {
            assertEquals("999k", showAmountInteger(999_499))
            assertEquals("1M", showAmountInteger(999_500))
            assertEquals("1G", showAmountInteger(999_500_000))
            assertEquals("1.0M", showAmount(BigDecimal(999_500)))
        }

    @Test
    fun smallAmountsStayWhole() =
        inEnglish {
            assertEquals("0", showAmountInteger(0))
            assertEquals("9999", showAmountInteger(9_999))
            assertEquals("", showAmount(BigDecimal("0.001")))
            assertEquals("0", showAmountWithZero(BigDecimal("0.001")))
        }
}
