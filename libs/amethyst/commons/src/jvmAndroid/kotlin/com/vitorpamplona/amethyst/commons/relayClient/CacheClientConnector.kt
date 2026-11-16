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
package com.vitorpamplona.amethyst.commons.relayClient

import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.EventCollector
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.RelayInsertConfirmationCollector
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

class CacheClientConnector(
    val client: INostrClient,
    val cache: LocalCache,
) {
    // A relay that is behind gets the deletion or newer version back through the client: the
    // outbox retries it until the relay answers, and the client's relay filters (blocked relays)
    // apply. The cache used to write it on the relay's socket, around both.
    private val sendBack: (Event, NormalizedRelayUrl) -> Unit = { event, relay -> client.publish(event, setOf(relay)) }

    val receiver =
        EventCollector(client) { event, relay ->
            cache.justConsume(event, relay.url, false, sendBack)
        }

    // markAsSeen drills into the gift-wrap chain (wrap → seal → rumor) via
    // LocalCache.addRelayToNoteAndInners, so an OK acceptance for a wrap also
    // tags the inner rumor note the chat UI renders.
    val confirmationWatcher =
        RelayInsertConfirmationCollector(client) { eventId, relay ->
            cache.markAsSeen(eventId, relay.url)
        }

    fun destroy() {
        receiver.destroy()
        confirmationWatcher.destroy()
    }
}
