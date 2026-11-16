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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepositories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.feeds.FeedState
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitLabelTag
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitMetaStyle
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.commons.ui.note.LongPressToQuickAction
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.commons.ui.note.clickableNoteModifier
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip34Git.repository.GitRepositoryEvent

/**
 * Explore's repositories as a list to scan, not a feed to scroll: owner, name, description and
 * topics, one compact row each. The same gating as a feed card applies (loading, mutes and reports,
 * the long-press menu); tapping a row opens the repository.
 */
@Composable
fun GitRepositoryExploreList(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()

    LazyColumn(contentPadding = rememberFeedContentPadding(FeedPadding), state = listState) {
        itemsIndexed(items.list, key = { _, item -> item.idHex }, contentType = { _, _ -> GitRepositoryEvent.KIND }) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                ExploreRow(item, items.showHidden, accountViewModel, nav)
            }
            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

@Composable
private fun ExploreRow(
    note: Note,
    isHiddenFeed: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val modifier = Modifier.fillMaxWidth()
    WatchNoteEvent(baseNote = note, accountViewModel = accountViewModel, nav = nav, modifier = modifier) {
        CheckHiddenFeedWatchBlockAndReport(
            note = note,
            modifier = modifier,
            showHiddenWarning = false,
            ignoreAllBlocksAndReports = isHiddenFeed,
            accountViewModel = accountViewModel,
            nav = nav,
        ) { _ ->
            LongPressToQuickAction(baseNote = note, accountViewModel = accountViewModel, nav = nav) { showPopup ->
                ExploreRowContent(note, clickableNoteModifier(note, modifier, accountViewModel, showPopup, nav), accountViewModel, nav)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExploreRowContent(
    note: Note,
    modifier: Modifier,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event by observeNoteEvent<GitRepositoryEvent>(note, accountViewModel)
    val repository = event ?: return
    val topics = remember(repository) { repository.hashtags().filter { it.isNotBlank() }.take(4) }

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        WatchAuthor(note, accountViewModel) { owner ->
            UserPicture(user = owner, size = 36.dp, accountViewModel = accountViewModel, nav = nav)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = repository.name() ?: repository.dTag(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            repository.description()?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.grayText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                ProvideTextStyle(gitMetaStyle()) {
                    NoteUsernameDisplay(note, textColor = MaterialTheme.colorScheme.grayText, accountViewModel = accountViewModel)
                }
                Text(text = timeAgo(repository.createdAt, prefix = "· "), style = gitMetaStyle(), maxLines = 1)
                topics.forEach { GitLabelTag(it) }
            }
        }
    }
}
