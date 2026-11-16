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
package com.vitorpamplona.amethyst.commons.chats.publicChannels.concord

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Channel
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.concordChannelLastReadRoute
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordChannelId
import com.vitorpamplona.quartz.concord.cord03Channels.ConcordTimerNoticeEvent
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * A reactive flow of how many messages in [communityId]/[channelKey] are newer than the
 * timestamp this account last read that channel. It combines the persisted last-read marker
 * ([concordChannelLastReadRoute]) with the channel's own notes flow, so the count updates both
 * when a fresh message folds in and when the user opens the channel (which advances last-read).
 *
 * Uses `getOrCreate` (not `getIfExists`): a channel folded on the Control Plane may have no
 * message-buffer note yet, and the flow must still exist so the badge appears the moment its
 * first message lands. Counting straight off [ConcordChannel.notes] ignores the synthetic
 * placeholder note (it is never added to that cache) and any note with no timestamp.
 */
fun concordChannelUnreadCountFlow(
    account: Account,
    communityId: String,
    channelKey: String,
): Flow<Int> {
    val channel = LocalCache.getOrCreateConcordChannel(ConcordChannelId(communityId, channelKey))
    // The notes flow drives recomputation (each new message re-emits its channel state); the count
    // itself reads the channel's own note store, so it survives the base-typed ChannelState.channel.
    return combine(
        account.loadLastReadFlow(concordChannelLastReadRoute(communityId, channelKey)),
        channel.flow().notes.stateFlow,
    ) { lastRead, _ ->
        channel.newMessagesSince(account, lastRead)
    }
}

/**
 * True when ANY channel in Concord [communityId] has unread messages — the unread signal for the
 * collapsed "grouped by community" Messages row ([ConcordServerRoomNote]). It follows the community's
 * live session state so a channel added/removed by a Control-Plane fold re-subscribes the fan-in, and
 * each channel contributes its own [concordChannelUnreadCountFlow]. Emits false for a community with
 * no session or no channels yet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun concordCommunityHasUnreadFlow(
    account: Account,
    communityId: String,
): Flow<Boolean> =
    // Re-resolve the session on every revision tick rather than capturing it once. A Refounding
    // rebuilds a still-joined community's session in place (same id, new object) and a fold changes
    // the channel set — both bump `revision`; capturing the session once would leave the fan-in
    // pointed at a dead session so the dot freezes. This mirrors how ConcordServerRoomCompose already
    // re-reads the row's name/icon off `revision`.
    account.concordSessions.revision
        .flatMapLatest {
            val channelKeys =
                account.concordSessions
                    .sessionFor(communityId)
                    ?.state
                    ?.value
                    ?.channels
                    ?.keys
                    ?.toList()
                    .orEmpty()
            if (channelKeys.isEmpty()) {
                flowOf(false)
            } else {
                combine(channelKeys.map { concordChannelUnreadCountFlow(account, communityId, it) }) { counts -> counts.any { it > 0 } }
            }
        }.distinctUntilChanged()

/**
 * True for a note the Concord channel *timeline* actually renders — the same predicate as
 * [com.vitorpamplona.amethyst.commons.chats.publicChannels.dal.ChannelFeedFilter]'s
 * `isTimelineMessage`: a loaded, acceptable message that is **not** a kind-1111 [CommentEvent].
 *
 * A [CommentEvent] is a *minichat thread reply* that lives inside its parent's thread, not on the
 * flat timeline, so it never composes on the channel screen and never advances the last-read
 * marker. Every list-row surface that summarizes a channel — the unread badge
 * ([newMessagesSince]), the last-message preview + timestamp ([newestTimelineNote]), and the
 * Messages hub row — reuses this so none of them can disagree with the open channel's feed:
 * a trailing comment can't stick the badge at a count the user can never clear, nor show up as a
 * "last message" that isn't in the timeline. Unacceptable (muted/blocked) authors are hidden for
 * the same reason — and so are expired disappearing messages (CORD-08 §3).
 *
 * A CORD-08 timer notice renders in the feed as a system line but is not a *message*: it neither
 * counts as unread nor stands in as the channel's last message (its content is empty).
 */
fun isConcordTimelineMessage(
    note: Note,
    account: Account,
): Boolean = note.event.let { it != null && it !is CommentEvent && it !is ConcordTimerNoticeEvent } && account.isAcceptable(note)

/**
 * The newest timeline message in this channel (see [isConcordTimelineMessage]), or null if none —
 * the note the list/hub rows show as the channel's "last message". Unlike [ConcordChannel.lastNote]
 * (the raw newest note of any kind), this skips thread replies and hidden authors so the preview
 * matches what the channel feed renders and the unread badge counts.
 */
fun ConcordChannel.newestTimelineNote(account: Account): Note? =
    notes
        .filter { _, note -> isConcordTimelineMessage(note, account) }
        .minWithOrNull(Channel.DefaultFeedOrder)

/** The number of this channel's timeline messages created strictly after [sinceSecs] (0 if none). */
private fun ConcordChannel.newMessagesSince(
    account: Account,
    sinceSecs: Long,
): Int =
    notes.count { _, note ->
        (note.createdAt() ?: 0L) > sinceSecs && isConcordTimelineMessage(note, account)
    }
