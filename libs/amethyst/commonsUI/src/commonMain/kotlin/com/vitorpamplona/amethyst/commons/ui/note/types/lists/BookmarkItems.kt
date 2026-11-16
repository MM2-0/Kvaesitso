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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastMapNotNullDense
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.HashtagTag
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.nip01Core.tags.references.ReferenceTag
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.tags.BookmarkIdTag

/**
 * One member of a NIP-51 bookmark-shaped list. The spec lets a bookmark list hold notes (`e`),
 * articles and other addressables (`a`), hashtags (`t`) and URLs (`r`); people (`p`) show up in
 * the wild too. Quartz's [BookmarkIdTag] covers only the first two, so a list of links or tags read
 * through it looks empty.
 */
@Immutable
sealed interface BookmarkItem {
    /** A stable key for `key(...)` in a member list. */
    val key: String

    @Immutable
    class NoteRef(
        val tag: BookmarkIdTag,
    ) : BookmarkItem {
        override val key = tag.toTagIdOnly().joinToString(":")
    }

    @Immutable
    class Hashtag(
        val hashtag: String,
    ) : BookmarkItem {
        // Hashtag feeds are case-insensitive, so `#Nostr` and `#nostr` are one member.
        override val key = "t:${hashtag.lowercase()}"
    }

    @Immutable
    class Link(
        val url: String,
    ) : BookmarkItem {
        override val key = "r:$url"
    }

    @Immutable
    class Person(
        val pubKey: HexKey,
    ) : BookmarkItem {
        override val key = "p:$pubKey"
    }

    companion object {
        fun parse(tag: Array<String>): BookmarkItem? {
            BookmarkIdTag.parse(tag)?.let { return NoteRef(it) }
            HashtagTag.parse(tag)?.let { return Hashtag(it) }
            ReferenceTag.parse(tag)?.let { return Link(it) }
            PTag.parseKey(tag)?.let { return Person(it) }
            return null
        }
    }
}

/** Every member this list carries, in tag order. Almost every tag of a bookmark list is a member. */
fun TagArray.bookmarkItems(): List<BookmarkItem> = fastMapNotNullDense(BookmarkItem::parse)

/**
 * The public members followed by the private ones (once decrypted), each member once: authors
 * bookmark the same note twice, or keep it both in the clear and in the encrypted half, and the
 * card should neither count nor draw it twice.
 */
fun distinctBookmarkMembers(
    public: List<BookmarkItem>,
    private: List<BookmarkItem>?,
): List<BookmarkItem> {
    val seen = HashSet<String>(public.size + (private?.size ?: 0))
    val result = ArrayList<BookmarkItem>(public.size + (private?.size ?: 0))
    public.forEach { if (seen.add(it.key)) result.add(it) }
    private?.forEach { if (seen.add(it.key)) result.add(it) }
    return result
}

/** How many members of each kind a bookmark list holds, for its header. */
@Immutable
data class BookmarkCounts(
    val notes: Int,
    val hashtags: Int,
    val links: Int,
    val people: Int,
) {
    companion object {
        fun of(items: List<BookmarkItem>): BookmarkCounts {
            var notes = 0
            var hashtags = 0
            var links = 0
            var people = 0
            items.forEach {
                when (it) {
                    is BookmarkItem.NoteRef -> notes++
                    is BookmarkItem.Hashtag -> hashtags++
                    is BookmarkItem.Link -> links++
                    is BookmarkItem.Person -> people++
                }
            }
            return BookmarkCounts(notes, hashtags, links, people)
        }
    }
}
