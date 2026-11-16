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
package com.vitorpamplona.amethyst.commons.chats.rooms.dal

import com.vitorpamplona.amethyst.commons.feeds.AdditiveFeedFilter
import com.vitorpamplona.amethyst.commons.feeds.sortedByDefaultFeedOrder
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.chats.ChatFeedType
import com.vitorpamplona.amethyst.commons.util.replace
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKeyable

class ChatroomListNewFeedFilter(
    val account: Account,
) : AdditiveFeedFilter<Note>() {
    override fun feedKey(): String = account.userProfile().pubkeyHex

    private fun isEnabled(type: ChatFeedType): Boolean = account.chatFeedToggles.isEnabled(type)

    // returns the last Note of each user.
    override fun feed(): List<Note> {
        val chatList = account.chatroomList
        val followingKeySet = account.followingKeySet()
        // A pinned room is Known (Account.isKnownChatroom), never a request.

        val privateMessages =
            chatList.rooms.mapNotNull { key, chatroom ->
                val newest = chatroom.newestMessage
                if (newest != null &&
                    !account.isKnownChatroom(key, chatroom, followingKeySet) &&
                    !account.isAllHidden(key.users)
                ) {
                    newest
                } else {
                    null
                }
            }

        val marmotGroups =
            if (!isEnabled(ChatFeedType.MARMOT)) {
                emptyList()
            } else {
                account.marmotGroupList.rooms.mapNotNull { _, chatroom ->
                    if (!chatroom.isKnown(followingKeySet)) {
                        chatroom.newestMessage ?: chatroom.placeholderNote()
                    } else {
                        null
                    }
                }
            }

        // NIP-29 groups a relay says somebody added me to, but that I have not answered: not on my
        // kind-10009, not dismissed, no later kind-44101 withdrawing it. Exactly the same standing as
        // an unaccepted DM — a room waiting on a decision — so it belongs in this list and not pinned
        // above it, sorted by when the invite landed like everything else here.
        //
        // The row is the kind-44100 itself, which carries the channel (`h`), the actor and the time.
        // ChatroomHeaderCompose renders it as the group's row with the invitation as its newest line.
        val relayGroupInvites =
            if (!isEnabled(ChatFeedType.NIP29)) {
                emptyList()
            } else {
                // Read the map the invalidation is driven by, not the sorted list derived from it:
                // `AccountFeedContentStates` rebuilds this feed when `pendingByEventId` emits, and
                // `flow` is a second StateFlow mapped off that one, so at the instant the rebuild runs
                // it can still hold the previous answer — an accepted invite then keeps its row until
                // something else refreshes the list. (Order is irrelevant here; the feed sorts below.)
                account.channelInvites.pendingByEventId.value.keys
                    .mapNotNull { LocalCache.getNoteIfExists(it) }
            }

        // Concord Direct Invites (CORD-05 §6) waiting on an answer: a community somebody handed us the
        // keys to, with the same standing as an unaccepted DM, so it sits here with the other requests.
        // AccountFeedContentStates rebuilds this feed when the pending set moves.
        val concordDirectInvites =
            if (!isEnabled(ChatFeedType.CONCORD)) emptyList() else account.concord.pendingConcordDirectInviteNotes.value

        return (privateMessages + marmotGroups + relayGroupInvites + concordDirectInvites).sortedByDefaultFeedOrder()
    }

    override fun updateListWith(
        oldList: List<Note>,
        newItems: Set<Note>,
    ): List<Note> {
        val me = account.userProfile()

        // Gets the latest message by room from the new items.
        val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)

        if (newRelevantPrivateMessages.isEmpty()) {
            return oldList
        }

        var myNewList = oldList

        newRelevantPrivateMessages.forEach { newNotePair ->
            var hasUpdated = false
            oldList.forEach { oldNote ->
                val oldRoom = (oldNote.event as? ChatroomKeyable)?.chatroomKey(me.pubkeyHex)

                if (newNotePair.key == oldRoom) {
                    hasUpdated = true
                    if ((newNotePair.value.createdAt() ?: 0L) > (oldNote.createdAt() ?: 0L)) {
                        myNewList = myNewList.replace(oldNote, newNotePair.value)
                    }
                }
            }
            if (!hasUpdated) {
                myNewList = myNewList.plus(newNotePair.value)
            }
        }

        return sort(myNewList.toSet()).take(1000)
    }

    override fun applyFilter(newItems: Set<Note>): Set<Note> {
        // Gets the latest message by room from the new items.
        val newRelevantPrivateMessages = filterRelevantPrivateMessages(newItems, account)

        return if (newRelevantPrivateMessages.isEmpty()) {
            emptySet()
        } else {
            newRelevantPrivateMessages.values.toSet()
        }
    }

    private fun filterRelevantPrivateMessages(
        newItems: Set<Note>,
        account: Account,
    ): MutableMap<ChatroomKey, Note> {
        val me = account.userProfile()
        val followingKeySet = account.followingKeySet()

        val newRelevantPrivateMessages = mutableMapOf<ChatroomKey, Note>()
        newItems.forEach { newNote ->
            val noteEvent = newNote.event
            // Same gate as ingestion: a protocol turned off never reaches the rooms.
            if (noteEvent is ChatroomKeyable && account.chatFeedToggles.isEnabled(noteEvent)) {
                val roomKey = noteEvent.chatroomKey(me.pubkeyHex)
                val room = account.chatroomList.rooms.get(roomKey)

                if (room != null &&
                    (
                        newNote.author?.pubkeyHex != me.pubkeyHex &&
                            !account.isKnownChatroom(roomKey, room, followingKeySet)
                    ) &&
                    !account.isAllHidden(roomKey.users)
                ) {
                    val lastNote = newRelevantPrivateMessages.get(roomKey)
                    if (lastNote != null) {
                        if ((newNote.createdAt() ?: 0L) > (lastNote.createdAt() ?: 0L)) {
                            newRelevantPrivateMessages.put(roomKey, newNote)
                        }
                    } else {
                        newRelevantPrivateMessages.put(roomKey, newNote)
                    }
                }
            }
        }

        return newRelevantPrivateMessages
    }

    override fun sort(items: Set<Note>): List<Note> = items.sortedByDefaultFeedOrder()
}
