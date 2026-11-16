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
package com.vitorpamplona.amethyst.commons.model.cordnGroups

import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Where a cordn room lands in the Messages list.
 *
 * Both rules here failed silently and in the same direction: the inbox showed
 * every cordn room with the right preview and the right time, at the wrong
 * place in the list. A test that only reads a room is blind to it, because
 * nothing about the room was wrong -- the row it hands the feed was.
 */
class CordnInboxRowTest {
    private val alice = "a".repeat(64)
    private val coordinator = "c".repeat(64)

    private fun message(
        id: String,
        at: Long,
        cursor: Long,
        author: HexKey = alice,
    ) = CordnDeliveredMessage(
        CordnEnvelope(
            id = id.padEnd(64, '0'),
            pubKey = author,
            createdAt = at,
            kind = CordnMessageKinds.TEXT,
            tags = emptyArray(),
            content = "msg-$id",
        ),
        cursor,
    )

    @Test
    fun `the row's time is the newest message's, so the inbox can sort on it`() {
        // The feed sorts on Note.createdAt() and maps null to 0L. A row that
        // returns null is not "undated", it is pinned to the bottom of the
        // Messages list forever, under every DM and every other group chat.
        val room = CordnGroupChatroom("gid-1", coordinator)
        room.add(message("1", at = 1_700_000_000, cursor = 1))

        assertEquals(1_700_000_000L, room.inboxRow().createdAt())
    }

    @Test
    fun `a room with no messages has no time rather than a wrong one`() {
        // 0L would be a claim about when the room last spoke. Null says there
        // is nothing to sort on, which is what a just-joined room means.
        assertNull(CordnGroupChatroom("gid-1", coordinator).inboxRow().createdAt())
    }

    @Test
    fun `the row follows the coordinator's newest, not the last one handed to it`() {
        // A catch_up walks the stream in order, but a live subscription can
        // interleave, and the room orders by cursor rather than arrival. The
        // row has to agree with the room about which message is newest, or the
        // inbox sorts a group by whichever message happened to land last.
        val room = CordnGroupChatroom("gid-1", coordinator)
        room.add(message("2", at = 20, cursor = 2))
        room.add(message("1", at = 10, cursor = 1))

        assertEquals(20L, room.inboxRow().createdAt())
    }

    @Test
    fun `the row is one stable instance, so the feed does not see a new room each rebuild`() {
        val room = CordnGroupChatroom("gid-1", coordinator)

        assertSame(room.inboxRow(), room.inboxRow())
    }

    @Test
    fun `a message for a room the list already holds still tells the inbox to rebuild`() {
        // The half of the bug that no amount of correct sorting fixes: the feed
        // rebuilds off a signal from the list, and the room *set* does not
        // change when a message arrives for a room already in it. Without this
        // the inbox sorted cordn rooms once, at join, and never again.
        val list = CordnGroupList()
        assertTrue(list.add(coordinator, "gid-1", message("1", at = 10, cursor = 1)))

        val roomsBefore = list.all.value
        val revisionBefore = list.revision.value

        assertTrue(list.add(coordinator, "gid-1", message("2", at = 20, cursor = 2)))

        assertSame(roomsBefore, list.all.value, "the room set did not change, which is the trap")
        assertTrue(list.revision.value > revisionBefore, "but the inbox still has to re-sort")
    }

    @Test
    fun `a message put straight onto the room still tells the inbox to rebuild`() {
        // The case that matters most and was missed: sending does not go through
        // CordnGroupList at all -- the chat screen adds the optimistic message to
        // the room directly, and the coordinator's echo is then already held by
        // id so it files as a duplicate. Hanging the signal off the list meant
        // your own messages never re-sorted the inbox, only other people's.
        val list = CordnGroupList()
        val room = list.getOrCreate(coordinator, "gid-1")
        val revisionBefore = list.revision.value

        assertTrue(room.add(message("1", at = 10, cursor = 1)))

        assertTrue(list.revision.value > revisionBefore, "an optimistic send has to re-sort the inbox")
    }

    @Test
    fun `an annotation does not tell the inbox to rebuild`() {
        // A reaction leaves the newest message exactly as it was, so re-sorting
        // for one would be a rebuild per reaction for no visible change.
        val list = CordnGroupList()
        val room = list.getOrCreate(coordinator, "gid-1")
        room.add(message("1", at = 10, cursor = 1))

        val revisionBefore = list.revision.value
        room.add(
            CordnDeliveredMessage(
                CordnEnvelope(
                    id = "2".padEnd(64, '0'),
                    pubKey = alice,
                    createdAt = 20,
                    kind = CordnMessageKinds.REACTION,
                    tags = arrayOf(arrayOf("e", "1".padEnd(64, '0'))),
                    content = "+",
                ),
                2,
            ),
        )

        assertEquals(revisionBefore, list.revision.value)
    }

    @Test
    fun `a re-delivered message does not tell the inbox to rebuild`() {
        // A re-sync re-walks the whole stream. Bumping per echo would rebuild
        // the Messages feed once per message for no visible change.
        val list = CordnGroupList()
        list.add(coordinator, "gid-1", message("1", at = 10, cursor = 1))

        val revisionBefore = list.revision.value
        list.add(coordinator, "gid-1", message("1", at = 10, cursor = 99))

        assertEquals(revisionBefore, list.revision.value)
    }
}
