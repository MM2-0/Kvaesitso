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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.AccountZapPreferencesInternal
import com.vitorpamplona.amethyst.commons.model.DefaultOnchainZapAmounts
import com.vitorpamplona.amethyst.commons.model.DefaultZapAmounts
import com.vitorpamplona.amethyst.commons.model.MIN_ONCHAIN_ZAP_SATS
import com.vitorpamplona.amethyst.commons.model.mergeZapAmounts
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The on-chain rail's separate amount-preset list was folded into the single
 * [AccountZapPreferencesInternal.zapAmountChoices]. These lock in that the
 * migration union preserves amounts from both the legacy lists, keeps the
 * user's saved ordering (Lightning list first, then on-chain extras — NOT
 * re-sorted), and round-trips idempotently (write the on-chain subset back,
 * union it again → same list).
 */
class ZapAmountMergeTest {
    private fun zaps(
        zap: List<Long>,
        onchain: List<Long>,
    ) = AccountZapPreferencesInternal(zapAmountChoices = zap, onchainZapAmountChoices = onchain)

    @Test
    fun unionsDefaultsPreservingOrderAndDeduped() {
        assertEquals(
            listOf(21L, 50L, 100L, 5_000L),
            mergeZapAmounts(zaps(DefaultZapAmounts, DefaultOnchainZapAmounts)),
        )
    }

    @Test
    fun preservesCustomOrderAndAmountsFromBothLists() {
        // Simulates a sync from an older client that still split the two lists.
        // First-seen order is kept (the Lightning list as-is, then on-chain
        // extras), so a user's deliberate ordering isn't silently re-sorted.
        assertEquals(
            listOf(21L, 100L, 10L, 7_777L, 1_000L),
            mergeZapAmounts(zaps(zap = listOf(21L, 100L, 10L), onchain = listOf(7_777L, 1_000L))),
        )
    }

    @Test
    fun dedupesAcrossLists() {
        assertEquals(
            listOf(100L, 5_000L),
            mergeZapAmounts(zaps(zap = listOf(100L, 5_000L), onchain = listOf(5_000L))),
        )
    }

    @Test
    fun roundTripIsIdempotent() {
        val merged = mergeZapAmounts(zaps(DefaultZapAmounts, DefaultOnchainZapAmounts))
        // toInternal() writes the full list as zap and the on-chain-eligible
        // subset as the legacy field; merging those again must not drift.
        val onchainSubset = merged.filter { it >= MIN_ONCHAIN_ZAP_SATS }
        assertEquals(merged, mergeZapAmounts(zaps(zap = merged, onchain = onchainSubset)))
    }
}
