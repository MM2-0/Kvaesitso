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
package com.vitorpamplona.amethyst.commons.model.buzz

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuzzChannelInvitesTest {
    private val me = "a".repeat(64)
    private val stranger = "b".repeat(64)
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://buzz.example.team/")!!

    private fun added(
        channelId: String,
        actor: String? = stranger,
        createdAt: Long = 1_000L,
    ) = MembershipNotice("add-" + channelId + "-" + createdAt, channelId, relay, actor, createdAt, removed = false)

    private fun removed(
        channelId: String,
        actor: String? = stranger,
        createdAt: Long = 2_000L,
    ) = MembershipNotice("del-" + channelId + "-" + createdAt, channelId, relay, actor, createdAt, removed = true)

    /** Every channel is a plain named channel unless a test says otherwise. */
    private val allNamed = { _: String, _: NormalizedRelayUrl -> ChannelClassification.NAMED }

    private fun invites(
        notices: List<MembershipNotice>,
        dismissed: Set<String> = emptySet(),
        joined: Set<String> = emptySet(),
        classify: (String, NormalizedRelayUrl) -> ChannelClassification = allNamed,
    ) = BuzzChannelInvites.pendingInvites(me, notices, dismissed, joined, classify)

    @Test
    fun anAddBySomebodyElseIsAnInvite() {
        val result = invites(listOf(added("chan-1")))

        assertEquals(1, result.size)
        assertEquals("chan-1", result[0].channelId)
        assertEquals(stranger, result[0].actor)
        assertEquals(relay, result[0].relay)
        assertEquals(1_000L, result[0].createdAt)
    }

    @Test
    fun aSelfJoinIsNotAnInvite() {
        assertTrue(invites(listOf(added("chan-1", actor = me))).isEmpty())
    }

    @Test
    fun aSelfJoinIsMatchedCaseInsensitively() {
        assertTrue(invites(listOf(added("chan-1", actor = me.uppercase()))).isEmpty())
    }

    @Test
    fun anAddWithNoReadableActorIsStillAnInvite() {
        // The relay body can be missing or malformed. "Somebody put me here and I can't tell who" is
        // still a question for the viewer — the card renders an unknown-actor row for exactly this.
        val result = invites(listOf(added("chan-1", actor = null)))

        assertEquals(1, result.size)
        assertEquals(null, result[0].actor)
    }

    @Test
    fun aRemovalSupersedesAnEarlierAdd() {
        assertTrue(invites(listOf(added("chan-1", createdAt = 1_000L), removed("chan-1", createdAt = 2_000L))).isEmpty())
    }

    @Test
    fun anAddAfterARemovalIsAnInviteAgain() {
        val result =
            invites(
                listOf(
                    added("chan-1", createdAt = 1_000L),
                    removed("chan-1", createdAt = 2_000L),
                    added("chan-1", createdAt = 3_000L),
                ),
            )

        assertEquals(1, result.size)
        assertEquals(3_000L, result[0].createdAt)
    }

    @Test
    fun orderOfArrivalDoesNotChangeTheAnswer() {
        // A re-subscribe replays the relay's whole history, and nothing guarantees the order it comes
        // back in. The projection resolves by created_at, so both orderings agree.
        val chronological = listOf(added("chan-1", createdAt = 1_000L), removed("chan-1", createdAt = 2_000L))

        assertEquals(
            invites(chronological).map { it.channelId },
            invites(chronological.reversed()).map { it.channelId },
        )
    }

    @Test
    fun aTieResolvesToTheRemoval() {
        assertTrue(invites(listOf(added("chan-1", createdAt = 5L), removed("chan-1", createdAt = 5L))).isEmpty())
        assertTrue(invites(listOf(removed("chan-1", createdAt = 5L), added("chan-1", createdAt = 5L))).isEmpty())
    }

    @Test
    fun redeliveringTheSameNoticeIsIdempotent() {
        // The regression this projection exists for: the old registry re-recorded an invite that
        // classification had already withdrawn, so the prompt flickered on every re-delivery.
        val once = invites(listOf(added("chan-1")))
        val twice = invites(listOf(added("chan-1"), added("chan-1")))

        assertEquals(once.map { it.channelId }, twice.map { it.channelId })
        assertEquals(1, twice.size)
    }

    @Test
    fun aDismissedChannelIsWithheld() {
        assertTrue(invites(listOf(added("chan-1")), dismissed = setOf("chan-1")).isEmpty())
    }

    @Test
    fun aJoinedChannelIsWithheld() {
        assertTrue(invites(listOf(added("chan-1")), joined = setOf("chan-1")).isEmpty())
    }

    @Test
    fun aDmIsNeverAnInvite() {
        assertTrue(invites(listOf(added("chan-1"))) { _, _ -> ChannelClassification.DM }.isEmpty())
    }

    @Test
    fun anUnclassifiedChannelIsWithheldRatherThanGuessed() {
        // A DM arrives as the same kind-44100 as a channel add. Surfacing before the kind-39000 lands
        // would flash a "somebody added you to a channel" card for every new DM and then withdraw it.
        assertTrue(invites(listOf(added("chan-1"))) { _, _ -> ChannelClassification.UNKNOWN }.isEmpty())
    }

    @Test
    fun invitesComeBackNewestFirst() {
        val result =
            invites(
                listOf(
                    added("older", createdAt = 1_000L),
                    added("newest", createdAt = 3_000L),
                    added("middle", createdAt = 2_000L),
                ),
            )

        assertEquals(listOf("newest", "middle", "older"), result.map { it.channelId })
    }

    @Test
    fun anInviteCarriesTheEventItCameFrom() {
        // The Notifications feed keys its card on this — it is the identity that lets an invite dedup,
        // scroll-to and page like every other notification row.
        val invite = invites(listOf(added("chan-1", createdAt = 7L))).single()

        assertEquals("add-chan-1-7", invite.eventId)
    }

    @Test
    fun pendingByEventIdIsKeyedForThePerNoteLookup() {
        // `NotificationFeedFilter.acceptableEvent` runs per note, so it needs this as a map rather than
        // a scan: a cached 44100 is a live question exactly when its id is a key here.
        val byId =
            BuzzChannelInvites.pendingInvitesByEventId(
                viewer = me,
                notices = listOf(added("chan-1", createdAt = 7L), added("chan-2", createdAt = 8L)),
                dismissed = setOf("chan-2"),
                joined = emptySet(),
                classify = allNamed,
            )

        assertEquals(setOf("add-chan-1-7"), byId.keys)
        assertEquals("chan-1", byId["add-chan-1-7"]?.channelId)
    }

    @Test
    fun aSupersededAddDropsOutOfThePerNoteLookup() {
        // The 44100 stays in the cache forever, so the accept gate has to answer "no" for one the relay
        // has since withdrawn — otherwise the card would outlive the membership.
        val byId =
            BuzzChannelInvites.pendingInvitesByEventId(
                viewer = me,
                notices = listOf(added("chan-1", createdAt = 1L), removed("chan-1", createdAt = 2L)),
                dismissed = emptySet(),
                joined = emptySet(),
                classify = allNamed,
            )

        assertTrue(byId.isEmpty())
    }

    @Test
    fun currentMembershipsCoverEveryChannelRegardlessOfTypeOrActor() {
        // The directory fetch iterates this, and a channel's type is only knowable once its kind-39000
        // has been fetched BY ID — so the set it works from cannot already be filtered by type.
        val memberships =
            BuzzChannelInvites.currentMemberships(
                listOf(
                    added("named"),
                    added("dm"),
                    added("self-joined", actor = me),
                    added("left", createdAt = 1_000L),
                    removed("left", createdAt = 2_000L),
                ),
            )

        assertEquals(setOf("named", "dm", "self-joined"), memberships.keys)
        assertEquals(relay, memberships["named"])
    }

    @Test
    fun latestPerChannelKeepsChannelsApart() {
        val newest =
            BuzzChannelInvites.latestPerChannel(
                listOf(
                    added("chan-1", createdAt = 1_000L),
                    added("chan-2", createdAt = 500L),
                    removed("chan-1", createdAt = 3_000L),
                ),
            )

        assertEquals(setOf("chan-1", "chan-2"), newest.keys)
        assertTrue(newest["chan-1"]!!.removed)
        assertEquals(500L, newest["chan-2"]!!.createdAt)
    }
}
