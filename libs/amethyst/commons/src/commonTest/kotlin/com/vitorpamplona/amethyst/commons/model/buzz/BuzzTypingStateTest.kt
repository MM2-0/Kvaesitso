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
package com.vitorpamplona.amethyst.commons.model.buzz

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuzzTypingStateTest {
    private val channel = "chan-1"
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)

    @BeforeTest fun setup() = BuzzTypingState.clearForTesting()

    @AfterTest fun teardown() = BuzzTypingState.clearForTesting()

    @Test
    fun recordsAFreshHeartbeat() {
        BuzzTypingState.record(channel, alice, atSecs = 100, nowSecs = 100)
        assertEquals(setOf(alice), BuzzTypingState.flow.value[channel]?.keys)
    }

    @Test
    fun staleOnArrivalIsDropped() {
        // atSecs is older than nowSecs by more than the window → never recorded.
        BuzzTypingState.record(channel, alice, atSecs = 100, nowSecs = 100 + BuzzTypingState.TYPING_STALE_SECS + 1)
        assertNull(BuzzTypingState.flow.value[channel])
    }

    @Test
    fun aNewerHeartbeatWins_andStaleTypistsArePruned() {
        BuzzTypingState.record(channel, alice, atSecs = 100, nowSecs = 100)
        BuzzTypingState.record(channel, bob, atSecs = 101, nowSecs = 101)
        assertEquals(setOf(alice, bob), BuzzTypingState.flow.value[channel]?.keys)

        // A heartbeat far in the future ages both prior ones out of the window; only the
        // fresh one survives, and the newer stamp for bob is what's kept.
        val later = 101 + BuzzTypingState.TYPING_STALE_SECS + 5
        BuzzTypingState.record(channel, bob, atSecs = later, nowSecs = later)
        assertEquals(setOf(bob), BuzzTypingState.flow.value[channel]?.keys)
        assertEquals(later, BuzzTypingState.flow.value[channel]?.get(bob))
    }

    @Test
    fun futureDatedHeartbeatIsClampedToNow() {
        // A peer claiming a wildly-future createdAt must not become un-expirable.
        BuzzTypingState.record(channel, alice, atSecs = 10_000, nowSecs = 100)
        assertEquals(100L, BuzzTypingState.flow.value[channel]?.get(alice))
    }

    @Test
    fun channelsAreIsolated() {
        BuzzTypingState.record("c1", alice, atSecs = 100, nowSecs = 100)
        BuzzTypingState.record("c2", bob, atSecs = 100, nowSecs = 100)
        assertEquals(setOf(alice), BuzzTypingState.flow.value["c1"]?.keys)
        assertEquals(setOf(bob), BuzzTypingState.flow.value["c2"]?.keys)
        assertTrue(BuzzTypingState.flow.value.size == 2)
    }
}
