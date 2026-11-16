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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.library_blossom_piece
import com.vitorpamplona.amethyst.commons.resources.library_directory
import com.vitorpamplona.amethyst.commons.resources.library_directory_items
import com.vitorpamplona.amethyst.commons.resources.library_directory_more_items
import com.vitorpamplona.amethyst.commons.resources.library_learning_resource
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlVideo
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.FileAttachmentCard
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.util.countToHumanReadableBytes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.library.BlossomPieceIndexEvent
import com.vitorpamplona.quartz.experimental.library.BookshelfDirectoryEvent
import com.vitorpamplona.quartz.experimental.library.LearningResourceEvent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

// Landscape rather than the publications' 2:3: a course banner and a file preview are wide, and
// letterboxing them into a book jacket would crop the part that identifies them.
private const val BANNER_ASPECT = 16f / 9f
private val BannerWidth = 96.dp

// A shelf can hold hundreds; each row carries its own relay subscription, so the cap is a fetch
// budget as much as a layout one.
private const val MAX_PREVIEW_ITEMS = 12

/**
 * Renders a kind-30142 learning resource — a course, tutorial or lesson.
 *
 * The reference Android client marks this kind `reader = true`, i.e. it is meant to be read
 * rather than skimmed, so the body renders in full underneath the header rather than as a blurb.
 */
@Composable
fun RenderLearningResource(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: all three library kinds are addressable, so an edited shelf or
    // resource replaces the event on the same Note instance. `Note` is @Stable and `event` is a
    // plain @Volatile var, so a bare read registers no snapshot dependency and Compose would keep
    // showing the superseded version. Observing also asks the relays for the event.
    val observedEvent by observeNoteEvent<LearningResourceEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    // What these events actually carry: the book publishers write title/author/published/image and
    // leave the body empty; the schema.org publishers write name/description plus a vocabulary of
    // facets. Reading only title+summary rendered both families as a line of text, which is why
    // the byline, the facets and the attached file are surfaced here.
    val author = remember(noteEvent) { noteEvent.author()?.ifBlank { null } }
    val published = remember(noteEvent) { noteEvent.published()?.ifBlank { null } }
    val facets = remember(noteEvent) { noteEvent.facetLabels().toImmutableList() }

    Column(Modifier.fillMaxWidth()) {
        LibraryHeader(
            symbol = MaterialSymbols.MenuBook,
            label = stringRes(Res.string.library_learning_resource),
            title = remember(noteEvent) { noteEvent.titleOrIdentifier() },
            subtitle = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
            // The byline is what tells a shelf of books apart; without it "Effective Executive"
            // and "Deep Work" are two titles floating on an author-less card.
            byline = listOfNotNull(author, published).takeIf { it.isNotEmpty() }?.joinToString(" · "),
            detail = null,
            banner = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
            accountViewModel = accountViewModel,
        )

        if (facets.isNotEmpty()) {
            FacetChips(facets)
        }

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                tags = tags,
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        LearningResourceFile(note, noteEvent, accountViewModel)

        DisplayUncitedHashtags(noteEvent, note.toNostrUri(), accountViewModel, nav)
    }
}

/**
 * The file a learning resource *is*, when it ships as one.
 *
 * These events attach the material through an `encoding:*` group — a Blossom URL, its MIME type,
 * its size and its hash — and name it nowhere in the body, so a card without this describes
 * something the reader cannot open. Most of them are PDFs (worksheets, instructions), which
 * Amethyst can render: the same classify-then-view path kind 1063 uses gives a PDF its inline
 * first page and a tap-to-read viewer, an image its picture, and a webxdc bundle or an archive —
 * anything no viewer can show — the attachment card rather than a URL to squint at.
 */
@Composable
private fun LearningResourceFile(
    note: Note,
    noteEvent: LearningResourceEvent,
    accountViewModel: AccountViewModel,
) {
    val url = remember(noteEvent) { noteEvent.contentUrl()?.ifBlank { null } } ?: return
    val mimeType = remember(noteEvent) { noteEvent.contentFormat()?.ifBlank { null } }
    val content =
        remember(noteEvent) {
            val description = noteEvent.title()
            val uri = note.toNostrUri()
            val hash = noteEvent.contentHash()

            when (RichTextParser.classifyMedia(url, mimeType)) {
                MediaContentKind.IMAGE -> MediaUrlImage(url = url, description = description, hash = hash, uri = uri, mimeType = mimeType)
                MediaContentKind.VIDEO -> MediaUrlVideo(url = url, description = description, hash = hash, uri = uri, mimeType = mimeType)
                MediaContentKind.PDF -> MediaUrlPdf(url = url, description = description, hash = hash, uri = uri, mimeType = mimeType)
                null -> null
            }
        }

    Box(Modifier.padding(top = 6.dp)) {
        if (content != null) {
            ZoomableContentView(
                content = content,
                roundedCorner = true,
                contentScale = ContentScale.FillWidth,
                accountViewModel = accountViewModel,
            )
        } else {
            FileAttachmentCard(
                url = url,
                description = remember(noteEvent) { noteEvent.title() },
                mimeType = mimeType,
                sizeInBytes = remember(noteEvent) { noteEvent.contentSize() },
            )
        }
    }
}

/**
 * The schema.org facets as a wrapped row of chips.
 *
 * These are the only thing that says what a resource is for — a subject, a school level, a
 * material type — and they are publisher-chosen labels from a controlled vocabulary, so they are
 * shown as published rather than mapped through a string table that would drop every value
 * nobody enumerated.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FacetChips(facets: ImmutableList<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Size5dp),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        facets.forEach { facet ->
            Text(
                text = facet,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Renders a kind-30045 directory — a curated shelf of publications and notes.
 *
 * The shelf's contents are shaped exactly like a publication's table of contents, so they render
 * through the same rows: titles resolve from the list's own tags first and upgrade when the
 * items arrive.
 */
@Composable
fun RenderBookshelfDirectory(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: all three library kinds are addressable, so an edited shelf or
    // resource replaces the event on the same Note instance. `Note` is @Stable and `event` is a
    // plain @Volatile var, so a bare read registers no snapshot dependency and Compose would keep
    // showing the superseded version. Observing also asks the relays for the event.
    val observedEvent by observeNoteEvent<BookshelfDirectoryEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val itemCount = remember(noteEvent) { noteEvent.itemCount() }

    Column(Modifier.fillMaxWidth()) {
        LibraryHeader(
            symbol = MaterialSymbols.Collections,
            label = stringRes(Res.string.library_directory),
            title = remember(noteEvent) { noteEvent.titleOrIdentifier() },
            subtitle = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
            detail = if (itemCount > 0) pluralStringRes(Res.plurals.library_directory_items, itemCount, itemCount) else null,
            banner = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
            accountViewModel = accountViewModel,
        )

        DirectoryContents(noteEvent, accountViewModel, nav)
    }
}

/** Renders a kind-32176 Blossom piece index — a titled record of a file, with its size and type. */
@Composable
fun RenderBlossomPieceIndex(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    // Observed, not read once: all three library kinds are addressable, so an edited shelf or
    // resource replaces the event on the same Note instance. `Note` is @Stable and `event` is a
    // plain @Volatile var, so a bare read registers no snapshot dependency and Compose would keep
    // showing the superseded version. Observing also asks the relays for the event.
    val observedEvent by observeNoteEvent<BlossomPieceIndexEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val url = remember(noteEvent) { noteEvent.url()?.ifBlank { null } }
    val uriHandler = LocalUriHandler.current

    LibraryHeader(
        symbol = MaterialSymbols.Storage,
        label = stringRes(Res.string.library_blossom_piece),
        title = remember(noteEvent) { noteEvent.titleOrIdentifier() },
        subtitle = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
        // What actually distinguishes one file record from another: what it is, how big, and how
        // many pieces it was cut into. The byte count is humanized -- "31838839" is a number the
        // reader has to decode, and the piece count is the whole point of the kind.
        detail =
            remember(noteEvent) {
                val pieces = noteEvent.pieceCount()
                listOfNotNull(
                    noteEvent.type(),
                    noteEvent.sizeInBytes()?.let { countToHumanReadableBytes(it) } ?: noteEvent.size(),
                    if (pieces > 0) "$pieces pieces" else null,
                ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
            },
        // The servers holding the pieces: without them the hashes name something unreachable.
        byline = remember(noteEvent) { noteEvent.blossomServers().takeIf { it.isNotEmpty() }?.joinToString(", ") { it.removePrefix("https://") } },
        banner = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
        accountViewModel = accountViewModel,
        // Nothing in-app reassembles the pieces yet, so the card opens the whole-file URL the
        // publisher offers. With no `r` there is nothing to open, and the card stays inert
        // rather than pretending otherwise.
        onClick = url?.let { { uriHandler.openUri(it) } },
    )
}

/**
 * The shelf's contents.
 *
 * A directory lists its items with the same `a`/`e` grammar a publication index uses for its
 * table of contents, so the rows are the same rows — resolving lazily, showing the list's own
 * title until the item arrives.
 */
@Composable
private fun DirectoryContents(
    noteEvent: BookshelfDirectoryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items = remember(noteEvent) { noteEvent.items().toImmutableList() }

    if (items.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        items.take(MAX_PREVIEW_ITEMS).forEachIndexed { index, ref ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }

            PublicationSectionRow(index + 1, ref, accountViewModel, nav)
        }

        if (items.size > MAX_PREVIEW_ITEMS) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

            val remaining = items.size - MAX_PREVIEW_ITEMS
            Text(
                text = pluralStringRes(Res.plurals.library_directory_more_items, remaining, remaining),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
    }
}

/** One header for the three: a banner when there is one, the mark's icon when there is not. */
@Composable
private fun LibraryHeader(
    symbol: MaterialSymbol,
    label: String,
    title: String,
    subtitle: String?,
    byline: String? = null,
    detail: String?,
    banner: String?,
    accountViewModel: AccountViewModel,
    onClick: (() -> Unit)? = null,
) {
    Column(
        if (onClick != null) {
            MaterialTheme.colorScheme.replyModifier.clickable(onClick = onClick)
        } else {
            MaterialTheme.colorScheme.replyModifier
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Size5dp)) {
            Icon(
                symbol = symbol,
                contentDescription = null,
                modifier = Modifier.width(16.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )

            Spacer(Modifier.width(Size5dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        Row(Modifier.fillMaxWidth()) {
            LibraryBanner(banner, symbol, accountViewModel)

            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                byline?.let {
                    Spacer(Modifier.padding(top = 2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                subtitle?.let {
                    Spacer(Modifier.padding(top = 2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                detail?.let {
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryBanner(
    banner: String?,
    symbol: MaterialSymbol,
    accountViewModel: AccountViewModel,
) {
    val shape = RoundedCornerShape(6.dp)
    val modifier = Modifier.width(BannerWidth).aspectRatio(BANNER_ASPECT).clip(shape)

    Box(modifier) {
        if (banner != null) {
            MyAsyncImage(
                imageUrl = banner,
                contentDescription = stringRes(Res.string.preview_card_image_for, banner),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { LibraryBannerPlaceholder(symbol) },
                onError = { LibraryBannerPlaceholder(symbol) },
            )
        } else {
            LibraryBannerPlaceholder(symbol)
        }
    }
}

@Composable
private fun LibraryBannerPlaceholder(symbol: MaterialSymbol) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.width(24.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
    }
}
