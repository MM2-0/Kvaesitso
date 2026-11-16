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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReplies
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_review_resolve
import com.vitorpamplona.amethyst.commons.resources.git_review_resolved
import com.vitorpamplona.amethyst.commons.resources.git_review_unresolve
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.NoteCompose
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffFile
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffLine
import com.vitorpamplona.quartz.nip34Git.patch.GitDiffLineType
import com.vitorpamplona.quartz.nip34Git.review.gitCodeLocation
import com.vitorpamplona.quartz.nip34Git.review.isGitThreadResolution
import com.vitorpamplona.quartz.nip34Git.review.resolvesThread
import com.vitorpamplona.quartz.utils.TimeUtils

/** Where an inline comment sits in a diff: the file, the line it ends on, and which side. */
@Immutable
data class GitDiffLineKey(
    val path: String,
    val line: Int,
    val removedSide: Boolean,
)

/** The line of a diff a comment on [line] of [file] anchors to: old numbers for removed lines, new ones otherwise. */
fun GitDiffLine.keyIn(file: GitDiffFile): GitDiffLineKey? =
    when (type) {
        GitDiffLineType.DELETE -> oldNumber?.let { GitDiffLineKey(file.displayPath, it, true) }
        else -> newNumber?.let { GitDiffLineKey(file.displayPath, it, false) }
    }

/** What a diff needs to be reviewed: tapping a line comments on it, and comments show under their lines. */
class GitReviewHooks(
    val onLineClick: ((GitDiffFile, GitDiffLine) -> Unit)?,
    val lineAnnotations: @Composable (GitDiffFile, GitDiffLine) -> Unit,
    /** Whether [lineAnnotations] draws anything under that line. */
    val hasAnnotations: (GitDiffFile, GitDiffLine) -> Boolean = { _, _ -> false },
)

/**
 * Inline review for [item]'s diff (a pull request or patch) read at [commit]: a tap on a line opens
 * the comment composer on it, and the comments already there show below the line they end on, as
 * gitworkshop places them (by file and line, whatever commit they were written at). Whoever can
 * judge the item (its author, a maintainer) can resolve a thread, or reopen one they resolved.
 */
@Composable
fun rememberGitReviewHooks(
    item: Note,
    commit: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
): GitReviewHooks {
    val replies by observeNoteReplies(item)
    val byLine =
        remember(replies, item) {
            item.replies
                .mapNotNull { note ->
                    val location = (note.event as? CommentEvent)?.gitCodeLocation() ?: return@mapNotNull null
                    GitDiffLineKey(location.path, location.endLine ?: location.startLine ?: 0, location.removedSide) to note
                }.groupBy({ it.first }, { it.second })
        }
    val canComment = accountViewModel.account.isWriteable()
    // One instance per change, so the diff's lines don't recompose for a new lambda each time.
    return remember(item, commit, byLine, canComment, nav) {
        GitReviewHooks(
            onLineClick =
                if (!canComment) {
                    null
                } else {
                    { file, line ->
                        line.keyIn(file)?.let { key ->
                            nav.nav(
                                Route.GenericCommentPost(
                                    replyTo = item.idHex,
                                    reviewPath = key.path,
                                    reviewCommit = commit,
                                    reviewLine = key.line,
                                    reviewRemovedSide = key.removedSide,
                                ),
                            )
                        }
                    }
                },
            lineAnnotations = { file, line ->
                val comments = line.keyIn(file)?.let { byLine[it] }
                if (!comments.isNullOrEmpty()) InlineThreads(item, comments, accountViewModel, nav)
            },
            hasAnnotations = { file, line -> line.keyIn(file)?.let { byLine.containsKey(it) } == true },
        )
    }
}

@Composable
private fun InlineThreads(
    item: Note,
    comments: List<Note>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)) {
        comments.sortedBy { it.createdAt() ?: 0 }.forEach { comment ->
            InlineThread(item, comment, accountViewModel, nav)
        }
    }
}

/** One inline comment, with its resolved state and the action that changes it. */
@Composable
private fun InlineThread(
    item: Note,
    comment: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val replies by observeNoteReplies(comment)
    val itemEvent = item.event ?: return
    val me = accountViewModel.account.signer.pubKey
    // Who may judge is read from the repository's announcements, which can arrive after this does.
    val announcements = observeGitRepositoryAnnouncements(itemEvent)
    // A thread is resolved while a resolution by someone who may resolve stands: the item's author
    // or a maintainer, the same rule that offers the button (gitworkshop's).
    val resolutions =
        remember(replies, comment, announcements) {
            comment.replies.filter { reply ->
                val event = reply.event as? CommentEvent ?: return@filter false
                event.isGitThreadResolution() && LocalCache.gitAuthority.isMaintainerAt(event.pubKey, itemEvent, event.createdAt)
            }
        }
    val canJudge = remember(itemEvent, me, announcements) { accountViewModel.account.isWriteable() && LocalCache.gitAuthority.isMaintainerAt(me, itemEvent, TimeUtils.now()) }
    // The conversation under the comment: its replies, without the resolution markers.
    val answers =
        remember(replies, comment) {
            comment.replies
                .filter { (it.event as? CommentEvent)?.isGitThreadResolution() != true }
                .sortedBy { it.createdAt() ?: 0 }
        }
    val mine = resolutions.filter { it.author?.pubkeyHex == me }
    // Reopening on another device deletes a resolution: watch each, or it stays resolved here.
    WatchGitDeletions(resolutions)

    NoteCompose(
        baseNote = comment,
        isQuotedNote = true,
        makeItShort = true,
        quotesLeft = 0,
        accountViewModel = accountViewModel,
        nav = nav,
    )
    answers.forEach { answer ->
        key(answer.idHex) {
            Box(Modifier.padding(start = 16.dp, top = 4.dp)) {
                NoteCompose(
                    baseNote = answer,
                    isQuotedNote = true,
                    makeItShort = true,
                    quotesLeft = 0,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (resolutions.isNotEmpty()) {
            Text(stringRes(Res.string.git_review_resolved), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.grayText)
        }
        if (canJudge && resolutions.isEmpty()) {
            TextButton(onClick = { accountViewModel.launchSigner { resolve(item, comment, accountViewModel) } }) {
                Text(stringRes(Res.string.git_review_resolve), style = MaterialTheme.typography.labelMedium)
            }
        }
        // Reopening is deleting one's own resolution: there is no "unresolved" event.
        if (mine.isNotEmpty()) {
            TextButton(onClick = { accountViewModel.launchSigner { accountViewModel.account.delete(mine) } }) {
                Text(stringRes(Res.string.git_review_unresolve), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** gitworkshop's resolution: a reply to the thread's first comment, rooted at the item, labelled `resolved`. */
private suspend fun resolve(
    item: Note,
    comment: Note,
    accountViewModel: AccountViewModel,
) {
    val root = item.toEventHint<Event>() ?: return
    val parent = comment.toEventHint<Event>() ?: return
    val template = CommentEvent.replyBuilder("marked as resolved", root = root, replyingTo = parent) { resolvesThread() }
    accountViewModel.account.signAndComputeBroadcast(template)
}
