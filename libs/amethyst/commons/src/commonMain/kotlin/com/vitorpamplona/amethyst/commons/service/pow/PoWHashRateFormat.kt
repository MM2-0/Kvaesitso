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

import com.vitorpamplona.amethyst.commons.util.formatDecimal

private val UNITS = arrayOf("H/s", "kH/s", "MH/s", "GH/s", "TH/s")

/**
 * A mining rate in SI units: "850 H/s", "4.2 MH/s", "312 MH/s". Below 100 of a
 * unit it keeps one decimal, always shown, so a live readout does not jump in
 * width as the rate moves between "4 MH/s" and "4.1 MH/s".
 */
fun formatHashRate(hashesPerSecond: Double): String {
    var value = hashesPerSecond.coerceAtLeast(0.0)
    var unit = 0
    while (value >= 1000.0 && unit < UNITS.lastIndex) {
        value /= 1000.0
        unit++
    }
    val decimals = if (unit > 0 && value < 100.0) 1 else 0
    return "${formatDecimal(value, decimals, decimals)} ${UNITS[unit]}"
}
