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
package com.vitorpamplona.amethyst.commons.model.nip53NestsServers

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.model.nipB7Blossom.BlossomServerListState
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServer
import com.vitorpamplona.quartz.nip53LiveActivities.nestsServers.NestsServersEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Per-account state for the user's preferred audio-room (NIP-53 / nests)
 * MoQ host servers — kind 10112 [NestsServersEvent]. Mirror of
 * [BlossomServerListState] for the nests use case.
 *
 * Surfaces:
 *   - [flow] — current `List<NestsServer>` of saved (relay, auth) pairs
 *   - [getNestsServersListFlow] — reactive `StateFlow<NoteState>` for
 *     downstream UI to recompose on event arrivals
 *   - [saveNestsServersList] — build + sign a new replaceable kind 10112
 *     event (preserving prior tags' alt etc.)
 *
 * Each saved entry carries the **two** URLs the kind-30312 event needs:
 * the moq-relay WebTransport endpoint and the moq-auth sidecar base.
 * They live on different hosts in the deployed nostrnests reference, so
 * the pair MUST stay together.
 */
class NestsServerListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
) {
    /** Long-term reference to keep the addressable note from being GC'd. */
    val nestsListNote = cache.getOrCreateAddressableNote(getNestsServersAddress())

    fun getNestsServersAddress() = NestsServersEvent.createAddress(signer.pubKey)

    fun getNestsServersListFlow(): StateFlow<NoteState> = nestsListNote.flow().metadata.stateFlow

    fun getNestsServersList(): NestsServersEvent? = nestsListNote.event as? NestsServersEvent

    fun normalizeServers(note: Note): List<NestsServer> {
        val event = note.event as? NestsServersEvent
        return event?.servers() ?: emptyList()
    }

    val flow: StateFlow<List<NestsServer>> =
        getNestsServersListFlow()
            .map {
                normalizeServers(it.note)
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    suspend fun saveNestsServersList(servers: List<NestsServer>): NestsServersEvent {
        val serverList = getNestsServersList()

        return if (serverList != null && serverList.tags.isNotEmpty()) {
            NestsServersEvent.updateRelayList(
                earlierVersion = serverList,
                servers = servers,
                signer = signer,
            )
        } else {
            NestsServersEvent.createFromScratch(
                servers = servers,
                signer = signer,
            )
        }
    }
}
