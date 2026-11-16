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
package com.vitorpamplona.amethyst.service.relayClient.notifyCommand.model

import com.vitorpamplona.amethyst.commons.relayClient.notify.NotifyRequest
import com.vitorpamplona.amethyst.commons.relayClient.notify.NotifyRequestsCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyRequestsCacheTest {
    private val paid = NormalizedRelayUrl("wss://paid.relay/")
    private val other = NormalizedRelayUrl("wss://other.relay/")

    @Test
    fun blockingARelayDropsEveryPendingPromptFromIt() {
        val cache = NotifyRequestsCache()
        cache.addPaymentRequestIfNew("Pay up", paid)
        cache.addPaymentRequestIfNew("Still unpaid", paid)
        cache.addPaymentRequestIfNew("Unrelated", other)

        cache.dismissAllFrom(paid)

        assertEquals(
            setOf(NotifyRequest(other, "Unrelated")),
            cache.transientPaymentRequests.value,
        )
    }

    @Test
    fun aDismissedPromptStaysDismissedWhenTheRelayRepeatsIt() {
        val cache = NotifyRequestsCache()
        cache.addPaymentRequestIfNew("Pay up", paid)

        cache.dismissAllFrom(paid)
        cache.addPaymentRequestIfNew("Pay up", paid)

        assertTrue(cache.transientPaymentRequests.value.isEmpty())
    }

    @Test
    fun aConcurrentPromptFromTheSameRelayIsNotSilentlySwallowed() {
        val cache = NotifyRequestsCache()
        cache.addPaymentRequestIfNew("Pay up", paid)

        // Stands in for a NOTIFY landing on the socket coroutine while the UI drains the relay:
        // whatever survives the drain must still be reachable, never removed-but-unrecorded.
        cache.dismissAllFrom(paid)
        cache.addPaymentRequestIfNew("A different demand", paid)

        val pending = cache.transientPaymentRequests.value
        val dismissed = cache.transientPaymentRequestDismissals.value

        assertEquals(setOf(NotifyRequest(paid, "Pay up")), dismissed)
        assertEquals(setOf(NotifyRequest(paid, "A different demand")), pending)
    }

    @Test
    fun dismissingARelayWithNoPromptsChangesNothing() {
        val cache = NotifyRequestsCache()
        cache.addPaymentRequestIfNew("Unrelated", other)

        cache.dismissAllFrom(paid)

        assertEquals(
            setOf(NotifyRequest(other, "Unrelated")),
            cache.transientPaymentRequests.value,
        )
        assertTrue(cache.transientPaymentRequestDismissals.value.isEmpty())
    }
}
