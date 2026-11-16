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
package com.vitorpamplona.amethyst.commons.chats.rooms

import com.vitorpamplona.amethyst.commons.chats.publicChannels.concord.concordCommunityHasUnreadFlow
import com.vitorpamplona.amethyst.commons.chats.publicChannels.relayGroup.relayGroupServerHasUnreadFlow
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.chats.ConcordServerRoomNote
import com.vitorpamplona.amethyst.commons.model.chats.RelayGroupServerRoomNote
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.concordChannelLastReadRoute
import com.vitorpamplona.amethyst.commons.model.geohashChat.GeohashChatChannel
import com.vitorpamplona.amethyst.commons.model.marmotGroupLastReadRoute
import com.vitorpamplona.amethyst.commons.model.marmotGroups.MarmotGroupChatroom
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.publicChatChannelIdOf
import com.vitorpamplona.amethyst.commons.model.relayGroupChannelLastReadRoute
import com.vitorpamplona.amethyst.commons.model.unreadPrivateChatRoute
import com.vitorpamplona.quartz.experimental.bitchat.geohash.GeohashChatEvent
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether one Messages row is showing a blue dot — the SAME question each row composable answers for
 * itself in [ChatroomHeaderCompose], hoisted so the bottom-bar badge can ask it about every row.
 *
 * The badge used to run on `unreadPrivateChatRoute` alone, which opens with
 * `if (newestMessage !is ChatroomKeyable) return null`. Only NIP-17/NIP-04 DM events implement that
 * interface, so eight of the nine row types — public chats, ephemeral rooms, geohash cells, Marmot
 * groups, NIP-29/Buzz channels and Concord channels, plus both collapsed "grouped" rows — were
 * silently skipped: their row could show a dot while the envelope stayed clean.
 *
 * Returns null when the row cannot be unread at all (no event, my own newest message in a DM, everyone
 * hidden), so callers can skip it rather than subscribe to a flow that is always false. A muted public
 * chat is deliberately NOT one of these cases: mute is a runtime-toggleable setting, not a structural
 * fact about the row, so it is folded into the emitted `Flow<Boolean>` (via [publicChatChannelIdOf] combined
 * with the mute set) instead of being resolved as a one-shot snapshot at construction time. Early-return
 * on a snapshot of the mute set would freeze the dot's mute state as of whenever the flow was built —
 * do not "simplify" this back into an early return.
 *
 * The two collapsed rows are why this carries a `Flow<Boolean>` rather than a `(route, createdAt)`
 * pair: their dot is a fan-in over every child channel, not one timestamp against one marker, and
 * approximating them by the newest child would miss an older channel that is still unread.
 */
class RowUnread(
    /**
     * The answer as of right now, for the caller's FIRST frame.
     *
     * A composable collecting [flow] only starts collecting after its first composition, so without
     * a seed every row would paint dotless and correct itself a frame later — a dot flash on every
     * recycled row while scrolling. Before the row was routed through this helper it read a
     * `StateFlow` directly and was right immediately; this keeps that property.
     *
     * `false` for the two collapsed rows, whose fan-in flows have no cheap synchronous answer. That
     * matches what those rows already did before this type existed.
     */
    val initial: Boolean,
    val flow: Flow<Boolean>,
)

fun rowHasUnread(
    row: Note,
    account: Account,
): RowUnread? {
    // Collapsed rows own a fan-in flow across their children — reuse the row's own signal verbatim.
    if (row is RelayGroupServerRoomNote) return RowUnread(false, relayGroupServerHasUnreadFlow(account, row.relay))
    if (row is ConcordServerRoomNote) return RowUnread(false, concordCommunityHasUnreadFlow(account, row.communityId))

    val route = rowLastReadRoute(row, account) ?: return null
    val createdAt = row.createdAt() ?: return null

    val lastRead = account.settings.getLastReadFlow(route)

    // Public chats can be silenced at runtime, so the mute set has to be part of the
    // emitted signal rather than a snapshot taken when this flow was built — otherwise
    // toggling mute would not move the dot until something else re-keyed the caller.
    // Every other row type skips the mute flow entirely and stays a plain map.
    val mutedChannelId = publicChatChannelIdOf(row.event)

    if (mutedChannelId == null) {
        return RowUnread(
            initial = createdAt > lastRead.value,
            flow = lastRead.map { createdAt > it }.distinctUntilChanged(),
        )
    }

    val muted = account.settings.mutedPublicChats

    // One expression feeds both the seed and the flow, so the first frame and every later
    // frame cannot disagree — the drift this helper exists to prevent, in miniature.
    val compute = { lastReadAt: Long, mutedSet: Set<String> ->
        createdAt > lastReadAt && mutedChannelId !in mutedSet
    }

    return RowUnread(
        initial = compute(lastRead.value, muted.value),
        flow = combine(lastRead, muted) { read, mutedSet -> compute(read, mutedSet) }.distinctUntilChanged(),
    )
}

/**
 * The last-read marker route behind a row's dot, mirroring what each row composable loads. Channel-type
 * rows are identified by their gatherer (the channel the note was filed into) rather than by event kind,
 * because a Buzz channel and a Concord channel can both carry a kind-9 message.
 */
private fun rowLastReadRoute(
    row: Note,
    account: Account,
): String? {
    row.inGatherers?.forEach { gatherer ->
        when (gatherer) {
            is RelayGroupChannel -> return relayGroupChannelLastReadRoute(gatherer.groupId)
            is ConcordChannel -> return concordChannelLastReadRoute(gatherer.channelId.communityId, gatherer.channelId.channelId)
            is MarmotGroupChatroom -> return marmotGroupLastReadRoute(gatherer.nostrGroupId)
            is GeohashChatChannel -> return "Geohash/${gatherer.geohash}"
            else -> Unit
        }
    }

    return when (val event = row.event) {
        // Same route strings the row composables use — see ChatroomHeaderCompose. The channel
        // id itself comes from [publicChatChannelIdOf], which is also what decides admin events
        // (ChannelHideMessageEvent/ChannelMuteUserEvent) are not room activity — listing the
        // three concrete types here keeps them falling through to `else`.
        is ChannelMessageEvent, is ChannelMetadataEvent, is ChannelCreateEvent ->
            publicChatChannelIdOf(event)?.let { "Channel/$it" }
        is EphemeralChatEvent -> event.roomId()?.let { "Channel/${it.toKey()}" }
        is GeohashChatEvent -> event.geohash()?.let { "Geohash/$it" }
        // DMs keep their own rule: a room whose newest message is mine counts as read.
        else -> unreadPrivateChatRoute(row.event, account.signer.pubKey, account::isAllHidden)?.first
    }
}
