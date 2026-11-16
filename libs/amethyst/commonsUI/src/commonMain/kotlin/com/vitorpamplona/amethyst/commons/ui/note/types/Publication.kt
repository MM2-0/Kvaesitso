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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.resources.publication_contents
import com.vitorpamplona.amethyst.commons.resources.publication_more_sections
import com.vitorpamplona.amethyst.commons.resources.publication_section_count
import com.vitorpamplona.amethyst.commons.resources.publication_untitled_section
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.library.BlossomPieceIndexEvent
import com.vitorpamplona.quartz.experimental.library.BookshelfDirectoryEvent
import com.vitorpamplona.quartz.experimental.library.LearningResourceEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationSectionRef
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import kotlinx.collections.immutable.toImmutableList

// A 2:3 portrait cover — a book jacket, which is what NKBIP-01's default `book` type is. The
// wide 16:9 hero LongForm uses would letterbox every one of them.
private const val COVER_ASPECT = 2f / 3f
private val CoverWidth = 96.dp

// A feed card lists a taste of the contents rather than a whole book's worth of rows; each row
// carries its own relay subscription, so the cap is a fetch budget as much as a layout one.
private const val MAX_PREVIEW_SECTIONS = 12

/**
 * The cap the thread view uses.
 *
 * Not unbounded: this table of contents is a plain `Column` inside one lazy-list item, so every
 * row it draws is composed at once and every row opens its own event observation and relay
 * subscription. Real books make that a lot of rows -- of 897 indexes on the public relays, 234
 * list more than 12 sections, 21 list more than 100, and the largest lists 240.
 *
 * Big enough that almost every publication still shows its whole contents, small enough that the
 * few that do not cannot cost 240 subscriptions in one frame. Past it the row count is shown, and
 * the previous/next pager walks the rest.
 */
const val MAX_THREAD_SECTIONS = 60

/**
 * Renders a kind-30040 NKBIP-01 publication index: cover, title, author, and what the index knows
 * about the work.
 *
 * The index event carries no `content` of its own — the prose lives in the kind-30041 sections it
 * points at, which we do not parse yet. So this deliberately renders the *card*, not a reader: it
 * exists so a publication linked from an [EntityRating] resolves to something, rather than to a
 * note with nothing in it but a hashtag.
 */
@Composable
fun RenderPublicationIndex(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: 30040 is addressable, so a re-published index (a new edition, a
    // reordered table of contents) replaces the event on the same Note instance. `Note` is @Stable
    // and `event` is a plain @Volatile var, so a bare read registers no snapshot dependency and
    // Compose would keep showing the superseded index.
    val observedEvent by observeNoteEvent<PublicationIndexEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    PublicationHeader(noteEvent, note, makeItShort, canPreview, quotesLeft, backgroundColor, accountViewModel, nav)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicationHeader(
    noteEvent: PublicationIndexEvent,
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
    // A feed card shows a taste of the contents; the thread view is where you actually read, so
    // it lifts the cap.
    maxSections: Int = MAX_PREVIEW_SECTIONS,
) {
    // `title` is mandatory in NKBIP-01 and missing from events in the wild, hence the slug
    // fallback rather than a blank card.
    val title = remember(noteEvent) { noteEvent.titleOrIdentifier() }
    val author = remember(noteEvent) { noteEvent.author()?.ifBlank { null } }
    val summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } }
    val cover = remember(noteEvent) { noteEvent.image()?.ifBlank { null } }
    val type = remember(noteEvent) { noteEvent.type().replaceFirstChar { it.uppercase() } }
    val version = remember(noteEvent) { noteEvent.version()?.ifBlank { null } }
    val sectionCount = remember(noteEvent) { noteEvent.sectionCount() }
    val topics =
        remember(noteEvent) {
            noteEvent
                .topics()
                .distinct()
                .take(3)
                .toImmutableList()
        }

    // No card: a 30040 carries no content beside this, so the note row's own frame is the only
    // one there should be. `replyModifier` here would draw a quote border around the whole post
    // and read as a citation of something else. (LongForm splits the same way -- card in the feed
    // row, bare column in the thread.)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            PublicationCover(cover, accountViewModel)

            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                author?.let {
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    // `type` is open-ended spec vocabulary, not a translatable label, so it is
                    // shown as published rather than mapped through a string table that would
                    // silently drop every value the spec has not enumerated.
                    text =
                        buildList {
                            add(type)
                            version?.let { add(it) }
                            if (sectionCount > 0) {
                                add(pluralStringRes(Res.plurals.publication_section_count, sectionCount, sectionCount))
                            }
                        }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }

        summary?.let {
            // A blurb is authored prose like any other: it deserves the translate offer and live
            // links/mentions that a bare Text would render as dead literal characters.
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = it,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                tags = tags,
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        if (topics.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Size5dp),
                verticalArrangement = Arrangement.spacedBy(Size5dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                topics.forEach { PublicationTopicChip(it) }
            }
        }

        PublicationTableOfContents(noteEvent, maxSections, accountViewModel, nav)
    }
}

/**
 * The publication's table of contents: its `a` tags, in the order the index lists them, each
 * opening that section.
 *
 * Without this a publication is a cover that announces "34 sections" and offers no way to read
 * one — the sections parse and render individually, but nothing ever links to them.
 *
 * Rows resolve lazily. [observeNoteEvent] drives the EventFinder subscription per row, so a
 * section Amethyst has never seen is fetched by being listed; until it arrives the row shows its
 * position and a placeholder rather than collapsing, so the contents keep their shape.
 */
@Composable
private fun PublicationTableOfContents(
    noteEvent: PublicationIndexEvent,
    maxSections: Int,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val sections = remember(noteEvent) { noteEvent.sections().toImmutableList() }

    if (sections.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = stringRes(Res.string.publication_contents),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.grayText,
            modifier = Modifier.padding(bottom = Size5dp),
        )

        sections.take(maxSections).forEachIndexed { index, ref ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }

            PublicationSectionRow(index + 1, ref, accountViewModel, nav)
        }

        if (sections.size > maxSections) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

            val remaining = sections.size - maxSections
            Text(
                text = pluralStringRes(Res.plurals.publication_more_sections, remaining, remaining),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
    }
}

/**
 * One contents entry.
 *
 * The index's own title is used when it has one, so the whole table of contents is readable
 * immediately — no round trip. The section event is still observed, because it carries the
 * better title and because observing is what fetches it; when it lands the row upgrades in
 * place. An entry listed only by event id has nothing to show until then.
 */
@Composable
internal fun PublicationSectionRow(
    position: Int,
    ref: PublicationSectionRef,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val address = ref.address

    if (address != null) {
        LoadAddressableNote(address) { sectionNote ->
            if (sectionNote != null) {
                ObservedSectionRow(position, ref, sectionNote, accountViewModel, nav)
            } else {
                SectionRowContent(position, ref, ref.title ?: ref.fallbackTitle(), null)
            }
        }
    } else if (ref.eventId != null) {
        LoadNote(ref.eventId!!) { sectionNote ->
            if (sectionNote != null) {
                ObservedSectionRow(position, ref, sectionNote, accountViewModel, nav)
            } else {
                SectionRowContent(position, ref, ref.title ?: ref.fallbackTitle(), null)
            }
        }
    }
}

@Composable
private fun ObservedSectionRow(
    position: Int,
    ref: PublicationSectionRef,
    sectionNote: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observing drives the EventFinder subscription, so a section listed but never seen is
    // fetched by appearing here.
    val sectionEvent by observeNoteEvent<Event>(sectionNote, accountViewModel)

    val title =
        when (val event = sectionEvent) {
            // An index may list another index (a part holding chapters), and NKBIP-01 lets it
            // list long-form, wiki and spec events as sections too.
            is PublicationIndexEvent -> event.titleOrIdentifier()
            is PublicationContentEvent -> event.titleOrIdentifier()
            is LongFormContentEvent -> event.title()
            is WikiArticleEvent -> event.title()
            // A bookshelf directory lists whatever it likes, including another directory, so the
            // row has to name the library kinds too -- otherwise a nested entry falls through to
            // the section placeholder and reads "Untitled section", which it is not.
            is BookshelfDirectoryEvent -> event.titleOrIdentifier()
            is LearningResourceEvent -> event.titleOrIdentifier()
            is BlossomPieceIndexEvent -> event.titleOrIdentifier()
            else -> null
        } ?: ref.title ?: ref.fallbackTitle()

    SectionRowContent(position, ref, title) { nav.nav(Route.Note(sectionNote.idHex)) }
}

/**
 * The name to show for a contents entry before its section event arrives.
 *
 * NKBIP-01 lets an index carry each entry's title in the `a` tag's slot 2, and reading it is why
 * that slot is parsed -- but across 897 indexes on the public relays, not one of 11,176 entries
 * uses it. Every publisher writes `["a", coord, relay, <event id>]` instead, so relying on the
 * slot meant a whole table of contents read "Untitled section" until 240 separate fetches came
 * back.
 *
 * The coordinate's own `d` is right there and is a slug of the title
 * (`pg59225-chapter-1-introduction`), so it names the row immediately and for free. It is a
 * fallback, not a replacement: the section's real title still wins the moment it lands.
 */
private fun PublicationSectionRef.fallbackTitle(): String? = address?.dTag?.takeIf { it.isNotBlank() }?.let { PublicationIndexEvent.humanizeIdentifier(it) }

@Composable
private fun SectionRowContent(
    position: Int,
    ref: PublicationSectionRef,
    title: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                // A nested entry is indented by its level, so a part/chapter structure reads as one.
                .padding(start = ((ref.level - 1) * 12).dp)
                .padding(vertical = 8.dp),
    ) {
        PublicationSectionPosition(position)

        Text(
            text = title ?: stringRes(Res.string.publication_untitled_section),
            style = MaterialTheme.typography.bodyMedium,
            color = if (title != null) LocalContentColor.current else MaterialTheme.colorScheme.grayText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PublicationSectionPosition(position: Int) {
    Text(
        text = position.toString(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.grayText,
        modifier = Modifier.width(28.dp).padding(end = 4.dp),
    )
}

@Composable
private fun PublicationCover(
    cover: String?,
    accountViewModel: AccountViewModel,
) {
    val shape = RoundedCornerShape(6.dp)
    val modifier = Modifier.width(CoverWidth).aspectRatio(COVER_ASPECT).clip(shape)

    Box(modifier) {
        if (cover != null) {
            MyAsyncImage(
                imageUrl = cover,
                contentDescription = stringRes(Res.string.preview_card_image_for, cover),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { PublicationCoverPlaceholder() },
                onError = { PublicationCoverPlaceholder() },
            )
        } else {
            PublicationCoverPlaceholder()
        }
    }
}

@Composable
private fun PublicationCoverPlaceholder() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.MenuBook,
            contentDescription = null,
            modifier = Modifier.width(28.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
    }
}

@Composable
private fun PublicationTopicChip(topic: String) {
    Text(
        text = "#$topic",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
