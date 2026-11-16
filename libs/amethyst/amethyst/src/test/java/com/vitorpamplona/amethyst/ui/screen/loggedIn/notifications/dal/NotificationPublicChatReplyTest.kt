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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.notifications.dal

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.amethyst.commons.notifications.dal.NotificationFeedFilter
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Public chats (NIP-28, kind 42) frequently reply to a user without adding a
 * `p` tag. [NotificationFeedFilter.isNotifiablePublicChatReply] recovers those
 * by walking the cached reply chain for one of the user's own messages — a
 * direct reply to my message, or a later message in a thread I am already part
 * of — so the push dispatcher and the in-app feed can relax the p-tag gate for
 * exactly these events and nothing else.
 */
class NotificationPublicChatReplyTest {
    // User eagerly pins a few addressable note shells on construction; empty
    // shells are enough since the relevance check only reads pubkeyHex.
    private val noContext = UserContext { addr -> AddressableNote(addr) }

    private val me = "1".repeat(64)
    private val other = "2".repeat(64)
    private val sig = "f".repeat(128)

    private fun authoredNote(
        id: String,
        authorHex: String,
        parents: List<Note> = emptyList(),
    ): Note =
        Note(id).apply {
            author = User(authorHex, noContext)
            replyTo = parents
        }

    private fun channelMessage(
        id: String,
        authorHex: String,
        parents: List<Note>,
    ): Note =
        Note(id).apply {
            event = ChannelMessageEvent(id, authorHex, 1000, emptyArray(), "hi", sig)
            author = User(authorHex, noContext)
            replyTo = parents
        }

    @Test
    fun `direct reply to my channel message without a p-tag is notifiable`() {
        val myMessage = channelMessage("a".repeat(64), me, emptyList())
        val reply = channelMessage("b".repeat(64), other, listOf(myMessage))

        assertTrue(NotificationFeedFilter.isNotifiablePublicChatReply(reply, me))
        assertTrue(NotificationFeedFilter.tagsAnEventByUser(reply, me))
    }

    @Test
    fun `reply two hops above me in a thread i posted in is notifiable`() {
        // root(other) <- myMessage(me) <- someoneElse(other) <- newReply(other)
        // newReply's immediate parent is `other`, so a single-hop check would
        // miss me — this only passes if the walk climbs to the grandparent.
        val root = channelMessage("d".repeat(64), other, emptyList())
        val myMessage = channelMessage("e".repeat(64), me, listOf(root))
        val someoneElse = channelMessage("9".repeat(64), other, listOf(myMessage))
        val newReply = channelMessage("8".repeat(64), other, listOf(someoneElse))

        // Sanity: the immediate parent is not me, so this is a true multi-hop hit.
        assertFalse(newReply.replyTo?.any { it.author?.pubkeyHex == me } == true)
        assertTrue(NotificationFeedFilter.isNotifiablePublicChatReply(newReply, me))
    }

    @Test
    fun `channel message in a thread i never posted in is not notifiable`() {
        val root = channelMessage("a1".repeat(32), other, emptyList())
        val reply = channelMessage("a2".repeat(32), other, listOf(root))

        assertFalse(NotificationFeedFilter.isNotifiablePublicChatReply(reply, me))
        assertFalse(NotificationFeedFilter.tagsAnEventByUser(reply, me))
    }

    @Test
    fun `unloaded ancestor without an author does not match`() {
        // Parent shell present in cache but its event/author hasn't loaded yet.
        val unloadedParent = Note("a3".repeat(32))
        val reply = channelMessage("a4".repeat(32), other, listOf(unloadedParent))

        assertFalse(NotificationFeedFilter.isNotifiablePublicChatReply(reply, me))
    }

    @Test
    fun `non channel replies are out of scope for the public-chat relaxation`() {
        // A kind-1 reply to my note must NOT be picked up by the public-chat
        // path — the normal p-tag gate governs those.
        val myNote = authoredNote("a5".repeat(32), me)
        val reply =
            Note("a6".repeat(32)).apply {
                event = TextNoteEvent("a6".repeat(32), other, 1000, emptyArray(), "hi", sig)
                author = User(other, noContext)
                replyTo = listOf(myNote)
            }

        assertFalse(NotificationFeedFilter.isNotifiablePublicChatReply(reply, me))
    }
}
