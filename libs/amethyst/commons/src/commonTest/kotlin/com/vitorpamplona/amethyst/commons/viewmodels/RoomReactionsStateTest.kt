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

import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomReactionsStateTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val charlie = "c".repeat(64)

    private var nextEventId = 1

    private fun reaction(
        from: String,
        to: String?,
        content: String,
        createdAt: Long,
        id: String = (nextEventId++).toString(16).padStart(64, '0'),
    ): ReactionEvent {
        val tags =
            buildList<Array<String>> {
                add(arrayOf("a", "30312:host:room"))
                if (to != null) add(arrayOf("p", to))
            }.toTypedArray()
        return ReactionEvent(
            id = id,
            pubKey = from,
            createdAt = createdAt,
            tags = tags,
            content = content,
            sig = "0".repeat(128),
        )
    }

    @Test
    fun fromEventGroupsByTargetPubkey() {
        val rxn = RoomReaction.from(reaction(alice, bob, "🔥", 100L))
        assertEquals(alice, rxn.sourcePubkey)
        assertEquals(bob, rxn.targetPubkey)
        assertEquals("🔥", rxn.content)
        assertEquals(100L, rxn.createdAtSec)
    }

    @Test
    fun fromEventNullTargetWhenNoPTag() {
        val rxn = RoomReaction.from(reaction(alice, null, "🎉", 100L))
        assertNull(rxn.targetPubkey)
    }

    @Test
    fun aggregatorGroupsBySender() {
        // Two reactions sent BY alice and charlie, both targeting bob.
        // Group by sender so the floating chip rises from the
        // reactor's avatar rather than the target speaker's.
        val agg = RoomReactionsAggregator()
        agg.apply(reaction(alice, bob, "🔥", 100L), nowSec = 100L, windowSec = 30L)
        val snap = agg.apply(reaction(charlie, bob, "👏", 100L), nowSec = 100L, windowSec = 30L)

        assertEquals(setOf(alice, charlie), snap.keys)
        assertEquals(1, snap[alice]!!.size)
        assertEquals("🔥", snap[alice]!![0].content)
        assertEquals(1, snap[charlie]!!.size)
        assertEquals("👏", snap[charlie]!![0].content)
    }

    @Test
    fun aggregatorEvictsOlderThanWindow() {
        val agg = RoomReactionsAggregator()
        // Old reaction (T=70) — outside the window when now=110, windowSec=30.
        agg.apply(reaction(alice, bob, "🔥", 70L), nowSec = 70L, windowSec = 30L)
        // Fresh reaction (T=105) — inside the window.
        val snap = agg.apply(reaction(charlie, bob, "👏", 105L), nowSec = 110L, windowSec = 30L)

        // alice's reaction is evicted, charlie's stays.
        assertNull(snap[alice])
        assertEquals(1, snap[charlie]!!.size)
        assertEquals("👏", snap[charlie]!![0].content)
    }

    @Test
    fun aggregatorRoomWideReactionsKeyedBySender() {
        val agg = RoomReactionsAggregator()
        val snap = agg.apply(reaction(alice, null, "🎉", 100L), nowSec = 100L, windowSec = 30L)

        // A reaction with no `p`-tag (room-wide) still groups under
        // the sender's key, so it floats from the reactor's avatar
        // exactly the same way a speaker-targeted reaction does.
        assertEquals(setOf(alice), snap.keys)
        assertEquals(1, snap[alice]!!.size)
    }

    @Test
    fun evictAndSnapshotIsIdempotentWhenNothingChanged() {
        val agg = RoomReactionsAggregator()
        agg.apply(reaction(alice, bob, "🔥", 100L), nowSec = 100L, windowSec = 30L)
        val a = agg.evictAndSnapshot(olderThanSec = 90L)
        val b = agg.evictAndSnapshot(olderThanSec = 90L)
        // Same input → same output, by VALUE (data class equality on
        // RoomReaction so the StateFlow doesn't re-emit on no-op ticks).
        assertEquals(a, b)
    }

    @Test
    fun aggregatorDedupsRepeatedEventIds() {
        val agg = RoomReactionsAggregator()
        // LocalCache.observeEvents re-emits the full matching list on
        // every cache mutation; the same kind-7 must collapse into one
        // overlay entry instead of stacking on each replay.
        val sharedId = "f".repeat(64)
        val first = reaction(alice, bob, "🔥", 100L, id = sharedId)
        val replay = reaction(alice, bob, "🔥", 100L, id = sharedId)
        agg.apply(first, nowSec = 100L, windowSec = 30L)
        agg.apply(replay, nowSec = 100L, windowSec = 30L)
        val snap = agg.apply(replay, nowSec = 100L, windowSec = 30L)

        // Sender-grouped: only one entry, under the sender (alice).
        assertEquals(1, snap[alice]!!.size)
    }
}
