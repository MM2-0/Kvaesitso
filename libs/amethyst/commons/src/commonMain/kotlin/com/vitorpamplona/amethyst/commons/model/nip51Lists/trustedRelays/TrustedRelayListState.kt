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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.trustedRelays

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.trustedRelays.TrustedRelayListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
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

class TrustedRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: TrustedRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val trustedListNote = cache.getOrCreateAddressableNote(getTrustedRelayListAddress())

    fun getTrustedRelayListAddress() = TrustedRelayListEvent.createAddress(signer.pubKey)

    fun getTrustedRelayListFlow(): StateFlow<NoteState> = trustedListNote.flow().metadata.stateFlow

    fun getTrustedRelayList(): TrustedRelayListEvent? = trustedListNote.event as? TrustedRelayListEvent

    suspend fun normalizeTrustedRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = note.event as? TrustedRelayListEvent ?: settings.backupTrustedRelayList
        return event?.let { decryptionCache.relays(it) } ?: emptySet()
    }

    val flow =
        getTrustedRelayListFlow()
            .map { normalizeTrustedRelayListWithBackup(it.note) }
            .onStart { emit(normalizeTrustedRelayListWithBackup(trustedListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                // Synchronously seed public tags from the backup; private tags may be absent on first boot.
                settings.backupTrustedRelayList?.let { decryptionCache.cachedRelays(it) } ?: emptySet(),
            )

    /** The list with [relay] added, or null when it is already there. */
    suspend fun addRelay(relay: NormalizedRelayUrl): TrustedRelayListEvent? {
        val current = normalizeTrustedRelayListWithBackup(trustedListNote)
        if (relay in current) return null
        return saveRelayList(current.toList() + relay)
    }

    suspend fun saveRelayList(trustedRelays: List<NormalizedRelayUrl>): TrustedRelayListEvent {
        val relayListForTrusted = getTrustedRelayList()

        return if (relayListForTrusted != null && relayListForTrusted.tags.isNotEmpty()) {
            TrustedRelayListEvent.updateRelayList(
                earlierVersion = relayListForTrusted,
                relays = trustedRelays,
                signer = signer,
            )
        } else {
            TrustedRelayListEvent.create(
                relays = trustedRelays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupTrustedRelayList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved Trusted relay list ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) { cache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Trusted Relay List Collector Start")
            getTrustedRelayListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating Trusted Relay List for ${signer.pubKey}" }
                (it.note.event as? TrustedRelayListEvent)?.let {
                    settings.updateTrustedRelayList(it)
                }
            }
        }
    }
}
