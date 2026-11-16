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
package com.vitorpamplona.amethyst.commons.model

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent

/**
 * The NIP-28 channel an event belongs to, or null when [event] is not one of the three
 * public-chat event types a channel row's newest event can be.
 *
 * This is THE definition of "which event identifies a public-chat room" — the row dispatch
 * (ChatroomHeaderCompose), the last-read route (ChatroomRowUnread.rowLastReadRoute), the
 * feed's row de-duplication (ChatroomListKnownFeedFilter) and the mute predicate below all
 * call it. It used to be copied into each of those by hand, and the copies drifted: one of
 * them matched only [ChannelMessageEvent], so a channel whose latest activity was a topic
 * edit silently lost its unread dot. Keep it a single function.
 *
 * Deliberately NOT `threadRootIdOrSelf()`. That returns the same channel id here — a
 * NIP-28 message's NIP-10 root marker IS its channel — but it means something else
 * (the NIP-51 "muted thread" key, which HIDES content). Keeping the two apart is what
 * stops "mute notifications" and "mute thread" from bleeding into each other.
 *
 * Matched on concrete types rather than the IsInPublicChatChannel interface, which the
 * channel-admin events ChannelHideMessageEvent/ChannelMuteUserEvent also implement: those
 * must fall through to null rather than be treated as room activity.
 */
fun publicChatChannelIdOf(event: Event?): HexKey? =
    when (event) {
        is ChannelMessageEvent, is ChannelMetadataEvent -> event.channelId()
        is ChannelCreateEvent -> event.id
        else -> null
    }

/** True when [event] is a public-chat message in a channel the user has silenced. */
fun isMutedPublicChatMessage(
    event: Event?,
    mutedChannels: Set<HexKey>,
): Boolean {
    if (mutedChannels.isEmpty()) return false
    val channelId = publicChatChannelIdOf(event) ?: return false
    return channelId in mutedChannels
}

/**
 * The inbound-sync decision for the mute set, kept separate from [AccountSettings] so it can be
 * tested: [AccountSettings] builds a default `AccountSyncedSettingsInternal`, whose language
 * preferences call `Resources.getSystem()`, so it cannot be constructed in a JVM unit test.
 *
 * [remote] is `null` when an older client rewrote the NIP-78 blob without the key. The local set
 * must survive that — and because `AppSpecificState` replays the cached backup event on every app
 * start, treating absent as empty would re-clear the user's mutes on every single launch.
 *
 * An explicitly empty list is different: it is a real "unmute everything" from a client that knows
 * the field, and is adopted.
 */
fun mergeMutedPublicChats(
    local: Set<HexKey>,
    remote: List<HexKey>?,
): Set<HexKey> = remote?.toSet() ?: local
