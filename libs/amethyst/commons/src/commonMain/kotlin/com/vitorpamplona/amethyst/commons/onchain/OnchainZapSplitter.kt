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
package com.vitorpamplona.amethyst.commons.onchain

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/** A per-recipient share of an onchain split zap. */
data class OnchainZapShare(
    val recipientPubKey: HexKey,
    val sats: Long,
    val weight: Double,
)

/** Thrown when one or more recipient shares fall below the configured dust threshold. */
class DustRecipientException(
    val belowDust: List<OnchainZapShare>,
    val dustThresholdSats: Long,
) : RuntimeException(
        "Recipients below dust threshold ($dustThresholdSats sats): " +
            belowDust.joinToString { "${it.recipientPubKey.take(8)}…=${it.sats}" },
    )

/**
 * Distributes a total amount of sats across weighted recipients using integer
 * math, leaving every share `>= dustThresholdSats` or throwing.
 *
 * Distribution rules:
 *   - share_i = floor(totalSats * weight_i / totalWeight)
 *   - the rounding remainder is added one-sat at a time to the recipients with
 *     the largest weights (largest first; ties broken by input order) so the
 *     sum of shares equals `totalSats` exactly
 *   - any share that lands below dust is reported via [DustRecipientException]
 */
object OnchainZapSplitter {
    /**
     * Clean raw `["zap", pubkey, relay, weight]` splits for the on-chain path:
     *
     * 1. drop the sender's own pubkey (the on-chain builder refuses self-pays,
     *    and a self-share would otherwise abort the whole zap when the user
     *    zaps their own post — common, since most splits include the author)
     * 2. merge duplicates by pubkey, summing their weights (Lightning splits
     *    can repeat a pubkey; on-chain we want exactly one output per pubkey
     *    so each recipient gets one receipt with one consolidated amount)
     *
     * The returned list preserves first-seen input order.
     */
    fun prepare(
        rawSplits: List<Pair<HexKey, Double>>,
        senderPubKey: HexKey,
    ): List<Pair<HexKey, Double>> {
        val merged = linkedMapOf<HexKey, Double>()
        for ((pubKey, weight) in rawSplits) {
            if (pubKey == senderPubKey) continue
            if (weight <= 0.0) continue
            merged[pubKey] = (merged[pubKey] ?: 0.0) + weight
        }
        return merged.entries.map { it.key to it.value }
    }

    fun distribute(
        totalSats: Long,
        splits: List<Pair<HexKey, Double>>,
        dustThresholdSats: Long,
    ): List<OnchainZapShare> {
        val shares = computeShares(totalSats, splits)
        val belowDust = shares.filter { it.sats < dustThresholdSats }
        if (belowDust.isNotEmpty()) throw DustRecipientException(belowDust, dustThresholdSats)
        return shares
    }

    /**
     * Same allocation as [distribute] but never throws on dust. Returns every
     * share (including below-dust ones) so a UI preview can render the full
     * shape and the caller can decide what to do with offenders. Use
     * [distribute] for the actual send path where below-dust must hard-fail.
     */
    fun distributeUnchecked(
        totalSats: Long,
        splits: List<Pair<HexKey, Double>>,
    ): List<OnchainZapShare> = computeShares(totalSats, splits)

    private fun computeShares(
        totalSats: Long,
        splits: List<Pair<HexKey, Double>>,
    ): List<OnchainZapShare> {
        require(totalSats > 0) { "total must be positive" }
        require(splits.isNotEmpty()) { "splits must be non-empty" }
        require(splits.none { it.second <= 0.0 }) { "weights must be positive" }

        val totalWeight = splits.sumOf { it.second }

        // Floor every share, then distribute the leftover one sat at a time in
        // descending-weight order — gives the largest recipients the rounding
        // benefit and avoids drifting the small ones below dust by accident.
        val shares = LongArray(splits.size)
        var assigned = 0L
        splits.forEachIndexed { i, (_, weight) ->
            val s = (totalSats * weight / totalWeight).toLong()
            shares[i] = s
            assigned += s
        }
        // Each floor() loses < 1 sat, so the total remainder is strictly less
        // than `splits.size` — well within Int range for any plausible N.
        val remainder = totalSats - assigned
        check(remainder < splits.size) { "remainder $remainder exceeds splits.size ${splits.size}" }
        if (remainder > 0) {
            val orderByWeight =
                splits.indices.sortedWith(
                    compareByDescending<Int> { splits[it].second }.thenBy { it },
                )
            for (k in 0 until remainder.toInt()) {
                // remainder < splits.size, so k % size is just k — kept
                // defensively in case the bound ever loosens.
                shares[orderByWeight[k % orderByWeight.size]] += 1
            }
        }

        return splits.mapIndexed { i, (pubKey, weight) ->
            OnchainZapShare(recipientPubKey = pubKey, sats = shares[i], weight = weight)
        }
    }
}
