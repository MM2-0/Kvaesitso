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
package com.vitorpamplona.amethyst.commons.model.nip72Communities

import com.vitorpamplona.amethyst.commons.model.AccountSettings
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip72Communities.CommunityListDecryptionCache
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip72ModCommunities.definition.CommunityDefinitionEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.tags.CommunityTag
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
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
import kotlinx.coroutines.launch

class CommunityListState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: CommunityListDecryptionCache,
    val scope: CoroutineScope,
    val settings: AccountSettings,
) {
    // Creates a long-term reference for this note so that the GC doesn't collect the note it self
    val communityListNote = cache.getOrCreateAddressableNote(getCommunityListAddress())

    fun getCommunityListAddress() = CommunityListEvent.createAddress(signer.pubKey)

    fun getCommunityListFlow(): StateFlow<NoteState> = communityListNote.flow().metadata.stateFlow

    fun getCommunityList(): CommunityListEvent? = communityListNote.event as? CommunityListEvent

    suspend fun communityListWithBackup(note: Note): Set<CommunityTag> {
        val event = note.event as? CommunityListEvent ?: settings.backupCommunityList
        return event?.let { decryptionCache.communities(it).toSet() } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow: StateFlow<Set<CommunityTag>> =
        getCommunityListFlow()
            .transformLatest { noteState ->
                emit(communityListWithBackup(noteState.note))
            }.onStart {
                emit(communityListWithBackup(communityListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flowNotes: StateFlow<List<AddressableNote>> =
        flow
            .map { hint ->
                hint.map {
                    cache.getOrCreateAddressableNote(it.address)
                }
            }.onStart {
                emit(flow.value.map { cache.getOrCreateAddressableNote(it.address) })
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptyList(),
            )

    @OptIn(ExperimentalCoroutinesApi::class)
    val flowSet: StateFlow<Set<String>> =
        flow
            .map { hint ->
                hint.mapTo(mutableSetOf()) { it.address.toValue() }
            }.onStart {
                emit(flow.value.mapTo(mutableSetOf()) { it.address.toValue() })
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    suspend fun follow(communities: List<AddressableNote>): CommunityListEvent {
        val communityList = getCommunityList()

        val communityTags =
            communities.mapNotNull { community ->
                if (community.address.kind == CommunityDefinitionEvent.KIND) {
                    CommunityTag(community.address, community.relayHintUrl())
                } else {
                    null
                }
            }

        return if (communityList == null) {
            CommunityListEvent.create(communityTags, true, signer)
        } else {
            CommunityListEvent.add(communityList, communityTags, true, signer)
        }
    }

    suspend fun follow(community: AddressableNote): CommunityListEvent? {
        val communityList = getCommunityList()
        if (community.address.kind != CommunityDefinitionEvent.KIND) return communityList

        return if (communityList == null) {
            CommunityListEvent.create(
                CommunityTag(community.address, community.relayHintUrl()),
                true,
                signer,
            )
        } else {
            CommunityListEvent.add(communityList, CommunityTag(community.address, community.relayHintUrl()), true, signer)
        }
    }

    suspend fun unfollow(community: AddressableNote): CommunityListEvent? {
        val communityList = getCommunityList()
        if (community.address.kind != CommunityDefinitionEvent.KIND) return communityList

        return if (communityList != null) {
            CommunityListEvent.remove(communityList, CommunityTag(community.address, community.relayHintUrl()), signer)
        } else {
            null
        }
    }

    init {
        settings.backupCommunityList?.let { event ->
            Log.d("AccountRegisterObservers") { "Loading saved Community list ${event.toJson()}" }
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                LocalCache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "Community List Collector Start")
            getCommunityListFlow().collect {
                Log.d("AccountRegisterObservers") { "Community List for ${signer.pubKey}" }
                (it.note.event as? CommunityListEvent)?.let {
                    settings.updateCommunityListTo(it)
                }
            }
        }
    }
}
