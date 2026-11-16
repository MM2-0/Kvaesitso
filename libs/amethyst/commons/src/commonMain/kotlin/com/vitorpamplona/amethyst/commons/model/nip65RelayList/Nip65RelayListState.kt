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
package com.vitorpamplona.amethyst.commons.model.nip65RelayList

import com.vitorpamplona.amethyst.commons.defaults.Constants
import com.vitorpamplona.amethyst.commons.defaults.relayListOrDefaultsWhenUnknown
import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayInfo
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

class Nip65RelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val nip65ListNote = cache.getOrCreateAddressableNote(getNIP65RelayListAddress())

    fun getNIP65RelayListAddress() = AdvertisedRelayListEvent.createAddress(signer.pubKey)

    fun getNIP65RelayListFlow(): StateFlow<NoteState> = nip65ListNote.flow().metadata.stateFlow

    fun getNIP65RelayList(): AdvertisedRelayListEvent? = nip65ListNote.event as? AdvertisedRelayListEvent

    fun nip65Event(note: Note) = note.event as? AdvertisedRelayListEvent ?: settings.backupNIP65RelayList

    /**
     * Every write relay of the account's own published list, local ones included: what its
     * settings screen shows, and so what it saves back.
     */
    fun ownWriteRelays(): List<NormalizedRelayUrl> = getNIP65RelayList()?.allWriteRelaysNorm() ?: emptyList()

    /** Every read relay of the account's own published list, local ones included. See [ownWriteRelays]. */
    fun ownReadRelays(): List<NormalizedRelayUrl> = getNIP65RelayList()?.allReadRelaysNorm() ?: emptyList()

    // The account's OWN list, so every entry counts, local relays included: the user configured
    // them. Other users' lists go through relays/OthersRelays.kt instead.
    fun normalizeNIP65WriteRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> = relayListOrDefaultsWhenUnknown(nip65Event(note), Constants.eventFinderRelays) { it.allWriteRelaysNorm()?.toSet() }

    fun normalizeNIP65ReadRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> = relayListOrDefaultsWhenUnknown(nip65Event(note), Constants.bootstrapInbox) { it.allReadRelaysNorm()?.toSet() }

    fun normalizeNIP65WriteRelayListNoDefaults(note: Note): Set<NormalizedRelayUrl> = nip65Event(note)?.allWriteRelaysNorm()?.toSet() ?: emptySet()

    fun normalizeNIP65ReadRelayListNoDefaults(note: Note): Set<NormalizedRelayUrl> = nip65Event(note)?.allReadRelaysNorm()?.toSet() ?: emptySet()

    fun normalizeNIP65AllRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> = nip65Event(note)?.relays()?.map { it.relayUrl }?.toSet() ?: Constants.eventFinderRelays

    fun normalizeNIP65AllRelayListWithBackupNoDefaults(note: Note): Set<NormalizedRelayUrl> = nip65Event(note)?.relays()?.map { it.relayUrl }?.toSet() ?: emptySet()

    /**
     * The app defaults currently standing in for a user we have no kind:10002 for — empty as soon
     * as one exists, including an empty one.
     *
     * Uses the same `nip65Event(note) == null` predicate the substitution itself uses, so the two
     * cannot drift: whatever is listed here is exactly what the app is guessing on the user's
     * behalf. See [relayListOrDefaultsWhenUnknown].
     */
    fun assumedDefaults(note: Note): Set<NormalizedRelayUrl> = if (nip65Event(note) == null) Constants.bootstrapInbox + Constants.eventFinderRelays else emptySet()

    val assumedDefaultsFlow =
        getNIP65RelayListFlow()
            .map { assumedDefaults(it.note) }
            .onStart { emit(assumedDefaults(nip65ListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                assumedDefaults(nip65ListNote),
            )

    val outboxFlow =
        getNIP65RelayListFlow()
            .map { normalizeNIP65WriteRelayListWithBackup(it.note) }
            .onStart { emit(normalizeNIP65WriteRelayListWithBackup(nip65ListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val inboxFlow =
        getNIP65RelayListFlow()
            .map { normalizeNIP65ReadRelayListWithBackup(it.note) }
            .onStart { emit(normalizeNIP65ReadRelayListWithBackup(nip65ListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val outboxFlowNoDefaults =
        getNIP65RelayListFlow()
            .map { normalizeNIP65WriteRelayListNoDefaults(it.note) }
            .onStart { emit(normalizeNIP65WriteRelayListNoDefaults(nip65ListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val inboxFlowNoDefaults =
        getNIP65RelayListFlow()
            .map { normalizeNIP65ReadRelayListNoDefaults(it.note) }
            .onStart { emit(normalizeNIP65ReadRelayListNoDefaults(nip65ListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val allFlowNoDefaults =
        getNIP65RelayListFlow()
            .map { normalizeNIP65AllRelayListWithBackupNoDefaults(it.note) }
            .onStart { emit(normalizeNIP65AllRelayListWithBackupNoDefaults(nip65ListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun saveRelayList(relays: List<AdvertisedRelayInfo>): AdvertisedRelayListEvent {
        val nip65RelayList = getNIP65RelayList()

        return if (nip65RelayList != null) {
            AdvertisedRelayListEvent.replaceRelayListWith(
                earlierVersion = nip65RelayList,
                newRelays = relays,
                signer = signer,
            )
        } else {
            AdvertisedRelayListEvent.createFromScratch(
                relays = relays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupNIP65RelayList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved nip65 relay list ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) { cache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "NIP-65 Relay List Collector Start")
            getNIP65RelayListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating NIP-65 List for ${signer.pubKey}" }
                (it.note.event as? AdvertisedRelayListEvent)?.let {
                    settings.updateNIP65RelayList(it)
                }
            }
        }
    }
}
