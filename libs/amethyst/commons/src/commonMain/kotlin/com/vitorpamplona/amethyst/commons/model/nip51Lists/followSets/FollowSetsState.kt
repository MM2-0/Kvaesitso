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
@file:OptIn(ExperimentalUuidApi::class)

package com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.anyNotNullEvent
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.eventIdSet
import com.vitorpamplona.amethyst.commons.model.events
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.FollowSetDecryptionCache
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.PeopleList
import com.vitorpamplona.amethyst.commons.model.updateFlow
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.update
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.FollowSetEvent
import com.vitorpamplona.quartz.nip51Lists.followSet.description
import com.vitorpamplona.quartz.nip51Lists.followSet.image
import com.vitorpamplona.quartz.nip51Lists.followSet.title
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.utils.flattenToSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Maintains several stateflows for each step in processing PeopleLists
 *
 * This class must receive updates from Large Cache as it receives new events.
 */
class FollowSetsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val decryptionCache: FollowSetDecryptionCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    // Hides the fixed-dTag block-list shell when it is not loaded (it has no
    // meaningful name until it exists) and shells that a kind-5 deletion event from
    // the list's author has already deleted (e.g. a persisted TopFilter re-creates an
    // empty shell for the deleted address after a restart, and its name falls back to
    // the dTag/UUID). Shells that are merely not loaded yet stay in the list so the UI
    // can subscribe and fetch them from relays.
    fun existingPeopleListNotes() =
        cache.addressables
            .filter(FollowSetEvent.KIND, user.pubkeyHex)
            .filter { it.dTag() != FollowSetEvent.BLOCK_LIST_D_TAG || it.event != null }
            .filter { it.event != null || !cache.hasBeenDeleted(it.address) }

    val peopleListVersions = MutableStateFlow(0)

    val peopleListNotes =
        peopleListVersions
            .map { existingPeopleListNotes() }
            .onStart { emit(existingPeopleListNotes()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val peopleListsEventIds =
        peopleListNotes
            .map { it.eventIdSet() }
            .onStart { emit(peopleListNotes.value.eventIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestLists: StateFlow<List<FollowSetEvent>> =
        peopleListNotes
            .transformLatest { emitAll(it.updateFlow<FollowSetEvent>()) }
            .onStart { emit(peopleListNotes.value.events()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun FollowSetEvent.userIdSet() = decryptionCache.userIdSet(this)

    suspend fun List<FollowSetEvent>.mapToUserIdSet() = this.map { it.userIdSet() }.flattenToSet()

    suspend fun List<FollowSetEvent>.mapGoodUsersToIdSet() =
        this
            .mapNotNull {
                if (it.dTag() != FollowSetEvent.BLOCK_LIST_D_TAG) {
                    it.userIdSet()
                } else {
                    null
                }
            }.flattenToSet()

    val allGoodPeopleListProfiles: StateFlow<Set<HexKey>> =
        latestLists
            .map { it.mapGoodUsersToIdSet() }
            .onStart { emit(latestLists.value.mapGoodUsersToIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    suspend fun FollowSetEvent.toUI() =
        PeopleList(
            identifierTag = this.dTag(),
            title = this.titleOrName() ?: this.dTag(),
            description = this.description(),
            image = this.image(),
            privateMembers = cache.load(decryptionCache.privateUserIdSet(this)),
            publicMembers = cache.load(this.publicUsersIdSet()),
        )

    suspend fun List<FollowSetEvent>.toUI() = this.map { it.toUI() }.sortedBy { it.title }

    val uiListFlow =
        latestLists
            .map { it.toUI() }
            .onStart { emit(latestLists.value.toUI()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun List<PeopleList>.select(dTag: String) =
        this.firstOrNull {
            it.identifierTag == dTag
        }

    fun selectList(dTag: String) = uiListFlow.value.select(dTag)

    fun selectListFlow(dTag: String) =
        uiListFlow
            .map { it.select(dTag) }
            .onStart { emit(selectList(dTag)) }

    fun DeletionRequestEvent.hasDeletedAnyPeopleList() = deleteAddressesWithKind(FollowSetEvent.KIND) || deletesAnyEventIn(peopleListsEventIds.value)

    fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            if (event.pubKey == signer.pubKey) {
                event is FollowSetEvent || (event is DeletionRequestEvent && event.hasDeletedAnyPeopleList())
            } else {
                false
            }
        }

    fun newNotes(newNotes: Set<Note>) {
        if (hasItemInNoteList(newNotes)) {
            forceRefresh()
        }
    }

    fun deletedNotes(deletedNotes: Set<Note>) {
        if (hasItemInNoteList(deletedNotes)) {
            forceRefresh()
        }
    }

    fun forceRefresh() {
        peopleListVersions.update { it + 1 }
    }

    // --------------
    // Updating Lists
    // --------------

    fun getPeopleListNote(noteIdentifier: String): AddressableNote? = existingPeopleListNotes().find { it.dTag() == noteIdentifier }

    fun getPeopleList(noteIdentifier: String): FollowSetEvent = getPeopleListNote(noteIdentifier)?.event as FollowSetEvent

    fun User.toUserTag() = UserTag(this.pubkeyHex, this.bestRelayHint())

    fun Set<User>.toUserTags() = map { it.toUserTag() }

    suspend fun addFollowList(
        listName: String,
        listDescription: String?,
        listImage: String?,
        member: User? = null,
        isPrivate: Boolean = false,
        account: Account,
    ): String {
        val dTag = Uuid.random().toString()
        val newListTemplate =
            FollowSetEvent.build(
                dTag = dTag,
                title = listName,
                publicMembers = if (!isPrivate && member != null) listOf(member.toUserTag()) else emptyList(),
                privateMembers = if (isPrivate && member != null) listOf(member.toUserTag()) else emptyList(),
                signer = account.signer,
            ) {
                if (listDescription != null) description(listDescription)
                if (listImage != null) image(listImage)
            }

        val newList = signer.sign(newListTemplate)

        account.sendMyPublicAndPrivateOutbox(newList)
        return dTag
    }

    suspend fun updateMetadata(
        listName: String?,
        listDescription: String?,
        listImage: String?,
        peopleList: PeopleList,
        account: Account,
    ) {
        val listEvent = getPeopleList(peopleList.identifierTag)

        val template =
            listEvent.update {
                if (listName != null) title(listName)
                if (listDescription != null) description(listDescription)
                if (listImage != null) image(listImage)
            }

        val newList = signer.sign(template)

        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun cloneFollowSet(
        currentPeopleList: PeopleList,
        customCloneName: String?,
        customCloneDescription: String?,
        account: Account,
    ) {
        val newList =
            FollowSetEvent.createListWithDescription(
                dTag = Uuid.random().toString(),
                title = customCloneName ?: currentPeopleList.title,
                description = customCloneDescription ?: currentPeopleList.description,
                publicMembers = currentPeopleList.publicMembers.toUserTags(),
                privateMembers = currentPeopleList.privateMembers.toUserTags(),
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun deleteFollowSet(
        identifierTag: String,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)
        val deletionEvent = account.signer.sign(DeletionRequestEvent.build(listOf(followListEvent)))
        account.sendMyPublicAndPrivateOutbox(deletionEvent)
        // Any screen whose persisted feed filter still points at this list would keep
        // re-creating an empty shell for its address (and render the dTag/UUID in the
        // top bar) — reset those filters to their default.
        account.settings.resetFeedFiltersPointingTo(followListEvent.address())
    }

    suspend fun addUserToSet(
        user: User,
        identifierTag: String,
        shouldBePrivateMember: Boolean,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)
        val newList =
            FollowSetEvent.addUser(
                earlierVersion = followListEvent,
                pubKeyHex = user.pubkeyHex,
                relayHint = user.bestRelayHint(),
                isPrivate = shouldBePrivateMember,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun addUserFirstToSet(
        user: User,
        identifierTag: String,
        shouldBePrivateMember: Boolean,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)
        val newList =
            FollowSetEvent.addUserFirst(
                earlierVersion = followListEvent,
                pubKeyHex = user.pubkeyHex,
                relayHint = user.bestRelayHint(),
                isPrivate = shouldBePrivateMember,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun removeUserFromSet(
        user: User,
        isPrivate: Boolean,
        identifierTag: String,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)
        val newList =
            FollowSetEvent.removeUser(
                earlierVersion = followListEvent,
                pubKeyHex = user.pubkeyHex,
                isUserPrivate = isPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }
}
