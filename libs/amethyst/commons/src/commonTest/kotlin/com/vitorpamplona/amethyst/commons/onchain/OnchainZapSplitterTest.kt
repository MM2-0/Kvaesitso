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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OnchainZapSplitterTest {
    private val a = "a".repeat(64)
    private val b = "b".repeat(64)
    private val c = "c".repeat(64)

    @Test
    fun equalWeightsSplitEvenly() {
        val shares =
            OnchainZapSplitter.distribute(
                totalSats = 30_000L,
                splits = listOf(a to 1.0, b to 1.0, c to 1.0),
                dustThresholdSats = 330L,
            )
        assertEquals(listOf(10_000L, 10_000L, 10_000L), shares.map { it.sats })
        assertEquals(30_000L, shares.sumOf { it.sats })
    }

    @Test
    fun weightedSplitMatchesRatio() {
        val shares =
            OnchainZapSplitter.distribute(
                totalSats = 100_000L,
                splits = listOf(a to 3.0, b to 1.0),
                dustThresholdSats = 330L,
            )
        assertEquals(75_000L, shares[0].sats)
        assertEquals(25_000L, shares[1].sats)
        assertEquals(100_000L, shares.sumOf { it.sats })
    }

    @Test
    fun roundingRemainderGoesToLargestWeight() {
        // 10_001 / (2+1+1) = 2500.25 each. Floors: 5000, 2500, 2500 → 2 sats left.
        // Both extra sats go to the largest-weight recipient (index 0).
        val shares =
            OnchainZapSplitter.distribute(
                totalSats = 10_001L,
                splits = listOf(a to 2.0, b to 1.0, c to 1.0),
                dustThresholdSats = 330L,
            )
        assertEquals(5001L, shares[0].sats)
        assertEquals(2500L, shares[1].sats)
        assertEquals(2500L, shares[2].sats)
        assertEquals(10_001L, shares.sumOf { it.sats })
    }

    @Test
    fun belowDustThrows() {
        // 1000 sats split 1:99 → 10 sats and 990 sats. 10 is below 330 dust.
        val ex =
            assertFailsWith<DustRecipientException> {
                OnchainZapSplitter.distribute(
                    totalSats = 1000L,
                    splits = listOf(a to 1.0, b to 99.0),
                    dustThresholdSats = 330L,
                )
            }
        assertEquals(1, ex.belowDust.size)
        assertEquals(a, ex.belowDust[0].recipientPubKey)
    }

    @Test
    fun preservesInputOrder() {
        val shares =
            OnchainZapSplitter.distribute(
                totalSats = 30_000L,
                splits = listOf(c to 1.0, a to 1.0, b to 1.0),
                dustThresholdSats = 330L,
            )
        assertEquals(c, shares[0].recipientPubKey)
        assertEquals(a, shares[1].recipientPubKey)
        assertEquals(b, shares[2].recipientPubKey)
    }

    @Test
    fun singleRecipientGetsEverything() {
        val shares =
            OnchainZapSplitter.distribute(
                totalSats = 50_000L,
                splits = listOf(a to 1.0),
                dustThresholdSats = 330L,
            )
        assertEquals(1, shares.size)
        assertEquals(50_000L, shares[0].sats)
    }

    @Test
    fun prepareDropsSenderAndMergesDuplicates() {
        val cleaned =
            OnchainZapSplitter.prepare(
                rawSplits = listOf(a to 1.0, b to 2.0, a to 1.0, c to 0.0, b to 1.0),
                senderPubKey = c,
            )
        // sender c is dropped, a is merged (1+1=2), b is merged (2+1=3), c=0 filtered too.
        assertEquals(listOf(a to 2.0, b to 3.0), cleaned)
    }

    @Test
    fun prepareDropsSenderEvenIfOnlyEntry() {
        val cleaned = OnchainZapSplitter.prepare(listOf(a to 1.0), senderPubKey = a)
        assertTrue(cleaned.isEmpty())
    }

    @Test
    fun prepareSkipsNegativeOrZeroWeights() {
        val cleaned =
            OnchainZapSplitter.prepare(
                rawSplits = listOf(a to 1.0, b to 0.0, c to -1.0),
                senderPubKey = "deadbeef".repeat(8),
            )
        assertEquals(listOf(a to 1.0), cleaned)
    }

    @Test
    fun fractionalWeightsWork() {
        val shares =
            OnchainZapSplitter.distribute(
                totalSats = 100_000L,
                splits = listOf(a to 0.5, b to 0.3, c to 0.2),
                dustThresholdSats = 330L,
            )
        assertEquals(100_000L, shares.sumOf { it.sats })
        // a:b:c roughly 5:3:2
        assertTrue(shares[0].sats in 49_900..50_100)
        assertTrue(shares[1].sats in 29_900..30_100)
        assertTrue(shares[2].sats in 19_900..20_100)
    }

    @Test
    fun distributeUncheckedReturnsBelowDustShares() {
        // 1000 sats split 1:99 → 10 and 990. distribute() throws on the 10;
        // distributeUnchecked() returns both, leaving dust handling to caller.
        val shares =
            OnchainZapSplitter.distributeUnchecked(
                totalSats = 1000L,
                splits = listOf(a to 1.0, b to 99.0),
            )
        assertEquals(2, shares.size)
        assertEquals(10L, shares[0].sats)
        assertEquals(990L, shares[1].sats)
        assertEquals(1000L, shares.sumOf { it.sats })
    }

    @Test
    fun floatingPointWeightsSumExactly() {
        // 0.1 + 0.2 = 0.30000000000000004 in IEEE-754. Make sure that doesn't
        // leak a missing or extra sat.
        val shares =
            OnchainZapSplitter.distribute(
                totalSats = 1_000_000L,
                splits = listOf(a to 0.1, b to 0.2),
                dustThresholdSats = 330L,
            )
        assertEquals(1_000_000L, shares.sumOf { it.sats })
    }
}
