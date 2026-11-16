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
package com.vitorpamplona.amethyst.commons.nip53LiveActivities

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * A single entry on a stream's top-zappers leaderboard.
 *
 * @param bucketKey either a real zapper pubkey or [LiveActivityTopZappersAggregator.ANON_KEY]
 *                  for zaps whose zap-request carried an `anon` tag.
 * @param totalSats sum of amounts across every zap that mapped to this bucket.
 * @param isAnonymous true when the bucket is the shared anonymous pool.
 */
@Immutable
data class TopZapperEntry(
    val bucketKey: HexKey,
    val totalSats: Long,
    val isAnonymous: Boolean,
)

/**
 * Represents a single zap-receipt contribution before aggregation. All inputs are
 * plain values so this stays testable in pure Kotlin without Compose or Android.
 *
 * @param receiptId the kind-9735 receipt event id — used to de-duplicate receipts that
 *                  appear in both the stream's #a and the goal's #e feed.
 * @param zapperPubKey pubkey of the zap-request signer (random one-time key for anon/private).
 * @param isAnonymous true when the zap-request carried an `anon` tag (empty or encrypted).
 * @param sats amount in satoshis.
 */
@Immutable
data class ZapContribution(
    val receiptId: HexKey,
    val zapperPubKey: HexKey,
    val isAnonymous: Boolean,
    val sats: Long,
)

/**
 * Computes a top-N zap leaderboard for a live stream.
 *
 * Inputs are raw [ZapContribution]s from any number of sources; the aggregator handles:
 *   - de-duplication by receipt id (a zap that tags both `#a` and `#e` counts once),
 *   - anonymous bucketing (all `anon`-tagged zaps collapse into one entry),
 *   - sum-by-contributor and sort-desc-by-total,
 *   - top-N truncation.
 *
 * This is a pure function — no Compose, no Android, no LocalCache — so it can be unit
 * tested in isolation and reused from any platform target.
 */
object LiveActivityTopZappersAggregator {
    /** Sentinel key that collapses every anonymous / private zap into a single leaderboard entry. */
    const val ANON_KEY: HexKey = "anon"

    /** Default cap — matches zap.stream's TopZappers limit. */
    const val DEFAULT_LIMIT = 10

    fun aggregate(
        contributions: Iterable<ZapContribution>,
        limit: Int = DEFAULT_LIMIT,
    ): List<TopZapperEntry> {
        if (limit <= 0) return emptyList()

        // receiptId -> contribution. Dedupes a single zap that arrives via both #a and #e.
        val unique = HashMap<HexKey, ZapContribution>()
        for (c in contributions) {
            unique[c.receiptId] = c
        }
        if (unique.isEmpty()) return emptyList()

        val totals = HashMap<HexKey, Long>(unique.size)
        var hasAnon = false
        for (c in unique.values) {
            val key = if (c.isAnonymous) ANON_KEY else c.zapperPubKey
            if (c.isAnonymous) hasAnon = true
            totals[key] = (totals[key] ?: 0L) + c.sats
        }

        return totals.entries
            .asSequence()
            .sortedByDescending { it.value }
            .take(limit)
            .map { TopZapperEntry(it.key, it.value, isAnonymous = it.key == ANON_KEY && hasAnon) }
            .toList()
    }
}
