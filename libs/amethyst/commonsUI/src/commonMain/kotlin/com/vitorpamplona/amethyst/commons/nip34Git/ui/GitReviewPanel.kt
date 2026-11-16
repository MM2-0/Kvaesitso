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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteReplies
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.git_merge_cancel
import com.vitorpamplona.amethyst.commons.resources.git_review_action
import com.vitorpamplona.amethyst.commons.resources.git_review_dialog_title
import com.vitorpamplona.amethyst.commons.resources.git_review_hint
import com.vitorpamplona.amethyst.commons.resources.git_review_submit
import com.vitorpamplona.amethyst.commons.resources.git_review_summary
import com.vitorpamplona.amethyst.commons.resources.git_review_verdict_ack
import com.vitorpamplona.amethyst.commons.resources.git_review_verdict_changes
import com.vitorpamplona.amethyst.commons.resources.git_review_verdict_concept_ack
import com.vitorpamplona.amethyst.commons.resources.git_review_verdict_concept_nack
import com.vitorpamplona.amethyst.commons.resources.git_review_verdict_nack
import com.vitorpamplona.amethyst.commons.ui.note.StatusKind
import com.vitorpamplona.amethyst.commons.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip34Git.review.GitReviewEvent
import com.vitorpamplona.quartz.nip34Git.review.gitCodeLocation

@Composable
fun verdictLabel(verdict: GitReviewEvent.Verdict): String =
    when (verdict) {
        GitReviewEvent.Verdict.ACK -> stringRes(Res.string.git_review_verdict_ack)
        GitReviewEvent.Verdict.NACK -> stringRes(Res.string.git_review_verdict_nack)
        GitReviewEvent.Verdict.CONCEPT_ACK -> stringRes(Res.string.git_review_verdict_concept_ack)
        GitReviewEvent.Verdict.CONCEPT_NACK -> stringRes(Res.string.git_review_verdict_concept_nack)
        GitReviewEvent.Verdict.CHANGES_REQUIRED -> stringRes(Res.string.git_review_verdict_changes)
    }

@Composable
fun verdictColor(verdict: GitReviewEvent.Verdict): Color =
    when (verdict) {
        GitReviewEvent.Verdict.ACK, GitReviewEvent.Verdict.CONCEPT_ACK -> gitStateColor(StatusKind.OPEN)
        GitReviewEvent.Verdict.NACK, GitReviewEvent.Verdict.CONCEPT_NACK -> gitStateColor(StatusKind.CLOSED)
        // A call to act, not a refusal: the amber the palette uses for what waits on someone.
        GitReviewEvent.Verdict.CHANGES_REQUIRED -> gitPalette().pending
    }

/**
 * The reviews of [item] (a pull request or patch): each reviewer's latest verdict, newest first,
 * and a Review button that records the user's own, gathering the inline comments they left.
 */
@Composable
fun GitReviewPanel(
    item: Note,
    accountViewModel: AccountViewModel,
) {
    val replies by observeNoteReplies(item)
    val reviews =
        remember(replies, item) {
            item.replies.filter { (it.event as? GitReviewEvent)?.let { review -> review.reviewedEventId() == item.idHex && review.verdict() != null } == true }
        }
    // A review taken back on another device is a deletion naming the review, not the item.
    WatchGitDeletions(reviews)
    val latest =
        remember(reviews) {
            reviews
                .mapNotNull { it.event as? GitReviewEvent }
                .groupBy { it.pubKey }
                .mapNotNull { (_, reviews) -> reviews.maxWithOrNull(compareBy<GitReviewEvent> { it.createdAt }.thenByDescending { it.id }) }
                .sortedByDescending { it.createdAt }
        }
    var open by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (latest.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                latest.forEach { review ->
                    val verdict = review.verdict() ?: return@forEach
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val user = remember(review.pubKey) { LocalCache.getOrCreateUser(review.pubKey) }
                        UsernameDisplay(user, fontWeight = FontWeight.Medium, accountViewModel = accountViewModel)
                        Text(verdictLabel(verdict), style = MaterialTheme.typography.labelMedium, color = verdictColor(verdict))
                    }
                }
            }
        }
        if (accountViewModel.account.isWriteable()) {
            OutlinedButton(onClick = { open = true }) { Text(stringRes(Res.string.git_review_action)) }
        }
    }

    if (open) {
        ReviewDialog(
            onDismiss = { open = false },
            onSubmit = { verdict, summary ->
                open = false
                accountViewModel.launchSigner { submitReview(item, verdict, summary, accountViewModel) }
            },
        )
    }
}

@Composable
private fun ReviewDialog(
    onDismiss: () -> Unit,
    onSubmit: (GitReviewEvent.Verdict, String) -> Unit,
) {
    var verdict by rememberSaveable { mutableStateOf(GitReviewEvent.Verdict.ACK) }
    var summary by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(Res.string.git_review_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GitReviewEvent.Verdict.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = verdict == option, onClick = { verdict = option }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = verdict == option, onClick = { verdict = option })
                        Text(verdictLabel(option))
                    }
                }
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringRes(Res.string.git_review_summary)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2,
                )
                Text(stringRes(Res.string.git_review_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.grayText)
            }
        },
        confirmButton = { Button(onClick = { onSubmit(verdict, summary.trim()) }) { Text(stringRes(Res.string.git_review_submit)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringRes(Res.string.git_merge_cancel)) } },
    )
}

/** Publishes the verdict, quoting the reviewer's own inline comments on [item] (`q`), as gitworkshop's spec asks. */
private suspend fun submitReview(
    item: Note,
    verdict: GitReviewEvent.Verdict,
    summary: String,
    accountViewModel: AccountViewModel,
) {
    val reviewed = item.toEventHint<Event>() ?: return
    val me = accountViewModel.account.signer.pubKey
    // Only the inline comments (those with a code location), not plain replies or resolutions.
    val mine = item.replies.filter { (it.event as? CommentEvent)?.let { c -> c.pubKey == me && c.gitCodeLocation() != null } == true }.mapNotNull { it.toEventHint<Event>() }
    accountViewModel.account.signAndComputeBroadcast(GitReviewEvent.build(verdict, summary, reviewed, mine))
}
