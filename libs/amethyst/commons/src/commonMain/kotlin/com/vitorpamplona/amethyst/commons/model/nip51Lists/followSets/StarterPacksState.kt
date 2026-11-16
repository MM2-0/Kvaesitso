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
import com.vitorpamplona.amethyst.commons.model.nip51Lists.followSets.PeopleList
import com.vitorpamplona.amethyst.commons.model.updateFlow
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.update
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.starterPack.StarterPackEvent
import com.vitorpamplona.quartz.nip51Lists.starterPack.description
import com.vitorpamplona.quartz.nip51Lists.starterPack.image
import com.vitorpamplona.quartz.nip51Lists.starterPack.person
import com.vitorpamplona.quartz.nip51Lists.starterPack.personFirst
import com.vitorpamplona.quartz.nip51Lists.starterPack.removePerson
import com.vitorpamplona.quartz.nip51Lists.starterPack.title
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
class StarterPacksState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    // Hides shells that a kind-5 deletion event from the list's author has already
    // deleted (e.g. a persisted TopFilter re-creates an empty shell for the deleted
    // address after a restart, and its name falls back to the dTag/UUID). Shells that
    // are merely not loaded yet stay in the list so the UI can subscribe and fetch
    // them from relays.
    fun existingPeopleListNotes() =
        cache.addressables
            .filter(StarterPackEvent.KIND, user.pubkeyHex)
            .filter { it.event != null || !cache.hasBeenDeleted(it.address) }

    val followListVersions = MutableStateFlow(0)

    val starterPackNotes =
        followListVersions
            .map { existingPeopleListNotes() }
            .onStart { emit(existingPeopleListNotes()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val starterPacksEventIds =
        starterPackNotes
            .map { it.eventIdSet() }
            .onStart { emit(starterPackNotes.value.eventIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestLists: StateFlow<List<StarterPackEvent>> =
        starterPackNotes
            .transformLatest { emitAll(it.updateFlow<StarterPackEvent>()) }
            .onStart { emit(starterPackNotes.value.events()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun List<StarterPackEvent>.mapToUserIdSet() = this.map { it.followIdSet() }.flattenToSet()

    val allPeopleListProfiles: StateFlow<Set<HexKey>> =
        latestLists
            .map { it.mapToUserIdSet() }
            .onStart { emit(latestLists.value.mapToUserIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun StarterPackEvent.toUI() =
        PeopleList(
            identifierTag = this.dTag(),
            title = this.title() ?: this.dTag(),
            description = this.description(),
            image = this.image(),
            privateMembers = emptySet(),
            publicMembers = cache.load(this.followIdSet()),
        )

    fun List<StarterPackEvent>.toUI() = this.map { it.toUI() }

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

    fun isUserInFollowSets(user: User): Boolean = allPeopleListProfiles.value.contains(user.pubkeyHex)

    fun DeletionRequestEvent.hasDeletedAnyStarterPack() = deleteAddressesWithKind(StarterPackEvent.KIND) || deletesAnyEventIn(starterPacksEventIds.value)

    fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            if (event.pubKey == signer.pubKey) {
                event is StarterPackEvent || (event is DeletionRequestEvent && event.hasDeletedAnyStarterPack())
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
        followListVersions.update { it + 1 }
    }

    // --------------
    // Updating Lists
    // --------------

    fun getPeopleListNote(noteIdentifier: String): AddressableNote? = existingPeopleListNotes().find { it.dTag() == noteIdentifier }

    fun getPeopleList(noteIdentifier: String): StarterPackEvent = getPeopleListNote(noteIdentifier)?.event as StarterPackEvent

    fun User.toUserTag() = UserTag(this.pubkeyHex, this.bestRelayHint())

    fun Set<User>.toUserTags() = map { it.toUserTag() }

    suspend fun addFollowList(
        name: String,
        desc: String?,
        image: String?,
        member: User? = null,
        isPrivate: Boolean = false,
        account: Account,
    ): String {
        val dTag = Uuid.random().toString()

        val newListTemplate =
            StarterPackEvent.build(
                name = name,
                people = if (!isPrivate && member != null) listOf(member.toUserTag()) else emptyList(),
                dTag = dTag,
            ) {
                if (desc != null) description(desc)
                if (image != null) image(image)
            }

        val newList = signer.sign(newListTemplate)

        account.sendMyPublicAndPrivateOutbox(newList)
        return dTag
    }

    suspend fun updateMetadata(
        name: String?,
        desc: String?,
        image: String?,
        peopleList: PeopleList,
        account: Account,
    ) {
        val listEvent = getPeopleList(peopleList.identifierTag)

        val template =
            listEvent.update {
                if (name != null) title(name)
                if (desc != null) description(desc)
                if (image != null) image(image)
            }

        val newList = signer.sign(template)

        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun cloneFollowSet(
        currentFollowPack: PeopleList,
        customCloneName: String?,
        customCloneDescription: String?,
        account: Account,
    ) {
        val listEvent = getPeopleList(currentFollowPack.identifierTag)

        val template =
            listEvent.update {
                // new list
                dTag(Uuid.random().toString())

                // updates names
                if (customCloneName != null) title(customCloneName)
                if (customCloneDescription != null) description(customCloneDescription)
            }

        val newList = signer.sign(template)

        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun deleteFollowSet(
        identifierTag: String,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)
        val deletionEvent = account.signer.sign(DeletionRequestEvent.build(listOf(followListEvent)))
        account.sendMyPublicAndPrivateOutbox(deletionEvent)
        // Any screen whose persisted feed filter still points at this follow pack would
        // keep re-creating an empty shell for its address (and render the dTag/UUID in
        // the top bar) — reset those filters to their default.
        account.settings.resetFeedFiltersPointingTo(followListEvent.address())
    }

    suspend fun addUserToSet(
        user: User,
        identifierTag: String,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)

        val template =
            followListEvent.update {
                person(user.pubkeyHex, user.bestRelayHint())
            }

        val newList = signer.sign(template)

        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun addUserFirstToSet(
        user: User,
        identifierTag: String,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)

        val template =
            followListEvent.update {
                personFirst(user.pubkeyHex, user.bestRelayHint())
            }

        val newList = signer.sign(template)

        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun removeUserFromSet(
        user: User,
        identifierTag: String,
        account: Account,
    ) {
        val followListEvent = getPeopleList(identifierTag)

        val template =
            followListEvent.update {
                removePerson(user.pubkeyHex)
            }

        val newList = signer.sign(template)

        account.sendMyPublicAndPrivateOutbox(newList)
    }
}
