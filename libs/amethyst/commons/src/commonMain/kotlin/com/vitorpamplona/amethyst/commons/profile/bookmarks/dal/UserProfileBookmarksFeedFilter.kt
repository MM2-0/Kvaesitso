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
package com.vitorpamplona.amethyst.commons.profile.bookmarks.dal

import com.vitorpamplona.amethyst.commons.feeds.FeedFilter
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark

class UserProfileBookmarksFeedFilter(
    val user: User,
    val account: Account,
) : FeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex + "-" + user.pubkeyHex

    override fun feed(): List<Note> {
        val newBookmarks = getBookmarksFromNew()
        val oldBookmarks = getBookmarksFromOld()

        return (newBookmarks + oldBookmarks).distinctBy { it.idHex }.reversed()
    }

    private fun getBookmarksFromNew(): List<Note> {
        val note = LocalCache.getOrCreateAddressableNote(BookmarkListEvent.createBookmarkAddress(user.pubkeyHex))
        val noteEvent = note.event as? BookmarkListEvent ?: return emptyList()
        return resolveBookmarks(noteEvent.publicBookmarks())
    }

    private fun getBookmarksFromOld(): List<Note> {
        val note = LocalCache.getOrCreateAddressableNote(OldBookmarkListEvent.createBookmarkAddress(user.pubkeyHex))
        val noteEvent = note.event as? OldBookmarkListEvent ?: return emptyList()
        return resolveBookmarks(noteEvent.publicBookmarks())
    }

    private fun resolveBookmarks(bookmarks: List<BookmarkIdTag>): List<Note> =
        bookmarks.mapNotNull {
            when (it) {
                is AddressBookmark -> LocalCache.getOrCreateAddressableNote(it.address)
                is EventBookmark -> LocalCache.checkGetOrCreateNote(it.eventId)
            }
        }
}
