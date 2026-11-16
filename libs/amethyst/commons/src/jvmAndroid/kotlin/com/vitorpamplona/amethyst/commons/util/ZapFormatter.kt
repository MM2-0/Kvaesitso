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

val TenGiga = BigDecimal(10_000_000_000)
val OneGiga = BigDecimal(1_000_000_000)
val TenMega = BigDecimal(10_000_000)
val OneMega = BigDecimal(1_000_000)
val TenKilo = BigDecimal(10_000)
val OneKilo = BigDecimal(1_000)

/** Below this an amount rounds to nothing. Built once: it was a new `BigDecimal` per call. */
internal val MinDisplayableAmount = BigDecimal(0.01)

private val dfGBig = ThreadLocal.withInitial { DecimalFormat("#.#G") }
private val dfGSmall = ThreadLocal.withInitial { DecimalFormat("#.0G") }
private val dfMBig = ThreadLocal.withInitial { DecimalFormat("#.#M") }
private val dfMSmall = ThreadLocal.withInitial { DecimalFormat("#.0M") }
private val dfK = ThreadLocal.withInitial { DecimalFormat("#.#k") }
private val dfN = ThreadLocal.withInitial { DecimalFormat("#") }

/**
 * Formats a BigDecimal amount to human-readable format with G/M/K suffixes.
 * Returns empty string for null or very small amounts.
 *
 * Amounts are rounded half-up to a whole number of the unit before the unit is picked, so
 * 999,500 reads "1.0M" rather than "1000k".
 *
 * Examples:
 * - 1500 -> "1500"
 * - 12500 -> "13k"
 * - 2500000 -> "3.0M"
 * - 10000000000 -> "10G"
 */
actual fun showAmount(amount: BigDecimal?): String {
    if (amount == null) return ""
    if (amount.abs() < MinDisplayableAmount) return ""
    if (amount < TenKilo) return dfN.get()!!.format(amount)

    // `divide(_, 0, HALF_UP)`, not `div(_).setScale(0, HALF_UP)`: Kotlin's `div` already rounds
    // to the dividend's scale with HALF_EVEN, so the HALF_UP never ran and 12,500 read "12k".
    val kilos = amount.divide(OneKilo, 0, RoundingMode.HALF_UP)
    if (kilos < OneKilo) return dfK.get()!!.format(kilos)
    val megas = amount.divide(OneMega, 0, RoundingMode.HALF_UP)
    if (megas < OneKilo) return (if (megas < BigDecimal.TEN) dfMSmall else dfMBig).get()!!.format(megas)
    val gigas = amount.divide(OneGiga, 0, RoundingMode.HALF_UP)
    return (if (gigas < BigDecimal.TEN) dfGSmall else dfGBig).get()!!.format(gigas)
}

/**
 * Extension function to format Long as zap amount.
 */
fun Long.toZapAmount(): String = showAmount(BigDecimal(this))

/**
 * Extension function to format Int as zap amount.
 */
fun Int.toZapAmount(): String = showAmount(BigDecimal(this))
