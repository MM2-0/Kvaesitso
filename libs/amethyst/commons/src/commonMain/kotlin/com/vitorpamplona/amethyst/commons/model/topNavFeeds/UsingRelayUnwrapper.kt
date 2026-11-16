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

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.relays.dialableRelays
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isDialableFromOthers
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.utils.mapOfSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class UsingRelayUnwrapper {
    fun usersPerRelay(relayListNotes: Array<NoteState>): Map<NormalizedRelayUrl, Set<HexKey>> =
        mapOfSet {
            relayListNotes.forEach { outboxNote ->
                val note = outboxNote.note

                val authorHex =
                    if (note is AddressableNote) {
                        note.address.pubKeyHex
                    } else {
                        note.author?.pubkeyHex
                    }

                if (authorHex != null) {
                    val relays =
                        when (val noteEvent = outboxNote.note.event) {
                            // Other users' lists: only the relays we may dial for them.
                            is AdvertisedRelayListEvent -> noteEvent.relaysNorm().filter { it.isDialableFromOthers() }
                            is DmRelayListEvent -> noteEvent.dialableRelays()
                            else -> emptySet()
                        }

                    relays.forEach {
                        add(it, authorHex)
                    }
                    relays.forEach {
                        add(it, authorHex)
                    }
                }
            }
        }

    fun <T> usersPerRelaySnapshot(
        users: Set<HexKey>,
        cache: ICacheProvider,
        transformation: (Map<NormalizedRelayUrl, Set<HexKey>>) -> T,
    ): T {
        val nip65RelayNotes =
            users
                .map { pubkeyHex ->
                    cache
                        .getOrCreateAddressableNote(AdvertisedRelayListEvent.createAddress(pubkeyHex))
                        .flow()
                        .metadata.stateFlow.value
                }.toTypedArray()

        val nip17RelayNotes =
            users
                .map { pubkeyHex ->
                    cache
                        .getOrCreateAddressableNote(DmRelayListEvent.createAddress(pubkeyHex))
                        .flow()
                        .metadata.stateFlow.value
                }.toTypedArray()

        return transformation(usersPerRelay(nip65RelayNotes + nip17RelayNotes))
    }

    fun <T> toUsersPerRelayFlow(
        users: Set<HexKey>,
        cache: ICacheProvider,
        transformation: (Map<NormalizedRelayUrl, Set<HexKey>>) -> T,
    ): Flow<T> {
        val relayNoteFlows =
            users.map { pubkeyHex ->
                val note = cache.getOrCreateAddressableNote(AdvertisedRelayListEvent.createAddress(pubkeyHex))
                note.flow().metadata.stateFlow
            } +
                users.map { pubkeyHex ->
                    val note = cache.getOrCreateAddressableNote(DmRelayListEvent.createAddress(pubkeyHex))
                    note.flow().metadata.stateFlow
                }

        return if (relayNoteFlows.isEmpty()) {
            MutableStateFlow(transformation(emptyMap()))
        } else {
            combine(relayNoteFlows) { relayListNotes ->
                transformation(usersPerRelay(relayListNotes))
            }
        }
    }
}
