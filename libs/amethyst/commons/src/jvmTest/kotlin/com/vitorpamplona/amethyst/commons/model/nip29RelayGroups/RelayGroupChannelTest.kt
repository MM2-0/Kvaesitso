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
package com.vitorpamplona.amethyst.commons.model.nip29RelayGroups

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzCommunityMembership
import com.vitorpamplona.amethyst.commons.model.buzz.BuzzRelayDialect
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupPinnedEvent
import com.vitorpamplona.quartz.nip29RelayGroups.tags.EventPin
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Client-side derivations in [RelayGroupChannel] — the relay-signed roster is the
 * source of truth, so this exercises how the channel folds 39000/39001/39002 into
 * a membership answer and a member count, and that the `createdAt` supersede
 * guards drop stale events. Protocol parsing itself is covered by Quartz's
 * Nip29ArmadaInteropTest; here we test the fold, not the parse.
 */
class RelayGroupChannelTest {
    private val relaySelf = "aa".repeat(32)
    private val alice = "bb".repeat(32)
    private val bob = "cc".repeat(32)
    private val carol = "dd".repeat(32)
    private val gid = "0123456789abcdef"
    private val relay = RelayUrlNormalizer.normalize("wss://relay.example.com")

    private fun channel() = RelayGroupChannel(GroupId(gid, relay))

    private fun metadata(
        createdAt: Long,
        flags: List<String> = emptyList(),
        name: String? = null,
    ): GroupMetadataEvent {
        val tags =
            buildList {
                add(arrayOf("d", gid))
                name?.let { add(arrayOf("name", it)) }
                flags.forEach { add(arrayOf(it)) }
            }.toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, createdAt, GroupMetadataEvent.KIND, tags, "", "22".repeat(64)) as GroupMetadataEvent
    }

    private fun members(
        createdAt: Long,
        vararg pubkeys: String,
    ): GroupMembersEvent {
        val tags = (listOf(arrayOf("d", gid)) + pubkeys.map { arrayOf("p", it) }).toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, createdAt, GroupMembersEvent.KIND, tags, "", "22".repeat(64)) as GroupMembersEvent
    }

    private fun admins(
        createdAt: Long,
        vararg entries: Pair<String, List<String>>,
    ): GroupAdminsEvent {
        val tags = (listOf(arrayOf("d", gid)) + entries.map { (pk, roles) -> arrayOf("p", pk, *roles.toTypedArray()) }).toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, createdAt, GroupAdminsEvent.KIND, tags, "", "22".repeat(64)) as GroupAdminsEvent
    }

    private val msg1 = "11".repeat(32)
    private val msg2 = "22".repeat(32)
    private val msg3 = "33".repeat(32)

    private fun pinned(
        createdAt: Long,
        vararg eventIds: String,
    ): GroupPinnedEvent {
        val tags = (listOf(arrayOf("d", gid)) + eventIds.map { arrayOf("e", it) }).toTypedArray()
        return EventFactory.create("00".repeat(32), relaySelf, createdAt, GroupPinnedEvent.KIND, tags, "", "22".repeat(64)) as GroupPinnedEvent
    }

    @Test
    fun pinnedListFoldsInOrderAndReportsMembership() {
        val c = channel()
        assertTrue(c.pinnedEventIds.isEmpty())
        assertFalse(c.isPinned(msg1))

        c.updatePinned(pinned(100, msg1, msg2))

        assertEquals(listOf(msg1, msg2), c.pinnedEventIds)
        assertTrue(c.isPinned(msg1))
        assertTrue(c.isPinned(msg2))
        assertFalse(c.isPinned(msg3))
    }

    @Test
    fun pinListIsFullyReplacedByNewerEvent() {
        val c = channel()
        c.updatePinned(pinned(100, msg1, msg2))
        // A newer list drops msg1, keeps msg2, adds msg3 — the whole set is replaced.
        c.updatePinned(pinned(200, msg2, msg3))

        assertEquals(listOf(msg2, msg3), c.pinnedEventIds)
        assertFalse(c.isPinned(msg1))
        assertTrue(c.isPinned(msg3))
    }

    @Test
    fun clearingPinsWithEmptyNewerList() {
        val c = channel()
        c.updatePinned(pinned(100, msg1))
        c.updatePinned(pinned(200))
        assertTrue(c.pinnedEventIds.isEmpty())
        assertFalse(c.isPinned(msg1))
    }

    @Test
    fun stalePinEventDoesNotSupersede() {
        val c = channel()
        c.updatePinned(pinned(200, msg1, msg2))
        // An OLDER pin list arrives late — ignore it.
        c.updatePinned(pinned(100, msg3))
        assertEquals(listOf(msg1, msg2), c.pinnedEventIds)
    }

    @Test
    fun equalCreatedAtPinDoesNotResupersede() {
        val c = channel()
        c.updatePinned(pinned(100, msg1, msg2))
        c.updatePinned(pinned(100, msg3))
        assertEquals(listOf(msg1, msg2), c.pinnedEventIds)
    }

    @Test
    fun placeholderNoteCarriesChannelGathererAndIsStable() {
        val c = channel()
        val note = c.placeholderNote()

        // Stable, group-derived id and — crucially — the channel is registered as a gatherer so the
        // Messages row renderer (ChatroomHeaderCompose) resolves the event-less note back to the
        // group instead of blanking it.
        assertEquals("relaygroup-empty-${c.groupId.toKey()}", note.idHex)
        assertTrue(note.inGatherers?.any { it === c } == true)
        // Cached: same instance on repeat so equality-based feed diffing keeps one stable row.
        assertTrue(c.placeholderNote() === note)
    }

    @Test
    fun membershipFromRolesAndMembers() {
        val c = channel()
        c.updateAdmins(admins(100, alice to listOf("admin"), bob to listOf("moderator")))
        c.updateMembers(members(100, alice, bob, carol))

        assertEquals(RelayGroupMembership.ADMIN, c.membershipOf(alice))
        assertEquals(RelayGroupMembership.MODERATOR, c.membershipOf(bob))
        // carol is only in the members list — a plain member.
        assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(carol))
        // unknown key.
        assertEquals(RelayGroupMembership.NONE, c.membershipOf("ee".repeat(32)))
    }

    @Test
    fun adminInAdminsListButAbsentFromMembersStillCounts() {
        // Relays commonly list admins in 39001 only, not 39002. The admin must
        // still resolve as a member, not NONE.
        val c = channel()
        c.updateAdmins(admins(100, alice to listOf("admin")))
        c.updateMembers(members(100, bob))

        assertEquals(RelayGroupMembership.ADMIN, c.membershipOf(alice))
        assertTrue(c.membershipOf(alice).isMember())
    }

    @Test
    fun adminWithUnknownRoleStillModerates() {
        // Presence in the 39001 admins list is the moderation signal; an
        // unrecognized (or empty) role label must not demote to plain MEMBER.
        val c = channel()
        c.updateAdmins(admins(100, alice to listOf("ceo"), bob to emptyList()))
        assertEquals(RelayGroupMembership.MODERATOR, c.membershipOf(alice))
        assertEquals(RelayGroupMembership.MODERATOR, c.membershipOf(bob))
        assertTrue(c.membershipOf(alice).canModerate())
    }

    @Test
    fun equalCreatedAtDoesNotResupersede() {
        val c = channel()
        c.updateMembers(members(100, alice, bob))
        // A second 39002 with the SAME createdAt but fewer members must not win.
        c.updateMembers(members(100, alice))
        assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(bob))
        assertEquals(2, c.memberCount())
    }

    @Test
    fun memberCountDedupesAdminsAndMembers() {
        val c = channel()
        // alice appears in BOTH lists; she must be counted once.
        c.updateAdmins(admins(100, alice to listOf("admin")))
        c.updateMembers(members(100, alice, bob, carol))
        assertEquals(3, c.memberCount())
    }

    @Test
    fun staleMembersEventDoesNotSupersede() {
        val c = channel()
        c.updateMembers(members(200, alice, bob))
        // An OLDER roster arrives late (out-of-order relay delivery) — ignore it.
        c.updateMembers(members(100, alice))
        assertEquals(2, c.memberCount())
        assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(bob))
    }

    @Test
    fun newerMembersEventReplaces() {
        val c = channel()
        c.updateMembers(members(100, alice))
        c.updateMembers(members(200, alice, bob))
        assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(bob))
        assertEquals(2, c.memberCount())
    }

    @Test
    fun staleMetadataDoesNotSupersedeFlagsOrName() {
        val c = channel()
        c.updateGroupInfo(metadata(200, flags = listOf("private", "closed"), name = "Newer"))
        c.updateGroupInfo(metadata(100, flags = emptyList(), name = "Older"))

        assertEquals("Newer", c.toBestDisplayName())
        assertTrue(c.isPrivate())
        assertTrue(c.isClosed())
    }

    @Test
    fun flagsReflectLatestMetadata() {
        val c = channel()
        assertFalse(c.isPrivate())
        assertFalse(c.isClosed())
        c.updateGroupInfo(metadata(100, flags = listOf("private")))
        assertTrue(c.isPrivate())
        assertFalse(c.isClosed())
    }

    @Test
    fun displayNameFallsBackToGroupIdWithoutMetadata() {
        assertEquals(gid, channel().toBestDisplayName())
    }

    @Test
    fun standardNip29RelayAlwaysRequiresMembershipToPost() {
        // A non-Buzz relay: NIP-29 requires membership to post to EVERY group (open only means a
        // 9021 join is auto-approved), so a non-member can't post even on an open/public channel.
        BuzzRelayDialect.clearForTesting()
        val c = channel()
        c.updateGroupInfo(metadata(100, flags = emptyList())) // open (no "private" tag)
        c.updateMembers(members(100, alice))

        assertTrue(c.requiresMembershipToPost())
        assertTrue(c.canPost(alice)) // member
        assertFalse(c.canPost(bob)) // non-member is blocked on standard NIP-29
    }

    @Test
    fun buzzOpenChannelLetsAnyMemberPostWithoutJoining() {
        // Buzz relaxes NIP-29: an open (non-"private") channel accepts writes from any authenticated
        // relay member without a per-channel join. Buzz stamps "closed" on every channel, so that flag
        // must NOT gate posting — only "private" does.
        BuzzRelayDialect.clearForTesting()
        BuzzRelayDialect.mark(relay)
        try {
            val c = channel()
            c.updateGroupInfo(metadata(100, flags = listOf("closed"))) // open + Buzz's always-on "closed"
            c.updateMembers(members(100, alice))

            assertFalse(c.requiresMembershipToPost())
            assertTrue(c.canPost(alice)) // roster member
            assertTrue(c.canPost(bob)) // NOT in the roster, but the open Buzz channel still accepts writes
        } finally {
            BuzzRelayDialect.clearForTesting()
        }
    }

    @Test
    fun buzzPrivateChannelStillGatesPostingOnRoster() {
        BuzzRelayDialect.clearForTesting()
        BuzzCommunityMembership.clearForTesting()
        BuzzRelayDialect.mark(relay)
        try {
            val c = channel()
            c.updateGroupInfo(metadata(100, flags = listOf("private", "closed")))
            c.updateMembers(members(100, alice))

            assertTrue(c.requiresMembershipToPost())
            assertTrue(c.canPost(alice)) // member
            assertFalse(c.canPost(bob)) // non-member blocked on a private channel
        } finally {
            BuzzRelayDialect.clearForTesting()
            BuzzCommunityMembership.clearForTesting()
        }
    }

    @Test
    fun buzzCommunityMemberCountsEvenWhenAbsentFromChannelRoster() {
        // A Buzz community member (NIP-43 kind 13534 / 8000) can participate in every channel on the
        // relay, even a private one whose per-channel 39002 doesn't list them. membershipOf must fall
        // back to the community roster so they aren't wrongly gated.
        BuzzRelayDialect.clearForTesting()
        BuzzCommunityMembership.clearForTesting()
        BuzzRelayDialect.mark(relay)
        try {
            val c = channel()
            c.updateGroupInfo(metadata(100, flags = listOf("private", "closed")))
            c.updateMembers(members(100, alice)) // channel roster: only alice

            // bob is not in the channel roster → NONE, blocked.
            assertEquals(RelayGroupMembership.NONE, c.membershipOf(bob))
            assertFalse(c.canPost(bob))

            // bob joins the community (snapshot lists alice+bob).
            BuzzCommunityMembership.updateSnapshot(relay, setOf(alice, bob), createdAt = 200)
            assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(bob))
            assertTrue(c.canPost(bob))
            // A per-channel admin still outranks the community fallback.
            c.updateAdmins(admins(100, alice to listOf("admin")))
            assertEquals(RelayGroupMembership.ADMIN, c.membershipOf(alice))

            // 8001 remove delta drops bob again.
            BuzzCommunityMembership.applyDelta(relay, remove = setOf(bob), createdAt = 300)
            assertEquals(RelayGroupMembership.NONE, c.membershipOf(bob))
        } finally {
            BuzzRelayDialect.clearForTesting()
            BuzzCommunityMembership.clearForTesting()
        }
    }

    @Test
    fun buzzCommunityMembershipIgnoresNonBuzzRelaysAndStaleDeltas() {
        BuzzRelayDialect.clearForTesting()
        BuzzCommunityMembership.clearForTesting()
        try {
            // Not a Buzz relay: community roster is irrelevant even if somehow populated.
            BuzzCommunityMembership.updateSnapshot(relay, setOf(carol), createdAt = 100)
            val c = channel()
            assertEquals(RelayGroupMembership.NONE, c.membershipOf(carol))

            // Now mark Buzz: the same roster applies.
            BuzzRelayDialect.mark(relay)
            assertEquals(RelayGroupMembership.MEMBER, c.membershipOf(carol))

            // A stale delta (older created_at) must not resurrect a removed member.
            BuzzCommunityMembership.applyDelta(relay, remove = setOf(carol), createdAt = 200)
            assertEquals(RelayGroupMembership.NONE, c.membershipOf(carol))
            BuzzCommunityMembership.applyDelta(relay, add = setOf(carol), createdAt = 150) // stale
            assertEquals(RelayGroupMembership.NONE, c.membershipOf(carol))
        } finally {
            BuzzRelayDialect.clearForTesting()
            BuzzCommunityMembership.clearForTesting()
        }
    }

    @Test
    fun threadsDedupePublishToFlowAndRemove() {
        val c = channel()
        val t1 = Note("11".repeat(32))
        val t2 = Note("22".repeat(32))

        c.addThread(t1)
        c.addThread(t2)
        c.addThread(t1) // duplicate id — must not double-count

        assertEquals(2, c.threadCount())
        assertEquals(2, c.threads.value.size)
        assertTrue(c.threads.value.any { it.idHex == t1.idHex })

        c.removeThread(t1)
        assertEquals(1, c.threadCount())
        assertTrue(c.threads.value.none { it.idHex == t1.idHex })
    }

    /**
     * NIP-29 #2416: a pin list mixes `e` and `a` entries. Pinning or unpinning one entry must
     * re-submit every OTHER entry intact — another client's `a` pin (with its relay hint) included.
     */
    @Test
    fun pinEditsPreserveOtherClientsAddressPins() {
        val c = channel()
        val addr = "30023:$alice:article"
        val tags = arrayOf(arrayOf("d", gid), arrayOf("e", msg1), arrayOf("a", addr, "wss://hint.example/"), arrayOf("e", msg2))
        c.updatePinned(EventFactory.create("00".repeat(32), relaySelf, 100, GroupPinnedEvent.KIND, tags, "", "22".repeat(64)) as GroupPinnedEvent)

        assertEquals(listOf(msg1, addr, msg2), c.pins.map { it.ref })
        assertEquals(listOf(msg1, msg2), c.pinnedEventIds)
        assertEquals(listOf(addr), c.pinnedAddresses.map { it.toValue() })
        assertTrue(c.isPinned(addr))

        val afterUnpin = c.pinsWithout(msg1)
        assertEquals(listOf(listOf("a", addr, "wss://hint.example/"), listOf("e", msg2)), afterUnpin.map { it.toTagArray().toList() })

        val afterPin = c.pinsWith(EventPin(msg3))
        assertEquals(listOf(msg1, addr, msg2, msg3), afterPin.map { it.ref })
        assertEquals(c.pins, c.pinsWith(EventPin(msg2)), "re-pinning an already pinned id is a no-op")
        assertTrue(c.hasRelaySignedState())
    }
}
