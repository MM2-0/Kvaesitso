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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.publicChats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.commons.ui.note.ClickableNote
import com.vitorpamplona.amethyst.commons.ui.note.LongPressToQuickAction
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.commons.ui.note.calculateBackgroundColor
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.discover.nip28Chats.RenderPublicChatChannelThumb
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.StdPadding
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel

@Composable
fun PublicChatsFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val followedSet by accountViewModel.account.publicChatList.flowSet
        .collectAsStateWithLifecycle()

    val pinned by accountViewModel.account.publicChatList.flowSetNote
        .collectAsStateWithLifecycle()

    val unpinned =
        remember(items.list, followedSet) {
            items.list.filter { it.idHex !in followedSet }
        }

    LaunchedEffect(pinned.firstOrNull()?.idHex, unpinned.firstOrNull()?.idHex) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        items(
            pinned,
            key = { item -> "pinned-" + item.idHex },
        ) { item ->
            PublicChatRow(item, pinned = true, accountViewModel, nav)
        }

        if (pinned.isNotEmpty() && unpinned.isNotEmpty()) {
            item(key = "pinned-unpinned-gap", contentType = "section-gap") {
                Box(Modifier.fillMaxWidth().height(8.dp))
            }
        }

        itemsIndexed(
            unpinned,
            key = { _, item -> item.idHex },
        ) { _, item ->
            PublicChatRow(item, pinned = false, accountViewModel, nav)
        }
    }
}

@Composable
private fun LazyItemScope.PublicChatRow(
    baseNote: Note,
    pinned: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val modifier = Modifier.fillMaxWidth()

    Box(Modifier.fillMaxWidth().animateItem()) {
        WatchNoteEvent(
            baseNote = baseNote,
            accountViewModel = accountViewModel,
            onBlank = {
                RenderChannel(baseNote, modifier, accountViewModel, nav)
            },
            onNoteEventFound = {
                CheckHiddenFeedWatchBlockAndReport(
                    note = baseNote,
                    modifier = modifier,
                    ignoreAllBlocksAndReports = false,
                    showHiddenWarning = false,
                    accountViewModel = accountViewModel,
                    nav = nav,
                ) { _ ->
                    RenderChannel(baseNote, modifier, accountViewModel, nav)
                }
            },
        )

        if (pinned) {
            PinBadge(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 14.dp),
            )
        }
    }

    HorizontalDivider(
        thickness = DividerThickness,
    )
}

@Composable
private fun RenderChannel(
    baseNote: Note,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    LongPressToQuickAction(baseNote, accountViewModel, nav) { showPopup ->
        ClickableNote(
            baseNote = baseNote,
            backgroundColor =
                calculateBackgroundColor(
                    createdAt = baseNote.createdAt(),
                    routeForLastRead = "PublicChatsFeed",
                    parentBackgroundColor = null,
                    accountViewModel = accountViewModel,
                ),
            modifier = modifier,
            accountViewModel = accountViewModel,
            showPopup = showPopup,
            nav = nav,
        ) {
            Column(StdPadding) {
                SensitivityWarning(
                    note = baseNote,
                    accountViewModel = accountViewModel,
                ) {
                    RenderPublicChatChannelThumb(baseNote, accountViewModel, nav)
                }
            }
        }
    }
}

@Composable
private fun PinBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(22.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.PushPin,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
