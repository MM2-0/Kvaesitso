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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.relayLists

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEvent
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEventCache
import com.vitorpamplona.quartz.nip51Lists.relayLists.tags.relaySet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

open class GenericRelayListCache<T : PrivateTagArrayEvent>(
    val signer: NostrSigner,
) {
    val cachedPrivateLists = PrivateTagArrayEventCache<T>(signer)

    fun cachedRelays(event: T) = cachedPrivateLists.mergeTagListPrecached(event).relaySet()

    suspend fun relays(event: T) = cachedPrivateLists.mergeTagList(event).relaySet()

    @Suppress("UNCHECKED_CAST")
    fun fastStartValueForRelayList(note: Note): RelayListCard {
        val noteEvent = note.event as? T
        return if (noteEvent != null) {
            RelayListCard(cachedRelays(noteEvent).toList())
        } else {
            EmptyRelayListCard
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Suppress("UNCHECKED_CAST")
    fun observeDecryptedRelayList(note: Note): Flow<RelayListCard> =
        note
            .flow()
            .metadata.stateFlow
            .mapLatest { noteState ->
                val event = noteState.note.event as? T
                RelayListCard(event?.let { relays(it).toList() } ?: emptyList())
            }.onStart {
                val event = note.event as? T
                if (event != null) {
                    val list = relays(event)
                    if (list.isNotEmpty()) {
                        emit(RelayListCard(list.toList()))
                    } else {
                        emit(EmptyRelayListCard)
                    }
                } else {
                    emit(EmptyRelayListCard)
                }
            }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
}
