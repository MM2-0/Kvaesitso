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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.highlights.HighlightQuote
import com.vitorpamplona.amethyst.commons.model.highlights.HighlightSourceLabels
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNote
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.highlight_source_article
import com.vitorpamplona.amethyst.commons.resources.highlight_source_book
import com.vitorpamplona.amethyst.commons.resources.highlight_source_comment
import com.vitorpamplona.amethyst.commons.resources.highlight_source_doi
import com.vitorpamplona.amethyst.commons.resources.highlight_source_isan
import com.vitorpamplona.amethyst.commons.resources.highlight_source_isbn
import com.vitorpamplona.amethyst.commons.resources.highlight_source_more_from
import com.vitorpamplona.amethyst.commons.resources.highlight_source_movie
import com.vitorpamplona.amethyst.commons.resources.highlight_source_note
import com.vitorpamplona.amethyst.commons.resources.highlight_source_paper
import com.vitorpamplona.amethyst.commons.resources.highlight_source_picture
import com.vitorpamplona.amethyst.commons.resources.highlight_source_podcast
import com.vitorpamplona.amethyst.commons.resources.highlight_source_podcast_episode
import com.vitorpamplona.amethyst.commons.resources.highlight_source_video
import com.vitorpamplona.amethyst.commons.resources.highlight_source_wiki
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.components.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.components.SiteIcon
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.UrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.components.rememberTranslation
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.HighlightQuoteIndent
import com.vitorpamplona.amethyst.commons.ui.note.HighlightQuoteSpacing
import com.vitorpamplona.amethyst.commons.ui.note.HighlightedQuote
import com.vitorpamplona.amethyst.commons.ui.note.nip22Comments.DisplayExternalId
import com.vitorpamplona.amethyst.commons.ui.note.platform.rememberUrlPreviewState
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.relays.kindName
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.mockAccountViewModel
import com.vitorpamplona.quartz.drss.clips.clipRangeMillis
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.firstTagValueFor
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.references.HttpUrlFormatter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip23LongContent.LongFormContentEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import com.vitorpamplona.quartz.nip71Video.AddressableNormalVideoEvent
import com.vitorpamplona.quartz.nip71Video.AddressableShortVideoEvent
import com.vitorpamplona.quartz.nip71Video.VideoNormalEvent
import com.vitorpamplona.quartz.nip71Video.VideoShortEvent
import com.vitorpamplona.quartz.nip73ExternalIds.ExternalId
import com.vitorpamplona.quartz.nip73ExternalIds.books.BookId
import com.vitorpamplona.quartz.nip73ExternalIds.location.GeohashId
import com.vitorpamplona.quartz.nip73ExternalIds.movies.MovieId
import com.vitorpamplona.quartz.nip73ExternalIds.papers.PaperId
import com.vitorpamplona.quartz.nip73ExternalIds.podcasts.PodcastEpisodeId
import com.vitorpamplona.quartz.nip73ExternalIds.podcasts.PodcastFeedId
import com.vitorpamplona.quartz.nip73ExternalIds.podcasts.PodcastPublisherId
import com.vitorpamplona.quartz.nip73ExternalIds.topics.HashtagId
import com.vitorpamplona.quartz.nip73ExternalIds.urls.UrlId
import com.vitorpamplona.quartz.nip84Highlights.HighlightEvent
import com.vitorpamplona.quartz.nip84Highlights.parse.TextFragmentParser
import com.vitorpamplona.quartz.nip84Highlights.parse.UrlTrackerCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@Composable
fun RenderHighlight(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? HighlightEvent ?: return

    val selector = noteEvent.textQuoteSelector()

    DisplayHighlight(
        comment = noteEvent.comment(),
        commentTags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() },
        highlight = noteEvent.quote(),
        context = noteEvent.contextOrReconstructed(),
        authorHex = noteEvent.author(),
        url = noteEvent.inReference(),
        externalId = remember(noteEvent) { noteEvent.inExternalIds().firstOrNull() },
        externalIdText = remember(noteEvent) { noteEvent.inExternalIdValues().firstOrNull() },
        textFragmentPrefix = selector?.prefix,
        textFragmentSuffix = selector?.suffix,
        postAddress = noteEvent.inPostAddress(),
        postVersion = noteEvent.inPostVersion(),
        clipRange = remember(noteEvent) { noteEvent.clipRangeMillis() },
        makeItShort = makeItShort,
        canPreview = canPreview,
        quotesLeft = quotesLeft,
        backgroundColor = backgroundColor,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Preview
@Composable
fun DisplayHighlightPreview() {
    ThemeComparisonColumn {
        Column {
            DisplayHighlight(
                comment = null,
                highlight = "new architectures of freedom",
                context = "He never wrote a line of cryptographic code and never lectured on Austrian economics. Yet the cultural terrain he helped seed, particularly the psychedelic, post-industrial counterculture of the 1960s and ’70s, became the moral and metaphysical groundwork from which new architectures of freedom would later emerge.",
                authorHex = "eaa06714ac905aa5583860391e161edc7a815359b7c3e9b9b202c0558aefbeac",
                url = null,
                postAddress = Address(30023, "eaa06714ac905aa5583860391e161edc7a815359b7c3e9b9b202c0558aefbeac", "bitcoin-here-now"),
                postVersion = null,
                makeItShort = false,
                canPreview = true,
                quotesLeft = 3,
                backgroundColor = remember { mutableStateOf(Color.White) },
                accountViewModel = mockAccountViewModel(),
                nav = EmptyNav(),
            )
        }
    }
}

@Preview
@Composable
fun DisplayHighlightPreviewNewLine() {
    ThemeComparisonColumn {
        Column {
            DisplayHighlight(
                comment = null,
                highlight = "He never wrote a line of cryptographic code and never lectured on Austrian economics.\nYet the cultural terrain he helped seed, particularly the psychedelic",
                context = "He never wrote a line of cryptographic code and never lectured on Austrian economics.\nYet the cultural terrain he helped seed, particularly the psychedelic, post-industrial counterculture of the 1960s and ’70s, became the moral and metaphysical groundwork from which new architectures of freedom would later emerge.",
                authorHex = "eaa06714ac905aa5583860391e161edc7a815359b7c3e9b9b202c0558aefbeac",
                url = null,
                postAddress = Address(30023, "eaa06714ac905aa5583860391e161edc7a815359b7c3e9b9b202c0558aefbeac", "bitcoin-here-now"),
                postVersion = null,
                makeItShort = false,
                canPreview = true,
                quotesLeft = 3,
                backgroundColor = remember { mutableStateOf(Color.White) },
                accountViewModel = mockAccountViewModel(),
                nav = EmptyNav(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisplayHighlight(
    comment: String?,
    commentTags: ImmutableListOfLists<String> = EmptyTagList,
    highlight: String,
    context: String?,
    authorHex: String?,
    url: String?,
    textFragmentPrefix: String? = null,
    textFragmentSuffix: String? = null,
    postAddress: Address?,
    postVersion: ETag?,
    makeItShort: Boolean,
    clipRange: LongRange? = null,
    externalId: ExternalId? = null,
    externalIdText: String? = null,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    comment?.let {
        TranslatableRichTextViewer(
            content = it,
            canPreview = canPreview && !makeItShort,
            quotesLeft = quotesLeft,
            modifier = Modifier.fillMaxWidth(),
            tags = commentTags,
            backgroundColor = backgroundColor,
            id = it,
            callbackUri = null,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Spacer(Modifier.height(HighlightQuoteSpacing))
    }

    if (clipRange != null && highlight.isBlank()) {
        // A drss.io audio clip without its transcript: the range stands in for the quote.
        DrssClipLabel(clipRange, Modifier.padding(start = HighlightQuoteIndent))
    } else {
        DisplayHighlightQuote(highlight, context, textFragmentPrefix, accountViewModel)
    }

    Spacer(Modifier.height(HighlightQuoteSpacing))

    // Indented to sit under the quote text, not under the quote bar.
    Box(modifier = Modifier.padding(start = HighlightQuoteIndent)) {
        DisplayQuoteAuthor(
            highlightQuote = highlight,
            authorHex = authorHex,
            baseUrl = url,
            textFragmentPrefix = textFragmentPrefix,
            textFragmentSuffix = textFragmentSuffix,
            postAddress = postAddress,
            postVersion = postVersion,
            externalId = externalId,
            externalIdText = externalIdText,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
private fun DisplayQuoteAuthor(
    highlightQuote: String,
    authorHex: String?,
    baseUrl: String?,
    textFragmentPrefix: String? = null,
    textFragmentSuffix: String? = null,
    postAddress: Address?,
    postVersion: ETag?,
    externalId: ExternalId? = null,
    externalIdText: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    var userBase by remember { mutableStateOf(authorHex?.let { LocalCache.getUserIfExists(it) }) }

    if (userBase == null && authorHex != null) {
        LaunchedEffect(authorHex) {
            userBase = LocalCache.checkGetOrCreateUser(authorHex)
        }
    }

    var addressable by remember {
        mutableStateOf(postAddress?.let { LocalCache.getAddressableNoteIfExists(it) })
    }

    if (addressable == null && postAddress != null) {
        LaunchedEffect(key1 = postAddress) {
            val newNote =
                withContext(Dispatchers.IO) {
                    LocalCache.getOrCreateAddressableNote(postAddress)
                }
            if (addressable != newNote) {
                addressable = newNote
            }
        }
    }

    var version by remember {
        mutableStateOf(postVersion?.let { LocalCache.getNoteIfExists(it.eventId) })
    }

    if (version == null && postVersion != null) {
        LaunchedEffect(key1 = postVersion) {
            val newNote =
                withContext(Dispatchers.IO) {
                    LocalCache.getOrCreateNote(postVersion.eventId)
                }
            if (version != newNote) {
                version = newNote
            }
        }
    }

    when {
        addressable != null -> {
            addressable?.let {
                HighlightSourceFromNote(it, userBase, accountViewModel, nav)
            }
        }

        version != null -> {
            version?.let {
                HighlightSourceFromNote(it, userBase, accountViewModel, nav)
            }
        }

        // NIP-84: `r` may be a URL or any text naming the source; only a web address is a link.
        baseUrl != null && HighlightEvent.isUrlReference(baseUrl) -> {
            HighlightSourceFromUrl(baseUrl, highlightQuote, textFragmentPrefix, textFragmentSuffix, userBase, accountViewModel, nav)
        }

        // The same web page named through NIP-73 gets the same row as an `r` one.
        externalId is UrlId -> {
            HighlightSourceFromUrl(externalId.url, highlightQuote, textFragmentPrefix, textFragmentSuffix, userBase, accountViewModel, nav)
        }

        // Places and topics already have their own navigable chips.
        externalId is GeohashId || externalId is HashtagId -> {
            DisplayEntryForExternalId(externalId, userBase, accountViewModel, nav)
        }

        // A NIP-73 work (`i` tag): a book, paper, podcast episode, movie...
        externalId != null -> {
            HighlightSourceFromExternalId(externalId, userBase, accountViewModel, nav)
        }

        baseUrl != null || externalIdText != null -> {
            HighlightSourceRow(
                title = baseUrl ?: externalIdText ?: "",
                onTitleClick = null,
                icon = { SourceSymbol(MaterialSymbols.FormatQuote) },
                byline =
                    if (userBase == null) {
                        null
                    } else {
                        { userBase?.let { DisplayEntryForUser(it, accountViewModel, nav) } }
                    },
            )
        }

        userBase != null -> {
            userBase?.let {
                DisplayEntryForUser(it, accountViewModel, nav)
            }
        }
    }
}

@Composable
fun DisplayEntryForUser(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val userMetadata by observeUserInfo(baseUser, accountViewModel)

    CreateClickableTextWithEmoji(
        clickablePart = userMetadata?.info?.bestName() ?: baseUser.pubkeyDisplayHex(),
        maxLines = 1,
        route = remember(baseUser) { routeFor(baseUser) },
        nav = nav,
        tags = userMetadata?.tags,
    )
}

/**
 * The one shape every highlight source takes: a small icon, the source's title on its own line
 * (tappable when there is somewhere to go), and a muted byline underneath -- the site, the kind
 * of work, the author. It replaces a row of `Author - medium.com` fragments that wrapped
 * unpredictably and said little about what was highlighted.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HighlightSourceRow(
    title: String,
    onTitleClick: (() -> Unit)?,
    icon: @Composable () -> Unit,
    byline: (@Composable () -> Unit)?,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(modifier = SourceIconModifier, contentAlignment = Alignment.Center) { icon() }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (onTitleClick != null) {
                ClickableTextPrimary(text = title, maxLines = 2, onClick = onTitleClick)
            } else {
                Text(text = title, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            if (byline != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    byline()
                }
            }
        }
    }
}

@Composable
private fun SourceSymbol(symbol: MaterialSymbol) {
    Icon(
        symbol = symbol,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        tint = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
private fun BylineText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.placeholderText,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BylineDot() {
    Text(text = "\u00B7", color = MaterialTheme.colorScheme.placeholderText, maxLines = 1)
}

/**
 * A web page. The title is the page's OpenGraph title when previews are on and the fetch worked,
 * else the words in its URL, else the site. Tapping the title opens the page scrolled to the
 * passage (text fragment); tapping the site opens the in-app page that gathers every note and
 * highlight about this URL.
 */
@Composable
private fun HighlightSourceFromUrl(
    pageUrl: String,
    highlightQuote: String,
    textFragmentPrefix: String?,
    textFragmentSuffix: String?,
    userBase: User?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val absoluteUrl = remember(pageUrl) { HttpUrlFormatter.addSchemeIfNeeded(pageUrl) }
    val passageUrl =
        remember(absoluteUrl, highlightQuote, textFragmentPrefix, textFragmentSuffix) {
            TextFragmentParser.buildUrl(absoluteUrl, highlightQuote, textFragmentPrefix, textFragmentSuffix)
        }
    // The URL page gathers notes by the page's address: without the `utm_`/`fbclid` noise a
    // highlighter's copy-paste often carries, which comments on the same page don't share.
    val pageRoute = remember(absoluteUrl) { Route.Url(UrlTrackerCleaner.clean(absoluteUrl).substringBefore('#')) }
    val site = remember(absoluteUrl) { HighlightSourceLabels.siteFromUrl(absoluteUrl) }
    val slugTitle = remember(absoluteUrl) { HighlightSourceLabels.titleFromUrl(absoluteUrl) }

    // Fetching the preview reaches out to the third-party page: only when the user allows it.
    val previewInfo =
        if (LocalMediaCapture.current != null || accountViewModel.settings.showUrlPreview()) {
            (rememberUrlPreviewState(absoluteUrl, accountViewModel) as? UrlPreviewState.Loaded)?.previewInfo
        } else {
            null
        }

    val previewTitle =
        remember(previewInfo) {
            previewInfo?.title?.ifBlank { null }?.let { HighlightSourceLabels.cleanTitle(it, previewInfo.siteName) }
        }

    val title = previewTitle ?: slugTitle ?: site ?: absoluteUrl
    // The site already is the title when nothing better was found: don't say it twice.
    val siteLabel = site?.takeIf { it != title }
    val pageAuthor = previewInfo?.author?.ifBlank { null }

    val uriHandler = LocalUriHandler.current

    HighlightSourceRow(
        title = title,
        onTitleClick = { runCatching { uriHandler.openUri(passageUrl) } },
        icon = {
            SiteIcon(previewInfo?.iconUrlFullPath, Modifier.fillMaxSize()) {
                SourceSymbol(MaterialSymbols.Language)
            }
        },
        byline =
            if (siteLabel == null && userBase == null && pageAuthor == null) {
                null
            } else {
                {
                    if (siteLabel != null) {
                        val description = stringRes(Res.string.highlight_source_more_from, siteLabel)
                        Text(
                            text = siteLabel,
                            color = MaterialTheme.colorScheme.placeholderText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .semantics { contentDescription = description }
                                    .clickable { nav.nav(pageRoute) },
                        )
                    }

                    // A nostr author outranks the page's own byline: it is a profile.
                    if (userBase != null) {
                        if (siteLabel != null) BylineDot()
                        DisplayEntryForUser(userBase, accountViewModel, nav)
                    } else if (pageAuthor != null) {
                        if (siteLabel != null) BylineDot()
                        BylineText(pageAuthor)
                    }
                }
            },
    )
}

/**
 * A nostr event: an article, a wiki page, a note, a video... Titled by its `title`/`subject`, or
 * by its `d` tag while the event is still loading (or when it has no title), else by its kind.
 * An `a` source knows its kind and author before the event arrives, so the row is never empty.
 */
@Composable
private fun HighlightSourceFromNote(
    note: Note,
    userBase: User?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(note, accountViewModel)
    val event = noteState.note.event
    val address = (note as? AddressableNote)?.address

    val author = userBase ?: noteState.note.author ?: address?.let { rememberUser(it.pubKeyHex) }

    // Any event's tags will do: NIP-84 can quote a wiki page or a video as well as an article.
    val tagTitle = remember(event) { event?.tags?.firstTagValueFor("title", "subject")?.ifBlank { null } }
    val slugTitle = remember(address) { address?.dTag?.let { HighlightSourceLabels.titleFromSlug(it) } }

    val kind = event?.kind ?: address?.kind
    val kindLabel = kind?.let { sourceKindLabel(it) }

    val title = tagTitle ?: slugTitle ?: kindLabel

    if (title == null) {
        // An `e` source that hasn't loaded: its kind is unknown, so the author is all there is.
        author?.let { DisplayEntryForUser(it, accountViewModel, nav) }
        return
    }

    HighlightSourceRow(
        title = title,
        onTitleClick = { routeFor(note, accountViewModel.account)?.let { nav.nav(it) } },
        icon = { SourceSymbol(kindSymbol(kind)) },
        byline =
            if ((kindLabel == null || kindLabel == title) && author == null) {
                null
            } else {
                {
                    val shownKind = kindLabel?.takeIf { it != title }
                    if (shownKind != null) BylineText(shownKind)

                    if (author != null) {
                        if (shownKind != null) BylineDot()
                        DisplayEntryForUser(author, accountViewModel, nav)
                    }
                }
            },
    )
}

/** A NIP-73 work that isn't a web page: a book, paper, podcast or movie. */
@Composable
private fun HighlightSourceFromExternalId(
    externalId: ExternalId,
    userBase: User?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title =
        when (externalId) {
            is BookId -> stringRes(Res.string.highlight_source_isbn, externalId.isbn)
            is PaperId -> stringRes(Res.string.highlight_source_doi, externalId.doi)
            is MovieId -> stringRes(Res.string.highlight_source_isan, externalId.isan)
            is PodcastEpisodeId -> stringRes(Res.string.highlight_source_podcast_episode)
            is PodcastFeedId, is PodcastPublisherId -> stringRes(Res.string.highlight_source_podcast)
            else -> externalId.toScope()
        }

    val kindLabel =
        when (externalId) {
            is BookId -> stringRes(Res.string.highlight_source_book)
            is PaperId -> stringRes(Res.string.highlight_source_paper)
            is MovieId -> stringRes(Res.string.highlight_source_movie)
            else -> null
        }

    val symbol =
        when (externalId) {
            is BookId -> MaterialSymbols.MenuBook
            is PaperId -> MaterialSymbols.Science
            is MovieId -> MaterialSymbols.Movie
            is PodcastEpisodeId, is PodcastFeedId, is PodcastPublisherId -> MaterialSymbols.Podcasts
            else -> MaterialSymbols.Public
        }

    // Where to go: the NIP-73 hint when the event carries one, else doi.org for a paper -- the
    // DOI's own resolver. Nothing for the rest: no other id has a neutral home to send it to.
    val target =
        remember(externalId) {
            externalId.hint()?.takeIf { HighlightEvent.isUrlReference(it) }?.let(HttpUrlFormatter::addSchemeIfNeeded)
                ?: (externalId as? PaperId)?.let { "https://doi.org/" + it.doi }
        }

    val uriHandler = LocalUriHandler.current

    HighlightSourceRow(
        title = title,
        onTitleClick =
            if (target != null) {
                { runCatching { uriHandler.openUri(target) } }
            } else {
                null
            },
        icon = { SourceSymbol(symbol) },
        byline =
            if (kindLabel == null && userBase == null) {
                null
            } else {
                {
                    if (kindLabel != null) BylineText(kindLabel)

                    if (userBase != null) {
                        if (kindLabel != null) BylineDot()
                        DisplayEntryForUser(userBase, accountViewModel, nav)
                    }
                }
            },
    )
}

@Composable
fun DisplayEntryForExternalId(
    externalId: ExternalId,
    userBase: User?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (userBase != null) {
            DisplayEntryForUser(userBase, accountViewModel, nav)
            Spacer(Modifier.width(4.dp))
            Text("\u00B7", maxLines = 1)
            Spacer(Modifier.width(4.dp))
        }
        DisplayExternalId(externalId, accountViewModel, nav)
    }
}

/**
 * What one event of [kind] is called, singular: "Article", not the "Blogs" a relay's kind filter
 * lists. Kinds without a singular name here fall back to the catalog's label.
 */
@Composable
private fun sourceKindLabel(kind: Int): String =
    when (kind) {
        TextNoteEvent.KIND -> stringRes(Res.string.highlight_source_note)
        LongFormContentEvent.KIND -> stringRes(Res.string.highlight_source_article)
        WikiArticleEvent.KIND -> stringRes(Res.string.highlight_source_wiki)
        PictureEvent.KIND -> stringRes(Res.string.highlight_source_picture)
        VideoNormalEvent.KIND, VideoShortEvent.KIND, AddressableNormalVideoEvent.KIND, AddressableShortVideoEvent.KIND ->
            stringRes(Res.string.highlight_source_video)
        CommentEvent.KIND -> stringRes(Res.string.highlight_source_comment)
        else -> kindName(kind)
    }

private fun kindSymbol(kind: Int?): MaterialSymbol =
    when (kind) {
        LongFormContentEvent.KIND, WikiArticleEvent.KIND -> MaterialSymbols.AutoMirrored.Article
        PictureEvent.KIND -> MaterialSymbols.Image
        VideoNormalEvent.KIND, VideoShortEvent.KIND, AddressableNormalVideoEvent.KIND, AddressableShortVideoEvent.KIND -> MaterialSymbols.Videocam
        else -> MaterialSymbols.Description
    }

/** The cached user for [pubKeyHex], created in the background when it isn't cached yet. */
@Composable
private fun rememberUser(pubKeyHex: String): User? {
    var user by remember(pubKeyHex) { mutableStateOf(LocalCache.getUserIfExists(pubKeyHex)) }
    if (user == null) {
        LaunchedEffect(pubKeyHex) {
            user = LocalCache.checkGetOrCreateUser(pubKeyHex)
        }
    }
    return user
}

// Sized to the first line of body text, so the icon sits beside the title.
private val SourceIconModifier = Modifier.padding(top = 2.dp).size(18.dp)

@Composable
private fun DisplayHighlightQuote(
    highlight: String,
    context: String?,
    textFragmentPrefix: String?,
    accountViewModel: AccountViewModel,
) {
    val quote =
        remember(highlight, context, textFragmentPrefix) {
            HighlightQuote.of(highlight, context, textFragmentPrefix)
        }

    TranslatableRichTextViewer(
        content = quote.text,
        id = quote.text,
        // Indented like the attribution, so the "Auto-translated from …" line sits under the
        // quote text rather than under the bar.
        translationMessageModifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp, start = HighlightQuoteIndent),
        accountViewModel = accountViewModel,
    ) { shown ->
        // A translation rewrites the passage, so the original offsets stop locating anything.
        // Translate the quote too and re-find it inside the translated context — ML Kit works
        // sentence by sentence, so a quote that is one or more whole sentences comes back the
        // same either way. HighlightQuote.of degrades to the quote alone when the two
        // translations don't line up, which beats marking a span the reader never highlighted.
        val translatedQuote = rememberTranslation(highlight, accountViewModel)

        val display =
            remember(shown, translatedQuote, quote) {
                if (shown == quote.text) quote else HighlightQuote.of(translatedQuote, shown)
            }

        HighlightedQuote(
            text = display.text,
            highlight = display.marked,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
