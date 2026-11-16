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
package com.vitorpamplona.amethyst.commons.model.nip51Bookmarks

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark

/**
 * Handles NIP-51 bookmark operations.
 * Shared between Android and Desktop.
 */
object BookmarkAction {
    /**
     * Creates a new bookmark list with a single event bookmarked.
     */
    suspend fun createWithBookmark(
        eventId: HexKey,
        relayHint: NormalizedRelayUrl? = null,
        isPrivate: Boolean = false,
        signer: NostrSigner,
    ): BookmarkListEvent {
        val bookmark = EventBookmark(eventId, relayHint)
        return BookmarkListEvent.create(
            bookmarkIdTag = bookmark,
            isPrivate = isPrivate,
            signer = signer,
        )
    }

    /**
     * Adds an event to an existing bookmark list.
     */
    suspend fun addBookmark(
        existingList: BookmarkListEvent,
        eventId: HexKey,
        relayHint: NormalizedRelayUrl? = null,
        isPrivate: Boolean = false,
        signer: NostrSigner,
    ): BookmarkListEvent {
        val bookmark = EventBookmark(eventId, relayHint)
        return BookmarkListEvent.add(
            earlierVersion = existingList,
            bookmarkIdTag = bookmark,
            isPrivate = isPrivate,
            signer = signer,
        )
    }

    /**
     * Removes an event from a bookmark list.
     * Checks both public and private bookmarks.
     */
    suspend fun removeBookmark(
        existingList: BookmarkListEvent,
        eventId: HexKey,
        signer: NostrSigner,
    ): BookmarkListEvent {
        val bookmark = EventBookmark(eventId)
        return BookmarkListEvent.remove(
            earlierVersion = existingList,
            bookmarkIdTag = bookmark,
            signer = signer,
        )
    }

    /**
     * Removes an event from a bookmark list (public or private specifically).
     */
    suspend fun removeBookmark(
        existingList: BookmarkListEvent,
        eventId: HexKey,
        isPrivate: Boolean,
        signer: NostrSigner,
    ): BookmarkListEvent {
        val bookmark = EventBookmark(eventId)
        return BookmarkListEvent.remove(
            earlierVersion = existingList,
            bookmarkIdTag = bookmark,
            isPrivate = isPrivate,
            signer = signer,
        )
    }

    /**
     * Checks if an event ID is in the public bookmarks.
     */
    fun isInPublicBookmarks(
        bookmarkList: BookmarkListEvent?,
        eventId: HexKey,
    ): Boolean {
        if (bookmarkList == null) return false
        return bookmarkList.publicBookmarks().any {
            it is EventBookmark && it.eventId == eventId
        }
    }

    /**
     * Checks if an event ID is in the private bookmarks.
     * Requires decryption via signer.
     */
    suspend fun isInPrivateBookmarks(
        bookmarkList: BookmarkListEvent?,
        eventId: HexKey,
        signer: NostrSigner,
    ): Boolean {
        if (bookmarkList == null) return false
        val privateBookmarks = bookmarkList.privateBookmarks(signer) ?: return false
        return privateBookmarks.any {
            it is EventBookmark && it.eventId == eventId
        }
    }

    /**
     * Checks if an event ID is bookmarked (public or private).
     */
    suspend fun isBookmarked(
        bookmarkList: BookmarkListEvent?,
        eventId: HexKey,
        signer: NostrSigner,
    ): Boolean =
        isInPublicBookmarks(bookmarkList, eventId) ||
            isInPrivateBookmarks(bookmarkList, eventId, signer)

    /**
     * Gets the bookmark list address for a user.
     */
    fun getBookmarkListAddress(pubKey: HexKey) = BookmarkListEvent.createBookmarkAddress(pubKey)
}
