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
package com.vitorpamplona.amethyst.commons.ui.note.types.lists

import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.AddressBookmark
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.EventBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BookmarkItemTest {
    private val eventId = "a".repeat(64)
    private val pubKey = "b".repeat(64)

    @Test
    fun parsesEveryBookmarkMemberType() {
        val tags =
            arrayOf(
                arrayOf("d", "reading"),
                arrayOf("e", eventId),
                arrayOf("a", "30023:$pubKey:my-article"),
                arrayOf("t", "nostr"),
                arrayOf("r", "https://example.com/post"),
                arrayOf("p", pubKey),
                arrayOf("title", "Reading"),
            )

        val items = tags.bookmarkItems()

        assertEquals(5, items.size)
        assertIs<EventBookmark>(assertIs<BookmarkItem.NoteRef>(items[0]).tag)
        assertIs<AddressBookmark>(assertIs<BookmarkItem.NoteRef>(items[1]).tag)
        assertEquals("nostr", assertIs<BookmarkItem.Hashtag>(items[2]).hashtag)
        assertEquals("https://example.com/post", assertIs<BookmarkItem.Link>(items[3]).url)
        assertEquals(pubKey, assertIs<BookmarkItem.Person>(items[4]).pubKey)

        assertEquals(BookmarkCounts(notes = 2, hashtags = 1, links = 1, people = 1), BookmarkCounts.of(items))
    }

    @Test
    fun skipsMalformedMembers() {
        assertNull(BookmarkItem.parse(arrayOf("e", "short")))
        assertNull(BookmarkItem.parse(arrayOf("p", "short")))
        assertNull(BookmarkItem.parse(arrayOf("t", "")))
        assertNull(BookmarkItem.parse(arrayOf("r")))
    }

    @Test
    fun keysAreDistinctPerType() {
        val keys = listOf(BookmarkItem.Hashtag("x"), BookmarkItem.Link("x"), BookmarkItem.Person("x")).map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun publicAndPrivateDuplicatesCountOnce() {
        val public = arrayOf(arrayOf("e", eventId), arrayOf("t", "nostr"), arrayOf("e", eventId)).bookmarkItems()
        val private = arrayOf(arrayOf("e", eventId), arrayOf("t", "Nostr"), arrayOf("r", "https://example.com")).bookmarkItems()

        val members = distinctBookmarkMembers(public, private)

        assertEquals(listOf("e", "t", "r"), members.map { it.key.substringBefore(":") })
        assertEquals(BookmarkCounts(notes = 1, hashtags = 1, links = 1, people = 0), BookmarkCounts.of(members))
    }

    @Test
    fun undecryptedPrivateMembersAreIgnored() {
        val public = arrayOf(arrayOf("p", pubKey), arrayOf("p", pubKey)).bookmarkItems()
        assertEquals(1, distinctBookmarkMembers(public, null).size)
    }
}
