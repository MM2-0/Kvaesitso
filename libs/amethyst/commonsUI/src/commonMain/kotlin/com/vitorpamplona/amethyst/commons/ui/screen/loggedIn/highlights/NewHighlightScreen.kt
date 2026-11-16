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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.highlights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.nip30CustomEmojis.ui.ShowEmojiSuggestionList
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.new_highlight_note_label
import com.vitorpamplona.amethyst.commons.resources.new_highlight_note_placeholder
import com.vitorpamplona.amethyst.commons.resources.new_highlight_passage_placeholder
import com.vitorpamplona.amethyst.commons.resources.new_highlight_source_label
import com.vitorpamplona.amethyst.commons.resources.new_highlight_title
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.insets.imePaddingSafe
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.PostingTopBar
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.creators.emojiSuggestions.WatchAndLoadMyEmojiList
import com.vitorpamplona.amethyst.commons.ui.note.creators.messagefield.MessageField
import com.vitorpamplona.amethyst.commons.ui.note.creators.userSuggestions.ShowUserSuggestionList
import com.vitorpamplona.amethyst.commons.ui.note.types.ReplyRenderType
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.SuggestionListDefaultHeightPage
import com.vitorpamplona.amethyst.commons.ui.theme.replyModifier
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

/** A warm highlighter amber — the highlight metaphor reads as yellow regardless of theme. */
private val MarkerAccent = Color(0xFFF5C518)

/**
 * The "New Highlight" composer. Reached either from the "Add highlight" action, a browser
 * share, or the "Highlight" note-action, routed in as
 * [com.vitorpamplona.amethyst.commons.model.navigation.Route.NewHighlight].
 *
 * A NIP-84 highlight is a quoted passage, its source, and an optional annotation:
 * - the passage is a pull-quote you craft (accent bar + quotation-mark watermark),
 * - the source is either a nostr event — rendered as a reply-style preview — or a web URL,
 * - the annotation is the same rich composer field the short-note screen uses, so it gets
 *   @-mention and custom-emoji autocomplete and inline previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHighlightScreen(
    quote: String? = null,
    url: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    comment: String? = null,
    context: String? = null,
    sourceAddress: String? = null,
    sourceEventId: String? = null,
    author: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: NewHighlightPostViewModel = rememberViewModel { NewHighlightPostViewModel() }
    postViewModel.init(accountViewModel)

    WatchAndLoadMyEmojiList(accountViewModel)

    LaunchedEffect(Unit) {
        postViewModel.load(quote, url, prefix, suffix, comment, context, sourceAddress, sourceEventId, author)
    }

    Scaffold(
        topBar = {
            PostingTopBar(
                titleRes = Res.string.new_highlight_title,
                isActive = postViewModel::canPost,
                onCancel = { nav.popBack() },
                onPost = {
                    // Uses the accountViewModel scope so releasing the post ViewModel on
                    // popBack can't cancel the in-flight publish.
                    accountViewModel.launchSigner {
                        postViewModel.sendHighlight()
                        nav.popBack()
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePaddingSafe()
                    .fillMaxSize(),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HighlightEditorCard(
                    passage = postViewModel.quote,
                    onPassageChange = { postViewModel.quote = it },
                )

                val source = postViewModel.originalNote
                if (source != null) {
                    NoteCompose(
                        baseNote = source,
                        modifier = MaterialTheme.colorScheme.replyModifier,
                        isQuotedNote = true,
                        unPackReply = ReplyRenderType.NONE,
                        makeItShort = true,
                        quotesLeft = 1,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                } else {
                    IconField(
                        symbol = MaterialSymbols.Link,
                        value = postViewModel.url,
                        onValueChange = { postViewModel.url = it },
                        label = stringRes(Res.string.new_highlight_source_label),
                        placeholder = "https://example.com/article",
                        singleLine = true,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        symbol = MaterialSymbols.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringRes(Res.string.new_highlight_note_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MessageField(
                    placeholder = Res.string.new_highlight_note_placeholder,
                    viewModel = postViewModel,
                    requestFocus = false,
                )
            }

            postViewModel.userSuggestions?.let {
                ShowUserSuggestionList(
                    it,
                    postViewModel::autocompleteWithUser,
                    accountViewModel,
                    modifier = SuggestionListDefaultHeightPage,
                )
            }

            postViewModel.emojiSuggestions?.let {
                ShowEmojiSuggestionList(
                    it,
                    postViewModel::autocompleteWithEmoji,
                    postViewModel::autocompleteWithEmojiUrl,
                    modifier = SuggestionListDefaultHeightPage,
                )
            }
        }
    }
}

/**
 * The hero: a rounded, tonal card carrying a quotation-mark watermark, a highlighter-yellow
 * accent bar, and the editable passage set in a large, comfortable type.
 */
@Composable
private fun HighlightEditorCard(
    passage: String,
    onPassageChange: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Icon(
                symbol = MaterialSymbols.FormatQuote,
                contentDescription = null,
                tint = MarkerAccent.copy(alpha = 0.20f),
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 8.dp, y = (-6).dp)
                        .size(80.dp),
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(20.dp),
            ) {
                Spacer(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(MarkerAccent),
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    BasicTextField(
                        value = passage,
                        onValueChange = onPassageChange,
                        textStyle =
                            LocalTextStyle.current.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 20.sp,
                                lineHeight = 1.4.em,
                                fontWeight = FontWeight.Medium,
                            ),
                        cursorBrush = SolidColor(MarkerAccent),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                        decorationBox = { inner ->
                            if (passage.isEmpty()) {
                                Text(
                                    text = stringRes(Res.string.new_highlight_passage_placeholder),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 20.sp,
                                    lineHeight = 1.4.em,
                                )
                            }
                            inner()
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${passage.length}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconField(
    symbol: MaterialSymbol,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        leadingIcon = {
            Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(20.dp))
        },
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(16.dp),
    )
}
