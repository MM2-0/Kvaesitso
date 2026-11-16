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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.broadcastRelays

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.broadcastRelays.BroadcastRelayListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.relayLists.BroadcastRelayListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

class BroadcastRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: BroadcastRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val broadcastListNote = cache.getOrCreateAddressableNote(getBroadcastRelayListAddress())

    fun getBroadcastRelayListAddress() = BroadcastRelayListEvent.createAddress(signer.pubKey)

    fun getBroadcastRelayListFlow(): StateFlow<NoteState> = broadcastListNote.flow().metadata.stateFlow

    fun getBroadcastRelayList(): BroadcastRelayListEvent? = broadcastListNote.event as? BroadcastRelayListEvent

    suspend fun normalizeBroadcastRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = note.event as? BroadcastRelayListEvent
        return event?.let { decryptionCache.relays(it) } ?: emptySet()
    }

    val flow =
        getBroadcastRelayListFlow()
            .map { normalizeBroadcastRelayListWithBackup(it.note) }
            .onStart { emit(normalizeBroadcastRelayListWithBackup(broadcastListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun saveRelayList(broadcastRelays: List<NormalizedRelayUrl>): BroadcastRelayListEvent {
        val relayListForBroadcast = getBroadcastRelayList()

        return if (relayListForBroadcast != null && relayListForBroadcast.tags.isNotEmpty()) {
            BroadcastRelayListEvent.updateRelayList(
                earlierVersion = relayListForBroadcast,
                relays = broadcastRelays,
                signer = signer,
            )
        } else {
            BroadcastRelayListEvent.create(
                relays = broadcastRelays,
                signer = signer,
            )
        }
    }
}
