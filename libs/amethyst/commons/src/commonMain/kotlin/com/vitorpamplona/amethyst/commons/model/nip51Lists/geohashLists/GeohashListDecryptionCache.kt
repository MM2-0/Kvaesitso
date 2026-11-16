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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.geohashLists

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.PrivateTagArrayEventCache
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.geohashSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

class GeohashListDecryptionCache(
    val signer: NostrSigner,
) {
    val cachedPrivateLists = PrivateTagArrayEventCache<GeohashListEvent>(signer)

    fun cachedGeohashes(event: GeohashListEvent) = cachedPrivateLists.mergeTagListPrecached(event).geohashSet()

    suspend fun geohashes(event: GeohashListEvent) = cachedPrivateLists.mergeTagList(event).geohashSet()

    fun fastStartValueForGeohashList(note: Note): GeohashListCard {
        val noteEvent = note.event as? GeohashListEvent
        return if (noteEvent != null) {
            GeohashListCard(cachedGeohashes(noteEvent).toList())
        } else {
            EmptyGeohashListCard
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeDecryptedGeohashList(note: Note): Flow<GeohashListCard> =
        note
            .flow()
            .metadata.stateFlow
            .mapLatest { noteState ->
                val event = noteState.note.event as? GeohashListEvent
                GeohashListCard(event?.let { geohashes(it).toList() } ?: emptyList())
            }.onStart {
                val event = note.event as? GeohashListEvent
                if (event != null) {
                    val list = geohashes(event)
                    if (list.isNotEmpty()) {
                        emit(GeohashListCard(list.toList()))
                    } else {
                        emit(EmptyGeohashListCard)
                    }
                } else {
                    emit(EmptyGeohashListCard)
                }
            }.distinctUntilChanged()
            .flowOn(Dispatchers.IO)
}
