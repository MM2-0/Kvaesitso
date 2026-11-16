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
package com.vitorpamplona.amethyst.commons.viewmodels

import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParticipantGridTest {
    private val host = "a".repeat(64)
    private val speaker = "b".repeat(64)
    private val listener = "c".repeat(64)

    private fun pTag(
        pk: String,
        role: ROLE,
    ) = ParticipantTag(pubKey = pk, relayHint = null, role = role.code, proof = null)

    private fun presence(
        pk: String,
        ts: Long,
        onstage: Boolean = true,
        handRaised: Boolean = false,
        publishing: Boolean = false,
        muted: Boolean? = null,
    ) = RoomPresence(
        pubkey = pk,
        handRaised = handRaised,
        muted = muted,
        publishing = publishing,
        onstage = onstage,
        updatedAtSec = ts,
    )

    @Test
    fun hostAndSpeakerLandOnStageWhenPresenceMatches() {
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST), pTag(speaker, ROLE.SPEAKER)),
                presences = mapOf(host to presence(host, 100L), speaker to presence(speaker, 100L)),
            )
        assertEquals(2, grid.onStage.size)
        assertEquals(0, grid.audience.size)
        assertEquals(setOf(host, speaker), grid.onStage.map { it.pubkey }.toSet())
    }

    @Test
    fun speakerWithOnstageFalseDropsToAudience() {
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST), pTag(speaker, ROLE.SPEAKER)),
                presences =
                    mapOf(
                        host to presence(host, 100L),
                        speaker to presence(speaker, 100L, onstage = false),
                    ),
            )
        assertEquals(setOf(host), grid.onStage.map { it.pubkey }.toSet())
        assertEquals(setOf(speaker), grid.audience.map { it.pubkey }.toSet())
    }

    @Test
    fun pureAudienceMembersAppearInAudience() {
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST)),
                presences = mapOf(host to presence(host, 100L), listener to presence(listener, 100L)),
            )
        // host on stage; the kind-10312-only listener in audience.
        assertEquals(setOf(host), grid.onStage.map { it.pubkey }.toSet())
        assertEquals(setOf(listener), grid.audience.map { it.pubkey }.toSet())
        // The audience member has no role.
        assertEquals(null, grid.audience.first().role)
    }

    @Test
    fun absentParticipantTagIsAbsentTrueOnAudienceRow() {
        // Speaker whose presence we've never seen — kind-30312 says
        // they're a member, but they haven't joined yet. nostrnests
        // greys them out; we mark absent=true so the renderer can.
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST), pTag(speaker, ROLE.SPEAKER)),
                presences = mapOf(host to presence(host, 100L)),
            )
        // The speaker has no presence — defaults onstage=true, so
        // they DO appear on stage, marked absent.
        val onStageSpeaker = grid.onStage.first { it.pubkey == speaker }
        assertTrue(onStageSpeaker.absent, "speaker without presence should be marked absent")
    }

    @Test
    fun emptyInputProducesEmptyGrid() {
        val grid = buildParticipantGrid(emptyList(), emptyMap())
        assertEquals(0, grid.onStage.size)
        assertEquals(0, grid.audience.size)
    }

    @Test
    fun implicitHostNotInPTagsLandsOnStage() {
        // nostrnests publishes kind-30312 with no self-`p`-tag for
        // the room author — the author is the implicit host. Without
        // synthesizing them, they fell through into the pure-audience
        // branch (role=null) AND were left out of the audio
        // subscription set, producing "host shown in audience + no
        // sound" — the exact regression this case exists to catch.
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(speaker, ROLE.SPEAKER)),
                presences = emptyMap(),
                hostPubkey = host,
            )
        val hostRow = grid.onStage.firstOrNull { it.pubkey == host }
        assertEquals(ROLE.HOST, hostRow?.role)
        // No presence yet — host shows up greyed-out, matching how
        // p-tagged speakers without presence already render.
        assertEquals(true, hostRow?.absent)
        assertTrue(grid.audience.none { it.pubkey == host })
    }

    @Test
    fun implicitHostWithOnstageTrueStaysOnStage() {
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(speaker, ROLE.SPEAKER)),
                presences = mapOf(host to presence(host, 100L, onstage = true)),
                hostPubkey = host,
            )
        val hostRow = grid.onStage.firstOrNull { it.pubkey == host }
        assertEquals(ROLE.HOST, hostRow?.role)
        assertEquals(false, hostRow?.absent)
    }

    @Test
    fun implicitHostLeavesStageDropsToAudienceKeepingHostRole() {
        // The host taps "Leave Stage" on nostrnests → kind-10312 with
        // onstage=0. They should drop to audience but the host crown
        // stays — exact same behaviour as an explicit speaker stepping
        // off the stage.
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(speaker, ROLE.SPEAKER)),
                presences = mapOf(host to presence(host, 100L, onstage = false)),
                hostPubkey = host,
            )
        assertTrue(grid.onStage.none { it.pubkey == host })
        val hostRow = grid.audience.firstOrNull { it.pubkey == host }
        assertEquals(ROLE.HOST, hostRow?.role)
    }

    @Test
    fun stalePresenceWithOnstageFalseIsIgnoredAfterRoleGrant() {
        // Audience-promoted-to-speaker scenario: presence event was
        // emitted at t=100 with onstage=false (their pre-promotion
        // audience-mode kind-10312). The host then promotes them at
        // t=200 (the room's new createdAt). Without the staleness
        // gate, this lands the speaker in the audience tab even
        // though their role just changed — the bug reported on
        // 2026-05-13. With roleGrantSec=200, the t=100 presence is
        // stale and the role tag wins → speaker on stage.
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST), pTag(speaker, ROLE.SPEAKER)),
                presences =
                    mapOf(
                        host to presence(host, 200L),
                        speaker to presence(speaker, 100L, onstage = false),
                    ),
                roleGrantSec = 200L,
            )
        assertEquals(setOf(host, speaker), grid.onStage.map { it.pubkey }.toSet())
        assertEquals(0, grid.audience.size)
    }

    @Test
    fun freshOnstageFalseAfterRoleGrantStillDropsToAudience() {
        // Inverse: speaker emits onstage=0 AFTER the role grant
        // (deliberate "stepped off stage" while keeping the role).
        // Should still drop to audience — the existing leave-stage
        // contract.
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST), pTag(speaker, ROLE.SPEAKER)),
                presences =
                    mapOf(
                        host to presence(host, 200L),
                        speaker to presence(speaker, 300L, onstage = false),
                    ),
                roleGrantSec = 200L,
            )
        assertEquals(setOf(host), grid.onStage.map { it.pubkey }.toSet())
        assertEquals(setOf(speaker), grid.audience.map { it.pubkey }.toSet())
    }

    @Test
    fun explicitHostInPTagsIsNotDuplicated() {
        // The author already tagged themselves with role=host. The
        // synthesized entry must NOT shadow / duplicate them.
        val grid =
            buildParticipantGrid(
                participants = listOf(pTag(host, ROLE.HOST), pTag(speaker, ROLE.SPEAKER)),
                presences = mapOf(host to presence(host, 100L)),
                hostPubkey = host,
            )
        assertEquals(2, grid.onStage.size)
        assertEquals(1, grid.onStage.count { it.pubkey == host })
    }
}
