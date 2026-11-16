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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.blockedRelays

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.blockedRelays.BlockedRelayListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
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

class BlockedRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: BlockedRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val blockedListNote = cache.getOrCreateAddressableNote(getBlockedRelayListAddress())

    fun getBlockedRelayListAddress() = BlockedRelayListEvent.createAddress(signer.pubKey)

    fun getBlockedRelayListFlow(): StateFlow<NoteState> = blockedListNote.flow().metadata.stateFlow

    fun getBlockedRelayList(): BlockedRelayListEvent? = blockedListNote.event as? BlockedRelayListEvent

    suspend fun normalizeBlockedRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = note.event as? BlockedRelayListEvent ?: settings.backupBlockedRelayList
        return event?.let { decryptionCache.relays(it) } ?: emptySet()
    }

    val flow =
        getBlockedRelayListFlow()
            .map {
                normalizeBlockedRelayListWithBackup(it.note)
            }.onStart { emit(normalizeBlockedRelayListWithBackup(blockedListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    /**
     * Adds [relay] to the kind-10006 list, keeping whatever is already there.
     *
     * Callers that only want to block one relay (the NOTIFY prompt's "Block Relay" button, for
     * instance) must not rebuild the list from a snapshot they captured earlier: the list is
     * shared across clients and may have grown since. Reading the current note here keeps the
     * add additive.
     */
    suspend fun addRelay(relay: NormalizedRelayUrl): BlockedRelayListEvent {
        val current = normalizeBlockedRelayListWithBackup(blockedListNote).toMutableList()
        if (relay !in current) current.add(relay)
        return saveRelayList(current)
    }

    suspend fun saveRelayList(blockedRelays: List<NormalizedRelayUrl>): BlockedRelayListEvent {
        if (!signer.isWriteable()) throw SignerExceptions.ReadOnlyException()
        val relayListForBlocked = getBlockedRelayList()

        return if (relayListForBlocked != null && relayListForBlocked.tags.isNotEmpty()) {
            BlockedRelayListEvent.updateRelayList(
                earlierVersion = relayListForBlocked,
                relays = blockedRelays,
                signer = signer,
            )
        } else {
            BlockedRelayListEvent.create(
                relays = blockedRelays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupBlockedRelayList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved Blocked relay list ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Blocked Relay List Collector Start")
            getBlockedRelayListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating Blocked Relay List for ${signer.pubKey}" }
                (it.note.event as? BlockedRelayListEvent)?.let {
                    settings.updateBlockedRelayList(it)
                }
            }
        }
    }
}
