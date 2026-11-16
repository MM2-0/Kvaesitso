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
package com.vitorpamplona.amethyst.commons.model.navigation

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.concord.ConcordChannel
import com.vitorpamplona.amethyst.commons.model.emphChat.EphemeralChatChannel
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.commons.model.nip29RelayGroups.RelayGroupChannel
import com.vitorpamplona.amethyst.commons.model.nip53LiveActivities.LiveActivitiesChannel
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip22Comments.CommentEvent
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId

/**
 * A minichat reply — a kind-1111 [CommentEvent] posted into a chat message's thread — should open
 * that message's minichat ([Route.ChatMinichat]), not the whole channel it lives in. Regular chat
 * messages are kind 9 / 42, so a [CommentEvent] in a *chat context* is always a thread reply. We
 * gate on the chat context (the reply is attached to a Concord / relay-group / public-chat gatherer,
 * or its root message is) precisely so a generic NIP-22 comment on an article or note keeps its own
 * thread route and isn't mistaken for a minichat. Returns null when it isn't a chat-context comment.
 */
fun minichatRouteFor(note: Note): Route? {
    val comment = note.event as? CommentEvent ?: return null
    val rootId = comment.rootEventIds().firstOrNull() ?: return null

    // Prefer the reply's own Concord channel (the reply arrived over that plane, so its gatherer
    // always carries the community/channel), else the root note's if it happens to be loaded. Passing
    // these lets the minichat screen resolve the plane + relays even when the parent isn't cached.
    val concord =
        note.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
            ?: LocalCache.getNoteIfExists(rootId)?.inGatherers?.firstNotNullOfOrNull { it as? ConcordChannel }
    if (concord != null) {
        return Route.ChatMinichat(rootId, concord.channelId.communityId, concord.channelId.channelId)
    }

    val inChatContext = note.isInChatGatherer() || LocalCache.getNoteIfExists(rootId)?.isInChatGatherer() == true
    return if (inChatContext) Route.ChatMinichat(rootId) else null
}

private fun Note.isInChatGatherer(): Boolean = inGatherers?.any { it is ConcordChannel || it is RelayGroupChannel || it is PublicChatChannel } == true

fun routeFor(note: EphemeralChatChannel): Route = Route.EphemeralChat(note.roomId.id, note.roomId.relayUrl.url)

fun routeFor(note: PublicChatChannel): Route = Route.PublicChatChannel(note.idHex)

fun routeFor(note: LiveActivitiesChannel): Route = Route.LiveActivityChannel(note.address.kind, note.address.pubKeyHex, note.address.dTag)

fun routeFor(roomId: RoomId): Route = Route.EphemeralChat(roomId.id, roomId.relayUrl.url)

fun routeFor(note: RelayGroupChannel): Route = Route.RelayGroup(note.groupId.id, note.groupId.relayUrl.url)

fun routeFor(groupId: GroupId): Route = Route.RelayGroup(groupId.id, groupId.relayUrl.url)

fun routeFor(channel: ConcordChannel): Route = Route.Concord(channel.channelId.communityId, channel.channelId.channelId)

fun routeFor(user: User): Route.Profile = Route.Profile(user.pubkeyHex)

fun routeForUser(userHex: HexKey): Route.Profile = Route.Profile(userHex)

fun authorRouteFor(note: Note): Route.Profile? = note.author?.pubkeyHex?.let { Route.Profile(it) }
