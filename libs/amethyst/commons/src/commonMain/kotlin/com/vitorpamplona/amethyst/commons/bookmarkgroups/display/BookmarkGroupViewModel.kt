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
package com.vitorpamplona.amethyst.commons.bookmarkgroups.display

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.reflect.KClass

@Stable
class BookmarkGroupViewModel(
    val account: Account,
    val bookmarkGroupIdentifier: String,
) : ViewModel() {
    val selectedBookmarkGroupFlow =
        account.bookmarkSets
            .getBookmarkSetFlow(bookmarkGroupIdentifier)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2500), null)

    // Built once per model: as functions, every call added another collector to viewModelScope, and
    // the list views called them on every recomposition.
    val publicPosts: StateFlow<List<Note>> =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group -> group.publicPostBookmarks.map { account.cache.getOrCreateNote(it.eventId) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val privatePosts: StateFlow<List<Note>> =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group -> group.privatePostBookmarks.map { account.cache.getOrCreateNote(it.eventId) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val publicArticles: StateFlow<List<AddressableNote>> =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group -> group.publicArticleBookmarks.map { account.cache.getOrCreateAddressableNote(it.address) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val privateArticles: StateFlow<List<AddressableNote>> =
        selectedBookmarkGroupFlow
            .filterNotNull()
            .map { group -> group.privateArticleBookmarks.map { account.cache.getOrCreateAddressableNote(it.address) } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    suspend fun deleteBookmarkGroup(groupIdentifier: String) {
        account.bookmarkSets.deleteBookmarkList(groupIdentifier, account)
    }

    suspend fun addBookmarkToGroup(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmark: BookmarkIdTag,
        isPrivate: Boolean,
    ) {
        account.bookmarkSets.addBookmarkToList(
            bookmark,
            groupIdentifier,
            isPrivate,
            account,
        )
    }

    suspend fun movePostBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        postId: String,
        isCurrentlyPrivate: Boolean,
    ) {
        val eventBookmark = EventBookmark(postId)
        moveBookmark(groupIdentifier, eventBookmark, isCurrentlyPrivate)
    }

    suspend fun moveArticleBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        articleAddress: Address,
        isCurrentlyPrivate: Boolean,
    ) {
        val eventBookmark = AddressBookmark(articleAddress)
        moveBookmark(groupIdentifier, eventBookmark, isCurrentlyPrivate)
    }

    suspend fun moveBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmark: BookmarkIdTag,
        isCurrentlyPrivate: Boolean,
    ) {
        account.bookmarkSets.moveBookmarkInList(
            bookmark,
            groupIdentifier,
            isCurrentlyPrivate,
            account,
        )
    }

    suspend fun removePostBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmarkPostId: String,
        isPrivate: Boolean,
    ) {
        val eventBookmark = EventBookmark(bookmarkPostId)
        removeBookmarkFromGroup(
            groupIdentifier,
            eventBookmark,
            isPrivate,
        )
    }

    suspend fun removeArticleBookmark(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmarkArticleAddress: Address,
        isPrivate: Boolean,
    ) {
        val eventBookmark = AddressBookmark(bookmarkArticleAddress)
        removeBookmarkFromGroup(
            groupIdentifier,
            eventBookmark,
            isPrivate,
        )
    }

    suspend fun removeBookmarkFromGroup(
        groupIdentifier: String = bookmarkGroupIdentifier,
        bookmark: BookmarkIdTag,
        isPrivate: Boolean,
    ) {
        account.bookmarkSets.removeBookmarkFromList(
            bookmark,
            groupIdentifier,
            isPrivate,
            account,
        )
    }

    suspend fun removeDeletedBookmarksFromGroup(
        groupIdentifier: String = bookmarkGroupIdentifier,
        deletedEventIds: Set<String>,
        deletedAddresses: Set<Address>,
    ) {
        account.bookmarkSets.removeDeletedBookmarksFromList(
            groupIdentifier,
            deletedEventIds,
            deletedAddresses,
            account,
        )
    }

    @Suppress("UNCHECKED_CAST")
    class Initializer(
        val account: Account,
        val bookmarkGroupIdentifier: String,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(
            modelClass: KClass<T>,
            extras: CreationExtras,
        ): T = BookmarkGroupViewModel(account, bookmarkGroupIdentifier) as T
    }
}
