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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ebook_open_file
import com.vitorpamplona.amethyst.commons.resources.ebook_read
import com.vitorpamplona.amethyst.commons.resources.kind_ebook
import com.vitorpamplona.amethyst.commons.resources.kind_magazine
import com.vitorpamplona.amethyst.commons.resources.kind_magazine_issue
import com.vitorpamplona.amethyst.commons.resources.magazine_issue_count
import com.vitorpamplona.amethyst.commons.resources.magazine_issue_label
import com.vitorpamplona.amethyst.commons.resources.magazine_latest_issue
import com.vitorpamplona.amethyst.commons.resources.magazine_show_all_issues
import com.vitorpamplona.amethyst.commons.resources.magazine_show_fewer_issues
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.DateSkeletonFormatter
import com.vitorpamplona.amethyst.commons.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.note.platform.PdfViewerDialog
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.util.countToHumanReadableBytes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent
import com.vitorpamplona.quartz.experimental.pressstr.issue.MagazineIssueEvent
import com.vitorpamplona.quartz.experimental.pressstr.magazine.MagazineEvent
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.floor

/*
 * PressStr's three kinds: ebooks (33953), magazines (34609) and magazine issues (39731).
 *
 * Ebooks and issues are files with a cover, so they share one book card: the cover drawn as a
 * book beside a kind label, the title, a byline and chips for what a reader decides on (issue,
 * date, language), then the summary, the blurb and a button that says what it opens ("Read · PDF
 * · 159 MB"). A PDF opens in the in-app reader. The card never draws the inline first-page preview
 * a PDF link gets: that downloads the whole file to show a page the `image` cover already shows,
 * and issues in the wild run to 150 MB. Anything else (EPUB) goes to the system, since no reader
 * in the app can show it.
 *
 * A magazine is a masthead over a shelf of its issues' covers, a grid rather than a carousel so
 * it works with a mouse as well as a finger.
 */

private val CardShape = RoundedCornerShape(15.dp)

// A book's spine is the squarer edge; the fore-edge is rounded like a paperback's.
private val BookShape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 6.dp, bottomEnd = 6.dp)
private const val BOOK_ASPECT = 2f / 3f
private val BookCoverWidth = 112.dp
private val QuotedBookCoverWidth = 84.dp

private val MaxMastheadHeight = 200.dp

// The shelf fits as many covers of at least this width as the card allows, never fewer than 3.
private val MinShelfCoverWidth = 96.dp
private val ShelfSpacing = 12.dp
private const val MIN_SHELF_COLUMNS = 3

// Rows shown before "Show all": two in a card, one in a quote. Past MAX_SHELF_ISSUES the rest
// are a tap away in each issue, since every cover is an image load and a note observer.
private const val SHELF_ROWS = 2
private const val QUOTED_SHELF_ROWS = 1
private const val MAX_SHELF_ISSUES = 60

/** "Jun 2026": a publication date is a month, not a weekday. */
private val PublicationDateFormat = DateSkeletonFormatter("yMMM")

private fun publicationDate(unixSeconds: Long) = PublicationDateFormat.format(unixSeconds * 1000)

/** Renders a kind-33953 PressStr ebook. */
@Composable
fun RenderEbook(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: 33953 is addressable, so a revised ebook replaces the event on the
    // same Note instance, and a bare read of `event` registers no snapshot dependency.
    val observedEvent by observeNoteEvent<EbookEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val file = remember(noteEvent) { pressStrFile(note, noteEvent.url(), noteEvent.mimeType(), noteEvent.title(), noteEvent.hash()) }
    val open = rememberFileOpener(file, accountViewModel)
    val authors = remember(noteEvent) { noteEvent.authors().joinToString(", ").ifBlank { null } }

    BookCard(
        kindIcon = MaterialSymbols.MenuBook,
        kindLabel = stringRes(Res.string.kind_ebook),
        cover = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
        title = remember(noteEvent) { noteEvent.titleOrIdentifier() },
        byline = authors?.let { { BylineText(it) } },
        chips =
            remember(noteEvent) {
                listOfNotNull(
                    noteEvent.language()?.uppercase(),
                    noteEvent.publishedAt()?.let { publicationDate(it) },
                ).toImmutableList()
            },
        summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
        fileDetail = remember(noteEvent) { fileDetail(noteEvent.formatLabel(), noteEvent.size()) },
        note = note,
        noteEvent = noteEvent,
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        file = file,
        open = open,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

/**
 * Renders a kind-39731 magazine issue, with its magazine's name as a link to it.
 *
 * The magazine is resolved from the issue's `a` tag. Issues routinely reach a relay their
 * magazine never did, so until (or unless) it arrives the byline is simply absent.
 */
@Composable
fun RenderMagazineIssue(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: 39731 is addressable, so a re-uploaded issue replaces the event on
    // the same Note instance.
    val observedEvent by observeNoteEvent<MagazineIssueEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val file = remember(noteEvent) { pressStrFile(note, noteEvent.url(), noteEvent.mimeType(), noteEvent.title(), noteEvent.hash()) }
    val open = rememberFileOpener(file, accountViewModel)
    // Anyone can tag a magazine's address. Only its own publisher's issues belong to it, so a
    // stranger's issue gets no byline rather than borrowing a magazine it is not part of.
    val magazine =
        remember(noteEvent) {
            noteEvent.magazine()?.takeIf { it.pubKeyHex == noteEvent.pubKey }?.let { Address(it.kind, it.pubKeyHex, it.dTag) }
        }
    val title = remember(noteEvent) { noteEvent.titleOrIdentifier() }
    // The label is dropped when the title already says it ("Issue 7", "… — May 2024").
    val issueLabel = remember(noteEvent) { noteEvent.issue()?.takeUnless { title.containsWord(it) } }
    val date = remember(noteEvent) { noteEvent.publishedAt()?.let { publicationDate(it) } }
    val chips = listOfNotNull(issueLabel?.let { issueLabelText(it) }, date).toImmutableList()

    val card =
        @Composable { byline: (@Composable () -> Unit)? ->
            BookCard(
                kindIcon = MaterialSymbols.AutoMirrored.Article,
                kindLabel = stringRes(Res.string.kind_magazine_issue),
                cover = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
                title = title,
                byline = byline,
                chips = chips,
                summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
                fileDetail = remember(noteEvent) { fileDetail(noteEvent.formatLabel(), noteEvent.size()) },
                note = note,
                noteEvent = noteEvent,
                makeItShort = makeItShort,
                canPreview = canPreview,
                quotesLeft = quotesLeft,
                backgroundColor = backgroundColor,
                file = file,
                open = open,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

    if (magazine != null) {
        LoadAddressableNote(magazine) { magazineNote ->
            if (magazineNote != null) {
                val magazineEvent by observeNoteEvent<MagazineEvent>(magazineNote, accountViewModel)
                val name = magazineEvent?.titleOrIdentifier()
                card(name?.let { { BylineLink(it) { nav.nav(Route.Note(magazineNote.idHex)) } } })
            } else {
                card(null)
            }
        }
    } else {
        card(null)
    }
}

/**
 * Renders a kind-34609 magazine: its masthead, what it is about, and a shelf of its issues.
 *
 * Nothing on the magazine lists its issues; each issue points back at it with an `a` tag. The
 * engagement watcher fetches those (`FilterRepliesAndReactionsToAddresses`) while the magazine is
 * on screen, and the shelf shows the ones in the cache. Only the magazine's own author can add an
 * issue to it here: anyone can tag the address, but an issue someone else signed is not part of
 * the publication.
 */
@Composable
fun RenderMagazine(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val observedEvent by observeNoteEvent<MagazineEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val masthead = remember(noteEvent) { noteEvent.image()?.ifBlank { null } }
    val title = remember(noteEvent) { noteEvent.titleOrIdentifier() }
    val summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } }
    val website = remember(noteEvent) { noteEvent.website()?.ifBlank { null } }
    val language = remember(noteEvent) { noteEvent.language()?.uppercase() }
    val issues = rememberMagazineIssues(noteEvent, accountViewModel)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(CardShape)
            .background(cardTint()),
    ) {
        masthead?.let {
            // Fit within a capped height on the card's own tint: a masthead is a logo, so it must
            // not be cropped, and a tall one must not take over the feed.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().background(cardTint()).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                MyAsyncImage(
                    imageUrl = it,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    mainImageModifier = Modifier.fillMaxWidth().heightIn(max = MaxMastheadHeight),
                    loadedImageModifier = Modifier,
                    accountViewModel = accountViewModel,
                    onLoadingBackground = null,
                    onError = null,
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            KindLabel(
                MaterialSymbols.AutoMirrored.Article,
                listOfNotNull(
                    stringRes(Res.string.kind_magazine),
                    language,
                    if (issues.isNotEmpty()) pluralStringRes(Res.plurals.magazine_issue_count, issues.size, issues.size) else null,
                ).joinToString(" · "),
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )

            summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.grayText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            website?.let { WebsiteChip(it, Modifier.padding(top = 10.dp)) }

            if (!makeItShort && noteEvent.content.isNotBlank()) {
                val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

                TranslatableRichTextViewer(
                    content = noteEvent.content,
                    canPreview = canPreview,
                    quotesLeft = quotesLeft,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    tags = tags,
                    backgroundColor = rememberCardBackground(backgroundColor),
                    id = note.idHex,
                    callbackUri = note.toNostrUri(),
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            if (issues.isNotEmpty()) {
                IssueShelf(
                    issues = issues,
                    magazineAddress = remember(noteEvent) { noteEvent.addressTag() },
                    rows = if (makeItShort) QUOTED_SHELF_ROWS else SHELF_ROWS,
                    canExpand = !makeItShort,
                    accountViewModel = accountViewModel,
                    nav = nav,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }

    DisplayUncitedHashtags(noteEvent, note.toNostrUri(), accountViewModel, nav)
}

/** The magazine's issues in the cache, by its own author, newest publication first. */
@Composable
private fun rememberMagazineIssues(
    magazine: MagazineEvent,
    accountViewModel: AccountViewModel,
): ImmutableList<Note> {
    val address = remember(magazine) { magazine.addressTag() }
    val issuesFlow =
        remember(address) {
            accountViewModel.account.cache.observeNotes(
                Filter(
                    kinds = listOf(MagazineIssueEvent.KIND),
                    authors = listOf(magazine.pubKey),
                    tags = mapOf("a" to listOf(address)),
                ),
            )
        }

    val notes by issuesFlow.collectAsStateWithLifecycle(emptyList())

    // Newest issue first, by when it came out rather than when it was posted: a publisher
    // uploading a back catalogue posts the oldest issue last. Each key is read once before the
    // sort, since publishedAt() scans the tags. The list only re-emits when an issue is added, so
    // each cover observes its own note for edits.
    return remember(notes) {
        notes
            .map { issueNote ->
                val issue = issueNote.event as? MagazineIssueEvent
                issueNote to (issue?.let { it.publishedAt() ?: it.createdAt } ?: Long.MIN_VALUE)
            }.sortedByDescending { it.second }
            .take(MAX_SHELF_ISSUES)
            .map { it.first }
            .toImmutableList()
    }
}

/**
 * The magazine's issues as a grid of covers, as many per row as the card fits. The newest is
 * marked; "Show all" opens the rest of the run in place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IssueShelf(
    issues: ImmutableList<Note>,
    magazineAddress: String,
    rows: Int,
    canExpand: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = maxOf(MIN_SHELF_COLUMNS, ((maxWidth + ShelfSpacing) / (MinShelfCoverWidth + ShelfSpacing)).toInt())
        // Rounded down to a whole dp: an exact share can round a pixel past the row once laid
        // out, and FlowRow then wraps the last cover of every row.
        val coverWidth = floor(((maxWidth - ShelfSpacing * (columns - 1)) / columns).value).dp
        val collapsed = columns * rows
        val shown = if (expanded) issues else issues.take(collapsed)

        Column(Modifier.fillMaxWidth()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ShelfSpacing),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                maxItemsInEachRow = columns,
                modifier = Modifier.fillMaxWidth(),
            ) {
                shown.forEachIndexed { index, issueNote ->
                    ShelfIssue(
                        issueNote = issueNote,
                        magazineAddress = magazineAddress,
                        coverWidth = coverWidth,
                        isLatest = index == 0,
                        accountViewModel = accountViewModel,
                    ) { nav.nav(Route.Note(issueNote.idHex)) }
                }
            }

            if (canExpand && issues.size > collapsed) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
                ) {
                    Text(
                        if (expanded) {
                            stringRes(Res.string.magazine_show_fewer_issues)
                        } else {
                            pluralStringRes(Res.plurals.magazine_show_all_issues, issues.size, issues.size)
                        },
                    )
                    Icon(
                        symbol = if (expanded) MaterialSymbols.ExpandLess else MaterialSymbols.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShelfIssue(
    issueNote: Note,
    magazineAddress: String,
    coverWidth: Dp,
    isLatest: Boolean,
    accountViewModel: AccountViewModel,
    onClick: () -> Unit,
) {
    // Observed, not read once: an edited issue replaces the event on the same Note, and the list
    // the shelf draws from does not re-emit for it.
    val observedIssue by observeNoteEvent<MagazineIssueEvent>(issueNote, accountViewModel)
    val issue = observedIssue ?: return

    // A newer version can move the issue to another magazine, or drop the link.
    val stillHere = remember(issue) { issue.magazine()?.toTag() == magazineAddress }
    if (!stillHere) return

    val title = remember(issue) { issue.titleOrIdentifier() }
    val date = remember(issue) { issue.publishedAt()?.let { publicationDate(it) } }

    Column(Modifier.width(coverWidth).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)) {
        Box {
            BookCover(remember(issue) { issue.image()?.ifBlank { null } }, title, coverWidth, accountViewModel)
            if (isLatest) {
                Text(
                    text = stringRes(Res.string.magazine_latest_issue),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier =
                        Modifier
                            .padding(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )

        date?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 1,
            )
        }
    }
}

/**
 * The book card shared by ebooks and issues: cover and identity on top, then the summary, the
 * blurb and the button that opens the file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookCard(
    kindIcon: MaterialSymbol,
    kindLabel: String,
    cover: String?,
    title: String,
    byline: (@Composable () -> Unit)?,
    chips: ImmutableList<String>,
    summary: String?,
    fileDetail: String?,
    note: Note,
    noteEvent: Event,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    file: PressStrFile?,
    open: (() -> Unit)?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(CardShape)
            .background(cardTint())
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            BookCover(
                cover = cover,
                description = title,
                width = if (makeItShort) QuotedBookCoverWidth else BookCoverWidth,
                accountViewModel = accountViewModel,
                onClick = open,
            )

            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                KindLabel(kindIcon, kindLabel)

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )

                byline?.let {
                    Spacer(Modifier.height(4.dp))
                    it()
                }

                if (chips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 10.dp),
                    ) {
                        chips.forEach { MetaChip(it) }
                    }
                }

                // The one-line pitch sits beside the cover, where the column has room; the blurb
                // below is the long read.
                summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = if (makeItShort) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }

        if (!makeItShort && noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                tags = tags,
                backgroundColor = rememberCardBackground(backgroundColor),
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        if (file != null && open != null) {
            ReadButton(isPdf = file.pdf != null, detail = fileDetail, onClick = open, modifier = Modifier.padding(top = 14.dp))
        }
    }

    DisplayUncitedHashtags(noteEvent, note.toNostrUri(), accountViewModel, nav)
}

/**
 * A cover drawn as a book: a drop shadow, a squarer spine with a shading gradient along it, and a
 * placeholder when there is no image or it fails to load.
 */
@Composable
private fun BookCover(
    cover: String?,
    description: String,
    width: Dp,
    accountViewModel: AccountViewModel,
    onClick: (() -> Unit)? = null,
) {
    val outline = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val modifier =
        Modifier
            .width(width)
            .aspectRatio(BOOK_ASPECT)
            .shadow(6.dp, BookShape)
            .clip(BookShape)
            .background(placeholder)
            .border(0.5.dp, outline, BookShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Box(modifier) {
        if (cover != null) {
            MyAsyncImage(
                imageUrl = cover,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = Modifier.fillMaxSize(),
                accountViewModel = accountViewModel,
                onLoadingBackground = null,
                onError = { CoverPlaceholderIcon() },
            )
        } else {
            CoverPlaceholderIcon()
        }

        // The spine: a fold of shade along the bound edge, then a faint highlight.
        Box(Modifier.fillMaxHeight().width(width * 0.08f).background(SpineShading))
    }
}

private val SpineShading =
    Brush.horizontalGradient(
        0f to Color.Black.copy(alpha = 0.32f),
        0.6f to Color.White.copy(alpha = 0.06f),
        1f to Color.Transparent,
    )

@Composable
private fun CoverPlaceholderIcon() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            symbol = MaterialSymbols.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
    }
}

/** The small colored label that says what this card is: "EBOOK", "MAGAZINE · 8 ISSUES". */
@Composable
private fun KindLabel(
    icon: MaterialSymbol,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            symbol = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun BylineText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.grayText,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The issue's magazine, as a link that opens it. */
@Composable
private fun BylineLink(
    text: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onClick),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            symbol = MaterialSymbols.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MetaChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
private fun WebsiteChip(
    url: String,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clip(CircleShape)
                // runCatching: the handler throws when no app takes the link, and the URL is the publisher's.
                .clickable { runCatching { uriHandler.openUri(url) } }
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Language,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = url.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** A full-width button that says what it opens: "Read" plus "PDF · 159 MB" on the far side. */
@Composable
private fun ReadButton(
    isPdf: Boolean,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        contentPadding = ButtonDefaults.ContentPadding,
        modifier = modifier.fillMaxWidth().heightIn(min = 44.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringRes(if (isPdf) Res.string.ebook_read else Res.string.ebook_open_file),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun cardTint() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

/**
 * The card's own color, opaque, for text that fades out into its background: "Show More" paints
 * its fade in this color, and the note's would draw a dark band across the tinted card.
 */
@Composable
private fun rememberCardBackground(noteBackground: MutableState<Color>): MutableState<Color> {
    // Resolved the way getGradient() does: a transparent note background means the theme's.
    val base = noteBackground.value.let { if (it.alpha == 0f) MaterialTheme.colorScheme.background else it.copy(alpha = 1f) }
    val color = cardTint().compositeOver(base)
    return remember(color) { mutableStateOf(color) }
}

/** The file a card opens: [pdf] when the in-app reader can show it, else just its [url]. */
private class PressStrFile(
    val url: String,
    val pdf: MediaUrlPdf?,
)

private fun pressStrFile(
    note: Note,
    url: String?,
    mimeType: String?,
    title: String?,
    hash: String?,
): PressStrFile? {
    val fileUrl = url?.ifBlank { null } ?: return null
    val pdf =
        if (RichTextParser.classifyMedia(fileUrl, mimeType) == MediaContentKind.PDF) {
            MediaUrlPdf(url = fileUrl, description = title, hash = hash, uri = note.toNostrUri(), mimeType = mimeType)
        } else {
            null
        }
    return PressStrFile(fileUrl, pdf)
}

/** Opens [file] in the in-app reader when it is a PDF, else in the system; null when there is none. */
@Composable
private fun rememberFileOpener(
    file: PressStrFile?,
    accountViewModel: AccountViewModel,
): (() -> Unit)? {
    // Keyed on the URL, not the file object: a new version of the event rebuilds the object, and
    // keying on it closed an open reader whenever an edit arrived.
    val url = file?.url
    var reading by remember(url) { mutableStateOf(false) }
    val currentFile by rememberUpdatedState(file)
    val uriHandler by rememberUpdatedState(LocalUriHandler.current)

    val pdf = file?.pdf
    if (reading && pdf != null) {
        PdfViewerDialog(pdf, onDismiss = { reading = false }, accountViewModel = accountViewModel)
    }

    return remember(url) {
        if (url == null) {
            null
        } else {
            {
                val current = currentFile
                if (current?.pdf != null) {
                    reading = true
                } else if (current != null) {
                    // The handler throws when no app takes the scheme (`ipfs:`, `magnet:`, a typo).
                    runCatching { uriHandler.openUri(current.url) }
                }
            }
        }
    }
}

/**
 * "Issue 7" for a bare number, the label as published otherwise: publishers also write
 * "Issue 7", "May 2024" or "Spring 2025", and "Issue Issue 7" or "Issue May 2024" read wrong.
 */
@Composable
private fun issueLabelText(label: String): String = if (label.first().isDigit()) stringRes(Res.string.magazine_issue_label, label) else label

/** Whether [word] appears in this string as a whole word, ignoring case. */
private fun String.containsWord(word: String): Boolean = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE).containsMatchIn(this)

/** "PDF · 2 MB", whichever of the two the event carries. */
private fun fileDetail(
    format: String?,
    size: Long?,
): String? = listOfNotNull(format, size?.let { countToHumanReadableBytes(it) }).takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** "PDF", "EPUB", or the MIME subtype as published when it is something else. */
private fun EbookEvent.formatLabel(): String? =
    when {
        isPdf() -> "PDF"
        isEpub() -> "EPUB"
        else -> mimeType()?.substringAfter('/')?.ifBlank { null }?.uppercase()
    }

private fun MagazineIssueEvent.formatLabel(): String? = if (isPdf()) "PDF" else mimeType().substringAfter('/').ifBlank { null }?.uppercase()
