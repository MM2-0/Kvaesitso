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
package com.vitorpamplona.amethyst.commons.relayClient.search

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.filterMissingEvents
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.potentialRelaysToFindEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

fun filterByEvent(
    cache: ICacheProvider,
    eventId: HexKey,
    default: Set<NormalizedRelayUrl>,
): List<RelayBasedFilter> {
    val note = cache.checkGetOrCreateNote(eventId) ?: return emptyList()

    val list =
        mapOfSet {
            if (note !is AddressableNote && note.event == null) {
                potentialRelaysToFindEvent(cache, note).ifEmpty { default }.forEach { relayUrl ->
                    add(relayUrl, note.idHex)
                }
            }

            // loads threading that is event-based
            note.replyTo?.forEach { parentNote ->
                if (parentNote !is AddressableNote && parentNote.event == null) {
                    potentialRelaysToFindEvent(cache, parentNote).ifEmpty { default }.forEach { relayUrl ->
                        add(relayUrl, parentNote.idHex)
                    }
                }
            }
        }

    return filterMissingEvents(list)
}
