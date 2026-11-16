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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.gitRepo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitCiStatusIcon
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitLabelTag
import com.vitorpamplona.amethyst.commons.nip34Git.ui.GitStateIcon
import com.vitorpamplona.amethyst.commons.nip34Git.ui.gitMetaStyle
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitItemLabels
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitItemTitle
import com.vitorpamplona.amethyst.commons.nip34Git.ui.observeGitStatus
import com.vitorpamplona.amethyst.commons.nip34Git.ui.statusKind
import com.vitorpamplona.amethyst.commons.relayClient.gitRepo.LoadGitRepositoryAnnouncements
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_untitled
import com.vitorpamplona.amethyst.commons.ui.layouts.rememberFeedContentPadding
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.commons.ui.note.LongPressToQuickAction
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.commons.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.commons.ui.note.clickableNoteModifier
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.commons.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.reply.GitReplyEvent
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Compact feed renderer for the repository screen's Issues and Patches & PRs tabs.
 *
 * Unlike the generic [com.vitorpamplona.amethyst.commons.ui.note.NoteCompose], which is tuned
 * for items appearing inside a regular feed, this shows a simple one-line-per-item list:
 * author picture, name, NIP-05, subject, time and the shared 3-dot options. It still
 * layers the same gating NoteCompose applies — event loading ([WatchNoteEvent]),
 * mute/block/report hiding ([CheckHiddenFeedWatchBlockAndReport]) and the long-press
 * quick-action menu ([LongPressToQuickAction]) — so blocked authors and reported items
 * are hidden here exactly as they are elsewhere. No note body or media is rendered, so
 * sensitive content never reaches this list in the first place.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GitItemFeedLoaded(
    loaded: FeedState.Loaded,
    listState: LazyListState,
    accountViewModel: AccountViewModel,
    nav: INav,
    labelFilter: String? = null,
    itemFilter: ((Note) -> Boolean)? = null,
) {
    val items by loaded.feed.collectAsStateWithLifecycle()
    val list =
        remember(items, labelFilter, itemFilter) {
            items.list.filter { (labelFilter == null || labelFilter in gitLabelsOf(it)) && (itemFilter == null || itemFilter(it)) }
        }

    LazyColumn(
        contentPadding = rememberFeedContentPadding(FeedPadding),
        state = listState,
    ) {
        itemsIndexed(
            list,
            key = { _, item -> item.idHex },
            contentType = { _, item -> item.event?.kind ?: -1 },
        ) { _, item ->
            Row(Modifier.fillMaxWidth().animateItem()) {
                GitItemRow(
                    note = item,
                    isHiddenFeed = items.showHidden,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }

            HorizontalDivider(thickness = DividerThickness)
        }
    }
}

@Composable
private fun GitItemRow(
    note: Note,
    isHiddenFeed: Boolean,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val modifier = Modifier.fillMaxWidth()

    WatchNoteEvent(
        baseNote = note,
        accountViewModel = accountViewModel,
        nav = nav,
        modifier = modifier,
    ) {
        CheckHiddenFeedWatchBlockAndReport(
            note = note,
            modifier = modifier,
            showHiddenWarning = false,
            ignoreAllBlocksAndReports = isHiddenFeed,
            accountViewModel = accountViewModel,
            nav = nav,
        ) { _ ->
            LongPressToQuickAction(baseNote = note, accountViewModel = accountViewModel, nav = nav) { showPopup ->
                GitItemRowContent(
                    note = note,
                    modifier = modifier,
                    showPopup = showPopup,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}

@Composable
private fun GitItemRowContent(
    note: Note,
    modifier: Modifier,
    showPopup: () -> Unit,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    note.event?.let { LoadGitRepositoryAnnouncements(it) }

    val isIssue = note.event is GitIssueEvent
    val kind = observeGitStatus(note)?.statusKind() ?: StatusKind.OPEN

    // Title first, as a forge lists them: the state's glyph, the title, then one muted line of
    // labels, author and age. The options menu is on long press.
    Row(
        modifier =
            clickableNoteModifier(note, modifier, accountViewModel, showPopup, nav)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GitStateIcon(
            kind = kind,
            forIssue = isIssue,
            contentDescription = null,
            modifier = Modifier.padding(top = 2.dp),
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = observeGitItemTitle(note) ?: stringRes(Res.string.git_untitled),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                observeGitItemLabels(note).take(4).forEach { GitLabelTag(it) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WatchAuthor(note, accountViewModel) { author ->
                        UserPicture(user = author, size = 16.dp, accountViewModel = accountViewModel, nav = nav)
                    }
                    ProvideTextStyle(gitMetaStyle()) {
                        NoteUsernameDisplay(note, textColor = MaterialTheme.colorScheme.grayText, accountViewModel = accountViewModel)
                    }
                    Text(text = timeAgo(note.createdAt(), prefix = "· "), style = gitMetaStyle(), maxLines = 1)
                    if (note.event is GitPullRequestEvent || note.event is GitPatchEvent) {
                        GitCiStatusIcon(targetIdHex = note.idHex, accountViewModel = accountViewModel, nav = nav)
                    }
                }
            }
        }

        Box(modifier = Modifier.padding(top = 2.dp)) {
            GitCommentCount(note, accountViewModel)
        }
    }
}

/** The number of comments on [item], left out at zero. Statuses, renames and pushes don't count. */
@Composable
private fun GitCommentCount(
    item: Note,
    accountViewModel: AccountViewModel,
) {
    // The repository's subscription loads the comments with the items' statuses.
    val flow =
        remember(item) {
            item
                .flow()
                .replies.stateFlow
                .map { state -> state.note.replies.count { it.event is CommentEvent || it.event is GitReplyEvent } }
                .distinctUntilChanged()
        }
    val count by flow.collectAsStateWithLifecycle(0)
    if (count == 0) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            symbol = MaterialSymbols.Chat,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
        Text(text = count.toString(), style = gitMetaStyle())
    }
}

internal fun gitSubjectOf(note: Note): String? = GitItemTitles.titleOf(note)

internal fun gitLabelsOf(note: Note): List<String> = GitItemTitles.labelsOf(note)
