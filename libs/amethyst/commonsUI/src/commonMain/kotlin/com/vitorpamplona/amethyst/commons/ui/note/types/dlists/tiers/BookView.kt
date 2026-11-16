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
package com.vitorpamplona.amethyst.commons.ui.note.types.dlists.tiers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListImageShape
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListItemView
import com.vitorpamplona.amethyst.commons.decentralizedLists.DListLink
import com.vitorpamplona.amethyst.commons.decentralizedLists.ResolvedConcept
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.Book
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.BookFields
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.bookOf
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.booksByAuthor
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.similarBooks
import com.vitorpamplona.amethyst.commons.decentralizedLists.tiers.topSubjects
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.chips_n_more
import com.vitorpamplona.amethyst.commons.resources.dlist_book_attribution
import com.vitorpamplona.amethyst.commons.resources.dlist_book_by
import com.vitorpamplona.amethyst.commons.resources.dlist_book_more_by
import com.vitorpamplona.amethyst.commons.resources.dlist_book_pages
import com.vitorpamplona.amethyst.commons.resources.dlist_book_similar
import com.vitorpamplona.amethyst.commons.resources.dlist_book_subjects
import com.vitorpamplona.amethyst.commons.resources.show_less
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListDefinitionLine
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListKicker
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListLinkButtons
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListMoreFields
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.DListPicture
import com.vitorpamplona.amethyst.commons.ui.note.types.dlists.observeListItems
import com.vitorpamplona.amethyst.commons.ui.note.types.trust.TrustSectionLabel
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SHELF = 6
private const val SUBJECTS_SHOWN = 4

/**
 * A book on its own page (tier three, Brainstorm `components/items/BookView`), the way a bookshop
 * shows one: the cover large on the left, the title and the author leading, the year, pages and
 * language under them, the definition's links (Open Library, a shop) as buttons, a few subjects,
 * and shelves from the same list: more by the author, and the books that share its subjects.
 *
 * Everything here is what the item says. Brainstorm also asks Open Library for the description,
 * subtitle, publisher and rating through its own origin; there is no such proxy here, so they are
 * not fetched.
 */
@Composable
internal fun BookView(
    event: Event,
    resolved: ResolvedConcept,
    hinted: DListItemView,
    links: List<DListLink>,
    listedNote: Note?,
    openList: (() -> Unit)?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val definition = resolved.governing
    val book = remember(event) { bookOf(event.tags) } ?: return

    DListKicker(MaterialSymbols.MenuBook, definition.singular, null, openList)

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        DListPicture(book.cover, null, 160.dp, accountViewModel, DListImageShape.PORTRAIT, mark = MaterialSymbols.MenuBook)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = book.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            book.author?.let {
                Text(text = stringRes(Res.string.dlist_book_by, it), style = MaterialTheme.typography.titleMedium)
            }
            val pages = book.pages?.let { pluralStringRes(Res.plurals.dlist_book_pages, it, it) }
            val meta = listOfNotNull(book.year, pages, book.language)
            if (meta.isNotEmpty()) {
                Text(text = meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    DListLinkButtons(links)

    if (book.subjects.isNotEmpty()) Subjects(book.subjects)

    if (listedNote != null) Shelves(event, book, listedNote, resolved, accountViewModel, nav)

    Text(
        text = stringRes(Res.string.dlist_book_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val used = remember(hinted) { hinted.usedFields + BookFields.ALL }
    DListMoreFields(hinted.moreFields.filter { it.field !in used }, hinted.undeclared.filter { it.field !in used }, definition.singular)
    DListDefinitionLine(resolved)
}

/** The subjects as one quiet line: a few a reader would recognise, the rest behind a count. */
@Composable
private fun Subjects(all: List<String>) {
    var open by rememberSaveable { mutableStateOf(false) }
    val few = remember(all) { topSubjects(all, SUBJECTS_SHOWN) }
    val shown = if (open) few + all.filter { it !in few } else few
    if (shown.isEmpty()) return
    val hidden = all.size - few.size
    Column {
        TrustSectionLabel(stringRes(Res.string.dlist_book_subjects))
        Text(text = shown.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hidden > 0) {
            Text(
                text = if (open) stringRes(Res.string.show_less) else pluralStringRes(Res.plurals.chips_n_more, hidden, hidden),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { open = !open },
            )
        }
    }
}

/** More by the author, then the list's books that share its subjects. */
@Composable
private fun Shelves(
    event: Event,
    book: Book,
    listedNote: Note,
    resolved: ResolvedConcept,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val header by observeNoteEvent<Event>(listedNote, accountViewModel)
    val headerEvent = header ?: return
    val items = observeListItems(listedNote, headerEvent, accountViewModel)
    // Every book of the list read once per change, off the main thread, and the shelves from those.
    val shelves by produceState(Pair(emptyList<Pair<Note, Book>>(), emptyList<Pair<Note, Book>>()), items, event, book) {
        value =
            withContext(Dispatchers.Default) {
                val others = items.mapNotNull { note -> note.event?.takeIf { it.id != event.id }?.let { e -> bookOf(e.tags)?.let { note to it } } }
                val byNote = others.toMap()
                val byAuthor = booksByAuthor(book, others).take(SHELF)
                val similar = similarBooks(book, others).filter { it !in byAuthor }.take(SHELF)
                Pair(byAuthor.map { it to byNote.getValue(it) }, similar.map { it to byNote.getValue(it) })
            }
    }
    val (byAuthor, similar) = shelves

    if (byAuthor.isNotEmpty() && book.author != null) Shelf(stringRes(Res.string.dlist_book_more_by, book.author), byAuthor, accountViewModel, nav)
    if (similar.isNotEmpty()) Shelf(stringRes(Res.string.dlist_book_similar), similar, accountViewModel, nav)
}

@Composable
private fun Shelf(
    heading: String,
    books: List<Pair<Note, Book>>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TrustSectionLabel(heading)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        books.forEach { (note, other) ->
            Column(
                modifier =
                    Modifier
                        .width(80.dp)
                        .clickable { routeFor(note, accountViewModel.account)?.let { nav.nav(it) } },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DListPicture(other.cover, null, 114.dp, accountViewModel, DListImageShape.PORTRAIT, mark = MaterialSymbols.MenuBook)
                Text(text = other.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
