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

import com.vitorpamplona.cordn.groups.CordnCredential
import com.vitorpamplona.cordn.groups.CordnGroupPolicy
import com.vitorpamplona.cordn.spec01GroupMetadata.CordnGroupMetadata
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageReferences
import com.vitorpamplona.mls.group.MlsGroup
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The room a cordn screen renders.
 *
 * The rules under test are the ones that fail quietly. A room that keys on the
 * cursor duplicates history the moment a client re-syncs; a room that sorts on
 * one clock alone either lets a coordinator reorder the past or lets one bad
 * device clock scatter its owner's messages through it. Neither throws.
 */
class CordnGroupChatroomTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val coordinator = "c".repeat(64)

    private fun room() = CordnGroupChatroom("gid-1", coordinator)

    /** The target an annotation points at: id, its author, and its kind. */
    private fun target(
        id: String,
        author: HexKey,
        kind: Int = CordnMessageKinds.TEXT,
    ) = CordnMessageReferences.Target(id = id.padEnd(64, '0'), pubKey = author, kind = kind)

    private fun message(
        id: String,
        at: Long,
        cursor: Long,
        author: HexKey = alice,
        content: String = "msg-$id",
        kind: Int = CordnMessageKinds.TEXT,
        tags: Array<Array<String>> = emptyArray(),
    ) = CordnDeliveredMessage(
        CordnEnvelope(id = id.padEnd(64, '0'), pubKey = author, createdAt = at, kind = kind, tags = tags, content = content),
        cursor,
    )

    @Test
    fun `messages are keyed by envelope id, so a re-sync does not duplicate them`() {
        // spec/02.md §7: the envelope id is the identity, the cursor is a
        // delivery primitive. A catch_up after a restart re-walks the stream
        // and hands back messages we already have — with a NEW cursor, because
        // a cursor is coordinator-local. Keying on it would double the room
        // every time it recovers, which is exactly when it must not.
        val room = room()
        assertTrue(room.add(message("1", at = 10, cursor = 1)))

        assertFalse(room.add(message("1", at = 10, cursor = 99)), "same message, different cursor")

        assertEquals(1, room.messages.value.size)
    }

    @Test
    fun `messages are ordered as the coordinator delivered them`() {
        val room = room()
        room.add(message("3", at = 30, cursor = 3))
        room.add(message("1", at = 10, cursor = 1))
        room.add(message("2", at = 20, cursor = 2))

        assertEquals(listOf("msg-1", "msg-2", "msg-3"), room.messages.value.map { it.envelope.content })
    }

    @Test
    fun `a sender's clock cannot move their message in the room`() {
        // The rule the ordering exists for, and the only test that can tell
        // the two orders apart — everywhere else the cursor and created_at
        // agree. A device with a wrong clock, or a member who simply sets
        // whatever they like, must not be able to reorder the room: sorting on
        // created_at would let this message sit at the top of the history
        // forever, above things said long before it.
        val room = room()
        room.add(message("1", at = 1_000, cursor = 1, content = "said first"))
        room.add(message("2", at = 5, cursor = 2, content = "backdated to 1970"))

        assertEquals(
            listOf("said first", "backdated to 1970"),
            room.messages.value.map { it.envelope.content },
            "a backdated message jumped the queue",
        )
    }

    @Test
    fun `annotations are folded, not rendered as messages`() {
        // A reaction is not a line in the chat. Showing one would put a bare
        // "+" in the transcript and, worse, make it the room's preview.
        val room = room()
        room.add(message("1", at = 10, cursor = 1, content = "hello"))
        room.add(
            message(
                "2",
                at = 11,
                cursor = 2,
                author = bob,
                kind = CordnMessageKinds.REACTION,
                content = "+",
                tags = CordnMessageReferences.reactionTags(target("1", alice)),
            ),
        )

        assertEquals(listOf("hello"), room.messages.value.map { it.envelope.content })
        assertEquals(
            "hello",
            room.newest.value
                ?.envelope
                ?.content,
            "a reaction must not become the preview",
        )

        val reacted = room.annotations.value.reactions["1".padEnd(64, '0')]
        assertEquals(setOf(bob), reacted?.get("+"), "the reaction still has to land on its target")
    }

    @Test
    fun `an edit changes the rendered text without adding a row`() {
        val room = room()
        room.add(message("1", at = 10, cursor = 1, content = "typo"))
        room.add(
            message(
                "2",
                at = 11,
                cursor = 2,
                content = "fixed",
                kind = CordnMessageKinds.EDIT,
                tags = CordnMessageReferences.editTags(target("1", alice)),
            ),
        )

        assertEquals(1, room.messages.value.size)
        assertEquals("fixed", room.annotations.value.contentOf("1".padEnd(64, '0')))
    }

    @Test
    fun `the newest message drives the inbox preview`() {
        val room = room()
        room.add(message("1", at = 10, cursor = 1, content = "older"))
        room.add(message("2", at = 20, cursor = 2, content = "newer"))

        assertEquals(
            "newer",
            room.newest.value
                ?.envelope
                ?.content,
        )
    }

    @Test
    fun `an empty room previews nothing rather than failing`() {
        val room = room()
        assertNull(room.newest.value)
        assertTrue(room.messages.value.isEmpty())
    }

    @Test
    fun `addAll reports how many were actually new`() {
        // The count feeds unread badges, so counting a re-delivery would make
        // a recovering room look like it had new traffic.
        val room = room()
        room.add(message("1", at = 10, cursor = 1))

        val added = room.addAll(listOf(message("1", at = 10, cursor = 50), message("2", at = 20, cursor = 51)))

        assertEquals(1, added)
        assertEquals(2, room.messages.value.size)
    }

    @Test
    fun `a new room knows nothing until it is pointed at an MLS group`() {
        val room = room()

        assertNull(room.name.value)
        assertNull(room.description.value)
        assertEquals(emptyList(), room.members.value)
        assertEquals(0L, room.epoch.value)
    }

    @Test
    fun `refreshFrom reads the name, description and admins out of the metadata extension`() {
        val room = room()
        val metadata = CordnGroupMetadata(name = "Stage B", description = "the visible half", adminPubkeys = listOf(alice))

        room.refreshFrom(groupOf(alice, metadata))

        assertEquals("Stage B", room.name.value)
        assertEquals("the visible half", room.description.value)
        assertEquals(listOf(alice), room.adminPubkeys.value)
    }

    @Test
    fun `refreshFrom reports the creator as a member by account pubkey, not by hex-of-hex`() {
        val room = room()

        room.refreshFrom(groupOf(alice, CordnGroupMetadata(name = "Stage B")))

        // The trap MlsGroup.memberIdentityHex falls into: a cordn credential is
        // already 64 chars of hex, so hex-encoding it again gives 128.
        assertEquals(listOf(alice), room.members.value)
    }

    @Test
    fun `a group carrying no metadata extension leaves the room unnamed rather than mis-named`() {
        val room = room()
        room.refreshFrom(groupOf(alice, CordnGroupMetadata(name = "Stage B")))

        room.refreshFrom(MlsGroup.create(CordnCredential.of(bob).identity, policy = CordnGroupPolicy))

        // Not "Stage B" left over: the fields are derived from whatever group
        // they were last pointed at, never accumulated across groups.
        assertNull(room.name.value)
        assertEquals(emptyList(), room.adminPubkeys.value)
    }

    @Test
    fun `an empty admin list stays empty, because egalitarian is a choice and not a gap`() {
        val room = room()

        room.refreshFrom(groupOf(alice, CordnGroupMetadata(name = "Flat", adminPubkeys = emptyList())))

        assertTrue(room.adminPubkeys.value.isEmpty())
    }

    @Test
    fun `forgetting a coordinator drops its rooms and keeps everyone else's`() {
        // CordnRuntime.purge deletes one coordinator's files; the in-memory
        // list has to lose exactly the same rooms. A gid is unique only within
        // a coordinator, so two of them can hold the same gid as unrelated
        // groups -- dropping by gid would take a stranger's room with it.
        val list = CordnGroupList()
        val other = "d".repeat(64)
        list.getOrCreate(coordinator, "shared-gid")
        list.getOrCreate(other, "shared-gid")
        list.getOrCreate(other, "another")

        list.forgetCoordinator(coordinator)

        assertNull(list.get(coordinator, "shared-gid"))
        assertEquals(2, list.all.value.size)
        assertEquals(
            setOf(other),
            list.all.value
                .map { it.coordinatorPubKey }
                .toSet(),
        )
    }

    @Test
    fun `unread counts what other people said, past the read position`() {
        val room = CordnGroupChatroom("gid-1", coordinator, accountPubKey = alice)
        room.addAll(
            listOf(
                message("01", at = 10, cursor = 1, author = bob),
                message("02", at = 11, cursor = 2, author = bob),
            ),
        )
        assertEquals(2, room.unreadCount.value)

        room.markRead()
        assertEquals(0, room.unreadCount.value)

        room.add(message("03", at = 12, cursor = 3, author = bob))
        assertEquals(1, room.unreadCount.value)
    }

    @Test
    fun `your own messages are not unread, however they come back`() {
        // A message echoes back from the coordinator with a cursor past the
        // read position, exactly like someone else's. Counting it would badge
        // every room the moment its owner spoke in it.
        val room = CordnGroupChatroom("gid-1", coordinator, accountPubKey = alice)
        room.add(message("01", at = 10, cursor = 1, author = alice))

        assertEquals(0, room.unreadCount.value)
    }

    @Test
    fun `a reaction to something already read is not an unread message`() {
        val room = CordnGroupChatroom("gid-1", coordinator, accountPubKey = alice)
        room.add(message("01", at = 10, cursor = 1, author = bob))
        room.markRead()

        room.add(message("02", at = 11, cursor = 2, author = bob, content = "\uD83D\uDC4D", kind = CordnMessageKinds.REACTION, tags = arrayOf(arrayOf("e", "01".padEnd(64, '0')))))

        // Badging a room for a thumbs-up trains people to ignore the badge.
        assertEquals(0, room.unreadCount.value)
    }

    @Test
    fun `a restored read position is honoured before anything new arrives`() {
        val room = CordnGroupChatroom("gid-1", coordinator, accountPubKey = alice)
        room.addAll(
            listOf(
                message("01", at = 10, cursor = 1, author = bob),
                message("02", at = 11, cursor = 2, author = bob),
            ),
        )
        room.restoreState(draft = "half typed", lastReadCursor = 1)

        assertEquals("half typed", room.draft.value)
        assertEquals(1, room.unreadCount.value, "only the message past the saved cursor")
    }

    /** A real cordn group, created the way [CordnGroupManager.createGroup] does. */
    private fun groupOf(
        creator: HexKey,
        metadata: CordnGroupMetadata,
    ) = MlsGroup.create(
        identity = CordnCredential.of(creator).identity,
        policy = CordnGroupPolicy,
        initialExtensions = listOf(metadata.toExtension()),
        groupId = "gid-1".encodeToByteArray(),
    )
}
