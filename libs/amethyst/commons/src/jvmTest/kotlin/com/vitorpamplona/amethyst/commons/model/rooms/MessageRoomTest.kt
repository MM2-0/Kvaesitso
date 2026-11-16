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
package com.vitorpamplona.amethyst.commons.model.rooms

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.privateChats.Chatroom
import com.vitorpamplona.amethyst.commons.notices.UserNoticeSink
import com.vitorpamplona.amethyst.commons.relayClient.event.watchers.filterRepliesAndReactionsToNotes
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nipC7Chats.ChatEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A room's messages, and the private reactions about them, are the room's: [isInRoom] is what the
 * account's public paths refuse on, and what keeps relays from being asked about them.
 */
class MessageRoomTest {
    private val signer = NostrSignerSync(KeyPair())
    private val relay = NormalizedRelayUrl("wss://relay.example.com/")

    private class FakeRoom : MessageRoom {
        override fun removeNote(note: Note) = Unit

        override fun isReadBy(account: Account) = true

        override suspend fun react(
            account: Account,
            target: Note,
            reaction: String,
            notices: UserNoticeSink,
        ) = true

        override suspend fun delete(
            account: Account,
            notes: List<Note>,
        ) = true

        override fun canEdit(
            account: Account,
            note: Note,
        ) = false
    }

    private fun message(
        cache: EventCache,
        content: String,
        signed: Boolean,
    ): Note {
        val event = signer.sign<ChatEvent>(1_000, ChatEvent.KIND, emptyArray(), content)
        val stored = if (signed) event else ChatEvent(event.id, event.pubKey, event.createdAt, event.tags, event.content, "")
        cache.justConsume(stored, null, true)
        return cache.getNoteIfExists(event.id)!!.also { it.addRelay(relay) }
    }

    @Test
    fun aMessageInARoomIsInIt() {
        val cache = EventCache()
        val note = message(cache, "hi", signed = false)
        assertFalse(note.isInRoom())
        note.addGatherer(FakeRoom())
        assertTrue(note.isInRoom())
    }

    @Test
    fun aPrivateReactionBelongsToTheRoomOfTheMessageItPointsAt() {
        val cache = EventCache()
        val target = message(cache, "hi", signed = false).also { it.addGatherer(FakeRoom()) }
        val signedReaction = signer.sign<ReactionEvent>(1_001, ReactionEvent.KIND, arrayOf(arrayOf("e", target.idHex)), "+")
        val rumor = ReactionEvent(signedReaction.id, signedReaction.pubKey, signedReaction.createdAt, signedReaction.tags, signedReaction.content, "")
        cache.justConsume(rumor, null, true)

        assertTrue(cache.getNoteIfExists(rumor.id)!!.isInRoom())
    }

    @Test
    fun aDirectMessageQuotingARoomMessageStaysInItsConversation() {
        val cache = EventCache()
        val target = message(cache, "hi", signed = false).also { it.addGatherer(FakeRoom()) }
        val signedDm = signer.sign<ChatMessageEvent>(1_001, ChatMessageEvent.KIND, arrayOf(arrayOf("e", target.idHex)), "look at this")
        val dm = ChatMessageEvent(signedDm.id, signedDm.pubKey, signedDm.createdAt, signedDm.tags, signedDm.content, "")
        cache.justConsume(dm, null, true)
        val dmNote = cache.getNoteIfExists(dm.id)!!.also { it.addGatherer(Chatroom()) }

        assertFalse(dmNote.isInRoom())
    }

    @Test
    fun relaysAreNotAskedAboutRoomOrPrivateMessages() {
        val cache = EventCache()
        val public = message(cache, "public", signed = true)
        val roomMessage = message(cache, "room", signed = true).also { it.addGatherer(FakeRoom()) }
        val rumor = message(cache, "rumor", signed = false)

        assertNull(filterRepliesAndReactionsToNotes(listOf(roomMessage, rumor), null)?.takeIf { it.isNotEmpty() })

        val askedIds = filterRepliesAndReactionsToNotes(listOf(public, roomMessage, rumor), null)!!.flatMap { it.filter.tags?.get("e") ?: emptyList() }.toSet()
        assertEquals(setOf(public.idHex), askedIds)
    }
}
