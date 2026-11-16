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
package com.vitorpamplona.amethyst.commons.nipsOnNostr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipLinks
import com.vitorpamplona.amethyst.commons.nipsOnNostr.NipTextView
import com.vitorpamplona.amethyst.commons.nipsOnNostr.OfficialNipState
import com.vitorpamplona.amethyst.commons.nipsOnNostr.OfficialNipViewModel
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip_load_failed
import com.vitorpamplona.amethyst.commons.resources.nip_number
import com.vitorpamplona.amethyst.commons.resources.nip_open_on_github
import com.vitorpamplona.amethyst.commons.resources.try_again
import com.vitorpamplona.amethyst.commons.ui.components.RichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.rememberViewModel
import com.vitorpamplona.amethyst.commons.ui.markdown.LocalMarkdownAnchors
import com.vitorpamplona.amethyst.commons.ui.markdown.MarkdownAnchors
import com.vitorpamplona.amethyst.commons.ui.markdown.markdownAnchorViewport
import com.vitorpamplona.amethyst.commons.ui.markdown.rememberMarkdownAnchors
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.coroutines.delay

/**
 * An official NIP from the nips repo, read in the app: the same header and Markdown reader as a
 * NIP published on Nostr, with a table of contents, highlighted code and links to other NIPs
 * that open here too. Scrolls to [anchor] once the text is laid out.
 */
@Composable
fun OfficialNipScreen(
    number: String,
    anchor: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel = rememberViewModel(key = "OfficialNip-$number") { OfficialNipViewModel(number, accountViewModel.httpClientBuilder) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.nip_number, number), nav) {
                IconButton(onClick = { runCatching { uriHandler.openUri(NipLinks.repoUrl(number)) } }) {
                    Icon(MaterialSymbols.AutoMirrored.OpenInNew, contentDescription = stringRes(Res.string.nip_open_on_github))
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                OfficialNipState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                OfficialNipState.Failed -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringRes(Res.string.nip_load_failed, number),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.grayText,
                        )
                        Button(onClick = viewModel::load) { Text(stringRes(Res.string.try_again)) }
                    }
                }

                is OfficialNipState.Loaded -> {
                    NipReader(current.view, number, anchor, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun NipReader(
    view: NipTextView,
    number: String,
    anchor: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val scroll = rememberScrollState()
    val anchors = rememberMarkdownAnchors(scroll)
    val background = MaterialTheme.colorScheme.background
    val backgroundColor = remember { mutableStateOf(background) }

    JumpToAnchorOnOpen(anchors, anchor)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .markdownAnchorViewport(anchors)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        NipHeader(view, nav, anchors)
        Spacer(Modifier.height(16.dp))
        ProvideTextStyle(TextStyle(fontSize = 18.sp, lineHeight = 1.20.em)) {
            CompositionLocalProvider(LocalMarkdownAnchors provides anchors) {
                TranslatableRichTextViewer(content = view.body, id = "nip-$number", accountViewModel = accountViewModel) { text ->
                    RichTextViewer(
                        content = text,
                        canPreview = true,
                        quotesLeft = 1,
                        modifier = Modifier.fillMaxWidth(),
                        tags = EmptyTagList,
                        backgroundColor = backgroundColor,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Waits for the section to be laid out, then scrolls to it; gives up after a couple of seconds. */
@Composable
private fun JumpToAnchorOnOpen(
    anchors: MarkdownAnchors,
    anchor: String?,
) {
    LaunchedEffect(anchors, anchor) {
        if (anchor == null) return@LaunchedEffect
        repeat(20) {
            delay(100)
            if (anchors.jumpTo(anchor)) return@LaunchedEffect
        }
    }
}
