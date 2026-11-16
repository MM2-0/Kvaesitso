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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.buzz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.newestTimelineNote
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.relayGroupChannelUnreadCountFlow
import com.vitorpamplona.amethyst.commons.concord.ui.ConcordUnreadBadge
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.relayClient.reqCommand.channel.observeChannel
import com.vitorpamplona.amethyst.commons.relayClient.user.observeUserName
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.relay_group_member_count
import com.vitorpamplona.amethyst.commons.resources.relay_group_no_messages_yet
import com.vitorpamplona.amethyst.commons.ui.note.timeAgo
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.observeChatPreviewText
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.feed.types.buzzTimelinePreviewSummary
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.chats.publicChannels.relayGroup.datasource.RelayGroupCardWarmupSubscription
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/** A first screen's worth of recent messages to prefetch per visible card, so previews fill in. */
private const val CARD_WARMUP_LIMIT = 10

/**
 * One channel row in a Buzz workspace's community view: a channel the user is a member of (via
 * kind-44100), rendered like the Concord server view — a colored monogram, the channel name with the
 * last message's relative time, and below it a preview of the last message (author + snippet, or the
 * Buzz activity summary for system/diff/job rows) with an unread-count badge.
 * Tapping the card opens the channel ([onOpen]); the row itself is a clean tap-to-open target — its
 * per-channel actions (Pin/Unpin, Add/Remove-from-Messages) live in the opened channel's/forum's
 * top-bar overflow, not on the row. A pinned channel still shows a pin marker here ([isStarred]).
 *
 * Reused by the relay group-list screen where Buzz membership discovery is folded in.
 *
 * [showActivityPreview] gates the chat-activity machinery — the recent-message warmup, the
 * last-message preview and the unread badge. Enable it for **chat** channels (whose content lives in
 * [RelayGroupChannel.notes]); leave it off for **forum** channels, whose posts are threads (a
 * separate store), so the row doesn't open a kind-9 chat subscription that would return nothing and
 * drives a member-count summary instead.
 */
@Composable
fun BuzzImportRow(
    groupId: GroupId,
    accountViewModel: AccountViewModel,
    onOpen: (() -> Unit)? = null,
    isStarred: Boolean = false,
    showActivityPreview: Boolean = true,
) {
    val account = accountViewModel.account
    val baseChannel = remember(groupId) { LocalCache.getOrCreateRelayGroupChannel(groupId) }

    // Warm a first screen's worth of recent messages while this card is visible (content only — the
    // directory subscription already streams metadata), so the preview + facepile fill in ahead of a
    // tap instead of staying blank until the channel is opened. Bounded to visible rows by the
    // LazyColumn and released as they scroll off. Skipped for forum channels (no chat to warm).
    if (showActivityPreview) {
        RelayGroupCardWarmupSubscription(
            baseChannel,
            accountViewModel.dataSources().relayGroupCardWarmup,
            accountViewModel,
            contentOnly = true,
            contentLimit = CARD_WARMUP_LIMIT,
        )
    }

    val channelState by observeChannel(baseChannel, accountViewModel)
    val channel = channelState?.channel as? RelayGroupChannel ?: baseChannel

    val name = channel.toBestDisplayName()
    val memberCount = channel.memberCount()
    val isPrivate = channel.isPrivate()

    // The channel's own notes flow drives the preview so it updates the moment a message folds in,
    // independent of the metadata-scoped [observeChannel] above. Only collected for chat channels; a
    // forum row shows a member-count summary with no unread.
    val lastNote: Note?
    val unread: Int
    if (showActivityPreview) {
        val notesState by channel
            .flow()
            .notes.stateFlow
            .collectAsStateWithLifecycle()
        lastNote = remember(notesState) { channel.newestTimelineNote(account) }
        unread =
            remember(groupId) { relayGroupChannelUnreadCountFlow(account, groupId) }
                .collectAsStateWithLifecycle(0)
                .value
    } else {
        lastNote = null
        unread = 0
    }
    val hasUnread = unread > 0

    val content =
        @Composable {
            BuzzImportRowContent(
                name = name,
                seed = groupId.id,
                isPrivate = isPrivate,
                memberCount = memberCount,
                lastNote = lastNote,
                unread = unread,
                hasUnread = hasUnread,
                isStarred = isStarred,
                accountViewModel = accountViewModel,
            )
        }
    // A plain row on the screen background, not a filled Card. Each row used to be its own Card, and
    // because they stack with no gaps their container colour merged into one grey slab behind the
    // whole Channels section — reading as a box around the channels that the Direct Messages rows
    // right below (plain rows) didn't have. Matches [BuzzDmInlineRow] and the Concord server list.
    if (onOpen != null) {
        Box(Modifier.fillMaxWidth().clickable(onClick = onOpen)) { content() }
    } else {
        Box(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun BuzzImportRowContent(
    name: String,
    seed: String,
    isPrivate: Boolean,
    memberCount: Int,
    lastNote: Note?,
    unread: Int,
    hasUnread: Boolean,
    isStarred: Boolean,
    accountViewModel: AccountViewModel,
) {
    Row(
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BuzzImportAvatar(name = name, seed = seed)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Line 1: a lock (private), the channel name, a pin marker (starred), and the last-message
            // time pushed to the right.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isPrivate) {
                    Icon(
                        symbol = MaterialSymbols.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isStarred) {
                    Icon(
                        symbol = MaterialSymbols.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                }
                lastNote?.createdAt()?.let { ts ->
                    Text(
                        timeAgo(ts, prefix = ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            // Line 2: the last-message preview, then the unread-message badge.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    BuzzChannelPreviewLine(lastNote, memberCount, accountViewModel)
                }
                ConcordUnreadBadge(unread)
            }
        }
    }
}

/**
 * The line under a channel name: the last message's author + a snippet ("author: hello"), the Buzz
 * activity summary for a system/diff/job row, or — before anything has folded in — the member count
 * (or a muted "No messages yet"). Author names resolve reactively (hex → profile name).
 */
@Composable
private fun BuzzChannelPreviewLine(
    lastNote: Note?,
    memberCount: Int,
    accountViewModel: AccountViewModel,
) {
    val event = lastNote?.event
    val author = lastNote?.author
    val preview: String =
        if (event == null) {
            if (memberCount > 0) {
                pluralStringRes(Res.plurals.relay_group_member_count, memberCount, memberCount)
            } else {
                stringRes(Res.string.relay_group_no_messages_yet)
            }
        } else {
            // A Buzz timeline row (system line, huddle/job activity, diff) carries JSON/diff in its
            // content, so show its human-readable summary — the same text the in-chat row renders —
            // rather than "author: {json}". A plain chat message falls through to "author: message".
            val summary = buzzTimelinePreviewSummary(event, accountViewModel)
            when {
                summary != null -> summary
                author != null -> {
                    val authorName by observeUserName(author, accountViewModel)
                    val body = observeChatPreviewText(lastNote, 80)
                    if (body.isBlank()) authorName else "$authorName: $body"
                }
                else -> observeChatPreviewText(lastNote, 80)
            }
        }
    Text(
        preview,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A round monogram whose color is derived deterministically from the channel id. */
@Composable
private fun BuzzImportAvatar(
    name: String,
    seed: String,
) {
    val hue = remember(seed) { (seed.hashCode().toLong() and 0xFFFFFF).toFloat() % 360f }
    val color = remember(hue) { Color.hsl(hue, 0.55f, 0.5f) }
    val initial =
        remember(name) {
            name
                .trim()
                .firstOrNull()
                ?.uppercaseChar()
                ?.toString() ?: "#"
        }
    Box(
        modifier = Modifier.size(44.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = initial, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }
}
