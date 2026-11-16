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

import com.vitorpamplona.quartz.nip53LiveActivities.presence.MeetingRoomPresenceEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomPresenceStateTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val roomA = "30312:${"c".repeat(64)}:my-room"

    private fun event(
        pubkey: String,
        createdAt: Long,
        handRaised: Boolean? = null,
        muted: Boolean? = null,
        publishing: Boolean? = null,
        onstage: Boolean? = null,
    ): MeetingRoomPresenceEvent {
        val tags =
            buildList<Array<String>> {
                add(arrayOf("a", roomA))
                handRaised?.let { add(arrayOf("hand", if (it) "1" else "0")) }
                muted?.let { add(arrayOf("muted", if (it) "1" else "0")) }
                publishing?.let { add(arrayOf("publishing", if (it) "1" else "0")) }
                onstage?.let { add(arrayOf("onstage", if (it) "1" else "0")) }
            }.toTypedArray()
        return MeetingRoomPresenceEvent(
            id = "0".repeat(64),
            pubKey = pubkey,
            createdAt = createdAt,
            tags = tags,
            content = "",
            sig = "0".repeat(128),
        )
    }

    @Test
    fun fromEventProjectsAllTags() {
        val rp = RoomPresence.from(event(alice, 100L, handRaised = true, muted = false, publishing = true, onstage = true))
        assertEquals(alice, rp.pubkey)
        assertTrue(rp.handRaised)
        assertEquals(false, rp.muted)
        assertTrue(rp.publishing)
        assertTrue(rp.onstage)
        assertEquals(100L, rp.updatedAtSec)
    }

    @Test
    fun fromEventDefaultsAbsentTagsConservatively() {
        val rp = RoomPresence.from(event(alice, 100L))
        assertFalse(rp.handRaised)
        assertNull(rp.muted)
        assertFalse(rp.publishing)
        // onstage defaults to TRUE for backwards compatibility with
        // pre-onstage clients — they all emit kind 10312 implicitly
        // as speakers.
        assertTrue(rp.onstage)
    }

    @Test
    fun aggregatorDedupesByPubkeyKeepingLatestCreatedAt() {
        val agg = RoomPresenceAggregator()
        agg.apply(event(alice, 100L, handRaised = false))
        agg.apply(event(alice, 200L, handRaised = true))
        // Out-of-order arrival: older event must NOT overwrite newer.
        val snapshot = agg.apply(event(alice, 150L, handRaised = false))

        assertEquals(1, snapshot.size)
        assertTrue(snapshot[alice]!!.handRaised)
        assertEquals(200L, snapshot[alice]!!.updatedAtSec)
    }

    @Test
    fun aggregatorTracksMultiplePubkeysIndependently() {
        val agg = RoomPresenceAggregator()
        agg.apply(event(alice, 100L, publishing = true))
        val snapshot = agg.apply(event(bob, 100L, publishing = false))

        assertEquals(2, snapshot.size)
        assertTrue(snapshot[alice]!!.publishing)
        assertFalse(snapshot[bob]!!.publishing)
    }

    @Test
    fun evictOlderThanDropsStalePeersAndKeepsFresh() {
        val agg = RoomPresenceAggregator()
        agg.apply(event(alice, 100L)) // stale
        agg.apply(event(bob, 500L)) // fresh

        val snapshot = agg.evictOlderThan(olderThanSec = 400L)
        assertEquals(setOf(bob), snapshot.keys)
    }

    @Test
    fun roomPresenceEqualityIsAllFields() {
        // Two snapshots of the same peer at different timestamps are
        // NOT equal — Map<String, RoomPresence>.equals(Map) reaches
        // value.equals(value); a pubkey-only equals would suppress
        // StateFlow emissions on heartbeat-only updates because
        // old.equals(new) would return true.
        val a1 = RoomPresence.from(event(alice, 100L, handRaised = false))
        val a2 = RoomPresence.from(event(alice, 200L, handRaised = true))
        assertEquals(false, a1 == a2)
    }
}
