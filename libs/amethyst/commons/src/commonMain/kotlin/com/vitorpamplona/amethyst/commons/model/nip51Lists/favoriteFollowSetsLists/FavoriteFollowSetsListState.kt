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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteFollowSetsLists

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.favoriteFollowSetsLists.FavoriteFollowSetsListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.favoriteFollowSetsList.FavoriteFollowSetsListEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * The account's NIP-51 kind 10021 "Favorite follow sets": kind:30000 follow sets, the user's own
 * or anyone else's, pinned so they show up as feeds in the top-nav picker.
 */
class FavoriteFollowSetsListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: FavoriteFollowSetsListDecryptionCache,
    val scope: CoroutineScope,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note itself
    val favoriteFollowSetsListNote = cache.getOrCreateAddressableNote(getFavoriteFollowSetsListAddress())

    fun getFavoriteFollowSetsListAddress() = FavoriteFollowSetsListEvent.createAddress(signer.pubKey)

    fun getFavoriteFollowSetsListFlow(): StateFlow<NoteState> = favoriteFollowSetsListNote.flow().metadata.stateFlow

    fun getFavoriteFollowSetsList(): FavoriteFollowSetsListEvent? = favoriteFollowSetsListNote.event as? FavoriteFollowSetsListEvent

    suspend fun favoriteFollowSets(note: Note): Set<Address> {
        val event = note.event as? FavoriteFollowSetsListEvent ?: return emptySet()
        return decryptionCache.favoriteFollowSets(event)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<Address>> =
        getFavoriteFollowSetsListFlow()
            .transformLatest { noteState ->
                emit(favoriteFollowSets(noteState.note))
            }.onStart {
                emit(favoriteFollowSets(favoriteFollowSetsListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    val flowNotes: StateFlow<List<AddressableNote>> =
        flow
            .map { addresses ->
                addresses.map { cache.getOrCreateAddressableNote(it) }
            }.onStart {
                emit(flow.value.map { cache.getOrCreateAddressableNote(it) })
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    suspend fun follow(followSet: AddressBookmark): FavoriteFollowSetsListEvent {
        val list = getFavoriteFollowSetsList()
        return if (list == null) {
            FavoriteFollowSetsListEvent.create(followSet, false, signer)
        } else {
            FavoriteFollowSetsListEvent.add(list, followSet, false, signer)
        }
    }

    suspend fun unfollow(followSet: Address): FavoriteFollowSetsListEvent? {
        val list = getFavoriteFollowSetsList() ?: return null
        return FavoriteFollowSetsListEvent.remove(list, followSet, signer)
    }
}
