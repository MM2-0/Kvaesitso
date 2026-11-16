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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.commons.chats.ui.ChatSystemMessage
import com.vitorpamplona.amethyst.commons.model.EmptyTagList
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.buzz_diff_truncated
import com.vitorpamplona.amethyst.commons.resources.buzz_forum_downvoted
import com.vitorpamplona.amethyst.commons.resources.buzz_forum_upvoted
import com.vitorpamplona.amethyst.commons.resources.buzz_huddle_ended
import com.vitorpamplona.amethyst.commons.resources.buzz_huddle_joined
import com.vitorpamplona.amethyst.commons.resources.buzz_huddle_left
import com.vitorpamplona.amethyst.commons.resources.buzz_huddle_started
import com.vitorpamplona.amethyst.commons.resources.buzz_job_accepted
import com.vitorpamplona.amethyst.commons.resources.buzz_job_cancelled
import com.vitorpamplona.amethyst.commons.resources.buzz_job_error
import com.vitorpamplona.amethyst.commons.resources.buzz_job_progress
import com.vitorpamplona.amethyst.commons.resources.buzz_job_requested
import com.vitorpamplona.amethyst.commons.resources.buzz_job_result
import com.vitorpamplona.amethyst.commons.resources.buzz_message_edited
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.buzzSystemMessageText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.observeUserNameByHex
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.buzz.forum.ForumVoteEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleEndedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantJoinedEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleParticipantLeftEvent
import com.vitorpamplona.quartz.buzz.huddles.HuddleStartedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobAcceptedEvent
import com.vitorpamplona.quartz.buzz.jobs.JobCancelEvent
import com.vitorpamplona.quartz.buzz.jobs.JobErrorEvent
import com.vitorpamplona.quartz.buzz.jobs.JobProgressEvent
import com.vitorpamplona.quartz.buzz.jobs.JobRequestEvent
import com.vitorpamplona.quartz.buzz.jobs.JobResultEvent
import com.vitorpamplona.quartz.buzz.stream.BuzzEditTagOverlay
import com.vitorpamplona.quartz.buzz.stream.StreamMessageDiffEvent
import com.vitorpamplona.quartz.buzz.stream.StreamMessageV2Event
import com.vitorpamplona.quartz.buzz.stream.SystemMessageEvent
import com.vitorpamplona.quartz.buzz.workspace.isBuzzChatTimelineContent
import com.vitorpamplona.quartz.nip01Core.core.Event

/**
 * A Buzz stream message whose content has been superseded by a kind-40003 edit:
 * renders the NEWEST edit's content (never the stale original) plus an "(edited)"
 * marker, mirroring Buzz's own last-write-wins presentation.
 */
@Composable
fun RenderBuzzEditedNote(
    note: Note,
    editNote: Note,
    canPreview: Boolean,
    innerQuote: Boolean,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // The edit note may still be loading; fall back to the original rendering rather
    // than committing to an edited branch that would show a blank row.
    val content = editNote.event?.content
    if (content == null) {
        RenderRegularTextNote(note, canPreview, innerQuote, bgColor, accountViewModel, nav)
        return
    }
    // Render with the tags the edit leaves the message with (Buzz's applyEditTagOverlay): the edit's
    // attachments and custom emoji, the original's channel/thread tags.
    val tags =
        remember(note.event, editNote.event) {
            note.event
                ?.tags
                ?.let { BuzzEditTagOverlay.apply(it, editNote.event?.tags) }
                ?.toImmutableListOfLists() ?: EmptyTagList
        }

    Column {
        TranslatableRichTextViewer(
            content = content,
            canPreview = canPreview,
            quotesLeft = if (innerQuote) 0 else 1,
            modifier = Modifier,
            tags = tags,
            backgroundColor = bgColor,
            id = note.idHex,
            callbackUri = note.toNostrUri(),
            authorPubKey = note.author?.pubkeyHex,
            accountViewModel = accountViewModel,
            nav = nav,
        )
        Text(
            text = stringRes(Res.string.buzz_message_edited),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

/**
 * True for the Buzz agent-job and huddle lifecycle kinds that [RenderBuzzActivityRow]
 * narrates as a centered system line rather than a chat bubble. Huddle events in
 * particular MUST be caught here — their `content` is JSON, so rendering them as a plain
 * chat message would show raw `{"ephemeral_channel_id":…}`.
 */
fun isBuzzActivityRow(event: Event?): Boolean =
    event is JobRequestEvent ||
        event is JobAcceptedEvent ||
        event is JobProgressEvent ||
        event is JobResultEvent ||
        event is JobCancelEvent ||
        event is JobErrorEvent ||
        event is HuddleStartedEvent ||
        event is HuddleParticipantJoinedEvent ||
        event is HuddleParticipantLeftEvent ||
        event is HuddleEndedEvent

/**
 * Narrates a Buzz agent-job or huddle lifecycle event as a centered system line. The
 * label is derived from the kind; job progress/result/error also append a short content
 * snippet (the human-readable status/result/error the agent wrote).
 */
@Composable
fun RenderBuzzActivityRow(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val event = note.event ?: return
    val text = buzzActivityLabel(event, accountViewModel) ?: remember(event) { event.content.take(120) }
    ChatSystemMessage(text = text)
}

/**
 * The centered-system-line label for a Buzz agent-job or huddle lifecycle event, or null if
 * [event] isn't one. Shared by the Messages-list preview and the in-chat activity row so the
 * two read the same.
 *
 * Huddle joins/leaves name the participant from the event's `p` tag (falling back to the
 * signer), and job lines name the signer — the agent that accepted, the human that requested —
 * because "someone joined the huddle" in a busy channel says nothing about who to talk to.
 * Job progress/result/error also append a short snippet of what the agent wrote.
 */
@Composable
fun buzzActivityLabel(
    event: Event,
    accountViewModel: AccountViewModel,
): String? {
    val signer = observeUserNameByHex(event.pubKey, accountViewModel)
    val participant =
        observeUserNameByHex(
            remember(event) {
                when (event) {
                    is HuddleParticipantJoinedEvent -> event.participant() ?: event.pubKey
                    is HuddleParticipantLeftEvent -> event.participant() ?: event.pubKey
                    else -> null
                }
            },
            accountViewModel,
        )

    return when (event) {
        is JobRequestEvent -> stringRes(Res.string.buzz_job_requested, signer) + remember(event) { event.request().snippet() }
        is JobAcceptedEvent -> stringRes(Res.string.buzz_job_accepted, signer)
        is JobProgressEvent ->
            stringRes(Res.string.buzz_job_progress) +
                remember(event) { (event.status()?.let { ": $it" } ?: "") + event.content.snippet() }
        is JobResultEvent -> stringRes(Res.string.buzz_job_result) + remember(event) { event.result().snippet() }
        is JobCancelEvent -> stringRes(Res.string.buzz_job_cancelled)
        is JobErrorEvent -> stringRes(Res.string.buzz_job_error) + remember(event) { event.error().snippet() }
        is HuddleStartedEvent -> stringRes(Res.string.buzz_huddle_started, signer)
        is HuddleParticipantJoinedEvent -> stringRes(Res.string.buzz_huddle_joined, participant)
        is HuddleParticipantLeftEvent -> stringRes(Res.string.buzz_huddle_left, participant)
        is HuddleEndedEvent -> stringRes(Res.string.buzz_huddle_ended)
        else -> null
    }
}

/**
 * A human-readable one-line preview of a Buzz chat-timeline event for the Messages list, or null
 * for a plain [StreamMessageV2Event] (whose `content` IS the text — the caller shows that with the
 * usual "author: message" framing). Every other Buzz timeline kind carries JSON/diff in `content`,
 * so this returns the same summary the in-chat system/activity/diff row shows instead of dumping
 * raw payload into the preview. Keyed off the exact set in [isBuzzChatTimelineContent].
 */
@Composable
fun buzzTimelinePreviewSummary(
    event: Event,
    accountViewModel: AccountViewModel,
): String? =
    when (event) {
        is StreamMessageV2Event -> null
        is SystemMessageEvent -> buzzSystemMessageText(event, accountViewModel)
        is StreamMessageDiffEvent -> remember(event) { event.diffMeta()?.filePath?.let { "📄 $it" } ?: "📄 diff" }
        else -> buzzActivityLabel(event, accountViewModel)
    }

/** A short one-line snippet of free-text content appended after a label, or "" if blank. */
private fun String.snippet(): String {
    val oneLine = trim().replace('\n', ' ')
    if (oneLine.isEmpty()) return ""
    return ": " + if (oneLine.length > 80) oneLine.take(80) + "…" else oneLine
}

/**
 * A Buzz kind-40008 stream diff: a code/text diff pushed into the channel. Renders the
 * repo/commit/file header from the `DiffMeta` tags plus the diff body in a monospace,
 * horizontally-scrollable block, rather than the plain-text fallback (which mangles the
 * `+`/`-` gutters into wrapped prose).
 */
@Composable
fun RenderBuzzDiff(note: Note) {
    val event = note.event as? StreamMessageDiffEvent ?: return
    val meta = remember(event) { event.diffMeta() }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            val header =
                remember(meta) {
                    meta?.let {
                        buildString {
                            it.filePath?.let(::append)
                            it.language?.let { lang -> append(if (isEmpty()) lang else "  ·  $lang") }
                            it.prNumber?.let { pr -> append("  ·  PR #$pr") }
                            it.commitSha
                                .takeIf { sha -> sha.isNotBlank() }
                                ?.let { sha -> append("  ·  ${sha.take(8)}") }
                        }.ifBlank { null }
                    }
                }
            if (header != null) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = event.content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            )
            if (meta?.truncated == true) {
                Text(
                    text = stringRes(Res.string.buzz_diff_truncated),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

/** A Buzz kind-45002 forum vote: a lightweight up/down signal, shown as a system line. */
@Composable
fun RenderBuzzForumVote(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val event = note.event as? ForumVoteEvent ?: return
    // Content is the vote token ("+"/"-" or similar); show a compact glyph line.
    val isDownVote = remember(event) { event.content.trim().startsWith("-") }
    val voter = observeUserNameByHex(event.pubKey, accountViewModel)

    ChatSystemMessage(
        text =
            if (isDownVote) {
                stringRes(Res.string.buzz_forum_downvoted, voter)
            } else {
                stringRes(Res.string.buzz_forum_upvoted, voter)
            },
    )
}
