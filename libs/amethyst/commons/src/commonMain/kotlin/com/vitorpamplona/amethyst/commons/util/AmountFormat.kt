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

import com.vitorpamplona.quartz.utils.BigDecimal
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

/**
 * A zap amount in compact form: whole units below 10,000, then k, M and G. The amount is rounded
 * half-up to a whole number of the unit before the unit is picked, so 999,500 reads "1.0M" rather
 * than "1000k"; M and G below ten carry one decimal place ("3.0M"). Empty for null or for an
 * amount that rounds to nothing.
 */
expect fun showAmount(amount: BigDecimal?): String

/** [showAmount] without the decimal place on M and G ("3M"). */
expect fun showAmountInteger(amount: BigDecimal?): String

/** [showAmount], reading "0" where that would be empty (null or an amount that rounds to nothing). */
fun showAmountWithZero(amount: BigDecimal?): String = showAmount(amount).ifEmpty { "0" }

/** [showAmountInteger], reading "0" where that would be empty (null or an amount that rounds to nothing). */
fun showAmountIntegerWithZero(amount: BigDecimal?): String = showAmountInteger(amount).ifEmpty { "0" }

/**
 * [showAmount] and [showAmountInteger] over a Double, for platforms without java.text. Exact for
 * zap amounts: a tie only happens at an exact .5, which a Double holds exactly at this magnitude.
 */
internal fun compactAmount(
    value: Double,
    withDecimal: Boolean,
    decimalSeparator: Char,
): String {
    // <=, not <: the JVM's threshold is BigDecimal(0.01), built from the double, a hair above 0.01.
    if (abs(value) <= 0.01) return ""
    // DecimalFormat("#") rounds half-even.
    if (value < 10_000) return round(value).toLong().toString()

    val kilos = roundHalfUp(value / 1_000)
    if (kilos < 1_000) return "${kilos}k"
    val megas = roundHalfUp(value / 1_000_000)
    if (megas < 1_000) return if (withDecimal && megas < 10) "$megas${decimalSeparator}0M" else "${megas}M"
    val gigas = roundHalfUp(value / 1_000_000_000)
    return if (withDecimal && gigas < 10) "$gigas${decimalSeparator}0G" else "${gigas}G"
}

private fun roundHalfUp(value: Double): Long = if (value >= 0) floor(value + 0.5).toLong() else -floor(-value + 0.5).toLong()
