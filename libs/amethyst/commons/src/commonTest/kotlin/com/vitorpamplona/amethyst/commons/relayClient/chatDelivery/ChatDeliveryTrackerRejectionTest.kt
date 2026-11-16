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
package com.vitorpamplona.amethyst.commons.relayClient.chatDelivery

import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A relay's refusal (OK false) must surface with its reason, not as a message still pending. */
class ChatDeliveryTrackerRejectionTest {
    private val noteId = "a".repeat(64)
    private val relayA = RelayUrlNormalizer.normalizeOrNull("wss://a.example.com")!!
    private val relayB = RelayUrlNormalizer.normalizeOrNull("wss://b.example.com")!!

    @Test
    fun everyTargetRefusing_isRejectedWithTheReason() {
        val tracker = ChatDeliveryTracker(EmptyNostrClient())
        tracker.trackPublic(noteId, setOf(relayA))

        tracker.onRejected(noteId, relayA, "invalid: imeta url must be a local /media/ path")

        val delivery = tracker.currentFor(noteId)!!
        assertTrue(delivery.isRejected)
        assertEquals("invalid: imeta url must be a local /media/ path", delivery.rejectedRelays[relayA])
    }

    @Test
    fun oneRelayStillOpen_isNotRejectedYet() {
        val tracker = ChatDeliveryTracker(EmptyNostrClient())
        tracker.trackPublic(noteId, setOf(relayA, relayB))

        tracker.onRejected(noteId, relayA, "blocked: no")

        assertFalse(tracker.currentFor(noteId)!!.isRejected)
    }

    @Test
    fun aLaterAcceptanceClearsTheRefusal() {
        val tracker = ChatDeliveryTracker(EmptyNostrClient())
        tracker.trackPublic(noteId, setOf(relayA))

        tracker.onRejected(noteId, relayA, "rate-limited: slow down")
        tracker.onAccepted(noteId, relayA)

        val delivery = tracker.currentFor(noteId)!!
        assertFalse(delivery.isRejected)
        assertTrue(delivery.rejectedRelays.isEmpty())
    }

    @Test
    fun aRefusalAfterAcceptanceKeepsTheTick() {
        val tracker = ChatDeliveryTracker(EmptyNostrClient())
        tracker.trackPublic(noteId, setOf(relayA))

        tracker.onAccepted(noteId, relayA)
        tracker.onRejected(noteId, relayA, "duplicate: already have it")

        assertTrue(tracker.currentFor(noteId)!!.rejectedRelays.isEmpty())
    }
}
