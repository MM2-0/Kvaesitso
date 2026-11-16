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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.publication_untitled_section
import com.vitorpamplona.amethyst.commons.richtext.CachedAsciiDocToMarkdown
import com.vitorpamplona.amethyst.commons.ui.components.ExpandableTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.LoadNote
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size10dp
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.publications.PublicationSectionRef
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent

/**
 * Renders a kind-30041 NKBIP-01 publication section — a chapter, zettel or episode.
 *
 * Unlike the kind-30040 index this one *is* prose, so it renders as a titled body.
 *
 * NKBIP-01 says the body may be **AsciiDoc**, and for this kind the reference implementation
 * treats it as AsciiDoc unconditionally rather than sniffing — so we do the same. The body goes
 * through [AsciiDocToMarkdown] and then the same CommonMark renderer that draws kind-30023
 * long-form, which brings media, imeta and `nostr:` link handling with it for free. See that
 * converter for what it does and does not cover; anything it does not recognize is passed
 * through as the plain text it already was.
 *
 * Like a kind-1 note, the body is offered for translation and cropped to a preview with
 * "Show More": a chapter runs to tens of KB, far past what a feed card should show unasked.
 */
@Composable
fun RenderPublicationSection(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: these kinds are DEFINED by replacement — a newer rating,
    // review or redirect lands on the same Note instance. `Note` is @Stable and `event`
    // is a plain @Volatile var, so a bare read registers no snapshot dependency and
    // Compose would keep showing the superseded version.
    val observedEvent by observeNoteEvent<PublicationContentEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val title = remember(noteEvent) { noteEvent.titleOrIdentifier() }

    // Cached across compositions, not just remembered: `remember` is dropped the moment the card
    // leaves the lazy list, and at chapter size the conversion is far from free (worst measured
    // 7.2ms on a desktop JVM for a 13.5 KB section, and a phone is slower), so scrolling back to
    // a chapter would re-convert it on the composition thread every time.
    val markdown = remember(noteEvent) { publicationSectionMarkdown(noteEvent) }

    Column(Modifier.fillMaxWidth()) {
        // A chapter arrived at from a search, a mention or a wikilink has nothing around it to say
        // what it is a chapter *of*. The index names itself, so show it above the title.
        noteEvent.publicationAddress()?.let { address ->
            LoadAddressableNote(address) { indexNote ->
                indexNote?.let { PublicationCrumb(it, accountViewModel, nav) }
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (markdown.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }
            val callbackUri = remember(note) { note.toNostrUri() }
            val platform = LocalRichTextPlatform.current

            // Translated and cropped the way a kind-1 note is. The converted markdown is what gets
            // translated, and the markdown renderer draws it directly: going through the rich-text
            // viewer would re-detect the format on every crop, and a chapter whose first lines
            // happen to carry no markdown syntax would render its emphasis as raw underscores.
            TranslatableRichTextViewer(
                content = markdown,
                id = noteEvent.id,
                accountViewModel = accountViewModel,
            ) { text ->
                ExpandableTextViewer(
                    content = text,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = backgroundColor,
                    id = noteEvent.id,
                ) { shown, modifier ->
                    Column(modifier) {
                        platform.Markdown(
                            content = shown,
                            tags = tags,
                            canPreview = canPreview && !makeItShort,
                            quotesLeft = quotesLeft,
                            backgroundColor = backgroundColor,
                            callbackUri = callbackUri,
                            accountViewModel = accountViewModel,
                            nav = nav,
                        )
                    }
                }
            }
        }

        noteEvent.publicationAddress()?.let { address ->
            LoadAddressableNote(address) { indexNote ->
                indexNote?.let { PublicationPager(noteEvent, it, accountViewModel, nav) }
            }
        }
    }
}

/**
 * The markdown a section's AsciiDoc body renders as. It is also the exact string handed to the
 * translator, and so the key the translation cache stores the result under: the copy-text menus
 * look the translation up through [displayedNoteText], which calls this for a section.
 */
fun publicationSectionMarkdown(event: PublicationContentEvent): String = CachedAsciiDocToMarkdown.convert(event.id, event.content, wikilinkResolver(event))

/**
 * The index a section belongs to, as one tappable line above the chapter title.
 *
 * Deliberately a crumb and not the full [PublicationHeader]: the reader came here to read this
 * chapter, and a cover, blurb and 34-row table of contents on top of it would bury the thing they
 * opened.
 */
@Composable
private fun PublicationCrumb(
    indexNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val index by observeNoteEvent<PublicationIndexEvent>(indexNote, accountViewModel)
    val label = index?.titleOrIdentifier() ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { routeFor(indexNote, accountViewModel.account)?.let { nav.nav(it) } }
                .padding(bottom = Size5dp),
    ) {
        Icon(
            symbol = MaterialSymbols.MenuBook,
            contentDescription = null,
            modifier = Modifier.width(16.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )

        Spacer(Modifier.width(Size5dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.grayText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Previous/next across the index's own ordering.
 *
 * Only drawn when this section is actually listed by the index -- that listing is the only thing
 * that defines an order, so a section the index does not mention has no neighbours to offer. The
 * ends are left blank rather than disabled: there is no previous chapter before the first, and a
 * greyed control invites a tap that cannot do anything.
 */
@Composable
private fun PublicationPager(
    noteEvent: PublicationContentEvent,
    indexNote: AddressableNote,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val index by observeNoteEvent<PublicationIndexEvent>(indexNote, accountViewModel)
    val sections = remember(index) { index?.sections().orEmpty() }

    val position =
        remember(index, noteEvent) {
            val self = noteEvent.address().toValue()
            sections.indexOfFirst { it.address?.toValue() == self || it.eventId == noteEvent.id }
        }

    if (position < 0) return

    val previous = sections.getOrNull(position - 1)
    val next = sections.getOrNull(position + 1)

    if (previous == null && next == null) return

    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = Size5dp),
    ) {
        PagerEnd(previous, isBack = true, accountViewModel = accountViewModel, nav = nav, modifier = Modifier.weight(1f))
        PagerEnd(next, isBack = false, accountViewModel = accountViewModel, nav = nav, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PagerEnd(
    ref: PublicationSectionRef?,
    isBack: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    if (ref == null) {
        Spacer(modifier)
        return
    }

    val address = ref.address
    val eventId = ref.eventId

    // The index's own title shows immediately; the neighbour is still observed, because observing
    // is what fetches it and it carries the better title.
    if (address != null) {
        LoadAddressableNote(address) { target ->
            target?.let { PagerButton(ref, it, isBack, accountViewModel, nav, modifier) }
        }
    } else if (eventId != null) {
        LoadNote(eventId) { target ->
            target?.let { PagerButton(ref, it, isBack, accountViewModel, nav, modifier) }
        }
    }
}

@Composable
private fun PagerButton(
    ref: PublicationSectionRef,
    target: Note,
    isBack: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier,
) {
    val event by observeNoteEvent<PublicationContentEvent>(target, accountViewModel)
    val label = event?.titleOrIdentifier() ?: ref.title ?: stringRes(Res.string.publication_untitled_section)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isBack) Arrangement.Start else Arrangement.End,
        modifier =
            modifier
                .clickable { nav.nav(Route.Note(target.idHex)) }
                .padding(vertical = Size10dp),
    ) {
        if (isBack) {
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowBack,
                contentDescription = null,
                modifier = Modifier.width(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Size5dp))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!isBack) {
            Spacer(Modifier.width(Size5dp))
            Icon(
                symbol = MaterialSymbols.AutoMirrored.ArrowForward,
                contentDescription = null,
                modifier = Modifier.width(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Turns a `[[target]]` into a `nostr:` URI using the section's `wikilink` tags, so a reference
 * opens the page inside Amethyst instead of leaving for someone's web wiki.
 *
 * Preference order matters: an event id addresses one exact revision, whereas a coordinate
 * addresses "whatever that author's page says now". The tag tells us which it meant.
 *
 * A target with no usable tag returns null, and the converter falls back to the bare label —
 * better than a dead link to a page we cannot name.
 */
private fun wikilinkResolver(event: PublicationContentEvent): (String) -> String? =
    { target ->
        val link = event.wikilinkFor(target)

        when {
            link == null -> null

            link.eventId != null ->
                "nostr:" + NEvent.create(link.eventId!!, link.pubKey, WikiArticleEvent.KIND, link.relay)

            link.pubKey != null ->
                NAddress.createNostrUriOrNull(
                    WikiArticleEvent.KIND,
                    link.pubKey!!,
                    PublicationContentEvent.normalizeWikilink(link.target),
                    link.relay,
                )

            else -> null
        }
    }
