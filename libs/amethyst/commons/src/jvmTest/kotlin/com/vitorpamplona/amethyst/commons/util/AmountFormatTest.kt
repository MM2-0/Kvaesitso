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

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Double-based [compactAmount] other platforms use, against the JVM's DecimalFormat actuals. */
class AmountFormatTest {
    private val separator = DecimalFormatSymbols.getInstance().decimalSeparator

    private val samples: List<BigDecimal> =
        buildList {
            (0L..20_000L step 7).forEach { add(BigDecimal(it)) }
            listOf(
                0L,
                1L,
                9_999L,
                10_000L,
                10_499L,
                10_500L,
                12_500L,
                13_500L,
                999_499L,
                999_500L,
                1_000_000L,
                1_499_999L,
                1_500_000L,
                9_499_999L,
                9_500_000L,
                9_999_999L,
                10_000_000L,
                999_499_999L,
                999_500_000L,
                1_500_000_000L,
                9_500_000_000L,
                10_000_000_000L,
                2_100_000_000_000_000L,
            ).forEach { add(BigDecimal(it)) }
            // Millisat precision: three decimals, ties included.
            listOf("0.005", "0.01", "0.5", "1.5", "2.5", "2.501", "9999.499", "9999.5", "10499.999", "12345.678").forEach { add(BigDecimal(it)) }
            var x = 1L
            while (x < 1_000_000_000_000_000L) {
                add(BigDecimal(x * 3 + 17))
                x *= 7
            }
        }

    @Test
    fun matchesShowAmount() {
        samples.forEach { assertEquals(showAmount(it), compactAmount(it.toDouble(), withDecimal = true, separator), "$it") }
    }

    @Test
    fun matchesShowAmountInteger() {
        samples.forEach { assertEquals(showAmountInteger(it), compactAmount(it.toDouble(), withDecimal = false, separator), "$it") }
    }
}
