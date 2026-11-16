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
package com.vitorpamplona.amethyst.commons.model.edits

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.edits.PrivateStorageRelayListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PrivateStorageRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: PrivateStorageRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val privateOutboxListNote = cache.getOrCreateAddressableNote(getPrivateOutboxRelayListAddress())

    fun getPrivateOutboxRelayListAddress() = PrivateOutboxRelayListEvent.createAddress(signer.pubKey)

    fun getPrivateOutboxRelayListFlow(): StateFlow<NoteState> = privateOutboxListNote.flow().metadata.stateFlow

    fun getPrivateOutboxRelayList(): PrivateOutboxRelayListEvent? = privateOutboxListNote.event as? PrivateOutboxRelayListEvent

    suspend fun normalizePrivateOutboxRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = note.event as? PrivateOutboxRelayListEvent ?: settings.backupPrivateHomeRelayList
        return event?.let { decryptionCache.relays(it) } ?: emptySet()
    }

    val flow =
        getPrivateOutboxRelayListFlow()
            .map { normalizePrivateOutboxRelayListWithBackup(it.note) }
            .onStart {
                emit(normalizePrivateOutboxRelayListWithBackup(privateOutboxListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun saveRelayList(relays: List<NormalizedRelayUrl>): PrivateOutboxRelayListEvent {
        val relayListForPrivateOutbox = getPrivateOutboxRelayList()

        return if (relayListForPrivateOutbox != null) {
            PrivateOutboxRelayListEvent.updateRelayList(
                earlierVersion = relayListForPrivateOutbox,
                relays = relays,
                signer = signer,
            )
        } else {
            PrivateOutboxRelayListEvent.create(
                relays = relays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupPrivateHomeRelayList?.let { event ->
            Log.d("AccountRegisterObservers") { "Loading saved private home relay list ${event.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Private Home Relay List Collector Start")
            getPrivateOutboxRelayListFlow().collect { noteState ->
                Log.d("AccountRegisterObservers") { "Updating Private Home Relay List for ${signer.pubKey}" }
                (noteState.note.event as? PrivateOutboxRelayListEvent)?.let {
                    settings.updatePrivateHomeRelayList(it)
                }
            }
        }
    }
}
