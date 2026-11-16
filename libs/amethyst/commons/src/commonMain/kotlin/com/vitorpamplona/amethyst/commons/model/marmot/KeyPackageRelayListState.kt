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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
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

class KeyPackageRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val keyPackageListNote = cache.getOrCreateAddressableNote(getKeyPackageRelayListAddress())

    fun getKeyPackageRelayListAddress() = KeyPackageRelayListEvent.createAddress(signer.pubKey)

    fun getKeyPackageRelayListFlow(): StateFlow<NoteState> = keyPackageListNote.flow().metadata.stateFlow

    fun getKeyPackageRelayList(): KeyPackageRelayListEvent? = keyPackageListNote.event as? KeyPackageRelayListEvent

    fun normalizeKeyPackageRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> {
        val event = note.event as? KeyPackageRelayListEvent ?: settings.backupKeyPackageRelayList
        // The account's own list: a local relay the user configured is theirs.
        return event?.allRelays()?.toSet() ?: emptySet()
    }

    val flow =
        getKeyPackageRelayListFlow()
            .map { normalizeKeyPackageRelayListWithBackup(it.note) }
            .onStart { emit(normalizeKeyPackageRelayListWithBackup(keyPackageListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun saveRelayList(relays: List<NormalizedRelayUrl>): KeyPackageRelayListEvent {
        val existing = getKeyPackageRelayList()
        return if (existing != null && existing.tags.isNotEmpty()) {
            KeyPackageRelayListEvent.updateRelayList(
                earlierVersion = existing,
                relays = relays,
                signer = signer,
            )
        } else {
            KeyPackageRelayListEvent.create(
                relays = relays,
                signer = signer,
            )
        }
    }

    init {
        settings.backupKeyPackageRelayList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved KeyPackage Relay List ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                cache.justConsumeMyOwnEvent(it)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "MIP-00 KeyPackage Relay List Collector Start")
            getKeyPackageRelayListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating KeyPackage Relay List for ${signer.pubKey}" }
                (it.note.event as? KeyPackageRelayListEvent)?.let {
                    settings.updateKeyPackageRelayList(it)
                }
            }
        }
    }
}
