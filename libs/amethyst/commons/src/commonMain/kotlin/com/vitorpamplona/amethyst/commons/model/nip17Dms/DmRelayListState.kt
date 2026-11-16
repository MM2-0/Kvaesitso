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
package com.vitorpamplona.amethyst.commons.model.nip17Dms

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip17Dm.settings.DmRelayListEvent
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

class DmRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val dmListNote = cache.getOrCreateAddressableNote(getDMRelayListAddress())

    fun getDMRelayListAddress() = DmRelayListEvent.createAddress(signer.pubKey)

    fun getDMRelayListFlow(): StateFlow<NoteState> = dmListNote.flow().metadata.stateFlow

    fun getDMRelayList(): DmRelayListEvent? = dmListNote.event as? DmRelayListEvent

    fun normalizeDMRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = note.event as? DmRelayListEvent ?: settings.backupDMRelayList
        // The account's own list: a local DM relay the user configured is theirs to read from.
        return event?.allRelays()?.toSet() ?: emptySet()
    }

    val flow =
        getDMRelayListFlow()
            .map { normalizeDMRelayListWithBackup(it.note) }
            .onStart { emit(normalizeDMRelayListWithBackup(dmListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun saveRelayList(dmRelays: List<NormalizedRelayUrl>): DmRelayListEvent {
        val relayListForDMs = getDMRelayList()
        return if (relayListForDMs != null && relayListForDMs.tags.isNotEmpty()) {
            DmRelayListEvent.updateRelayList(
                earlierVersion = relayListForDMs,
                relays = dmRelays,
                signer = signer,
            )
        } else {
            DmRelayListEvent.create(
                relays = dmRelays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupDMRelayList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved DM Relay List ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(it)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "NIP-17 Relay List Collector Start")
            getDMRelayListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating DM Relay List for ${signer.pubKey}" }
                (it.note.event as? DmRelayListEvent)?.let {
                    settings.updateDMRelayList(it)
                }
            }
        }
    }
}
