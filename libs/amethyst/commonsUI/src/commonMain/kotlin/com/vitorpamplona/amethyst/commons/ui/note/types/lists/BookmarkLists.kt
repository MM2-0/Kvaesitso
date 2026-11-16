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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.kind_article_curation_set
import com.vitorpamplona.amethyst.commons.resources.kind_bookmark_list
import com.vitorpamplona.amethyst.commons.resources.kind_bookmark_set
import com.vitorpamplona.amethyst.commons.resources.kind_picture_curation_set
import com.vitorpamplona.amethyst.commons.resources.nip51_bookmark_count_hashtags
import com.vitorpamplona.amethyst.commons.resources.nip51_bookmark_count_links
import com.vitorpamplona.amethyst.commons.resources.nip51_bookmark_count_notes
import com.vitorpamplona.amethyst.commons.resources.nip51_bookmark_count_people
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.TopicChipFlow
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip51Lists.articleCurationSet.ArticleCurationSetEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkSet.BookmarkSetEvent
import com.vitorpamplona.quartz.nip51Lists.pictureCurationSet.PictureCurationSetEvent
import kotlinx.collections.immutable.toImmutableList

/** How many hashtag chips a bookmark card shows before a "+N more" chip. */
private const val COLLAPSED_HASHTAGS = 12

/** NIP-51 kind 10003: the user's bookmarks — notes, articles, hashtags and links. */
@Composable
fun RenderBookmarkList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? BookmarkListEvent ?: return

    val public = remember(noteEvent) { noteEvent.tags.bookmarkItems() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateTags(it)?.bookmarkItems() }

    BookmarkCard(
        title = listTitle(noteEvent.title(), null, Res.string.kind_bookmark_list),
        description = null,
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * NIP-51 kind 30001: the deprecated bookmark set. Still rendered, because the events are on relays
 * whether or not the spec wants them to be.
 */
@Suppress("DEPRECATION")
@Composable
fun RenderOldBookmarkList(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? OldBookmarkListEvent ?: return

    val public = remember(noteEvent) { noteEvent.tags.bookmarkItems() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateTags(it)?.bookmarkItems() }

    BookmarkCard(
        title = listTitle(noteEvent.title(), noteEvent.dTag(), Res.string.kind_bookmark_list),
        description = null,
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** NIP-51 kind 30003: "user-defined bookmarks categories, for when bookmarks must be in labeled separate groups". */
@Composable
fun RenderBookmarkSet(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? BookmarkSetEvent ?: return

    val public = remember(noteEvent) { noteEvent.tags.bookmarkItems() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateTags(it)?.bookmarkItems() }

    BookmarkCard(
        title = listTitle(noteEvent.titleOrName(), noteEvent.dTag(), Res.string.kind_bookmark_set),
        description = noteEvent.description(),
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** NIP-51 kind 30004: "groups of articles picked by users as interesting". */
@Composable
fun RenderArticleCurationSet(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? ArticleCurationSetEvent ?: return

    val public = remember(noteEvent) { noteEvent.tags.bookmarkItems() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateTags(it)?.bookmarkItems() }

    BookmarkCard(
        title = listTitle(noteEvent.title(), noteEvent.dTag(), Res.string.kind_article_curation_set),
        description = noteEvent.description(),
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/** NIP-51 kind 30006: "groups of pictures picked by users as interesting". */
@Composable
fun RenderPictureCurationSet(
    baseNote: Note,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = baseNote.event as? PictureCurationSetEvent ?: return

    val public = remember(noteEvent) { noteEvent.tags.bookmarkItems() }
    val private by loadPrivateItems(noteEvent, accountViewModel) { noteEvent.privateTags(it)?.bookmarkItems() }

    BookmarkCard(
        title = listTitle(noteEvent.title(), noteEvent.dTag(), Res.string.kind_picture_curation_set),
        description = noteEvent.description(),
        public = public,
        private = private,
        hidesPrivate = noteEvent.hidesPrivateMembers(private),
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * The five bookmark-shaped kinds differ only in where their name comes from; what they hold is the
 * same mix of notes, addressables, hashtags, links and people, so the body they draw is the same one.
 *
 * Hashtags are short, so they go in the header as a row of chips that open their feeds; the rest
 * are rows under it, collapsed like any other list card.
 */
@Composable
private fun BookmarkCard(
    title: String,
    description: String?,
    public: List<BookmarkItem>,
    private: List<BookmarkItem>?,
    hidesPrivate: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val all = remember(public, private) { distinctBookmarkMembers(public, private) }
    val counts = remember(all) { BookmarkCounts.of(all) }
    val hashtags = remember(all) { all.mapNotNull { (it as? BookmarkItem.Hashtag)?.hashtag }.toImmutableList() }
    val rows = remember(all) { all.filter { it !is BookmarkItem.Hashtag } }

    ListCard(
        title = title,
        description = description,
        items = rows,
        hasUnreadablePrivateItems = hidesPrivate && hashtags.isEmpty(),
        backgroundColor = backgroundColor,
        subtitle = {
            BookmarkCountsLine(counts)
            if (hashtags.isNotEmpty()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    // The chips sit above the member rows, outside their collapse, so a list of
                    // hundreds of hashtags would otherwise be hundreds of chips in a feed row.
                    TopicChipFlow(hashtags, nav, maxShown = COLLAPSED_HASHTAGS)
                }
            }
        },
    ) { item ->
        key(item.key) {
            when (item) {
                is BookmarkItem.NoteRef -> BookmarkMemberRow(item.tag, quotesLeft, backgroundColor, accountViewModel, nav)
                is BookmarkItem.Link -> LinkMemberRow(item.url, accountViewModel, nav)
                is BookmarkItem.Person -> UserMemberRow(item.pubKey, accountViewModel, nav)
                is BookmarkItem.Hashtag -> HashtagMemberRow(item.hashtag, nav)
            }
        }
    }
}

/** "3 notes · 2 hashtags · 1 link", skipping the kinds the list does not hold. */
@Composable
private fun BookmarkCountsLine(counts: BookmarkCounts) {
    val notes = if (counts.notes > 0) pluralStringRes(Res.plurals.nip51_bookmark_count_notes, counts.notes, counts.notes) else null
    val hashtags = if (counts.hashtags > 0) pluralStringRes(Res.plurals.nip51_bookmark_count_hashtags, counts.hashtags, counts.hashtags) else null
    val links = if (counts.links > 0) pluralStringRes(Res.plurals.nip51_bookmark_count_links, counts.links, counts.links) else null
    val people = if (counts.people > 0) pluralStringRes(Res.plurals.nip51_bookmark_count_people, counts.people, counts.people) else null

    val line = remember(notes, hashtags, links, people) { listOfNotNull(notes, hashtags, links, people).joinToString(" · ") }
    if (line.isEmpty()) return

    Text(
        text = line,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 2.dp),
    )
}
