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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.UserContext
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which inner app events become rows in a Marmot group's conversation.
 *
 * The kind:1210 rule is a security boundary, not a display preference. MLS
 * authenticates that a member SENT a payload; it says nothing about whether the
 * payload is TRUE. The reference client draws the same line — its own fuzz
 * target asserts that raw 1210 JSON "must not authenticate a payload actor" and
 * that a parsed payload stays an unauthenticated projection.
 */
class MarmotGroupFeedVisibilityTest {
    private val owner = "a".repeat(64)
    private val peer = "b".repeat(64)
    private val groupId = "c".repeat(64)

    private val context = UserContext { addr -> AddressableNote(addr) }

    private fun note(
        kind: Int,
        pubKey: String,
        id: String = "${kind}0".padEnd(64, 'f'),
        content: String = "",
    ): Note {
        val event = Event(id, pubKey, 1_800_000_000L, kind, emptyArray(), content, "")
        return Note(event.id).also { it.loadEvent(event, User(pubKey, context), emptyList()) }
    }

    private fun list() = MarmotGroupList(owner)

    private fun visibleCount(
        list: MarmotGroupList,
        note: Note,
    ): Int {
        list.addMessage(groupId, note)
        return list.getOrCreateGroup(groupId).messages.size
    }

    @Test
    fun `a chat message is shown`() {
        assertEquals(1, visibleCount(list(), note(9, peer, content = "hello")))
    }

    @Test
    fun `a system row this client derived is shown`() {
        // Derived rows are diffed from MLS-authenticated state and are always
        // authored by the account itself, so authorship is what marks them.
        assertEquals(1, visibleCount(list(), note(1210, owner)))
    }

    @Test
    fun `a system row sent by another member is refused`() {
        // The forgery this blocks: any member can send a well-formed 1210
        // naming someone else as the actor of a removal or a rename, and it
        // would render exactly like a real one in the part of the conversation
        // a reader trusts most.
        assertEquals(0, visibleCount(list(), note(1210, peer)))
    }

    @Test
    fun `the side-channel kinds never become rows`() {
        // Reactions, deletions, edits, stream anchors and push token gossip all
        // reach LocalCache — they drive other UI — but none is a message.
        listOf(5, 7, 1009, 1200, 447, 448, 449).forEach { kind ->
            assertEquals(0, visibleCount(list(), note(kind, peer)), "kind $kind must not render as a row")
        }
    }

    @Test
    fun `authorship is the only thing that admits a system row`() {
        // Not the group, not the arrival path, not the payload's own claims.
        val list = list()
        val forged = note(1210, peer, id = "1".repeat(64), content = """{"v":1,"system_type":"member_removed","data":{"actor":"$owner"}}""")
        list.addMessage(groupId, forged)
        assertTrue(list.getOrCreateGroup(groupId).messages.size == 0, "a payload cannot vouch for itself")
        assertFalse(list.groupIdForNote(forged.idHex) == groupId)
    }

    @Test
    fun `a note with no event never becomes a row`() {
        // Our own push-token answers (448) were indexed with a bare justConsume, which
        // does not dispatch that kind, so their notes stayed eventless — and an eventless
        // note skipped the kind rules above and rendered "Event is loading or can't be found".
        assertEquals(0, visibleCount(list(), Note("d".repeat(64))))
    }

    @Test
    fun `an admin removal that arrives before its target still removes it`() {
        // A catch-up returns newest first, so the kind-4891 can decrypt before the message.
        val list = list()
        val target = note(9, peer, content = "spam")
        list.applyAdminRemoval(groupId, target.idHex, null)
        assertEquals(0, visibleCount(list, target))
    }

    @Test
    fun `an admin removal in one group does not hide the same id in another`() {
        val list = list()
        val target = note(9, peer, content = "hello")
        list.applyAdminRemoval("e".repeat(64), target.idHex, null)
        assertEquals(1, visibleCount(list, target))
    }

    @Test
    fun `an admin removal drops a message already shown`() {
        val list = list()
        val target = note(9, peer, content = "spam")
        assertEquals(1, visibleCount(list, target))
        list.applyAdminRemoval(groupId, target.idHex, target)
        assertEquals(0, list.getOrCreateGroup(groupId).messages.size)
    }
}
