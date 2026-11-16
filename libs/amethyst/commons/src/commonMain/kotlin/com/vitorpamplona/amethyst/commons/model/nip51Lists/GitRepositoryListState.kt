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
package com.vitorpamplona.amethyst.commons.model.nip51Lists

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.gitRepositoryList.GitRepositoryListEvent
import com.vitorpamplona.quartz.nip51Lists.remove
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Account state for the user's bookmarked (starred) git repositories — NIP-51
 * kind 10018. Public repositories are followed in public tags; private ones
 * (GRASP-08) in the encrypted section, so their coordinates never show.
 */
@Stable
class GitRepositoryListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val scope: CoroutineScope,
) {
    // Long-term reference so the GC keeps the note alive.
    val repositoryList = cache.getOrCreateAddressableNote(getListAddress())

    fun getListAddress() = GitRepositoryListEvent.createAddress(signer.pubKey)

    fun getListFlow(): StateFlow<NoteState> = repositoryList.flow().metadata.stateFlow

    fun getList(): GitRepositoryListEvent? = repositoryList.event as? GitRepositoryListEvent

    private fun publicAddresses(note: Note): Set<Address> = (note.event as? GitRepositoryListEvent)?.publicRepositories()?.map { it.address }?.toSet() ?: emptySet()

    @OptIn(FlowPreview::class)
    val publicRepositoryAddressSet: StateFlow<Set<Address>> =
        getListFlow()
            .map { publicAddresses(it.note) }
            .onStart { emit(publicAddresses(repositoryList)) }
            .debounce(100)
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** The private section's repositories (decrypted with the user's own key): where private repositories are followed. */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val privateRepositoryAddressSet: StateFlow<Set<Address>> =
        getListFlow()
            .debounce(100)
            .mapLatest { state -> privateAddresses(state.note) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private suspend fun privateAddresses(note: Note): Set<Address> =
        (note.event as? GitRepositoryListEvent)?.let { list ->
            runCatching { list.privateRepositories(signer) }.getOrNull()?.map { it.address }?.toSet()
        } ?: emptySet()

    /** Every followed repository, public and private: what the star and the dashboard show. */
    val repositoryAddressSet: StateFlow<Set<Address>> =
        combine(publicRepositoryAddressSet, privateRepositoryAddressSet) { public, private -> public + private }
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun isBookmarked(address: Address): Boolean = repositoryAddressSet.value.contains(address)

    /**
     * Adds [note]'s address to the list, creating it if needed: to the encrypted section for a
     * private repository (GRASP-08), whose coordinate must not appear in public tags, else publicly.
     */
    suspend fun addRepository(note: AddressableNote): GitRepositoryListEvent {
        val list = getList()
        val bookmark = AddressBookmark(note.address, note.relayHintUrl())
        val private = (note.event as? GitRepositoryEvent)?.isPrivate() == true
        return if (list == null) {
            if (private) {
                GitRepositoryListEvent.create(privateRepositories = listOf(bookmark), signer = signer)
            } else {
                GitRepositoryListEvent.create(publicRepositories = listOf(bookmark), signer = signer)
            }
        } else {
            GitRepositoryListEvent.add(earlierVersion = list, repository = bookmark, isPrivate = private, signer = signer)
        }
    }

    /** Removes [note]'s address from the list: from the private section too when it is followed there. */
    suspend fun removeRepository(note: AddressableNote): GitRepositoryListEvent? {
        val list = getList() ?: return null
        if (note.address in privateRepositoryAddressSet.value) {
            return GitRepositoryListEvent.remove(list, AddressBookmark(note.address, note.relayHintUrl()), signer)
        }
        val idTag = AddressBookmark(note.address, note.relayHintUrl()).toTagIdOnly()
        val newTags = list.tags.remove(idTag)
        if (newTags.size == list.tags.size) return null
        return GitRepositoryListEvent.resign(content = list.content, tags = newTags, signer = signer)
    }
}
