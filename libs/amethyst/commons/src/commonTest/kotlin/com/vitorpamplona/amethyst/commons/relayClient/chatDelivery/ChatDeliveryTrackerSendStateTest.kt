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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The send lifecycle a chat bubble is drawn from: shown optimistically while
 * the message is still being encrypted/wrapped, then either handed to the
 * relay pool or left failed with a retry attached.
 */
class ChatDeliveryTrackerSendStateTest {
    private val noteId = "a".repeat(64)
    private val wrapId = "b".repeat(64)
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://relay.example.com")!!

    private fun tracker() = ChatDeliveryTracker(EmptyNostrClient())

    @Test
    fun markSending_showsAsSendingBeforeAnythingIsPublished() {
        val tracker = tracker()
        tracker.markSending(noteId) { }

        val delivery = tracker.currentFor(noteId)
        assertNotNull(delivery)
        assertEquals(ChatSendState.SENDING, delivery.sendState)
        assertTrue(delivery.targetRelays.isEmpty())
        // Nothing has been published, so nothing may read as delivered.
        assertFalse(delivery.isFullyAccepted)
    }

    @Test
    fun trackWrappedPublic_keepsTheSendingStateUntilMarkSent() {
        val tracker = tracker()
        tracker.markSending(noteId) { }

        // Registering the relay targets is not the same as having sent: the
        // envelope may still be mid-build when this lands.
        tracker.trackWrappedPublic(noteId, wrapId, setOf(relay))
        assertEquals(ChatSendState.SENDING, tracker.currentFor(noteId)?.sendState)
        assertEquals(setOf(relay), tracker.currentFor(noteId)?.targetRelays)

        tracker.markSent(noteId)
        assertEquals(ChatSendState.SENT, tracker.currentFor(noteId)?.sendState)
    }

    @Test
    fun aSendThatNeverAnnouncesItselfStaysSent() {
        // Every pre-existing caller registers targets without a sending phase;
        // those must keep reading as sent rather than becoming stuck.
        val tracker = tracker()
        tracker.trackPublic(noteId, setOf(relay))

        assertEquals(ChatSendState.SENT, tracker.currentFor(noteId)?.sendState)
    }

    @Test
    fun markFailed_keepsTheRetryRegisteredBySending() =
        runTest {
            val tracker = tracker()
            var retries = 0
            tracker.markSending(noteId) { retries += 1 }
            tracker.markFailed(noteId)

            assertEquals(ChatSendState.FAILED, tracker.currentFor(noteId)?.sendState)

            val retry = tracker.retryFor(noteId)
            assertNotNull(retry)
            retry()
            assertEquals(1, retries)
        }

    @Test
    fun retryFor_isNullForAMessageWeNeverTracked() {
        assertNull(tracker().retryFor(noteId))
    }

    @Test
    fun aFullyAcceptedRoomMessageIsOnlyDeliveredOnceSent() {
        val tracker = tracker()
        tracker.markSending(noteId) { }
        tracker.trackWrappedPublic(noteId, wrapId, setOf(relay))

        // Same relay set on both sides, so the only thing standing between this
        // and "delivered" is the send state itself.
        val sending = tracker.currentFor(noteId)
        assertNotNull(sending)
        assertEquals(sending.targetRelays, sending.targetRelays.intersect(setOf(relay)))
        assertFalse(sending.isFullyAccepted)
    }

    @Test
    fun destroy_dropsTheRetries() {
        val tracker = tracker()
        tracker.markSending(noteId) { }
        tracker.destroy()

        assertNull(tracker.retryFor(noteId))
    }
}
