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
package com.vitorpamplona.amethyst.commons.model.topNavFeeds

import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relays.dialableWriteRelays
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.mapOfSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class OutboxRelayLoader(
    val rawOutboxRelays: Boolean = false,
) {
    fun authorsPerRelay(
        outboxRelayNotes: Array<NoteState>,
        cache: ICacheProvider,
    ): Map<NormalizedRelayUrl, Set<HexKey>> =
        mapOfSet {
            outboxRelayNotes.forEach { outboxNote ->
                val note = outboxNote.note

                val authorHex =
                    if (note is AddressableNote) {
                        note.address.pubKeyHex
                    } else {
                        note.author?.pubkeyHex
                    }

                if (authorHex != null) {
                    val relays =
                        if (rawOutboxRelays) {
                            (outboxNote.note.event as? AdvertisedRelayListEvent)?.dialableWriteRelays() ?: emptySet()
                        } else {
                            (outboxNote.note.event as? AdvertisedRelayListEvent)?.dialableWriteRelays()
                                ?: cache.relayHints.hintsForKey(authorHex).ifEmpty { null }
                                ?: Constants.eventFinderRelays
                        }

                    relays.forEach {
                        if (!it.url.startsWith("wss://feeds.nostr.band") &&
                            !it.url.startsWith("wss://filter.nostr.wine") &&
                            !it.url.startsWith("wss://nwc.primal.net") &&
                            !it.url.startsWith("wss://relay.getalby.com")
                        ) {
                            add(it, authorHex)
                        }
                    }
                }
            }
        }

    fun <T> authorsPerRelaySnapshot(
        authors: Set<HexKey>,
        cache: ICacheProvider,
        transformation: (Map<NormalizedRelayUrl, Set<HexKey>>) -> T,
    ): T {
        val noteMetadata =
            authors
                .map { pubkeyHex ->
                    cache
                        .getOrCreateAddressableNote(AdvertisedRelayListEvent.createAddress(pubkeyHex))
                        .flow()
                        .metadata.stateFlow.value
                }.toTypedArray()
        return transformation(authorsPerRelay(noteMetadata, cache))
    }

    fun <T> toAuthorsPerRelayFlow(
        authors: Set<HexKey>,
        cache: ICacheProvider,
        transformation: (Map<NormalizedRelayUrl, Set<HexKey>>) -> T,
    ): Flow<T> {
        val noteMetadataFlows =
            authors.map { pubkeyHex ->
                val note = cache.getOrCreateAddressableNote(AdvertisedRelayListEvent.createAddress(pubkeyHex))
                note.flow().metadata.stateFlow
            }

        return if (noteMetadataFlows.isEmpty()) {
            MutableStateFlow(transformation(emptyMap()))
        } else {
            combine(noteMetadataFlows) { outboxRelays ->
                transformation(authorsPerRelay(outboxRelays, cache))
            }
        }
    }
}
