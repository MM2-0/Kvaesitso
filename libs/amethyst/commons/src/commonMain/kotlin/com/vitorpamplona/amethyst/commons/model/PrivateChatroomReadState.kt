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
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable

/**
 * Route key under which a private chat room's last-read time is stored in AccountSettings.
 * Every marker writer (send paths, ingestion, room view, hidden-room sweep) and reader
 * (Messages-tab dot, room-row bubble) must build the key through this function: a format
 * drift between a writer and a reader silently splits read state (#1286).
 */
fun privateChatLastReadRoute(room: ChatroomKey) = "Room/${room.hashCode()}"

/**
 * True when [message] marks [room] as read up to its timestamp: the logged-in user authored
 * it, so sending it — from this device, or from another one arriving via the self-addressed
 * gift wrap — means they had caught up with the conversation (#1286, #1287). Notes-to-self
 * rooms are exempt: there the user's own messages ARE the content still to be seen.
 */
fun chatMessageMarksRoomAsRead(
    message: Event,
    room: ChatroomKey,
    loggedInUser: HexKey,
): Boolean = message.pubKey == loggedInUser && room.users.singleOrNull() != loggedInUser

/**
 * Read-marker route + timestamp for the newest message of a private chat room, or null when
 * the room cannot be unread: no chat event, a newest message that counts as read (see
 * [chatMessageMarksRoomAsRead]), or every participant hidden.
 */
fun unreadPrivateChatRoute(
    newestMessage: Event?,
    loggedInUser: HexKey,
    isAllHidden: (Set<HexKey>) -> Boolean,
): Pair<String, Long>? {
    if (newestMessage !is ChatroomKeyable) return null
    val room = newestMessage.chatroomKey(loggedInUser)
    if (chatMessageMarksRoomAsRead(newestMessage, room, loggedInUser)) return null
    if (isAllHidden(room.users)) return null
    return privateChatLastReadRoute(room) to newestMessage.createdAt
}
