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
package com.vitorpamplona.amethyst.commons.relayClient.subscriptions

import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PurposeTaggingClientTest {
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")

    private class RecordingClient : INostrClient by EmptyNostrClient() {
        val reqs = mutableListOf<Map<NormalizedRelayUrl, List<Filter>>>()
        val counts = mutableListOf<Map<NormalizedRelayUrl, List<Filter>>>()

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            reqs.add(filters)
        }

        override fun count(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
        ) {
            counts.add(filters)
        }
    }

    @Test
    fun `plain filters reach the pool carrying the purpose`() {
        val inner = RecordingClient()
        inner.taggedAs(SubPurpose.SIGNER).subscribe("sub", mapOf(relay to listOf(Filter(kinds = listOf(24133)))))

        val sent =
            inner.reqs
                .single()
                .getValue(relay)
                .single()
        assertEquals(SubPurpose.SIGNER, sent.purposeOrNull())
        assertEquals(listOf(24133), sent.kinds)
    }

    @Test
    fun `counts are tagged too`() {
        val inner = RecordingClient()
        inner.taggedAs(SubPurpose.WALLET).count("sub", mapOf(relay to listOf(Filter(kinds = listOf(7375)))))

        assertEquals(
            SubPurpose.WALLET,
            inner.counts
                .single()
                .getValue(relay)
                .single()
                .purposeOrNull(),
        )
    }

    @Test
    fun `a filter that already names a purpose keeps it`() {
        val inner = RecordingClient()
        val own = ExplainedFilter(kinds = listOf(1), purpose = SubPurpose.NOTIFICATIONS)
        inner.taggedAs(SubPurpose.SIGNER).subscribe("sub", mapOf(relay to listOf(own)))

        assertSame(
            own,
            inner.reqs
                .single()
                .getValue(relay)
                .single(),
        )
    }
}
