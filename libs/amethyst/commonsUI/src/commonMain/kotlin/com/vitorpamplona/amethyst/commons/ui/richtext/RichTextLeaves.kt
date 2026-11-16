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
package com.vitorpamplona.amethyst.commons.ui.richtext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.emojicoder.EmojiCoder
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.RichTextViewerState
import com.vitorpamplona.amethyst.commons.richtext.SecretEmoji
import com.vitorpamplona.amethyst.commons.ui.components.AnimatedBorderTextCornerRadius
import com.vitorpamplona.amethyst.commons.ui.components.BleedingMedia
import com.vitorpamplona.amethyst.commons.ui.components.ClickableTextPrimary
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrlOrBlossom
import com.vitorpamplona.amethyst.commons.ui.components.rememberBlossomUriOpener
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.LoadUrlPreview
import com.vitorpamplona.amethyst.commons.ui.note.platform.ZoomableContentView
import com.vitorpamplona.amethyst.commons.ui.platform.LocalAppServices
import com.vitorpamplona.amethyst.commons.ui.theme.CashuCardBorders
import com.vitorpamplona.amethyst.commons.ui.theme.RichTextBlockPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nipB7Blossom.BlossomUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The server URL [blossomUri] resolves to through the app's BUD-10 resolver
 * ([LocalAppServices]' `blossomServerFinder`), null until (or unless) a server answers.
 */
@Composable
private fun rememberBlossomServerUrl(blossomUri: String): String? {
    val finder = LocalAppServices.current.blossomServerFinder
    var serverUrl by remember(blossomUri, finder) { mutableStateOf(finder.cachedServerUrl(blossomUri)) }

    if (serverUrl == null) {
        LaunchedEffect(blossomUri, finder) {
            serverUrl = finder.findServerUrl(blossomUri)
        }
    }

    return serverUrl?.takeIf { it.isNotBlank() }
}

private fun blossomFileName(blossomUri: String) = BlossomUri.parse(blossomUri)?.filename() ?: blossomUri

/**
 * A `blossom:` URI in a note: the zoomable media when the note lists it as media, otherwise the
 * link preview of the server that holds it, or a link that resolves it on tap.
 */
@Composable
fun BlossomUriRenderer(
    word: String,
    state: RichTextViewerState,
    callbackUri: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val media = state.mediaForPager[word]
    if (media != null) {
        BleedingMedia(RichTextBlockPadding, enabled = media !is MediaUrlPdf) { roundedCorner ->
            ZoomableContentView(
                content = media,
                images = state.mediaList,
                roundedCorner = roundedCorner,
                contentScale = ContentScale.FillWidth,
                accountViewModel = accountViewModel,
            )
        }
    } else {
        val serverUrl = rememberBlossomServerUrl(word)
        if (serverUrl != null) {
            LoadUrlPreview(serverUrl, remember(word) { blossomFileName(word) }, callbackUri, accountViewModel, nav)
        } else {
            ClickableBlossomUri(word, accountViewModel)
        }
    }
}

/** A `blossom:` URI where previews are off: a link to the resolved server, or one that resolves on tap. */
@Composable
fun BlossomUriRendererNoPreview(
    word: String,
    accountViewModel: AccountViewModel,
) {
    val serverUrl = rememberBlossomServerUrl(word)
    if (serverUrl != null) {
        ClickableUrlOrBlossom(remember(word) { blossomFileName(word) }, serverUrl)
    } else {
        ClickableBlossomUri(word, accountViewModel)
    }
}

/** The blob's file name, opening the `blossom:` URI on tap. */
@Composable
fun ClickableBlossomUri(
    blossomUri: String,
    accountViewModel: AccountViewModel,
) {
    val blossomOpener = rememberBlossomUriOpener()

    ClickableTextPrimary(
        text = remember(blossomUri) { blossomFileName(blossomUri) },
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
        onClick = { blossomOpener.open(blossomUri) { title, message -> accountViewModel.toastManager.toast(title, message) } },
    )
}

/**
 * An emoji carrying a hidden message in its variation selectors: a glowing emoji that reveals the
 * message on tap, rendered with the platform's leaves. Plain text where previews are off or the
 * quote budget is spent.
 */
@Composable
fun DisplaySecretEmoji(
    segment: SecretEmoji,
    state: RichTextViewerState,
    callbackUri: String?,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (canPreview && quotesLeft > 0) {
        var secretContent by remember(segment) {
            mutableStateOf(CachedRichTextParser.cachedText(EmojiCoder.decode(segment.segmentText), state.tags))
        }
        var showPopup by remember { mutableStateOf(false) }

        if (secretContent == null) {
            LaunchedEffect(segment) {
                secretContent =
                    withContext(Dispatchers.Default) {
                        CachedRichTextParser.parseText(EmojiCoder.decode(segment.segmentText), state.tags)
                    }
            }
        }

        AnimatedBorderTextCornerRadius(
            segment.segmentText,
            Modifier.clickable { showPopup = !showPopup },
        )

        val localSecretContent = secretContent
        if (localSecretContent != null && showPopup) {
            SecretMessageContent(localSecretContent, callbackUri, quotesLeft, backgroundColor, accountViewModel, nav)
        }
    } else {
        Text(segment.segmentText)
    }
}

/**
 * A decoded secret message, laid out by the shared viewer with the platform's segment renderer
 * (so it gets the same media, payment cards and LaTeX as a note body). Several paragraphs sit in
 * a card, so the reveal reads as one block.
 */
@Composable
fun SecretMessageContent(
    content: RichTextViewerState,
    callbackUri: String?,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (content.paragraphs.isEmpty()) return

    val platform = LocalRichTextPlatform.current
    val renderer =
        remember(platform, accountViewModel, nav, backgroundColor, callbackUri) {
            platform.segmentRenderer(accountViewModel, nav, backgroundColor, callbackUri, true)
        }
    val interactions =
        remember(nav) {
            RichTextInteractions(onClickHashtag = { nav.nav(Route.Hashtag(it.lowercase())) }, onClickNip = { nav.nav(Route.Nip(it)) })
        }

    CompositionLocalProvider(
        LocalRichTextSegmentRenderer provides renderer,
        LocalRichTextInteractions provides interactions,
    ) {
        Column(if (content.paragraphs.size > 1) CashuCardBorders else Modifier) {
            RichTextViewer(state = content, canPreview = true, quotesLeft = quotesLeft)
        }
    }
}
