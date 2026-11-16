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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.NoteState
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.GroupTag
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

/** Persistence hook for the last-known kind 10009 event (offline backup). */
interface RelayGroupRepository {
    fun relayGroupList(): SimpleGroupListEvent?

    fun updateRelayGroupListTo(newRelayGroupList: SimpleGroupListEvent?)
}

/**
 * The user's NIP-51 "simple groups" list (kind 10009): the cross-device source
 * of truth for which NIP-29 groups they've joined. Joined groups are written as
 * PUBLIC `["group", id, relay, name?]` tags (see [follow] for why); reads still
 * merge in any NIP-44-encrypted private items so older lists keep working. Mirrors
 * [com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatListState].
 */
class RelayGroupListState(
    val signer: NostrSigner,
    val cache: ICacheProvider,
    val decryptionCache: RelayGroupListDecryptionCache,
    val scope: CoroutineScope,
    val settings: RelayGroupRepository,
) {
    // Long-term reference so the GC doesn't collect the note itself.
    val relayGroupListNote = cache.getOrCreateAddressableNote(getRelayGroupListAddress())

    fun getRelayGroupListAddress() = SimpleGroupListEvent.createAddress(signer.pubKey)

    fun getRelayGroupListFlow(): StateFlow<NoteState> = relayGroupListNote.flow().metadata.stateFlow

    fun getRelayGroupList(): SimpleGroupListEvent? = relayGroupListNote.event as? SimpleGroupListEvent

    suspend fun relayGroupListWithBackup(note: Note): Set<GroupTag> {
        val event = note.event as? SimpleGroupListEvent ?: settings.relayGroupList()
        return event?.let { decryptionCache.groupSet(it) } ?: emptySet()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val liveRelayGroupList: StateFlow<Set<GroupTag>> =
        getRelayGroupListFlow()
            .transformLatest { noteState ->
                emit(relayGroupListWithBackup(noteState.note))
            }.onStart {
                emit(relayGroupListWithBackup(relayGroupListNote))
            }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                emptySet(),
            )

    /** The distinct host relays across all joined groups — the "servers" rail. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveRelayGroupServers: StateFlow<Set<String>> =
        liveRelayGroupList
            .transformLatest { groups -> emit(groups.mapTo(mutableSetOf()) { it.relayUrl }) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * The joined groups as normalized [GroupId]s — what the UI asks when it needs to know whether a
     * channel is on my Messages list ("Remove from Messages" vs "Add to Messages"). The stored tag
     * carries a raw relay url string (another client may not have normalized it the way we do), so
     * normalize before comparing instead of matching [GroupTag] strings.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val liveRelayGroupIds: StateFlow<Set<GroupId>> =
        liveRelayGroupList
            .transformLatest { groups ->
                emit(
                    groups.mapNotNullTo(mutableSetOf()) { tag ->
                        RelayUrlNormalizer.normalizeOrNull(tag.relayUrl)?.let { GroupId(tag.groupId, it) }
                    },
                )
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    /** Is [groupId] currently on my kind-10009 list (i.e. does it show on Messages)? */
    fun isOnMyList(groupId: GroupId) = groupId in liveRelayGroupIds.value

    private fun RelayGroupChannel.toGroupTag() = GroupTag(groupId.id, groupId.relayUrl.url, event?.name())

    suspend fun follow(channel: RelayGroupChannel): SimpleGroupListEvent {
        val relayGroupList = getRelayGroupList()
        val group = channel.toGroupTag()

        // Store joined groups as PUBLIC `["group", id, relay]` tags. NIP-29 group
        // membership is already public on the relay (the kind-39002 members list),
        // so private storage buys almost no privacy — and the reference NIP-29
        // clients (Flotilla, nostrord) read only the public tags, so a private list
        // makes our joined groups invisible to them. Reading still merges public +
        // any legacy private items, so existing private entries keep working.
        return if (relayGroupList == null) {
            SimpleGroupListEvent.create(
                publicGroups = listOf(group),
                signer = signer,
            )
        } else {
            SimpleGroupListEvent.add(
                earlierVersion = relayGroupList,
                group = group,
                isPrivate = false,
                signer = signer,
            )
        }
    }

    suspend fun unfollow(channel: RelayGroupChannel): SimpleGroupListEvent? {
        val relayGroupList = getRelayGroupList() ?: return null
        return SimpleGroupListEvent.remove(
            earlierVersion = relayGroupList,
            group = channel.toGroupTag(),
            signer = signer,
        )
    }

    /**
     * NIP-29 group migration: replace [from]'s entry with the same group id on [to]'s relay, in one
     * signed version of the list (the group keeps its id; only the relay hint changes).
     */
    suspend fun move(
        from: RelayGroupChannel,
        to: RelayGroupChannel,
    ): SimpleGroupListEvent {
        val target = GroupTag(to.groupId.id, to.groupId.relayUrl.url, to.event?.name() ?: from.event?.name())
        val relayGroupList = getRelayGroupList() ?: return SimpleGroupListEvent.create(publicGroups = listOf(target), signer = signer)
        return SimpleGroupListEvent.replace(relayGroupList, from.toGroupTag(), target, signer)
    }

    init {
        settings.relayGroupList()?.let { event ->
            Log.d("AccountRegisterObservers", "Loading saved relay group list")
            @OptIn(DelicateCoroutinesApi::class)
            scope.launch(Dispatchers.IO) {
                cache.justConsumeMyOwnEvent(event)
            }
        }

        scope.launch(Dispatchers.IO) {
            Log.d("AccountRegisterObservers", "RelayGroupList Collector Start")
            getRelayGroupListFlow().collect { noteState ->
                Log.d("AccountRegisterObservers") { "RelayGroupList for ${signer.pubKey}" }
                (noteState.note.event as? SimpleGroupListEvent)?.let {
                    settings.updateRelayGroupListTo(it)
                }
            }
        }
    }
}
