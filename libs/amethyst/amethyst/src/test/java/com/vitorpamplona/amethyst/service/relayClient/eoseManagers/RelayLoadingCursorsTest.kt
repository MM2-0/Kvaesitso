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
package com.vitorpamplona.amethyst.service.relayClient.eoseManagers

import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayLoadingCursors
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayLoadingCursorsTest {
    private val relayA = RelayUrlNormalizer.normalizeOrNull("wss://a.relay")!!
    private val relayB = RelayUrlNormalizer.normalizeOrNull("wss://b.relay")!!
    private val start = 1_000L

    @Test
    fun unarmedRelayIsNotRequestedAndSitsAtTheFloor() {
        val cursors = RelayLoadingCursors()
        // never advanced, so it carries no REQ
        assertEquals(emptyList<Any>(), cursors.armedRelays(listOf(relayA)))
        // marker sits at the floor until it delivers
        assertEquals(start, cursors.reachedUntilFor(relayA, start))
    }

    @Test
    fun firstAdvanceRequestsTheFloorThenSubsequentPagesStepBelowReached() {
        val cursors = RelayLoadingCursors()

        assertTrue(cursors.advance(relayA, start))
        assertEquals(start, cursors.requestedUntilFor(relayA))

        // page returns events; oldest seen = 800
        cursors.onEvent(relayA, 900)
        cursors.onEvent(relayA, 800)
        cursors.onEose(relayA)
        assertEquals(800L, cursors.reachedUntilFor(relayA, start))
        // EOSE does NOT move the requested cursor — the relay parks at the same filter
        assertEquals(start, cursors.requestedUntilFor(relayA))

        // next advance steps to reached - 1
        assertTrue(cursors.advance(relayA, start))
        assertEquals(799L, cursors.requestedUntilFor(relayA))
    }

    @Test
    fun emptyPageMarksRelayDoneAndBlocksFurtherAdvance() {
        val cursors = RelayLoadingCursors()
        cursors.advance(relayA, start)
        cursors.onEose(relayA) // no events
        assertTrue(cursors.isDone(relayA))
        assertFalse(cursors.advance(relayA, start))
        assertEquals(emptyList<Any>(), cursors.armedRelays(listOf(relayA)))
    }

    @Test
    fun aPageThatDoesNotStepOlderEndsTheRelayInsteadOfLooping() {
        val cursors = RelayLoadingCursors()
        cursors.advance(relayA, start)
        cursors.onEvent(relayA, 800)
        cursors.onEose(relayA)
        assertEquals(800L, cursors.reachedUntilFor(relayA, start))

        // misbehaving relay: next page echoes an event no older than what we already reached
        cursors.advance(relayA, start) // requested = 799
        cursors.onEvent(relayA, 900) // newer than reached(800) — not strictly older
        cursors.onEose(relayA)
        assertTrue("a non-advancing page should end the relay, not re-loop", cursors.isDone(relayA))
        assertEquals(800L, cursors.reachedUntilFor(relayA, start))
    }

    @Test
    fun relaysAreTrackedIndependently() {
        val cursors = RelayLoadingCursors()
        cursors.advance(relayA, start)
        cursors.onEvent(relayA, 500)
        cursors.onEose(relayA)
        // B never advanced
        assertEquals(listOf(relayA), cursors.armedRelays(listOf(relayA, relayB)))
        assertEquals(500L, cursors.reachedUntilFor(relayA, start))
        assertEquals(start, cursors.reachedUntilFor(relayB, start))
        // deepest reached across both = A's 500 (B counts as the floor)
        assertEquals(500L, cursors.deepestReached(listOf(relayA, relayB), start))
    }

    @Test
    fun deepestReachedIsNullWhenNoRelays() {
        val cursors = RelayLoadingCursors()
        assertEquals(null, cursors.deepestReached(emptyList(), start))
    }

    // ── rewindTo: realign the window after the cache prunes messages out of it ──

    @Test
    fun rewindReopensThePrunedBandAndResumesFromItOnNextAdvance() {
        val cursors = RelayLoadingCursors()
        cursors.floor = start

        // page deep: floor 1000 → reached 200
        cursors.advance(relayA, start)
        cursors.onEvent(relayA, 900)
        cursors.onEvent(relayA, 200)
        cursors.onEose(relayA)
        assertEquals(200L, cursors.reachedUntilFor(relayA, start))

        // prune drops everything older than 700 (newest pruned = 700)
        cursors.rewindTo(mapOf(relayA to 700L))

        // reached pulled up to just above the pruned band, not done, and un-armed (demand-driven)
        assertEquals(701L, cursors.reachedUntilFor(relayA, start))
        assertFalse(cursors.isDone(relayA))
        assertEquals(emptyList<Any>(), cursors.armedRelays(listOf(relayA)))

        // the next advance resumes at the boundary and re-requests the pruned band (until = 700),
        // NOT from the floor (which would re-stream the still-held tail above 700)
        assertTrue(cursors.advance(relayA, start))
        assertEquals(700L, cursors.requestedUntilFor(relayA))
    }

    @Test
    fun rewindClearsDoneSoAnExhaustedRelayCanReFetch() {
        val cursors = RelayLoadingCursors()
        cursors.floor = start

        cursors.advance(relayA, start)
        cursors.onEvent(relayA, 300)
        cursors.onEose(relayA) // reached 300
        cursors.advance(relayA, start)
        cursors.onEose(relayA) // empty page → done
        assertTrue(cursors.isDone(relayA))

        cursors.rewindTo(mapOf(relayA to 500L))

        assertFalse("a pruned relay must be re-fetchable even after it reached the bottom", cursors.isDone(relayA))
        assertEquals(501L, cursors.reachedUntilFor(relayA, start))
        assertTrue(cursors.advance(relayA, start))
        assertEquals(500L, cursors.requestedUntilFor(relayA))
    }

    @Test
    fun rewindNeverClimbsAboveTheFloor() {
        val cursors = RelayLoadingCursors()
        cursors.floor = start

        cursors.advance(relayA, start)
        cursors.onEvent(relayA, 300)
        cursors.onEose(relayA) // reached 300

        // a boundary at/above the floor clamps to the floor (history lives strictly below it)
        cursors.rewindTo(mapOf(relayA to start))
        assertEquals(start, cursors.reachedUntilFor(relayA, start))
    }

    @Test
    fun rewindSkipsRelaysWithoutACursorOrShallowerThanThePrunedBand() {
        val cursors = RelayLoadingCursors()
        cursors.floor = start

        // A delivered to 200; B never paged
        cursors.advance(relayA, start)
        cursors.onEvent(relayA, 200)
        cursors.onEose(relayA)

        // B has no cursor (never paged) → skipped, no entry minted; A's reach (200) is already shallower
        // than a boundary of 150 (target 151), so it needs no rewind either.
        cursors.rewindTo(mapOf(relayB to 500L, relayA to 150L))

        // B: untouched (still at the floor, unarmed)
        assertEquals(start, cursors.reachedUntilFor(relayB, start))
        assertEquals(emptyList<Any>(), cursors.armedRelays(listOf(relayB)))
        // A: unchanged
        assertEquals(200L, cursors.reachedUntilFor(relayA, start))
    }

    @Test
    fun rewindIsANoOpWhenTheWindowNeverPagedHistory() {
        val cursors = RelayLoadingCursors()
        // floor is null (never advanced any history page)
        cursors.advance(relayA, start)
        cursors.onEvent(relayA, 200)
        cursors.onEose(relayA)

        cursors.rewindTo(mapOf(relayA to 150L))

        // unchanged: with no pinned floor there is no history window to realign
        assertEquals(200L, cursors.reachedUntilFor(relayA, start))
    }
}
