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
package com.vitorpamplona.amethyst.commons.model.nip51Lists.bookmarkSets

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.anyNotNullEvent
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.amethyst.commons.model.eventIdSet
import com.vitorpamplona.amethyst.commons.model.events
import com.vitorpamplona.amethyst.commons.model.nip51Lists.bookmarkSets.BookmarkSet
import com.vitorpamplona.amethyst.commons.model.updateFlow
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.update
import com.vitorpamplona.quartz.nip09Deletions.DeletionRequestEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.description
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.image
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.title
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

class BookmarkSetsState(
    val signer: NostrSigner,
    val cache: LocalCache,
    val scope: CoroutineScope,
) {
    val user = cache.getOrCreateUser(signer.pubKey)

    fun existingBookmarkSetNotes() = cache.addressables.filter(BookmarkSetEvent.KIND, user.pubkeyHex)

    val bookmarkSetVersions = MutableStateFlow(0)

    val bookmarkSetNotes =
        bookmarkSetVersions
            .map { existingBookmarkSetNotes() }
            .onStart { emit(existingBookmarkSetNotes()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val bookmarkSetEventIds =
        bookmarkSetNotes
            .map { it.eventIdSet() }
            .onStart { emit(bookmarkSetNotes.value.eventIdSet()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    val latestBookmarkLists: StateFlow<List<BookmarkSetEvent>> =
        bookmarkSetNotes
            .transformLatest { emitAll(it.updateFlow<BookmarkSetEvent>()) }
            .onStart { emit(bookmarkSetNotes.value.events()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun BookmarkSetEvent.toBookmarkSet() =
        BookmarkSet(
            identifier = dTag(),
            title = titleOrName() ?: dTag(),
            description = description(),
            image = image(),
            privateBookmarks = privateBookmarks(signer)?.toSet() ?: emptySet(),
            publicBookmarks = publicBookmarks().toSet(),
        )

    suspend fun List<BookmarkSetEvent>.toBookmarkSetsFeed() = map { it.toBookmarkSet() }.sortedBy { it.title }

    val listFeedFlow =
        latestBookmarkLists
            .map { it.toBookmarkSetsFeed() }
            .onStart { emit(latestBookmarkLists.value.toBookmarkSetsFeed()) }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun List<BookmarkSet>.getList(bookmarkListId: String) =
        this.firstOrNull {
            it.identifier == bookmarkListId
        }

    fun getBookmarkList(dTag: String) = listFeedFlow.value.getList(bookmarkListId = dTag)

    fun DeletionRequestEvent.hasAnyDeletedBookmarkLists() = deleteAddressesWithKind(BookmarkSetEvent.KIND) || deletesAnyEventIn(bookmarkSetEventIds.value)

    fun hasItemInNoteList(notes: Set<Note>): Boolean =
        notes.anyNotNullEvent { event ->
            if (event.pubKey == signer.pubKey) {
                event is BookmarkSetEvent || (event is DeletionRequestEvent && event.hasAnyDeletedBookmarkLists())
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
        bookmarkSetVersions.update { it + 1 }
    }

    fun getBookmarkSetNote(bookmarkIdentifier: String): AddressableNote? = existingBookmarkSetNotes().find { it.dTag() == bookmarkIdentifier }

    fun getBookmarkSetEvent(bookmarkIdentifier: String): BookmarkSetEvent = getBookmarkSetNote(bookmarkIdentifier)?.event as BookmarkSetEvent

    fun getBookmarkSetFlow(bookmarkIdentifier: String) =
        listFeedFlow
            .map { it.getList(bookmarkIdentifier) }
            .onStart {
                emit(
                    listFeedFlow.value.getList(bookmarkIdentifier),
                )
            }.flowOn(Dispatchers.IO)

    suspend fun addBookmarkSet(
        listName: String,
        listDescription: String? = null,
        listImage: String? = null,
        firstBookmark: BookmarkIdTag? = null,
        isBookmarkPrivate: Boolean = false,
        account: Account,
    ) {
        val newList =
            BookmarkSetEvent.create(
                title = listName,
                description = listDescription,
                image = listImage,
                publicBookmarks = if (!isBookmarkPrivate && firstBookmark != null) listOf(firstBookmark) else emptyList(),
                privateBookmarks = if (isBookmarkPrivate && firstBookmark != null) listOf(firstBookmark) else emptyList(),
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun updateMetadata(
        listName: String?,
        listDescription: String?,
        listImage: String?,
        bookmarkList: BookmarkSet,
        account: Account,
    ) {
        val listEvent = getBookmarkSetEvent(bookmarkList.identifier)

        val template =
            listEvent.update {
                if (listName != null) title(listName)
                if (listDescription != null) description(listDescription)
                if (listImage != null) image(listImage)
            }

        val newList = signer.sign(template)

        account.sendMyPublicAndPrivateOutbox(newList)
    }

    suspend fun renameBookmarkList(
        newName: String,
        bookmarkList: BookmarkSet,
        account: Account,
    ) {
        val listEvent = getBookmarkSetEvent(bookmarkList.identifier)
        val renamedList =
            BookmarkSetEvent.modifyName(
                earlierVersion = listEvent,
                newTitle = newName,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(renamedList)
    }

    suspend fun modifyListDescription(
        newDescription: String?,
        bookmarkList: BookmarkSet,
        account: Account,
    ) {
        val listEvent = getBookmarkSetEvent(bookmarkList.identifier)
        val modifiedList =
            BookmarkSetEvent.modifyDescription(
                earlierVersion = listEvent,
                newDescription = newDescription,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(modifiedList)
    }

    suspend fun cloneBookmarkList(
        currentBookmarkList: BookmarkSet,
        customCloneName: String?,
        customCloneDescription: String?,
        account: Account,
    ) {
        val clonedList =
            BookmarkSetEvent.create(
                title = customCloneName ?: currentBookmarkList.title,
                description = customCloneDescription ?: currentBookmarkList.description,
                publicBookmarks = currentBookmarkList.publicBookmarks.toList(),
                privateBookmarks = currentBookmarkList.privateBookmarks.toList(),
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(clonedList)
    }

    suspend fun deleteBookmarkList(
        bookmarkListIdentifier: String,
        account: Account,
    ) {
        val listEvent = getBookmarkSetEvent(bookmarkListIdentifier)
        val deletionEventTemplate = DeletionRequestEvent.build(listOf(listEvent))
        val deletionEvent = account.signer.sign(deletionEventTemplate)
        account.sendMyPublicAndPrivateOutbox(deletionEvent)
    }

    suspend fun addBookmarkToList(
        bookmark: BookmarkIdTag,
        bookmarkListIdentifier: String,
        isBookmarkPrivate: Boolean,
        account: Account,
    ) {
        val currentBookmarkList = getBookmarkSetEvent(bookmarkListIdentifier)
        val updatedList =
            BookmarkSetEvent.addBookmark(
                earlierVersion = currentBookmarkList,
                bookmarkIdTag = bookmark,
                isPrivate = isBookmarkPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(updatedList)
    }

    suspend fun moveBookmarkInList(
        bookmark: BookmarkIdTag,
        bookmarkListIdentifier: String,
        isBookmarkCurrentlyPrivate: Boolean,
        account: Account,
    ) {
        val bookmarkList = getBookmarkSetEvent(bookmarkListIdentifier)
        val updatedList =
            BookmarkSetEvent.moveBookmark(
                earlierVersion = bookmarkList,
                bookmarkIdTag = bookmark,
                isCurrentlyPrivate = isBookmarkCurrentlyPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(updatedList)
    }

    suspend fun removeBookmarkFromList(
        bookmark: BookmarkIdTag,
        bookmarkListIdentifier: String,
        isBookmarkPrivate: Boolean,
        account: Account,
    ) {
        val currentBookmarkList = getBookmarkSetEvent(bookmarkListIdentifier)
        val updatedList =
            BookmarkSetEvent.removeBookmark(
                earlierVersion = currentBookmarkList,
                bookmarkIdTag = bookmark,
                isPrivate = isBookmarkPrivate,
                signer = account.signer,
            )
        account.sendMyPublicAndPrivateOutbox(updatedList)
    }

    suspend fun removeDeletedBookmarksFromList(
        bookmarkListIdentifier: String,
        deletedEventIds: Set<String>,
        deletedAddresses: Set<Address>,
        account: Account,
    ) {
        if (deletedEventIds.isEmpty() && deletedAddresses.isEmpty()) return

        val currentList = getBookmarkSetNote(bookmarkListIdentifier)?.event as? BookmarkSetEvent ?: return

        val newPublicTags =
            currentList.tags
                .filter { tag ->
                    when (val bookmark = BookmarkIdTag.parse(tag)) {
                        is EventBookmark -> bookmark.eventId !in deletedEventIds
                        is AddressBookmark -> bookmark.address !in deletedAddresses
                        null -> true
                    }
                }.toTypedArray()

        val oldPrivateTags = currentList.privateTags(account.signer)

        val updatedList =
            if (oldPrivateTags == null) {
                if (newPublicTags.size == currentList.tags.size) return
                BookmarkSetEvent.resign(
                    content = currentList.content,
                    tags = newPublicTags,
                    signer = account.signer,
                )
            } else {
                val newPrivateTags =
                    oldPrivateTags
                        .filter { tag ->
                            when (val bookmark = BookmarkIdTag.parse(tag)) {
                                is EventBookmark -> bookmark.eventId !in deletedEventIds
                                is AddressBookmark -> bookmark.address !in deletedAddresses
                                null -> true
                            }
                        }.toTypedArray()
                if (newPublicTags.size == currentList.tags.size && newPrivateTags.size == oldPrivateTags.size) return
                BookmarkSetEvent.resign(
                    tags = newPublicTags,
                    privateTags = newPrivateTags,
                    signer = account.signer,
                )
            }

        account.sendMyPublicAndPrivateOutbox(updatedList)
    }
}
