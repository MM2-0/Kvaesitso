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
package com.vitorpamplona.amethyst.commons.model.privateChats

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip04Dm.messages.EncryptedDmEvent
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Turning a DM protocol off in Settings › Messages unloads it: its messages leave every room (so a room
 * that only had them is empty and drops off Messages, instead of being hidden because its newest message
 * is of the disabled kind) and its history paging is forgotten.
 */
class DmProtocolToggleTest {
    private val me = "aa".repeat(32)
    private val other = "bb".repeat(32)
    private val third = "cc".repeat(32)
    private val someSig = "dd".repeat(64)
    private val relay = NormalizedRelayUrl("wss://relay.example/")

    private fun nip04(
        id: String,
        from: String,
        createdAt: Long,
    ) = EncryptedDmEvent(id.padEnd(64, '0'), from, createdAt, arrayOf(arrayOf("p", me)), "x?iv=y", someSig)

    private fun nip17(
        id: String,
        from: String,
        createdAt: Long,
    ) = ChatMessageEvent(id.padEnd(64, '0'), from, createdAt, arrayOf(arrayOf("p", me)), "hi", someSig)

    private fun <T> ChatroomList.ingest(event: T): Note where T : Event, T : ChatroomKeyable {
        val note = Note(event.id).apply { this.event = event }
        add(event, note)
        return note
    }

    private fun ChatroomList.unload(type: ChatFeedType) = removeMessagesIf { (it.event as? ChatroomKeyable)?.chatFeedType() == type }

    @Test
    fun kind4IsNip04AndRumorsAreNip17() {
        assertEquals(ChatFeedType.NIP04, nip04("a", other, 1).chatFeedType())
        assertEquals(ChatFeedType.NIP17, nip17("b", other, 1).chatFeedType())
    }

    @Test
    fun unloadingNip04KeepsTheRoomOnItsNip17Messages() {
        val list = ChatroomList(me)
        val older17 = list.ingest(nip17("b", other, 100))
        list.ingest(nip04("a", other, 200)) // the newest message in the room is NIP-04

        list.unload(ChatFeedType.NIP04)

        val room = list.rooms.get(nip17("b", other, 100).chatroomKey(me))!!
        assertEquals(setOf(older17), room.messages)
        assertEquals(older17, room.newestMessage)
    }

    @Test
    fun unloadingNip04EmptiesARoomThatOnlyHadNip04() {
        val list = ChatroomList(me)
        list.ingest(nip04("a", third, 200))

        list.unload(ChatFeedType.NIP04)

        val room = list.rooms.get(nip04("a", third, 200).chatroomKey(me))!!
        assertTrue(room.messages.isEmpty())
        assertNull(room.newestMessage)
    }

    @Test
    fun unloadingRebuildsSendersAndTheOwnerSentFlag() {
        val ctx = UserContext { Note("addr") }
        val meUser = User(me, ctx)
        val otherUser = User(other, ctx)
        val list = ChatroomList(me)

        // The other side wrote over NIP-17; only the owner's own messages were NIP-04.
        val theirs = nip17("b", other, 100)
        list.ingest(theirs).author = otherUser
        val mine = nip04("a", me, 200)
        val myNote =
            Note(mine.id).apply {
                event = mine
                author = meUser
            }
        list.addMessage(theirs.chatroomKey(me), myNote)
        val room = list.rooms.get(theirs.chatroomKey(me))!!
        assertTrue(list.hasSentMessagesTo(theirs.chatroomKey(me)))

        list.unload(ChatFeedType.NIP04)

        assertFalse(list.hasSentMessagesTo(theirs.chatroomKey(me)), "no message of the owner is left in the room")
        assertFalse(room.senderIntersects(setOf(me)), "the owner only spoke over NIP-04")
        assertTrue(room.senderIntersects(setOf(other)))
    }

    @Test
    fun aSingleRemovalKeepsTheRoomClassification() {
        // Expired (disappearing) messages and deletions go through the single-removal path; the room must
        // stay Known even when the owner's or a followed sender's only message is the one that left.
        val ctx = UserContext { Note("addr") }
        val meUser = User(me, ctx)
        val list = ChatroomList(me)

        val mine = nip17("m", me, 100)
        val myNote =
            Note(mine.id).apply {
                event = mine
                author = meUser
            }
        val key = mine.chatroomKey(me)
        list.addMessage(key, myNote)
        val room = list.rooms.get(key)!!

        list.removeMessage(key, myNote)

        assertTrue(list.hasSentMessagesTo(key))
        assertTrue(room.senderIntersects(setOf(me)))
    }

    @Test
    fun bulkAddRestoresRoomsSendersAndTheOwnerFlag() {
        val ctx = UserContext { Note("addr") }
        val meUser = User(me, ctx)
        val otherUser = User(other, ctx)
        val list = ChatroomList(me)

        val theirs =
            nip17("b", other, 100).let { e ->
                Note(e.id).apply {
                    event = e
                    author = otherUser
                }
            }
        // Sent by me to the other side: a kind 4 tags its recipient.
        val toOther = EncryptedDmEvent("a".padEnd(64, '0'), me, 200, arrayOf(arrayOf("p", other)), "x?iv=y", someSig)
        val mine =
            Note(toOther.id).apply {
                event = toOther
                author = meUser
            }

        list.addAll(listOf(theirs, mine, theirs))

        val key = (theirs.event as ChatroomKeyable).chatroomKey(me)
        val room = list.rooms.get(key)!!
        assertEquals(setOf(theirs, mine), room.messages)
        assertEquals(mine, room.newestMessage)
        assertTrue(room.senderIntersects(setOf(other)))
        assertTrue(list.hasSentMessagesTo(key))
    }

    @Test
    fun resettingNip04HistoryForgetsListAndRoomCursors() {
        val list = ChatroomList(me)
        list.ingest(nip04("a", other, 200))
        val room = list.rooms.get(nip04("a", other, 200).chatroomKey(me))!!

        list.nip04History.floor = 1_000
        list.nip04History.advance(relay, 1_000)
        room.nip04History.floor = 1_000
        room.nip04History.advance(relay, 1_000)

        list.resetNip04History()

        assertNull(list.nip04History.floor)
        assertNull(list.nip04History.requestedUntilFor(relay))
        assertNull(room.nip04History.floor)
        assertNull(room.nip04History.requestedUntilFor(relay))
        assertFalse(list.nip04History.isDone(relay))
    }
}
