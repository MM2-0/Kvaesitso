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
package com.vitorpamplona.amethyst.commons.nip99Classifieds

import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketAmount
import com.vitorpamplona.amethyst.commons.nip15Marketplace.formatMarketPrice
import com.vitorpamplona.amethyst.commons.nip15Marketplace.marketCurrencyLabel
import com.vitorpamplona.quartz.nip99Classifieds.shipping.ShippingOptionEvent
import com.vitorpamplona.quartz.nip99Classifieds.shipping.tags.DurationTag
import com.vitorpamplona.quartz.nip99Classifieds.tags.PriceTag
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

private val DECIMAL = Regex("""^\s*(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?\s*$""")

/**
 * An amount a seller wrote: a plain decimal, never negative. Null for "", "0x10", "-3" or "free",
 * so a missing cost is never read as zero.
 */
fun parseMarketAmount(text: String?): Double? {
    if (text == null || !DECIMAL.matches(text)) return null
    return text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
}

/** A price with an amount and a currency, or null: an option priced in nothing cannot be added to. */
private fun PriceTag.amountAndCurrency(): Pair<Double, String>? {
    val amount = parseMarketAmount(amount) ?: return null
    val currency = currency?.trim()?.ifEmpty { null } ?: return null
    return amount to currency
}

/** What one shipping option costs one product, as its row says it. */
sealed interface ShippingCost {
    /** Both the option and the product's extra come to nothing. */
    data object Free : ShippingCost

    /** One amount in one money: the option's price, plus the extra when it is the same money. */
    data class Priced(
        val text: String,
    ) : ShippingCost

    /** The option is unpriced (or not loaded) and the product adds [extra] to it. */
    data class Surcharge(
        val extra: String,
    ) : ShippingCost

    /** The option and the extra are in different moneys, so they are shown side by side, not added. */
    data class Split(
        val option: String,
        val extra: String,
    ) : ShippingCost
}

/**
 * The option's own price plus the product's extra cost (`["shipping_option", <address>, <extra>]`),
 * which is in the product's currency. They are added only when both are known to be one money;
 * otherwise they are shown side by side. Null when nothing is known.
 */
fun shippingCost(
    price: PriceTag?,
    extra: String?,
    productCurrency: String?,
): ShippingCost? {
    val extraAmount = parseMarketAmount(extra)?.takeIf { it > 0 }
    val option = price?.amountAndCurrency()
    if (option == null) return extraAmount?.let { ShippingCost.Surcharge(formatMarketPrice(it, productCurrency)) }
    val (amount, currency) = option
    if (extraAmount == null) return if (amount == 0.0) ShippingCost.Free else ShippingCost.Priced(formatMarketPrice(amount, currency))
    val productMoney = marketCurrencyLabel(productCurrency)
    if (productMoney != null && productMoney == marketCurrencyLabel(currency)) {
        // Eight decimals is a satoshi's worth of BTC: 0.1 + 0.2 is 0.3, not 0.30000000000000004.
        val total = round((amount + extraAmount) * 1e8) / 1e8
        return ShippingCost.Priced(formatMarketPrice(total, currency))
    }
    return ShippingCost.Split(formatMarketPrice(amount, currency), formatMarketPrice(extraAmount, productCurrency))
}

enum class ShippingDurationUnit {
    HOURS,
    DAYS,
    WEEKS,
}

/** How long delivery takes: "1–3" days, "24" hours. */
data class ShippingDuration(
    val min: Double,
    val max: Double,
    val unit: ShippingDurationUnit,
) {
    /** "1–3", or "2" when both bounds agree. */
    fun range(): String = if (min == max) formatMarketAmount(min) else "${formatMarketAmount(min)}–${formatMarketAmount(max)}"

    /** The number the unit's plural agrees with: "1 day", "1–3 days". */
    fun pluralCount(): Int = ceil(max).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
}

/**
 * `["duration", <min>, <max>, <H|D|W>]`. A blank bound is unknown, not zero ("" would read
 * "0–3 days"); with one bound it is both; a reversed window reads low to high.
 */
fun DurationTag.toShippingDuration(): ShippingDuration? {
    val unit =
        when (unit.trim().uppercase()) {
            "H" -> ShippingDurationUnit.HOURS
            "D" -> ShippingDurationUnit.DAYS
            "W" -> ShippingDurationUnit.WEEKS
            else -> return null
        }
    val lo = parseMarketAmount(min)
    val hi = parseMarketAmount(max)
    val low = if (lo != null && hi != null) min(lo, hi) else lo ?: hi ?: return null
    val high = if (lo != null && hi != null) max(lo, hi) else low
    return ShippingDuration(low, high, unit)
}

/** The package limits and per-unit rates an option may carry, in the order a buyer reads them. */
enum class ShippingLimit(
    val tagName: String,
    val isRate: Boolean,
) {
    MIN_WEIGHT("weight-min", false),
    MAX_WEIGHT("weight-max", false),
    MIN_SIZE("dim-min", false),
    MAX_SIZE("dim-max", false),
    PER_WEIGHT("price-weight", true),
    PER_VOLUME("price-volume", true),
    PER_DISTANCE("price-distance", true),
}

data class ShippingLimitFact(
    val limit: ShippingLimit,
    val value: String,
)

/**
 * The option's limits ("30 kg") and per-unit rates ("0.75 USD / kg"). A rate is a positive
 * amount, so a blank, zero or malformed one is no rate. Its currency comes first when it names
 * both (`["price-weight", "0.75", "USD", "kg"]`, the spec's example) and is the option's own when
 * it names only the unit (`["price-weight", "0.75", "kg"]`, the spec's table); an empty trailing
 * slot is padding, not a unit.
 */
fun ShippingOptionEvent.limits(): List<ShippingLimitFact> {
    val optionCurrency = price()?.currency?.trim()?.ifEmpty { null }
    return ShippingLimit.entries.mapNotNull { limit ->
        val tag = tags.firstOrNull { it.size > 1 && it[0] == limit.tagName } ?: return@mapNotNull null
        if (!limit.isRate) {
            val value =
                tag
                    .drop(1)
                    .take(2)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            return@mapNotNull if (value.isEmpty()) null else ShippingLimitFact(limit, value.joinToString(" "))
        }
        val amount = parseMarketAmount(tag[1])?.takeIf { it > 0 } ?: return@mapNotNull null
        val slots = tag.drop(2).map { it.trim() }.dropLastWhile { it.isEmpty() }
        val currency = if (slots.size > 1) slots[0] else optionCurrency
        val per = (if (slots.size > 1) slots[1] else slots.firstOrNull())?.ifEmpty { null }
        val price = formatMarketPrice(amount, currency)
        ShippingLimitFact(limit, if (per != null) "$price / $per" else price)
    }
}

/**
 * The countries an option names, once each, in order. Codes (`us`, `JPN`) are upper-cased; a
 * country written out ("United Kingdom", which sellers in the wild do) is kept as written.
 */
fun ShippingOptionEvent.countryCodes(): List<String> =
    countries()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { if (it.length <= 3) it.uppercase() else it }
        .distinctBy { it.lowercase() }

/** ISO 3166-2 codes, once each, in order. */
fun ShippingOptionEvent.regionCodes(): List<String> = regions().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** "US, CA, MX +2": where an option ships, briefly. Null when it names no country. */
fun countriesSummary(
    countries: List<String>,
    max: Int = 4,
): String? {
    if (countries.isEmpty()) return null
    val shown = countries.take(max).joinToString(", ")
    return if (countries.size > max) "$shown +${countries.size - max}" else shown
}

/**
 * A `d` worth showing while its option has not loaded: "voltvault-worldwide-free-shipping" reads
 * "Voltvault worldwide free shipping"; a UUID, a hash or a timestamp reads as nothing.
 */
fun shippingNameFromD(d: String): String? {
    val plain = d.trim()
    if (!Regex("[a-zA-Z]{3}").containsMatchIn(plain)) return null
    if (Regex("^[0-9a-fA-F-]{16,}$").matches(plain) || Regex("""\d{9,}""").containsMatchIn(plain)) return null
    val words = plain.replace(Regex("[-_]+"), " ").trim()
    return words.ifEmpty { null }?.replaceFirstChar { it.uppercaseChar() }
}
