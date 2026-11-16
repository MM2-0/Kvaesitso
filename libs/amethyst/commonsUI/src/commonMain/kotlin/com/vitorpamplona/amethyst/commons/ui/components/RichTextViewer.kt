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
package com.vitorpamplona.amethyst.commons.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.em
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.model.navigation.routeFor
import com.vitorpamplona.amethyst.commons.model.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.relayClient.user.UserFinderFilterAssemblerSubscription
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserDisplayNickname
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserInfo
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.richtext.HashIndexEventSegment
import com.vitorpamplona.amethyst.commons.richtext.HashIndexUserSegment
import com.vitorpamplona.amethyst.commons.richtext.HashTagSegment
import com.vitorpamplona.amethyst.commons.richtext.ParagraphState
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.Segment
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.components.CrossfadeIfEnabled
import com.vitorpamplona.amethyst.commons.ui.components.LocalInlineQuoteRenderer
import com.vitorpamplona.amethyst.commons.ui.components.ProvideMediaBleed
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.LoadUser
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateClickableTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.richtext.CreateTextWithEmoji
import com.vitorpamplona.amethyst.commons.ui.richtext.HashtagIcon
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextInteractions
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextPlatform
import com.vitorpamplona.amethyst.commons.ui.richtext.LocalRichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextInteractions
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextSegmentRenderer
import com.vitorpamplona.amethyst.commons.ui.richtext.checkForHashtagWithIcon
import com.vitorpamplona.amethyst.commons.ui.state.produceCachedState
import com.vitorpamplona.amethyst.commons.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.commons.ui.theme.inlinePlaceholder
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.amethyst.commons.ui.richtext.RichTextViewer as CommonsRichTextViewer

/**
 * Renders Nostr rich text. Markdown goes to the front end's renderer; plain rich text is parsed
 * once ([CachedRichTextParser]) and laid out by the shared [CommonsRichTextViewer] core, which
 * renders text, emoji, hashtags and mentions itself and hands every platform segment (media,
 * LaTeX, payments, link previews) to the [RichTextSegmentRenderer] the front end's
 * [LocalRichTextPlatform] builds for this call.
 */
@Composable
fun RichTextViewer(
    content: String,
    canPreview: Boolean,
    quotesLeft: Int,
    modifier: Modifier,
    tags: ImmutableListOfLists<String>,
    backgroundColor: MutableState<Color>,
    callbackUri: String? = null,
    authorPubKey: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val platform = LocalRichTextPlatform.current

    Column(modifier = modifier) {
        if (remember(content) { CachedRichTextParser.isMarkdown(content) }) {
            // Markdown hands the bleed only to the media on its top-level lines, not to lists or quotes.
            platform.Markdown(content, tags, canPreview, quotesLeft, backgroundColor, callbackUri, accountViewModel, nav)
            return@Column
        }

        val state by remember(content, tags) {
            mutableStateOf(CachedRichTextParser.parseText(content, tags, callbackUri, authorPubKey))
        }

        val renderer =
            remember(platform, accountViewModel, nav, backgroundColor, callbackUri, canPreview) {
                platform.segmentRenderer(accountViewModel, nav, backgroundColor, callbackUri, canPreview)
            }

        val interactions =
            remember(nav) {
                RichTextInteractions(
                    onClickHashtag = { nav.nav(Route.Hashtag(it.lowercase())) },
                    onClickNip = { nav.nav(Route.Nip(it)) },
                )
            }

        CompositionLocalProvider(
            LocalRichTextSegmentRenderer provides renderer,
            LocalRichTextInteractions provides interactions,
        ) {
            CommonsRichTextViewer(
                state = state,
                canPreview = canPreview,
                quotesLeft = quotesLeft,
                authorPubKey = authorPubKey,
            )
        }
    }
}

@Composable
fun RenderTextParagraph(
    paragraph: ParagraphState,
    spaceWidth: Dp,
    modifier: Modifier,
    renderWord: @Composable (word: Segment) -> Unit,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spaceWidth),
        // Center items on the cross axis so a taller item (an equation, whose image
        // is taller than a text line) sits centered on the line instead of hanging
        // below the baseline. No-op for the common all-text row.
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        paragraph.words.forEach { word ->
            renderWord(word)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderRegular(
    content: String,
    tags: ImmutableListOfLists<String>,
    callbackUri: String? = null,
    authorPubKey: String? = null,
    renderParagraph: @Composable (ParagraphState, state: RichTextViewerState, Dp, modifier: Modifier) -> Unit,
) {
    val state by remember(content, tags) { mutableStateOf(CachedRichTextParser.parseText(content, tags, callbackUri, authorPubKey)) }

    val spaceWidth = measureSpaceWidth(LocalTextStyle.current)

    val currentTextStyle = LocalTextStyle.current

    val textStyle =
        remember(currentTextStyle) {
            currentTextStyle.copy(
                lineHeight = 1.3.em,
            )
        }

    Column {
        // FlowRow doesn't work well with paragraphs. So we need to split them
        state.paragraphs.forEach { paragraph ->
            CompositionLocalProvider(
                LocalLayoutDirection provides
                    if (paragraph.isRTL) {
                        LayoutDirection.Rtl
                    } else {
                        LayoutDirection.Ltr
                    },
                LocalTextStyle provides textStyle,
            ) {
                renderParagraph(
                    paragraph,
                    state,
                    spaceWidth,
                    Modifier.align(if (paragraph.isRTL) Alignment.End else Alignment.Start),
                )
            }
        }
    }
}

@Composable
fun measureSpaceWidth(textStyle: TextStyle): Dp {
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    return remember(fontFamilyResolver, density, layoutDirection, textStyle) {
        val widthPx =
            TextMeasurer(fontFamilyResolver, density, layoutDirection, 1)
                .measure(" ", textStyle)
                .size
                .width
        with(density) { widthPx.toDp() }
    }
}

@Composable
fun RenderCustomEmoji(
    word: String,
    state: RichTextViewerState,
) {
    CreateTextWithEmoji(
        text = word,
        emojis = state.customEmoji,
    )
}

@Composable
fun BechLink(
    word: String,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val loadedLink by produceCachedState(cache = accountViewModel.bechLinkCache, key = word)

    val baseNote = loadedLink?.baseNote

    // Preload the placeholder's author NIP-65 outbox so a missing quoted event can be fetched from there.
    baseNote?.author?.let { author ->
        UserFinderFilterAssemblerSubscription(author, accountViewModel)
    }

    if (canPreview && quotesLeft > 0 && baseNote != null) {
        Row {
            DisplayFullNote(
                note = baseNote,
                extraChars = loadedLink?.nip19?.additionalChars?.ifBlank { null },
                quotesLeft = quotesLeft,
                backgroundColor = backgroundColor,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    } else if (loadedLink?.nip19 != null) {
        ClickableRoute(word, loadedLink?.nip19!!, accountViewModel, nav)
    } else {
        val text =
            remember(word) {
                if (word.length > 16) {
                    word.replaceRange(8, word.length - 8, ":")
                } else {
                    word
                }
            }

        Text(text = text, maxLines = 1)
    }
}

@Composable
fun DisplayFullNote(
    note: Note,
    extraChars: String?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LocalInlineQuoteRenderer.current.Render(note, quotesLeft, backgroundColor, accountViewModel, nav)

    extraChars?.let { Text(it) }
}

@Composable
fun HashTag(
    segment: HashTagSegment,
    nav: INav,
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.onBackground
    val hashtagIcon: HashtagIcon? = checkForHashtagWithIcon(segment.hashtag)

    val annotatedTermsString =
        remember(segment.segmentText) {
            buildAnnotatedString {
                withStyle(SpanStyle(color = primary)) {
                    pushStringAnnotation("routeToHashtag", "")
                    append("#${segment.hashtag}")
                    pop()
                }

                if (hashtagIcon != null) {
                    withStyle(SpanStyle(color = primary)) {
                        pushStringAnnotation("routeToHashtag", "")
                        appendInlineContent("inlineContent", "[icon]")
                        pop()
                    }
                }

                segment.extras?.let { withStyle(SpanStyle(color = background)) { append(it) } }
            }
        }

    Text(
        text = annotatedTermsString,
        modifier =
            remember {
                Modifier.clickable {
                    nav.nav(Route.Hashtag(segment.hashtag.lowercase()))
                }
            },
        inlineContent =
            if (hashtagIcon != null) {
                mapOf("inlineContent" to inlineIcon(hashtagIcon))
            } else {
                emptyMap()
            },
    )
}

@Composable
private fun inlineIcon(hashtagIcon: HashtagIcon) =
    InlineTextContent(inlinePlaceholder) {
        Icon(
            imageVector = hashtagIcon.icon,
            contentDescription = hashtagIcon.description,
            tint = Color.Unspecified,
            modifier = hashtagIcon.modifier,
        )
    }

@Composable
fun TagLink(
    word: HashIndexUserSegment,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadUser(baseUserHex = word.hex) {
        if (it == null) {
            Text(text = word.segmentText)
        } else {
            Row {
                DisplayUserFromTag(it, accountViewModel, nav)
                word.extras?.let { it2 ->
                    Text(text = it2)
                }
            }
        }
    }
}

@Composable
fun LoadNote(
    baseNoteHex: String,
    content: @Composable (Note?) -> Unit,
) {
    var note by
        remember(baseNoteHex) { mutableStateOf(LocalCache.getNoteIfExists(baseNoteHex)) }

    if (note == null) {
        LaunchedEffect(key1 = baseNoteHex) {
            note = LocalCache.checkGetOrCreateNote(baseNoteHex)
        }
    }

    content(note)
}

@Composable
fun TagLink(
    word: HashIndexEventSegment,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LoadNote(baseNoteHex = word.hex) {
        if (it == null) {
            Text(text = remember { word.segmentText.toShortDisplay() })
        } else {
            Row {
                DisplayNoteFromTag(
                    it,
                    word.extras,
                    canPreview,
                    quotesLeft,
                    accountViewModel,
                    backgroundColor,
                    nav,
                )
            }
        }
    }
}

@Preview
@Composable
fun DisplayNoteFromTagPreview() {
    val dummyPost =
        TextNoteEvent(
            id = "0b6d941c46411a95edb1c93da7ad6ca26370497d8c7b7d621f5cb59f48841bad",
            pubKey = "6dd3b72e325da7383b275eef1c66131ba4664326e162bc060527509b4e33ae43",
            createdAt = 1753988264,
            tags = emptyArray(),
            content = "test",
            sig = "ec39e60722a083cccbd2d82d2827e13f5499fa7cbcedac5b76011a844c077473adb629d50d01fab147835ac6c8a3d5ba9aaddd87d6723f0c3c864b9119fc4356",
        )

    LocalCache.justConsume(dummyPost, null, true)
    val note = LocalCache.getOrCreateNote(dummyPost.id)

    ThemeComparisonColumn(
        toPreview = {
            ClickableTextPrimary(
                text = "@${note.idNote().toShortDisplay()}",
                onClick = { },
            )
        },
    )
}

@Composable
private fun DisplayNoteFromTag(
    baseNote: Note,
    addedChars: String?,
    canPreview: Boolean,
    quotesLeft: Int,
    accountViewModel: AccountViewModel,
    backgroundColor: MutableState<Color>,
    nav: INav,
) {
    if (canPreview && quotesLeft > 0) {
        LocalInlineQuoteRenderer.current.Render(baseNote, quotesLeft, backgroundColor, accountViewModel, nav)
    } else {
        ClickableTextPrimary(
            text = "@${baseNote.idNote().toShortDisplay()}",
            onClick = { routeFor(baseNote, accountViewModel.account)?.let { nav.nav(it) } },
        )
    }

    addedChars?.ifBlank { null }?.let { Text(text = it) }
}

@Composable
private fun DisplayUserFromTag(
    baseUser: User,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val meta by observeUserInfo(baseUser, accountViewModel)
    val nickname by observeUserDisplayNickname(baseUser, accountViewModel)
    val petName = nickname?.petName

    CrossfadeIfEnabled(targetState = meta, label = "DisplayUserFromTag") {
        Row {
            CreateClickableTextWithEmoji(
                clickablePart = remember(meta, petName) { petName ?: it?.info?.bestName() ?: baseUser.pubkeyDisplayHex() },
                maxLines = 1,
                route = remember(baseUser) { routeFor(baseUser) },
                nav = nav,
                tags = if (petName != null) nickname?.tags else it?.tags,
            )
        }
    }
}
