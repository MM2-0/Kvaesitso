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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.interestSets

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.anyNotNullEvent
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.eventIdSet
import com.vitorpamplona.amethyst.commons.model.events
import com.vitorpamplona.amethyst.commons.model.nip51Lists.interestSets.InterestSet
import com.vitorpamplona.amethyst.commons.model.updateFlow
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip51Lists.interestSet.InterestSetEvent
import com.vitorpamplona.quartz.nip51Lists.tags.TitleTag
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

class InterestSetsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    fun existingInterestSetNotes() = cache.addressables.filter(InterestSetEvent.KIND, user.pubkeyHex)

    val interestSetVersions = MutableStateFlow(0)

    val interestSetNotes =
        interestSetVersions
            .map { existingInterestSetNotes() }
            .onStart { emit(existingInterestSetNotes()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val interestSetEventIds =
        interestSetNotes
            .map { it.eventIdSet() }
            .onStart { emit(interestSetNotes.value.eventIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestInterestSets: StateFlow<List<InterestSetEvent>> =
        interestSetNotes
            .transformLatest { emitAll(it.updateFlow<InterestSetEvent>()) }
            .onStart { emit(interestSetNotes.value.events()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun InterestSetEvent.toInterestSet(): InterestSet {
        val title = tags.firstNotNullOfOrNull(TitleTag::parse) ?: dTag()
        val publics = publicHashtags().toSet()
        val privates = privateHashtags(signer)?.toSet() ?: emptySet()
        return InterestSet(
            identifier = dTag(),
            title = title,
            publicHashtags = publics,
            privateHashtags = privates,
        )
    }

    suspend fun List<InterestSetEvent>.toInterestSetsFeed() = map { it.toInterestSet() }.sortedBy { it.title }

    val listFeedFlow =
        latestInterestSets
            .map { it.toInterestSetsFeed() }
            .onStart { emit(latestInterestSets.value.toInterestSetsFeed()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun List<InterestSet>.getSet(identifier: String) = firstOrNull { it.identifier == identifier }

    fun getInterestSet(dTag: String) = listFeedFlow.value.getSet(dTag)

    fun DeletionRequestEvent.hasAnyDeletedInterestSets() = deleteAddressesWithKind(InterestSetEvent.KIND) || deletesAnyEventIn(interestSetEventIds.value)

    fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            if (event.pubKey == signer.pubKey) {
                event is InterestSetEvent || (event is DeletionRequestEvent && event.hasAnyDeletedInterestSets())
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
        interestSetVersions.update { it + 1 }
    }

    fun getInterestSetNote(identifier: String): AddressableNote? = existingInterestSetNotes().find { it.dTag() == identifier }

    fun getInterestSetEvent(identifier: String): InterestSetEvent = getInterestSetNote(identifier)?.event as InterestSetEvent

    suspend fun createInterestSet(
        title: String,
        firstHashtag: String? = null,
        isHashtagPrivate: Boolean = false,
        account: Account,
    ) {
        val publicHashtags = if (firstHashtag != null && !isHashtagPrivate) listOf(firstHashtag.lowercase()) else emptyList()
        val privateHashtags = if (firstHashtag != null && isHashtagPrivate) listOf(firstHashtag.lowercase()) else emptyList()

        val newSet =
            InterestSetEvent.create(
                title = title,
                publicHashtags = publicHashtags,
                privateHashtags = privateHashtags,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newSet)
    }

    suspend fun renameInterestSet(
        newName: String,
        set: InterestSet,
        account: Account,
    ) {
        val template =
            InterestSetEvent.build(
                title = newName,
                publicHashtags = set.publicHashtags.toList(),
                privateHashtags = set.privateHashtags.toList(),
                dTag = set.identifier,
                signer = account.signer,
            )
        val renamed = account.signer.sign(template)
        account.sendMyPublicAndPrivateOutbox(renamed)
    }

    suspend fun deleteInterestSet(
        identifier: String,
        account: Account,
    ) {
        val event = getInterestSetEvent(identifier)
        val template = DeletionRequestEvent.build(listOf(event))
        val deletionEvent = account.signer.sign(template)
        account.sendMyPublicAndPrivateOutbox(deletionEvent)
    }

    suspend fun cloneInterestSet(
        source: InterestSet,
        customName: String?,
        account: Account,
    ) {
        val cloned =
            InterestSetEvent.create(
                title = customName ?: source.title,
                publicHashtags = source.publicHashtags.toList(),
                privateHashtags = source.privateHashtags.toList(),
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(cloned)
    }

    suspend fun addHashtagToSet(
        hashtag: String,
        identifier: String,
        isPrivate: Boolean,
        account: Account,
    ) {
        val event = getInterestSetEvent(identifier)
        val updated =
            InterestSetEvent.add(
                earlierVersion = event,
                hashtag = hashtag.lowercase(),
                isPrivate = isPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(updated)
    }

    suspend fun removeHashtagFromSet(
        hashtag: String,
        identifier: String,
        account: Account,
    ) {
        val event = getInterestSetEvent(identifier)
        val updated =
            InterestSetEvent.remove(
                earlierVersion = event,
                hashtag = hashtag,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(updated)
    }

    suspend fun moveHashtagInSet(
        hashtag: String,
        identifier: String,
        isCurrentlyPrivate: Boolean,
        account: Account,
    ) {
        val event = getInterestSetEvent(identifier)
        val removed =
            InterestSetEvent.remove(
                earlierVersion = event,
                hashtag = hashtag,
                signer = account.signer,
            )
        val moved =
            InterestSetEvent.add(
                earlierVersion = removed,
                hashtag = hashtag.lowercase(),
                isPrivate = !isCurrentlyPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(moved)
    }

    /**
     * Cached decrypted hashtags per set identifier. Used by the TopNav feed filter
     * to avoid blocking decryption on hot flows. Populated from [listFeedFlow].
     */
    val hashtagsByIdentifier: StateFlow<Map<String, Set<String>>> =
        listFeedFlow
            .map { list ->
                list.associate { it.identifier to it.allHashtags }
            }.onStart {
                emit(listFeedFlow.value.associate { it.identifier to it.allHashtags })
            }.flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun hashtagsFor(identifier: String): Set<String> = hashtagsByIdentifier.value[identifier].orEmpty()
}
