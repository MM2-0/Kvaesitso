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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A relay that serves an older version of an addressable event gets the newer one back, once,
 * through the caller's [EventCache.justConsume] `sendBack`. The cache used to write it on the
 * relay's socket itself, around the client's outbox (no retries) and its relay filters (a
 * blocked relay was written to anyway).
 */
class EventCacheSendBackTest {
    private val signer = NostrSignerSync(KeyPair())
    private val relay = NormalizedRelayUrl("wss://behind.example.com/")

    private fun article(createdAt: Long): LongFormContentEvent = signer.sign(createdAt, LongFormContentEvent.KIND, arrayOf(arrayOf("d", "post")), "version $createdAt")

    @Test
    fun aRelayServingAnOlderVersionGetsTheNewerOneBackOnce() {
        val cache = EventCache()
        val newer = article(2_000)
        val older = article(1_000)
        cache.justConsume(newer, null, true)

        val sent = mutableListOf<Pair<Event, NormalizedRelayUrl>>()
        cache.justConsume(older, relay, true) { event, to -> sent.add(event to to) }
        cache.justConsume(older, relay, true) { event, to -> sent.add(event to to) }

        assertEquals(listOf<Pair<Event, NormalizedRelayUrl>>(newer to relay), sent)
    }
}
