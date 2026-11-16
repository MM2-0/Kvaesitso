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
import com.vitorpamplona.amethyst.commons.model.privateChatLastReadRoute
import com.vitorpamplona.amethyst.commons.model.relayGroupChannelLastReadRoute
import com.vitorpamplona.quartz.experimental.ephemChat.chat.EphemeralChatEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.IsInPublicChatChannel
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.groupId
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupScoped
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent

/**
 * Marks one Messages-list row as read by advancing the very same last-read route(s) the row's unread
 * dot reads from. This MUST stay in lockstep with [ChatroomEntry]'s type dispatch: every row kind that
 * can light an unread dot needs a branch here, or "mark all as read" silently skips it and its dot can
 * only ever be cleared by opening the room. The resolution order mirrors [ChatroomEntry] exactly —
 * synthetic grouped rows, then gatherer-attached channels, then the `h`-tag fallback, then the raw
 * event type — so the route computed here is byte-for-byte the one each row badge collects.
 *
 * The two synthetic "grouped by server" rows collapse many rooms behind one aggregate dot, so they
 * fan out: every joined group on the relay ([RelayGroupServerRoomNote]) / every channel in the
 * community ([ConcordServerRoomNote]) is marked read up to the row's newest message. [Account.markAsRead]
 * only ever advances a route, so over-reaching a quieter room to the aggregate cutoff never un-reads it.
 */
fun markRoomNoteAsRead(
    account: Account,
    note: Note,
) {
    when (note) {
        is RelayGroupServerRoomNote -> {
            val cutoff = note.createdAt() ?: return
            account.relayGroupList.liveRelayGroupList.value.forEach { tag ->
                if (RelayUrlNormalizer.normalizeOrNull(tag.relayUrl) == note.relay) {
                    account.markAsRead(relayGroupChannelLastReadRoute(GroupId(tag.groupId, note.relay)), cutoff)
                }
            }
            return
        }

        is ConcordServerRoomNote -> {
            val cutoff = note.createdAt() ?: return
            account.concordSessions
                .sessionFor(note.communityId)
                ?.state
                ?.value
                ?.channels
                ?.keys
                ?.forEach { channelKey ->
                    account.markAsRead(concordChannelLastReadRoute(note.communityId, channelKey), cutoff)
                }
            return
        }
    }

    val createdAt = note.createdAt() ?: return

    // Gatherer-attached channels first, in the same priority order as ChatroomEntry.
    note.inGatherers?.firstNotNullOfOrNull { it as? MarmotGroupChatroom }?.let {
        account.markAsRead(marmotGroupLastReadRoute(it.nostrGroupId), createdAt)
        return
    }
    note.inGatherers?.firstNotNullOfOrNull { it as? RelayGroupChannel }?.let {
        account.markAsRead(relayGroupChannelLastReadRoute(it.groupId), createdAt)
        return
    }
    note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }?.let {
        account.markAsRead(concordChannelLastReadRoute(it.channelId.communityId, it.channelId.channelId), createdAt)
        return
    }
    note.inGatherers?.firstNotNullOfOrNull { it as? GeohashChatChannel }?.let {
        account.markAsRead("Geohash/${it.geohash}", createdAt)
        return
    }

    // A NIP-29 group message whose channel gatherer never attached: resolve the group from its `h` tag
    // + provenance relay, exactly like ChatroomEntry's fallback row.
    val groupScopedEvent = note.event?.takeIf { it.isGroupScoped() }
    if (groupScopedEvent != null) {
        val gid = groupScopedEvent.groupId()
        val hostRelay = note.relays.firstOrNull()
        if (gid != null && hostRelay != null) {
            account.markAsRead(relayGroupChannelLastReadRoute(GroupId(gid, hostRelay)), createdAt)
            return
        }
    }

    markEventRoomAsRead(account, note.event, createdAt)
}

/**
 * Marks the room of a plain (non-gathered) chat event read: public chats (NIP-28), ephemeral relay
 * chats, and 1:1/group DMs (NIP-17/04), unwrapping a [DraftWrapEvent] to the same three cases. Drafts
 * for group-scoped rooms are already caught by the gatherer checks above, so they don't recur here.
 */
private fun markEventRoomAsRead(
    account: Account,
    event: Event?,
    createdAt: Long,
) {
    when (event) {
        is IsInPublicChatChannel -> event.channelId()?.let { account.markAsRead("Channel/$it", createdAt) }
        is ChannelCreateEvent -> account.markAsRead("Channel/${event.id}", createdAt)
        is EphemeralChatEvent -> event.roomId()?.let { account.markAsRead("Channel/${it.toKey()}", createdAt) }
        is ChatroomKeyable -> account.markAsRead(privateChatLastReadRoute(event.chatroomKey(account.signer.pubKey)), createdAt)
        is DraftWrapEvent -> {
            when (val inner = account.draftsDecryptionCache.preCachedDraft(event)) {
                is IsInPublicChatChannel -> inner.channelId()?.let { account.markAsRead("Channel/$it", createdAt) }
                is ChannelCreateEvent -> account.markAsRead("Channel/${inner.id}", createdAt)
                is ChatroomKeyable -> account.markAsRead(privateChatLastReadRoute(inner.chatroomKey(account.signer.pubKey)), createdAt)
                else -> {}
            }
        }
        else -> {}
    }
}
