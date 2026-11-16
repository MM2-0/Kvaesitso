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
package com.vitorpamplona.amethyst.commons.nip15Marketplace

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.round

/**
 * A NIP-15 price as written: whole amounts without decimals ("2100"), others to the cent
 * ("12.50"). Not the compact zap format (`showAmount` reads 12.50 as "13" and 210000 as "210k"),
 * because a buyer reads a price exactly. Below one unit, cents would round a real price to
 * nothing (0.0021 BTC to "0.00"), so those keep four significant digits, up to a satoshi's eight
 * decimals: "0.0021", "0.5".
 */
fun formatMarketAmount(amount: Double): String {
    if (!amount.isFinite()) return ""
    val sign = if (amount < 0) "-" else ""
    val value = abs(amount)
    if (value == floor(value) && value < 1e15) return sign + value.toLong().toString()
    if (value < 1) return sign + formatSubUnit(value)
    val cents = round(value * 100).toLong()
    val fraction = (cents % 100).toString().padStart(2, '0')
    return "$sign${cents / 100}.$fraction"
}

/** Up to a satoshi's eight decimals, trailing zeros dropped: "1.2345", "0.00021", "2". */
private fun formatBitcoinAmount(amount: Double): String {
    if (!amount.isFinite()) return ""
    val sign = if (amount < 0) "-" else ""
    val sats = round(abs(amount) * 1e8).toLong()
    val whole = sats / 100_000_000
    val fraction = (sats % 100_000_000).toString().padStart(8, '0').trimEnd('0')
    return if (fraction.isEmpty()) "$sign$whole" else "$sign$whole.$fraction"
}

/** [value] in (0, 1): four significant digits, at most eight decimals, trailing zeros dropped. */
private fun formatSubUnit(value: Double): String {
    val decimals = (3 - floor(log10(value)).toInt()).coerceIn(2, 8)
    var scale = 1L
    repeat(decimals) { scale *= 10 }
    val scaled = round(value * scale).toLong()
    if (scaled >= scale) return "1"
    val fraction = scaled.toString().padStart(decimals, '0').trimEnd('0')
    return if (fraction.isEmpty()) "0" else "0.$fraction"
}

/** The currency as a buyer reads it: `sat`/`sats` (any case) as "sats", codes upper-cased. */
fun marketCurrencyLabel(currency: String?): String? {
    val code = currency?.trim()?.ifEmpty { null } ?: return null
    return if (code.equals("sat", ignoreCase = true) || code.equals("sats", ignoreCase = true)) "sats" else code.uppercase()
}

/** "2100 sats", "12.50 USD", or the bare amount when the currency is unknown. */
fun formatMarketPrice(
    amount: Double,
    currency: String?,
): String {
    val label = marketCurrencyLabel(currency)
    // Bitcoin is priced in fractions cents would erase: 1.2345 BTC is not "1.23".
    val number = if (label == "BTC") formatBitcoinAmount(amount) else formatMarketAmount(amount)
    return if (label == null) number else "$number $label"
}
