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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.chatMessageMarksRoomAsRead
import com.vitorpamplona.amethyst.commons.model.privateChatLastReadRoute
import com.vitorpamplona.amethyst.commons.model.unreadPrivateChatRoute
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The unread predicate behind the Messages tab dot and the room-row bubble: a room whose
 * newest message was authored by the logged-in user counts as read (#1286, #1287), except
 * notes-to-self rooms, where the user's own messages are the content still to be seen.
 */
class PrivateChatroomReadStateTest {
    private val me: HexKey = "a".repeat(64)
    private val peer: HexKey = "b".repeat(64)

    private val roomWithPeer = ChatroomKey(persistentSetOf(peer))
    private val selfRoom = ChatroomKey(persistentSetOf(me))

    private fun message(
        from: HexKey,
        to: HexKey,
        createdAt: Long,
    ) = ChatMessageEvent(
        id = "0".repeat(64),
        pubKey = from,
        createdAt = createdAt,
        tags = arrayOf(arrayOf("p", to)),
        content = "hello",
        sig = "",
    )

    @Test
    fun newestMessageFromPeerReturnsTheRoomRoute() {
        val route = unreadPrivateChatRoute(message(from = peer, to = me, createdAt = 100), me, isAllHidden = { false })

        assertEquals(privateChatLastReadRoute(roomWithPeer) to 100L, route)
    }

    @Test
    fun newestMessageAuthoredByMeCountsAsRead() {
        assertNull(unreadPrivateChatRoute(message(from = me, to = peer, createdAt = 100), me, isAllHidden = { false }))
    }

    @Test
    fun notesToSelfRoomsCanStillBeUnread() {
        val route = unreadPrivateChatRoute(message(from = me, to = me, createdAt = 100), me, isAllHidden = { false })

        assertEquals(privateChatLastReadRoute(selfRoom) to 100L, route)
    }

    @Test
    fun hiddenRoomsAreNeverUnread() {
        assertNull(unreadPrivateChatRoute(message(from = peer, to = me, createdAt = 100), me, isAllHidden = { true }))
    }

    @Test
    fun missingEventIsNotUnread() {
        assertNull(unreadPrivateChatRoute(null, me, isAllHidden = { false }))
    }

    @Test
    fun nonChatEventsAreNotUnread() {
        val reaction =
            ReactionEvent(
                id = "0".repeat(64),
                pubKey = peer,
                createdAt = 100,
                tags = arrayOf(arrayOf("p", me)),
                content = "+",
                sig = "",
            )

        assertNull(unreadPrivateChatRoute(reaction, me, isAllHidden = { false }))
    }

    @Test
    fun myMessageMarksAPeerRoomAsRead() {
        assertEquals(true, chatMessageMarksRoomAsRead(message(from = me, to = peer, createdAt = 100), roomWithPeer, me))
    }

    @Test
    fun aPeerMessageNeverMarksTheRoomAsRead() {
        assertEquals(false, chatMessageMarksRoomAsRead(message(from = peer, to = me, createdAt = 100), roomWithPeer, me))
    }

    @Test
    fun myMessageDoesNotMarkTheSelfRoomAsRead() {
        assertEquals(false, chatMessageMarksRoomAsRead(message(from = me, to = me, createdAt = 100), selfRoom, me))
    }
}
