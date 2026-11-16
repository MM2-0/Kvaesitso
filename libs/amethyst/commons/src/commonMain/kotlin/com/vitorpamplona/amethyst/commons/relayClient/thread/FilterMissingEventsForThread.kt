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
package com.vitorpamplona.amethyst.commons.relayClient.thread

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.ThreadAssembler
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.filterMissingAddressables
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.filterMissingEvents
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.potentialRelaysToFindAddress
import com.vitorpamplona.amethyst.commons.relayClient.event.loaders.potentialRelaysToFindEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

fun filterMissingEventsForThread(
    cache: ICacheProvider,
    threadInfo: ThreadAssembler.ThreadInfo,
    defaultRelays: Set<NormalizedRelayUrl>,
): List<RelayBasedFilter> {
    val missingEvents =
        mapOfSet {
            if (threadInfo.root.event == null && threadInfo.root !is AddressableNote) {
                potentialRelaysToFindEvent(cache, threadInfo.root).ifEmpty { defaultRelays }.forEach { relayUrl ->
                    add(relayUrl, threadInfo.root.idHex)
                }
            }

            threadInfo.allNotes.forEach {
                if (it !is AddressableNote && it.event == null) {
                    potentialRelaysToFindEvent(cache, it).ifEmpty { defaultRelays }.forEach { relayUrl ->
                        add(relayUrl, it.idHex)
                    }
                }
            }
        }

    val missingAddresses =
        mapOfSet {
            val rootNote = threadInfo.root
            if (rootNote.event == null && rootNote is AddressableNote) {
                // Must be the address-based resolver: the event-based one feeds the
                // note's aTag idHex into the hex-keyed event-hint index, which throws
                // on the non-hex string and kills the whole filter build — leaving a
                // thread opened on an uncached naddr permanently unfetched.
                potentialRelaysToFindAddress(cache, rootNote).ifEmpty { defaultRelays }.forEach { relayUrl ->
                    add(relayUrl, rootNote.address)
                }
            }

            threadInfo.allNotes.forEach {
                if (it is AddressableNote && it.event == null) {
                    potentialRelaysToFindAddress(cache, it).ifEmpty { defaultRelays }.forEach { relayUrl ->
                        add(relayUrl, it.address)
                    }
                }
            }
        }

    val missingEventsFilter = filterMissingEvents(missingEvents)
    val missingAddressFilter = filterMissingAddressables(missingAddresses)

    return missingEventsFilter + missingAddressFilter
}
