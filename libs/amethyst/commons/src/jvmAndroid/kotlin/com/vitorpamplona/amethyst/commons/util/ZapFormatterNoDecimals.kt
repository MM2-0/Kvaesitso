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
import java.math.RoundingMode
import java.text.DecimalFormat

private val dfG =
    object : ThreadLocal<DecimalFormat>() {
        override fun initialValue() = DecimalFormat("#G")
    }

private val dfM =
    object : ThreadLocal<DecimalFormat>() {
        override fun initialValue() = DecimalFormat("#M")
    }

private val dfK =
    object : ThreadLocal<DecimalFormat>() {
        override fun initialValue() = DecimalFormat("#k")
    }

private val dfN =
    object : ThreadLocal<DecimalFormat>() {
        override fun initialValue() = DecimalFormat("#")
    }

actual fun showAmountInteger(amount: BigDecimal?): String {
    if (amount == null) return ""
    if (amount.abs() < MinDisplayableAmount) return ""
    if (amount < TenKilo) return dfN.get()?.format(amount) ?: ""

    // Round before picking the unit, so 999,500 reads "1M" rather than "1000k".
    // `divide(_, 0, HALF_UP)` rather than `div(_).setScale(0, HALF_UP)`: Kotlin's `div` already
    // rounds to the dividend's scale with HALF_EVEN, which left 12,500 as "12k".
    val kilos = amount.divide(OneKilo, 0, RoundingMode.HALF_UP)
    if (kilos < OneKilo) return dfK.get()?.format(kilos) ?: ""
    val megas = amount.divide(OneMega, 0, RoundingMode.HALF_UP)
    if (megas < OneKilo) return dfM.get()?.format(megas) ?: ""
    return dfG.get()?.format(amount.divide(OneGiga, 0, RoundingMode.HALF_UP)) ?: ""
}

fun showAmountInteger(amount: Int?): String {
    if (amount == null) return "0"

    return showAmountIntegerWithZero(BigDecimal.valueOf(amount.toLong()))
}
