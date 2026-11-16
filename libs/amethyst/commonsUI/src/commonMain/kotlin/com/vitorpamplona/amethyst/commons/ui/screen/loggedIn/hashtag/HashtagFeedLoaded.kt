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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.hashtag

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.hashtag_label_added_by
import com.vitorpamplona.amethyst.commons.ui.feeds.PrefetchLoadedFeedMedia
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.HalfStartPadding
import com.vitorpamplona.amethyst.commons.ui.theme.Size16Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.tags.hashtags.isTaggedHash

/**
 * Hashtag feed renderer that adds an attribution banner above any post that a followed user
 * surfaced through a NIP-32 hashtag label (rather than the author tagging it directly).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HashtagFeedLoaded(
    tag: String,
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    PrefetchLoadedFeedMedia(loaded, listState, accountViewModel)

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(
            items.list,
            key = { _, item -> item.idHex },
            contentType = { _, item -> item.event?.kind ?: -1 },
        ) { _, item ->
            Column(Modifier.fillMaxWidth().animateItem()) {
                HashtagLabelAttribution(item, tag, accountViewModel)

                NoteCompose(
                    item,
                    modifier = Modifier.fillMaxWidth(),
                    routeForLastRead = null,
                    isBoostedNote = false,
                    isHiddenFeed = items.showHidden,
                    quotesLeft = 3,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(
                thickness = DividerThickness,
            )
        }
    }
}

/**
 * Shows "🏷 #tag added by <user>" when a followed user labeled this post with the hashtag
 * and the author didn't include it themselves. Renders nothing otherwise.
 */
@Composable
fun HashtagLabelAttribution(
    note: Note,
    tag: String,
    accountViewModel: AccountViewModel,
) {
    val labelState by note
        .flow()
        .labels.stateFlow
        .collectAsStateWithLifecycle()

    // The author already used the hashtag — nothing to attribute.
    if (note.event?.isTaggedHash(tag) == true) return

    val follows = accountViewModel.account.followingKeySet()
    val labeler =
        labelState.note.labels[tag.lowercase()]
            ?.firstOrNull { it.author?.pubkeyHex in follows }
            ?.author ?: return

    Row(
        modifier = Modifier.fillMaxWidth().then(HalfStartPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.Tag,
            contentDescription = null,
            modifier = Size16Modifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )
        Spacer(StdHorzSpacer)
        Text(
            "#$tag",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
        )
        Spacer(StdHorzSpacer)
        Text(
            stringRes(Res.string.hashtag_label_added_by),
            color = MaterialTheme.colorScheme.placeholderText,
            maxLines = 1,
        )
        Spacer(StdHorzSpacer)
        UsernameDisplay(
            labeler,
            fontWeight = FontWeight.Bold,
            textColor = MaterialTheme.colorScheme.placeholderText,
            accountViewModel = accountViewModel,
        )
    }
}
