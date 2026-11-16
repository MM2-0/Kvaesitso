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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.paging.RelayLoadingCursors
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.collections.immutable.persistentSetOf

class ChatroomList(
    val ownerPubKey: HexKey,
) {
    var rooms = LargeCache<ChatroomKey, Chatroom>()
        private set

    // Account-level DM history paging cursors (one scope per account), held here so they share the
    // lifetime of the cached messages and are dropped when the cache prunes them. The account-level
    // history loaders bind their orchestrator to these. (Per-conversation NIP-04 cursors live on the
    // individual [Chatroom] instead.)
    val giftWrapHistory = RelayLoadingCursors()
    val nip04History = RelayLoadingCursors()

    private fun getOrCreatePrivateChatroomSync(key: ChatroomKey): Chatroom = rooms.getOrCreate(key) { Chatroom() }

    fun getOrCreatePrivateChatroom(user: User): Chatroom {
        val key = ChatroomKey(persistentSetOf(user.pubkeyHex))
        return getOrCreatePrivateChatroom(key)
    }

    fun getOrCreatePrivateChatroom(key: ChatroomKey): Chatroom = getOrCreatePrivateChatroomSync(key)

    fun add(
        event: ChatroomKeyable,
        msg: Note,
    ) {
        if (event.isIncluded(ownerPubKey)) {
            val key = event.chatroomKey(ownerPubKey)
            addMessage(key, msg)
        }
    }

    fun delete(
        event: ChatroomKeyable,
        msg: Note,
    ) {
        if (event.isIncluded(ownerPubKey)) {
            val key = event.chatroomKey(ownerPubKey)
            removeMessage(key, msg)
        }
    }

    fun addMessage(
        room: ChatroomKey,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(room)
        if (msg !in privateChatroom.messages) {
            privateChatroom.addMessageSync(msg)
            if (msg.author?.pubkeyHex == ownerPubKey) {
                privateChatroom.ownerSentMessage = true
            }
        }
    }

    fun addMessage(
        user: User,
        msg: Note,
    ) {
        val privateChatroom = getOrCreatePrivateChatroom(user)
        if (msg !in privateChatroom.messages) {
            privateChatroom.addMessageSync(msg)
            if (msg.author?.pubkeyHex == ownerPubKey) {
                privateChatroom.ownerSentMessage = true
            }
        }
    }

    fun removeMessage(
        user: User,
        msg: Note,
    ) = removeMessage(getOrCreatePrivateChatroom(user), msg)

    fun removeMessage(
        room: ChatroomKey,
        msg: Note,
    ) = removeMessage(getOrCreatePrivateChatroom(room), msg)

    // ownerSentMessage stays as it was: a single removal is an expiration or deletion, and the owner
    // having written here still makes the room Known after their message is gone. Only a protocol
    // unload (removeMessagesIf) re-derives it.
    private fun removeMessage(
        privateChatroom: Chatroom,
        msg: Note,
    ) {
        privateChatroom.removeMessageSync(msg)
    }

    // Whether the owner still has a message in [room], re-derived only when one of theirs just left.
    private fun refreshOwnerSent(
        room: Chatroom,
        removed: Collection<Note>,
    ) {
        if (removed.any { it.author?.pubkeyHex == ownerPubKey }) {
            room.ownerSentMessage = room.messages.any { it.author?.pubkeyHex == ownerPubKey }
        }
    }

    /**
     * Adds every note whose event is a DM including the owner to its room, one batch per room (see
     * [Chatroom.addMessagesSync]).
     */
    fun addAll(notes: Collection<Note>) {
        notes
            .groupBy { (it.event as? ChatroomKeyable)?.takeIf { event -> event.isIncluded(ownerPubKey) }?.chatroomKey(ownerPubKey) }
            .forEach { (key, roomNotes) ->
                if (key == null) return@forEach
                val room = getOrCreatePrivateChatroom(key)
                if (room.addMessagesSync(roomNotes).any { it.author?.pubkeyHex == ownerPubKey }) room.ownerSentMessage = true
            }
    }

    /**
     * Drops every message matching [predicate] from every room (e.g. all of a DM protocol the user
     * turned off), then re-derives whether the owner still has a message in each room it touched, so a
     * room is not kept in Known on the strength of messages that are gone. Returns the dropped notes.
     */
    fun removeMessagesIf(predicate: (Note) -> Boolean): Set<Note> {
        val removed = mutableSetOf<Note>()
        rooms.forEach { _, room ->
            val fromRoom = room.removeMessagesIf(predicate)
            refreshOwnerSent(room, fromRoom)
            removed.addAll(fromRoom)
        }
        return removed
    }

    /** Forgets the NIP-04 paging progress of the rooms list and of every conversation. */
    fun resetNip04History() {
        nip04History.reset()
        rooms.forEach { _, room -> room.resetNip04History() }
    }

    fun hasSentMessagesTo(key: ChatroomKey?): Boolean {
        if (key == null) return false
        return rooms.get(key)?.ownerSentMessage == true
    }
}
