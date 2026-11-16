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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuzzDmChannelsTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val relayA = RelayUrlNormalizer.normalizeOrNull("wss://a.example.team/")!!
    private val relayB = RelayUrlNormalizer.normalizeOrNull("wss://b.example.team/")!!

    @BeforeTest fun setup() = BuzzDmChannels.clearForTesting()

    @AfterTest fun teardown() = BuzzDmChannels.clearForTesting()

    @Test
    fun recordsAndReadsAChannel() {
        assertTrue(BuzzDmChannels.record(alice, "chan-1", relayA), "a first sighting is new")
        assertEquals(mapOf("chan-1" to relayA), BuzzDmChannels.channelsFor(alice))
    }

    @Test
    fun reRecordingTheSameRelayIsNotNewAndDoesNotChurn() {
        BuzzDmChannels.record(alice, "chan-1", relayA)
        val before = BuzzDmChannels.flow.value
        assertFalse(BuzzDmChannels.record(alice, "chan-1", relayA), "re-confirming the same (channel, relay) is not new")
        assertTrue(before === BuzzDmChannels.flow.value, "the flow instance is unchanged on a no-op")
    }

    @Test
    fun aChannelMovingRelaysIsRecordedAsNew() {
        BuzzDmChannels.record(alice, "chan-1", relayA)
        assertTrue(BuzzDmChannels.record(alice, "chan-1", relayB), "a new relay for a known channel is a change")
        assertEquals(mapOf("chan-1" to relayB), BuzzDmChannels.channelsFor(alice))
    }

    @Test
    fun channelsArePerViewer() {
        BuzzDmChannels.record(alice, "chan-1", relayA)
        assertEquals(mapOf("chan-1" to relayA), BuzzDmChannels.channelsFor(alice))
        assertEquals(emptyMap(), BuzzDmChannels.channelsFor(bob))
    }

    @Test
    fun replaceDeclaresTheWholeSet() {
        BuzzDmChannels.record(alice, "gone", relayA)

        assertTrue(BuzzDmChannels.replace(alice, mapOf("kept" to relayB)))
        assertEquals(mapOf("kept" to relayB), BuzzDmChannels.channelsFor(alice))
    }

    @Test
    fun replaceWithTheSameSetIsANoOpAndDoesNotChurn() {
        // Discovery recomputes from the cache on every pass, so most passes declare a set that is
        // already current. Those must not emit — a churning flow would re-trigger every collector,
        // which is what the incremental record/remove version did on a loop.
        BuzzDmChannels.replace(alice, mapOf("chan-1" to relayA))
        val before = BuzzDmChannels.flow.value

        assertFalse(BuzzDmChannels.replace(alice, mapOf("chan-1" to relayA)))
        assertTrue(before === BuzzDmChannels.flow.value, "the flow instance is unchanged on a no-op")
    }

    @Test
    fun replaceWithAnEmptySetClearsTheViewer() {
        BuzzDmChannels.replace(alice, mapOf("chan-1" to relayA))

        assertTrue(BuzzDmChannels.replace(alice, emptyMap()))
        assertEquals(emptyMap(), BuzzDmChannels.channelsFor(alice))
        assertFalse(BuzzDmChannels.replace(alice, emptyMap()), "clearing an already-empty viewer is a no-op")
    }

    @Test
    fun replaceLeavesOtherViewersAlone() {
        BuzzDmChannels.replace(bob, mapOf("bobs" to relayA))
        BuzzDmChannels.replace(alice, mapOf("alices" to relayB))

        assertEquals(mapOf("bobs" to relayA), BuzzDmChannels.channelsFor(bob))
        assertEquals(mapOf("alices" to relayB), BuzzDmChannels.channelsFor(alice))
    }
}
