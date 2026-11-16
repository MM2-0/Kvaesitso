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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.quartz.nip57Zaps.ZapReceiptEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomZapsStateTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val charlie = "c".repeat(64)

    private var nextEventId = 1

    /**
     * Builds a kind-9735 zap receipt with no embedded `description`
     * request, so [RoomZap.from] falls back to the receipt's own
     * `pubKey` as the sender — i.e. [from] is the grouping key. A
     * `p` tag for [to] is attached when non-null (informational
     * `targetPubkey`); no `bolt11` tag, so the amount resolves to
     * null. The aggregator's grouping / eviction / dedup logic does
     * not depend on the amount, which keeps the fixtures invoice-free.
     */
    private fun zap(
        from: String,
        to: String?,
        createdAt: Long,
        id: String = (nextEventId++).toString(16).padStart(64, '0'),
    ): ZapReceiptEvent {
        val tags =
            buildList<Array<String>> {
                add(arrayOf("a", "30312:host:room"))
                if (to != null) add(arrayOf("p", to))
            }.toTypedArray()
        return ZapReceiptEvent(
            id = id,
            pubKey = from,
            createdAt = createdAt,
            tags = tags,
            content = "",
            sig = "0".repeat(128),
        )
    }

    @Test
    fun fromEventUsesSenderAndTarget() {
        val zap = RoomZap.from(zap(alice, bob, 100L))
        assertEquals(alice, zap.sourcePubkey)
        assertEquals(bob, zap.targetPubkey)
        assertNull(zap.amountSats)
        assertEquals(100L, zap.createdAtSec)
    }

    @Test
    fun fromEventNullTargetWhenNoPTag() {
        val zap = RoomZap.from(zap(alice, null, 100L))
        assertNull(zap.targetPubkey)
        // Source still resolves even without a `p` tag — the zap
        // floats from the zapper's avatar regardless of target.
        assertEquals(alice, zap.sourcePubkey)
    }

    @Test
    fun aggregatorGroupsBySender() {
        val agg = RoomZapsAggregator()
        // Two zaps from alice, aimed at different participants — both
        // float from alice's avatar, so both land under alice's key.
        agg.apply(zap(alice, bob, 100L), nowSec = 100L, windowSec = 30L)
        val snap = agg.apply(zap(alice, charlie, 100L), nowSec = 100L, windowSec = 30L)

        assertEquals(setOf(alice), snap.keys)
        assertEquals(2, snap[alice]!!.size)
    }

    @Test
    fun aggregatorEvictsOlderThanWindow() {
        val agg = RoomZapsAggregator()
        // Old zap (T=70) — outside the window when now=110, windowSec=30.
        agg.apply(zap(alice, bob, 70L), nowSec = 70L, windowSec = 30L)
        // Fresh zap (T=105) — inside the window.
        val snap = agg.apply(zap(bob, charlie, 105L), nowSec = 110L, windowSec = 30L)

        // alice's stale zap is gone; only bob's fresh one remains.
        assertNull(snap[alice])
        assertEquals(1, snap[bob]!!.size)
        assertEquals(105L, snap[bob]!![0].createdAtSec)
    }

    @Test
    fun evictAndSnapshotIsIdempotentWhenNothingChanged() {
        val agg = RoomZapsAggregator()
        agg.apply(zap(alice, bob, 100L), nowSec = 100L, windowSec = 30L)
        val a = agg.evictAndSnapshot(olderThanSec = 90L)
        val b = agg.evictAndSnapshot(olderThanSec = 90L)
        // Same input → same output, by VALUE (data class equality on
        // RoomZap so the StateFlow doesn't re-emit on no-op ticks).
        assertEquals(a, b)
    }

    @Test
    fun aggregatorDedupsRepeatedEventIds() {
        val agg = RoomZapsAggregator()
        // LocalCache.observeNotes re-emits the full matching list on
        // every cache mutation; the same receipt must collapse into one
        // overlay entry instead of stacking on each replay.
        val sharedId = "f".repeat(64)
        val first = zap(alice, bob, 100L, id = sharedId)
        val replay = zap(alice, bob, 100L, id = sharedId)
        agg.apply(first, nowSec = 100L, windowSec = 30L)
        agg.apply(replay, nowSec = 100L, windowSec = 30L)
        val snap = agg.apply(replay, nowSec = 100L, windowSec = 30L)

        assertEquals(1, snap[alice]!!.size)
    }
}
