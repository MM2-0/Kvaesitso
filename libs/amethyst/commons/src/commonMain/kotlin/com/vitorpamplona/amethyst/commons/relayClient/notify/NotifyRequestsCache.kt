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
package com.vitorpamplona.amethyst.commons.relayClient.notify

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

@Stable
class NotifyRequestsCache {
    val transientPaymentRequestDismissals: MutableStateFlow<Set<NotifyRequest>> = MutableStateFlow(emptySet())
    val transientPaymentRequests: MutableStateFlow<Set<NotifyRequest>> = MutableStateFlow(emptySet())

    fun addPaymentRequestIfNew(
        description: String,
        relayUrl: NormalizedRelayUrl,
    ) {
        addPaymentRequestIfNew(NotifyRequest(relayUrl, description))
    }

    fun addPaymentRequestIfNew(paymentRequest: NotifyRequest) {
        if (this.transientPaymentRequestDismissals.value.contains(paymentRequest)) return

        // `update` rather than `value +=`: NOTIFYs are filed from the relay's socket coroutine
        // while dismissals run from the UI, and a plain read-modify-write silently drops one of
        // two concurrent edits — either losing a prompt or resurrecting a dismissed one.
        this.transientPaymentRequests.update { if (paymentRequest in it) it else it + paymentRequest }
    }

    fun dismissPaymentRequest(request: NotifyRequest) {
        if (this.transientPaymentRequests.value.contains(request)) {
            this.transientPaymentRequests.update { it - request }
            this.transientPaymentRequestDismissals.update { it + request }
        }
    }

    /**
     * Drops every pending prompt from [relayUrl] at once.
     *
     * Used when the user blocks the relay: a relay that asks for payment usually queues one NOTIFY
     * per rejected AUTH, so dismissing them one at a time would keep re-showing the dialog for a
     * relay the user just told us never to talk to again.
     */
    fun dismissAllFrom(relayUrl: NormalizedRelayUrl) {
        // getAndUpdate so the drain and the snapshot of what was drained are one atomic step: a
        // NOTIFY filed by the socket coroutine between a separate read and write would otherwise
        // be dropped from the pending set without ever being recorded as dismissed.
        val before = this.transientPaymentRequests.getAndUpdate { pending -> pending.filterNotTo(mutableSetOf()) { it.relayUrl == relayUrl } }

        val dismissed = before.filterTo(mutableSetOf()) { it.relayUrl == relayUrl }
        if (dismissed.isEmpty()) return

        this.transientPaymentRequestDismissals.update { it + dismissed }
    }
}
