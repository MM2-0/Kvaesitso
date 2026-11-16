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
package com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup

import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.chats.isMinichatReply
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.relayGroupChannelLastReadRoute
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupChatContent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * True when this NIP-29 group has at least one chat message newer than the timestamp this account
 * last read it ([relayGroupChannelLastReadRoute]). Reactive: it recombines both when a fresh message
 * folds in (the channel's notes flow ticks) and when the user opens the group (which advances the
 * last-read marker). Only actual group chat content counts (see [isGroupChatContent]) so a trailing
 * reaction/deletion can't stick the dot on; unacceptable (muted/blocked) authors are ignored too.
 */
fun relayGroupChannelHasUnreadFlow(
    account: Account,
    groupId: GroupId,
): Flow<Boolean> {
    val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
    return combine(
        account.loadLastReadFlow(relayGroupChannelLastReadRoute(groupId)),
        channel.flow().notes.stateFlow,
    ) { lastRead, _ ->
        channel.hasChatNewerThan(account, lastRead)
    }
}

/**
 * True when ANY of the account's joined groups on [relay] has unread chat — the unread signal for
 * the collapsed "grouped by relay" Messages row ([RelayGroupServerRoomNote]). It follows the joined
 * list ([RelayGroupListState.liveRelayGroupList]) so a group joined/left on that relay re-subscribes
 * the fan-in, and each group contributes its own [relayGroupChannelHasUnreadFlow].
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun relayGroupServerHasUnreadFlow(
    account: Account,
    relay: NormalizedRelayUrl,
): Flow<Boolean> =
    account.relayGroupList.liveRelayGroupList
        .flatMapLatest { tags ->
            val groupIds =
                tags.mapNotNull { tag ->
                    if (RelayUrlNormalizer.normalizeOrNull(tag.relayUrl) == relay) GroupId(tag.groupId, relay) else null
                }
            if (groupIds.isEmpty()) {
                flowOf(false)
            } else {
                combine(groupIds.map { relayGroupChannelHasUnreadFlow(account, it) }) { perGroup -> perGroup.any { it } }
            }
        }.distinctUntilChanged()

/**
 * Whether [note] is one of this group's **timeline** messages — what the channel feed renders, what
 * the Messages row previews, and what the unread dot counts.
 *
 * Two exclusions, and both matter for the same reason: the row summary must not disagree with what
 * opening the channel shows.
 * - Non-content (`reaction`/deletion/label) carries the group's `h` tag too, so [isGroupChatContent]
 *   gates it out — a trailing 👍 must not become the "last message".
 * - A **minichat thread reply** lives in the thread opened from its parent, not in the timeline
 *   ([isMinichatReply], the same predicate `ChannelFeedFilter` uses). Without this the Messages row
 *   previews a reply the channel never displays, and the unread dot lights for activity that leaves
 *   the timeline unchanged — you open the group, see nothing new, and the dot clears.
 *
 * The Concord side solves this identically with `isConcordTimelineMessage`.
 */
fun isRelayGroupTimelineMessage(
    note: Note,
    account: Account,
): Boolean = note.event?.isGroupChatContent() == true && !isMinichatReply(note.event) && account.isAcceptable(note)

/**
 * The newest timeline message in this group (see [isRelayGroupTimelineMessage]), or null if none —
 * the note the Messages row shows as the group's "last message".
 */
fun RelayGroupChannel.newestTimelineNote(account: Account): Note? =
    notes
        .filter { _, note -> isRelayGroupTimelineMessage(note, account) }
        .sortedByDefaultFeedOrder()
        .firstOrNull()

/** Whether this group's message store holds any acceptable timeline message created after [sinceSecs]. */
private fun RelayGroupChannel.hasChatNewerThan(
    account: Account,
    sinceSecs: Long,
): Boolean = newMessagesSince(account, sinceSecs) > 0

/** The number of this group's timeline messages created strictly after [sinceSecs] (0 if none). */
private fun RelayGroupChannel.newMessagesSince(
    account: Account,
    sinceSecs: Long,
): Int =
    notes.count { _, note ->
        (note.createdAt() ?: 0L) > sinceSecs && isRelayGroupTimelineMessage(note, account)
    }

/**
 * The count of chat messages in [groupId] newer than the timestamp this account last read it — the
 * number the channel-row unread badge shows. Reactive: it recombines both when a fresh message folds
 * in (the channel's notes flow ticks) and when the user opens the group (which advances the last-read
 * marker), so opening a channel clears its badge. Mirrors [relayGroupChannelHasUnreadFlow].
 */
fun relayGroupChannelUnreadCountFlow(
    account: Account,
    groupId: GroupId,
): Flow<Int> {
    val channel = LocalCache.getOrCreateRelayGroupChannel(groupId)
    return combine(
        account.loadLastReadFlow(relayGroupChannelLastReadRoute(groupId)),
        channel.flow().notes.stateFlow,
    ) { lastRead, _ ->
        channel.newMessagesSince(account, lastRead)
    }
}
