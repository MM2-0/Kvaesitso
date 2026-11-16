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
package com.vitorpamplona.amethyst.commons.nip34Git.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevision
import com.vitorpamplona.amethyst.commons.nip34Git.pr.GitPullRequestRevisions
import com.vitorpamplona.amethyst.commons.nip34Git.title.GitItemTitles
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_pr_pushed
import com.vitorpamplona.amethyst.commons.resources.git_pr_pushed_rebased
import com.vitorpamplona.amethyst.commons.resources.git_pr_update_description
import com.vitorpamplona.amethyst.commons.resources.git_proposed
import com.vitorpamplona.amethyst.commons.resources.git_proposed_explanation
import com.vitorpamplona.amethyst.commons.resources.git_timeline_patch_added
import com.vitorpamplona.amethyst.commons.resources.git_timeline_patch_revision
import com.vitorpamplona.amethyst.commons.resources.git_timeline_pushed
import com.vitorpamplona.amethyst.commons.resources.git_timeline_pushed_rebased
import com.vitorpamplona.amethyst.commons.resources.git_timeline_renamed
import com.vitorpamplona.amethyst.commons.resources.git_timeline_reviewed
import com.vitorpamplona.amethyst.commons.resources.git_timeline_set_status
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.GitStatusPill
import com.vitorpamplona.amethyst.commons.ui.note.NoteAuthorPicture
import com.vitorpamplona.amethyst.commons.ui.note.NoteUsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.note.gitStatusStyle
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip32Labeling.LabelEvent
import com.vitorpamplona.quartz.nip34Git.coverNote.GitCoverNoteEvent
import com.vitorpamplona.quartz.nip34Git.issue.GitIssueEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitPatchEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestEvent
import com.vitorpamplona.quartz.nip34Git.pr.GitPullRequestUpdateEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.status.GitStatusEvent

private val LabelShape = RoundedCornerShape(6.dp)

/**
 * The NIP-34 events a thread draws as one-line timeline entries instead of posts: status changes
 * (1630-1633) and PR updates (1619). They record something that happened to the issue, patch or
 * PR rather than saying something about it, the way a forge's timeline lists "set status to
 * Closed" or "pushed abc1234" between comments. The full cards (with the item they act on quoted
 * below) stay for the feed, the notifications and quotes, where the item isn't on screen.
 */
fun isGitTimelineEvent(event: Event?): Boolean =
    event is GitStatusEvent ||
        event is GitPullRequestUpdateEvent ||
        // The rest of a patch series and its revisions: pushes, not posts.
        event is GitPatchEvent ||
        // A NIP-34 rename (`#subject`); other labels never link into a thread.
        (event is LabelEvent && event.subjectEdit() != null) ||
        // A review verdict (gitworkshop's 7321).
        event is GitReviewEvent

/**
 * Cover notes (1624) are versions of the item's summary: the item shows the current one and its
 * edit pencil steps through the rest, as kind-1 edits do, so the thread lists none of them.
 */
fun isHiddenInGitThread(event: Event?): Boolean = event is GitCoverNoteEvent

/**
 * One timeline entry in the thread of the issue, patch or PR that [baseNote] acts on. The thread
 * shows that item above, so the entry names only the change: who, what and when.
 */
@Composable
fun GitThreadTimelineRow(
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    when (val event = baseNote.event) {
        is GitStatusEvent -> StatusRow(event, baseNote, modifier, background, accountViewModel, nav)
        is GitPullRequestUpdateEvent -> UpdateRow(event, baseNote, modifier, background, accountViewModel, nav)
        is GitPatchEvent -> PatchRow(event, baseNote, modifier, background, accountViewModel, nav)
        is LabelEvent -> RenameRow(event, baseNote, modifier, background, accountViewModel, nav)
        is GitReviewEvent -> ReviewRow(event, baseNote, modifier, background, accountViewModel, nav)
        else -> {}
    }
}

/** "reviewed · Changes required", with the reviewer's summary under it. A verdict decides nothing: anyone may give one. */
@Composable
private fun ReviewRow(
    event: GitReviewEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val verdict = event.verdict()
    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier,
        background = background,
        symbol = if (verdict == GitReviewEvent.Verdict.ACK || verdict == GitReviewEvent.Verdict.CONCEPT_ACK) MaterialSymbols.CheckCircle else MaterialSymbols.Gavel,
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        action = stringRes(Res.string.git_timeline_reviewed),
        authorised = true,
        accountViewModel = accountViewModel,
        nav = nav,
        inline = { verdict?.let { Text(verdictLabel(it), style = MaterialTheme.typography.labelMedium, color = verdictColor(it)) } },
    ) {
        if (event.content.isNotBlank()) {
            Text(text = event.content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun StatusRow(
    event: GitStatusEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val kind = remember(event) { event.statusKind() }
    val target = baseNote.replyTo?.lastOrNull()?.event
    val isIssue = target is GitIssueEvent
    val style = gitStatusStyle(kind, isIssue)
    // Anyone may publish a status; only the author's or a member's changes the item.
    val authorised = rememberIsAuthorisedChange(event, target)

    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier,
        background = background,
        symbol = style.symbol,
        container = if (authorised) style.container else MaterialTheme.colorScheme.surfaceVariant,
        content = if (authorised) style.content else MaterialTheme.colorScheme.onSurfaceVariant,
        action = stringRes(Res.string.git_timeline_set_status),
        authorised = authorised,
        accountViewModel = accountViewModel,
        nav = nav,
        inline = { GitStatusPill(kind, forIssue = isIssue) },
    ) {
        if (event.content.isNotBlank()) {
            Text(
                text = event.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun UpdateRow(
    event: GitPullRequestUpdateEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val targetNote = baseNote.replyTo?.lastOrNull()
    val pullRequest = targetNote?.let { observeNoteEvent<GitPullRequestEvent>(it, accountViewModel).value }
    val previous = observePreviousRevision(event, pullRequest)
    val action = gitPullRequestPushText(GitPullRequestRevision.of(event), previous, sentence = false) ?: stringRes(Res.string.git_pr_update_description)

    // The thread's PR already lists its clone URLs: name only the ones this update adds.
    val newCloneUrls =
        remember(event, pullRequest) {
            val known = pullRequest?.cloneUrls().orEmpty().toSet()
            event.cloneUrls().filter { it.isNotBlank() && it !in known }
        }

    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier,
        background = background,
        symbol = MaterialSymbols.Sync,
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        action = action,
        accountViewModel = accountViewModel,
        nav = nav,
    ) {
        newCloneUrls.forEach {
            Text(
                text = it,
                style = gitMetaStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A later patch of the series, or the first patch of a new revision, pushed onto the root patch
 * the thread is about. Tapping it opens the patch with its diff.
 */
@Composable
private fun PatchRow(
    event: GitPatchEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(event) { event.title() }
    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier.clickable { nav.nav(Route.Note(baseNote.idHex)) },
        background = background,
        symbol = MaterialSymbols.Commit,
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        action = stringRes(if (event.isRootRevision()) Res.string.git_timeline_patch_revision else Res.string.git_timeline_patch_added),
        accountViewModel = accountViewModel,
        nav = nav,
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A rename (NIP-32 `#subject` label) of the item the thread is about: "changed the title to …". */
@Composable
private fun RenameRow(
    event: LabelEvent,
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val itemNote = baseNote.replyTo?.lastOrNull()
    val item = itemNote?.event
    val announcements = observeGitRepositoryAnnouncements(item)
    val authorised = remember(event, item, announcements) { item != null && LocalCache.gitAuthority.isMemberAt(event.pubKey, item, event.createdAt) }
    val previous =
        remember(event, itemNote, announcements) {
            itemNote?.let { GitItemTitles.renamesOf(it, LocalCache.gitAuthority).firstOrNull { rename -> rename.event.id == event.id }?.from }
        }

    TimelineRowLayout(
        baseNote = baseNote,
        modifier = modifier,
        background = background,
        symbol = MaterialSymbols.Edit,
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        action = stringRes(Res.string.git_timeline_renamed),
        authorised = authorised,
        accountViewModel = accountViewModel,
        nav = nav,
    ) {
        // "~~old title~~ new title", as a forge writes a rename.
        Text(
            text =
                buildAnnotatedString {
                    if (previous != null) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = MaterialTheme.colorScheme.grayText)) { append(previous) }
                        append("  ")
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(event.subjectEdit().orEmpty()) }
                },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The revision [update] replaced, from the other updates of its PR or the PR itself. */
@Composable
fun observePreviousRevision(
    update: GitPullRequestUpdateEvent,
    pullRequest: GitPullRequestEvent?,
): GitPullRequestRevision? {
    val pullRequestNote = remember(update) { update.parentPullRequestId()?.let { LocalCache.getOrCreateNote(it) } } ?: return null
    val siblings = observeGitPullRequestUpdates(pullRequestNote)
    return remember(update, siblings, pullRequest) { GitPullRequestRevisions.previous(update, siblings, pullRequest) }
}

/**
 * "rebased and pushed new commits": what a PR update changed, compared with the [previous]
 * revision, in words rather than commit hashes, which a reader can do nothing with. [sentence]
 * picks the capitalized form a card shows on its own over the lowercase one a timeline entry puts
 * after the author's name. Null when [current] names no commit.
 */
@Composable
fun gitPullRequestPushText(
    current: GitPullRequestRevision,
    previous: GitPullRequestRevision?,
    sentence: Boolean,
): String? {
    if (current.commit == null) return null
    val rebased = previous != null && current.rebasedFrom(previous)
    return when {
        rebased -> stringRes(if (sentence) Res.string.git_pr_pushed_rebased else Res.string.git_timeline_pushed_rebased)
        else -> stringRes(if (sentence) Res.string.git_pr_pushed else Res.string.git_timeline_pushed)
    }
}

/** The width of the rail column, the width of a reply's avatar column, so the badges line up with it. */
private val RailWidth = 55.dp
private val BadgeSize = 24.dp

/**
 * The shared shape of a timeline entry, as a forge draws its history: a small badge for the kind
 * of change on a rail that runs down the thread, then one line of who did what and when ([action]
 * and [inline] flowing together), with [body] for its detail. A change that did not apply
 * ([authorised] false) is drawn muted, tagged Proposed, with the reason under it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimelineRowLayout(
    baseNote: Note,
    modifier: Modifier,
    background: Color,
    symbol: MaterialSymbol,
    container: Color,
    content: Color,
    action: String,
    accountViewModel: AccountViewModel,
    nav: INav,
    authorised: Boolean = true,
    inline: @Composable () -> Unit = {},
    body: @Composable ColumnScope.() -> Unit = {},
) {
    val railColor = MaterialTheme.colorScheme.subtleBorder
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(background)
                // The rail runs the row's full height, so consecutive entries join into one line.
                .drawBehind {
                    val x = 12.dp.toPx() + RailWidth.toPx() / 2
                    drawLine(railColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                }
                // Match NoteComposeLayout's 12dp gutters so the rail runs under the reply avatars.
                .padding(start = 12.dp, end = 12.dp),
    ) {
        Box(
            modifier = Modifier.width(RailWidth),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .size(BadgeSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .background(container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(symbol = symbol, contentDescription = null, modifier = Modifier.size(14.dp), tint = content)
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .alpha(if (authorised) 1f else 0.65f),
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                NoteAuthorPicture(baseNote = baseNote, size = 16.dp, accountViewModel = accountViewModel, nav = nav)
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    NoteUsernameDisplay(baseNote, accountViewModel = accountViewModel, nav = nav)
                }
                Text(
                    text = action,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.grayText,
                )
                inline()
                if (!authorised) TimelineLabel(stringRes(Res.string.git_proposed))
                Text(text = timeAgo(baseNote.createdAt(), prefix = "· "), style = gitMetaStyle(), maxLines = 1)
            }

            body()

            if (!authorised) {
                Text(
                    text = stringRes(Res.string.git_proposed_explanation),
                    style = gitMetaStyle(),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier =
            Modifier
                .clip(LabelShape)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, LabelShape)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}
