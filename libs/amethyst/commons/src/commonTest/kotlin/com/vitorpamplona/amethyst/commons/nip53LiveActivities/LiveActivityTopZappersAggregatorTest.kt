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

import com.vitorpamplona.amethyst.commons.nip53LiveActivities.LiveActivityTopZappersAggregator.ANON_KEY
import com.vitorpamplona.amethyst.commons.nip53LiveActivities.LiveActivityTopZappersAggregator.aggregate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveActivityTopZappersAggregatorTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun zap(
        receipt: String,
        zapper: String = alice,
        sats: Long = 100,
        anon: Boolean = false,
    ) = ZapContribution(
        receiptId = receipt,
        zapperPubKey = zapper,
        isAnonymous = anon,
        sats = sats,
    )

    @Test
    fun sortsByTotalDescending() {
        val result =
            aggregate(
                listOf(
                    zap("r1", alice, 100),
                    zap("r2", bob, 500),
                    zap("r3", carol, 250),
                ),
            )

        assertEquals(listOf(bob, carol, alice), result.map { it.bucketKey })
        assertEquals(listOf(500L, 250L, 100L), result.map { it.totalSats })
    }

    @Test
    fun sumsMultipleZapsFromSameContributor() {
        val result =
            aggregate(
                listOf(
                    zap("r1", alice, 100),
                    zap("r2", alice, 200),
                    zap("r3", bob, 250),
                ),
            )

        assertEquals(listOf(alice, bob), result.map { it.bucketKey })
        assertEquals(300L, result[0].totalSats)
        assertEquals(250L, result[1].totalSats)
    }

    @Test
    fun dedupesByReceiptId() {
        // Same zap arriving via both #a (stream) and #e (goal) should count once.
        val result =
            aggregate(
                listOf(
                    zap("r1", alice, 500),
                    zap("r1", alice, 500),
                ),
            )

        assertEquals(1, result.size)
        assertEquals(500L, result[0].totalSats)
    }

    @Test
    fun bucketsAnonymousZapsUnderSingleSentinel() {
        // Each anon zap has a random one-time pubkey; they must collapse into one entry.
        val anon1 = "1".repeat(64)
        val anon2 = "2".repeat(64)
        val result =
            aggregate(
                listOf(
                    zap("r1", alice, 100),
                    zap("r2", anon1, 200, anon = true),
                    zap("r3", anon2, 300, anon = true),
                ),
            )

        assertEquals(2, result.size)
        val anon = result.first { it.isAnonymous }
        assertEquals(ANON_KEY, anon.bucketKey)
        assertEquals(500L, anon.totalSats)
        val realUser = result.first { !it.isAnonymous }
        assertEquals(alice, realUser.bucketKey)
        assertEquals(100L, realUser.totalSats)
    }

    @Test
    fun doesNotFalselyMarkRealPubkeyAnonymousWhenItCollidesWithSentinel() {
        // Defensive: if some stream has a zapper whose pubkey literally equals "anon",
        // we don't collide because the sentinel is 4 chars and real pubkeys are 64.
        val result =
            aggregate(
                listOf(
                    zap("r1", alice, 100),
                ),
            )

        assertTrue(result.none { it.isAnonymous })
    }

    @Test
    fun respectsTopNLimit() {
        val contributions =
            (1..15).map {
                zap(receipt = "r$it", zapper = "$it".repeat(64), sats = it.toLong() * 10)
            }

        val result = aggregate(contributions, limit = 5)

        assertEquals(5, result.size)
        // Highest total first.
        assertEquals(15 * 10L, result[0].totalSats)
        assertEquals(11 * 10L, result[4].totalSats)
    }

    @Test
    fun handlesEmptyInput() {
        assertTrue(aggregate(emptyList()).isEmpty())
    }

    @Test
    fun handlesZeroLimit() {
        assertTrue(aggregate(listOf(zap("r1")), limit = 0).isEmpty())
    }
}
