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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression guard for the room "Known vs New Request" classification.
 *
 * A chatroom is "Known" when a message sender is someone you follow
 * ([Chatroom.senderIntersects]) OR you have sent to it. The follow-based path
 * depends on [Chatroom.activeSenders] being populated as messages arrive. A
 * dropped-assignment bug (`activeSenders + author` instead of
 * `activeSenders = activeSenders + author`) left that set permanently empty, so
 * every incoming DM from a followed contact was misclassified into New Requests
 * and Known sat on the "Loading Feed" spinner until the much-slower history page
 * exhausted.
 */
class ChatroomSenderIntersectsTest {
    private val noCtx = UserContext { Note("addr") }

    private fun noteFrom(author: User): Note = Note(author.pubkeyHex + "-msg").apply { this.author = author }

    @Test
    fun senderIsTrackedAndIntersectsFollows() {
        val alice = User("a".repeat(64), noCtx)
        val room = Chatroom()

        room.addMessageSync(noteFrom(alice))

        assertTrue(room.senderIntersects(setOf(alice.pubkeyHex)), "a followed sender must make the room intersect the follow set")
    }

    @Test
    fun unrelatedSenderDoesNotIntersect() {
        val alice = User("a".repeat(64), noCtx)
        val bob = User("b".repeat(64), noCtx)
        val room = Chatroom()

        room.addMessageSync(noteFrom(bob))

        assertFalse(room.senderIntersects(setOf(alice.pubkeyHex)), "a non-followed sender must not intersect the follow set")
    }

    @Test
    fun multipleSendersAreAllTracked() {
        val alice = User("a".repeat(64), noCtx)
        val bob = User("b".repeat(64), noCtx)
        val room = Chatroom()

        room.addMessageSync(noteFrom(alice))
        room.addMessageSync(noteFrom(bob))

        assertTrue(room.senderIntersects(setOf(bob.pubkeyHex)))
        assertTrue(room.senderIntersects(setOf(alice.pubkeyHex)))
    }
}
