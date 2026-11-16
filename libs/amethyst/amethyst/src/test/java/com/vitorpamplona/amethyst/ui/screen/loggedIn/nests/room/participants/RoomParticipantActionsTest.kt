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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.participants

import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.nests.room.participants.RoomParticipantActions
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomParticipantActionsTest {
    private val host = "a".repeat(64)
    private val alice = "b".repeat(64)
    private val bob = "c".repeat(64)

    private fun room(extra: List<Array<String>> = emptyList()): MeetingSpaceEvent =
        MeetingSpaceEvent(
            id = "0".repeat(64),
            pubKey = host,
            createdAt = 100L,
            tags =
                (
                    listOf(
                        arrayOf("d", "rt-1"),
                        arrayOf("room", "Room"),
                        arrayOf("status", "open"),
                        arrayOf("service", "https://moq"),
                        arrayOf("endpoint", "https://moq"),
                        arrayOf("p", host, "", "host"),
                    ) + extra
                ).toTypedArray(),
            content = "",
            sig = "0".repeat(128),
        )

    @Test
    fun promoteAddsParticipantWithSpeakerRoleWhenAbsent() {
        val original = room()
        val template = RoomParticipantActions.setRole(original, alice, ROLE.SPEAKER)
        assertNotNull(template)
        val pTags = template!!.tags.filter { it.firstOrNull() == "p" }
        // Host plus the new speaker — exactly one entry per pubkey.
        assertEquals(2, pTags.size)
        val aliceTag = pTags.firstOrNull { it.getOrNull(1) == alice }
        assertNotNull(aliceTag)
        assertEquals(ROLE.SPEAKER.code, aliceTag!!.getOrNull(3))
    }

    @Test
    fun promoteUpdatesExistingRoleInPlace() {
        val original = room(listOf(arrayOf("p", alice, "", ROLE.PARTICIPANT.code)))
        val template = RoomParticipantActions.setRole(original, alice, ROLE.SPEAKER)
        assertNotNull(template)
        val pTags = template!!.tags.filter { it.firstOrNull() == "p" }
        // Still 2 rows — alice's row is updated, not duplicated.
        assertEquals(2, pTags.size)
        val aliceTag = pTags.first { it.getOrNull(1) == alice }
        assertEquals(ROLE.SPEAKER.code, aliceTag.getOrNull(3))
    }

    @Test
    fun cannotDemoteHost() {
        val original = room()
        // Trying to flip the host to PARTICIPANT must fail — there's
        // exactly one host per audio room, and demoting them produces
        // an unauthored event.
        assertNull(RoomParticipantActions.setRole(original, host, ROLE.PARTICIPANT))
        assertNull(RoomParticipantActions.demoteToListener(original, host))
    }

    @Test
    fun demoteFlipsSpeakerToParticipant() {
        val original =
            room(
                listOf(
                    arrayOf("p", alice, "", ROLE.SPEAKER.code),
                    arrayOf("p", bob, "", ROLE.MODERATOR.code),
                ),
            )
        val template = RoomParticipantActions.demoteToListener(original, alice)
        assertNotNull(template)
        val aliceTag = template!!.tags.first { it.firstOrNull() == "p" && it.getOrNull(1) == alice }
        assertEquals(ROLE.PARTICIPANT.code, aliceTag.getOrNull(3))
        // Other roles are untouched.
        val bobTag = template.tags.first { it.firstOrNull() == "p" && it.getOrNull(1) == bob }
        assertEquals(ROLE.MODERATOR.code, bobTag.getOrNull(3))
    }

    @Test
    fun demotingAbsentTargetIsNoOp() {
        // Audit #1: demote-to-listener used to fall through to setRole()
        // which adds an absent target as PARTICIPANT — pinning a
        // pure-audience listener to the participant list. After the
        // fix, the call returns null instead.
        val original = room()
        assertNull(RoomParticipantActions.demoteToListener(original, alice))
    }

    @Test
    fun demotingExistingParticipantIsNoOp() {
        // Already a listener (PARTICIPANT role) — re-publishing with
        // the same role is wasted relay traffic. Return null.
        val original = room(listOf(arrayOf("p", alice, "", ROLE.PARTICIPANT.code)))
        assertNull(RoomParticipantActions.demoteToListener(original, alice))
    }

    @Test
    fun republishKeepsSameDTag() {
        val original = room()
        val template = RoomParticipantActions.setRole(original, alice, ROLE.SPEAKER)!!
        val dTag = template.tags.first { it.firstOrNull() == "d" }.getOrNull(1)
        assertEquals(original.dTag(), dTag)
    }

    @Test
    fun promotingNewSpeakerLeavesOtherSpeakersAlone() {
        val original = room(listOf(arrayOf("p", bob, "", ROLE.SPEAKER.code)))
        val template = RoomParticipantActions.setRole(original, alice, ROLE.SPEAKER)!!
        val pTags = template.tags.filter { it.firstOrNull() == "p" }
        assertEquals(3, pTags.size)
        assertTrue(pTags.any { it.getOrNull(1) == host })
        assertTrue(pTags.any { it.getOrNull(1) == alice })
        assertTrue(pTags.any { it.getOrNull(1) == bob })
    }
}
