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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteRelays

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteRelays.FavoriteRelayListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.relayLists.FavoriteRelayListEvent
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

class FavoriteRelayListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: FavoriteRelayListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val favoriteRelayListNote = cache.getOrCreateAddressableNote(getFavoriteRelayListAddress())

    fun getFavoriteRelayListAddress() = FavoriteRelayListEvent.createAddress(signer.pubKey)

    fun getFavoriteRelayListFlow(): StateFlow<NoteState> = favoriteRelayListNote.flow().metadata.stateFlow

    fun getFavoriteRelayList(): FavoriteRelayListEvent? = favoriteRelayListNote.event as? FavoriteRelayListEvent

    fun favoriteRelayListEvent(note: Note) = note.event as? FavoriteRelayListEvent ?: settings.backupFavoriteRelayList

    suspend fun normalizeFavoriteRelayListWithBackup(note: Note): Set<NormalizedRelayUrl> = favoriteRelayListEvent(note)?.let { decryptionCache.relays(it) }?.ifEmpty { null } ?: emptySet()

    suspend fun normalizeFavoriteRelayListWithBackupNoDefaults(note: Note): Set<NormalizedRelayUrl> = favoriteRelayListEvent(note)?.let { decryptionCache.relays(it) } ?: emptySet()

    val flow =
        getFavoriteRelayListFlow()
            .map { normalizeFavoriteRelayListWithBackup(it.note) }
            .onStart { emit(normalizeFavoriteRelayListWithBackup(favoriteRelayListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val flowNoDefaults =
        getFavoriteRelayListFlow()
            .map { normalizeFavoriteRelayListWithBackupNoDefaults(it.note) }
            .onStart { emit(normalizeFavoriteRelayListWithBackupNoDefaults(favoriteRelayListNote)) }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun addRelay(relay: NormalizedRelayUrl): FavoriteRelayListEvent {
        val current = normalizeFavoriteRelayListWithBackupNoDefaults(favoriteRelayListNote).toMutableList()
        if (relay !in current) current.add(relay)
        return saveRelayList(current)
    }

    suspend fun removeRelay(relay: NormalizedRelayUrl): FavoriteRelayListEvent? {
        val current = normalizeFavoriteRelayListWithBackupNoDefaults(favoriteRelayListNote).toMutableList()
        if (relay !in current) return null
        current.remove(relay)
        return saveRelayList(current)
    }

    suspend fun saveRelayList(relayFeeds: List<NormalizedRelayUrl>): FavoriteRelayListEvent {
        val favoriteRelayList = getFavoriteRelayList()

        return if (favoriteRelayList != null && favoriteRelayList.tags.isNotEmpty()) {
            FavoriteRelayListEvent.updateRelayList(
                earlierVersion = favoriteRelayList,
                relays = relayFeeds,
                signer = signer,
            )
        } else {
            FavoriteRelayListEvent.create(
                relays = relayFeeds,
                signer = signer,
            )
        }
    }

    init {
        settings.backupFavoriteRelayList?.let {
            Log.d("AccountRegisterObservers") { "Loading saved relay feeds list ${it.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) { LocalCache.justConsumeMyOwnEvent(it) }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Relay feeds list Collector Start")
            getFavoriteRelayListFlow().collect {
                Log.d("AccountRegisterObservers") { "Updating Relay feeds list for ${signer.pubKey}" }
                (it.note.event as? FavoriteRelayListEvent)?.let {
                    settings.updateFavoriteRelayList(it)
                }
            }
        }
    }
}
