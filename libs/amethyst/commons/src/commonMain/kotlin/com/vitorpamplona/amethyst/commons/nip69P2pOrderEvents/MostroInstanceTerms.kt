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
package com.vitorpamplona.amethyst.commons.nip69P2pOrderEvents

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.commons.util.formatDecimal
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.tags.EscrowModeTag
import kotlin.math.round

/** The escrow backend a Mostro instance runs; [OTHER] for a backend a later daemon may add. */
enum class MostroEscrow {
    LIGHTNING,
    CASHU,
    OTHER,
    ;

    companion object {
        /** other_events.md: a missing `escrow_mode` means Lightning (older daemons omit it). */
        fun of(code: String?): MostroEscrow =
            when (code) {
                null, EscrowModeTag.LIGHTNING -> LIGHTNING
                EscrowModeTag.CASHU -> CASHU
                else -> OTHER
            }
    }
}

/**
 * The terms a Mostro instance (kind 38385) publishes, reduced to what a reader deciding whether to
 * trade there needs: who it is, what it charges, how much it trades, in which currencies, how long
 * an order waits and how the sats are escrowed. Everything machine-only (node keys, hashes, PoW,
 * invoice windows) stays on the event.
 */
@Immutable
class MostroInstanceTerms(
    val name: String?,
    val version: String?,
    /** The fee as a percentage (0.6 for a published `0.006`), or null when absent or nonsense. */
    val feePercent: Double?,
    val minOrderSats: Long?,
    val maxOrderSats: Long?,
    /** null when the tag is absent; empty when the instance accepts every currency. */
    val currencies: List<String>?,
    val pendingExpirationHours: Long?,
    val escrow: MostroEscrow,
    /** The published `escrow_mode`, shown as written for [MostroEscrow.OTHER]. */
    val escrowCode: String?,
    val inMaintenance: Boolean,
) {
    val acceptsEveryCurrency: Boolean get() = currencies?.isEmpty() == true

    companion object {
        fun from(event: MostroInfoEvent): MostroInstanceTerms =
            MostroInstanceTerms(
                name = event.instanceName()?.ifBlank { null },
                version = event.mostroVersion()?.ifBlank { null },
                feePercent = event.fee()?.let(::feeToPercent),
                minOrderSats = event.minOrderAmount()?.takeIf { it >= 0 },
                maxOrderSats = event.maxOrderAmount()?.takeIf { it >= 0 },
                currencies = event.fiatCurrenciesAccepted()?.map { it.uppercase() }?.distinct(),
                pendingExpirationHours = event.expirationHours()?.takeIf { it > 0 },
                escrow = MostroEscrow.of(event.escrowMode()),
                escrowCode = event.escrowMode(),
                inMaintenance = event.isInMaintenance(),
            )
    }
}

/**
 * A published fee fraction as a percentage, rounded to four decimals so `0.006` reads 0.6 rather
 * than 0.6000000000000001. A negative fee, or one of 100% or more, is not a fee anyone publishes on
 * purpose, so it is left out rather than shown wrong.
 */
fun feeToPercent(fee: Double): Double? {
    if (!fee.isFinite() || fee < 0.0 || fee >= 1.0) return null
    return round(fee * 100 * 10_000) / 10_000
}

/** [percent] as the reader's locale writes it, with at most three decimals: `0.6%`, `1.25%`. */
fun formatPercent(percent: Double): String = formatDecimal(percent, 3) + "%"

/**
 * At most [max] of the list, and how many were left out, so a card for an instance that trades 40
 * currencies shows a line of them and "+28" instead of a wall.
 */
fun <T> List<T>.takeWithOverflow(max: Int): Pair<List<T>, Int> = if (size <= max) this to 0 else take(max) to (size - max)
