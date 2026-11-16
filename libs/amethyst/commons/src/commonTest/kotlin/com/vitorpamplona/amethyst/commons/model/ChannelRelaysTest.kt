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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Lock in the descending-by-usage ordering and equal-count preservation of
 * [Channel.relays]. Phase 2 KMP migration replaced the JVM `toSortedSet`
 * (TreeSet) implementation — which silently dropped duplicates whose
 * comparator returned 0 — with a LinkedHashSet built from a pre-sorted list.
 * That keeps every distinct relay even when usage counts tie.
 */
class ChannelRelaysTest {
    /** Minimal concrete subclass — the base [Channel] is abstract. */
    private class TestChannel : Channel() {
        override fun toBestDisplayName(): String = "test"
    }

    private val relayA = NormalizedRelayUrl("wss://a.example/")
    private val relayB = NormalizedRelayUrl("wss://b.example/")
    private val relayC = NormalizedRelayUrl("wss://c.example/")

    @Test
    fun emptyWhenNoRelaysAdded() {
        assertEquals(emptySet<NormalizedRelayUrl>(), TestChannel().relays())
    }

    @Test
    fun singleRelayIsReturned() {
        val channel = TestChannel()
        channel.addRelay(relayA)
        assertEquals(setOf(relayA), channel.relays())
    }

    @Test
    fun relaysAreOrderedDescendingByUsageCount() {
        val channel = TestChannel()
        // C will end up with the highest count.
        channel.addRelay(relayA) // A: 1
        channel.addRelay(relayB) // B: 1
        channel.addRelay(relayB) // B: 2
        channel.addRelay(relayC) // C: 1
        channel.addRelay(relayC) // C: 2
        channel.addRelay(relayC) // C: 3

        // LinkedHashSet preserves insertion order, which is the
        // descending-by-count order produced by sortedByDescending.
        assertEquals(listOf(relayC, relayB, relayA), channel.relays().toList())
    }

    /**
     * Behavioural change from the JVM-only implementation: the previous
     * `toSortedSet` returned a [java.util.TreeSet] whose membership uses the
     * comparator for equality. Two relays with equal counts would be treated
     * as duplicates by the TreeSet and one would be silently dropped. The
     * KMP-friendly replacement uses [LinkedHashSet], whose membership uses
     * `equals`, so every distinct relay is preserved regardless of count.
     */
    @Test
    fun relaysWithEqualUsageCountsAreAllPreserved() {
        val channel = TestChannel()
        channel.addRelay(relayA) // A: 1
        channel.addRelay(relayB) // B: 1
        channel.addRelay(relayC) // C: 1

        // All three relays share count 1. The old behaviour would return
        // exactly one of them; the new behaviour returns all three.
        assertEquals(setOf(relayA, relayB, relayC), channel.relays())
        assertEquals(3, channel.relays().size)
    }
}
