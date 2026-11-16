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

import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.global.GlobalTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.hashtag.HashtagTopNavPerRelayFilterSet
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilter
import com.vitorpamplona.amethyst.commons.model.topNavFeeds.noteBased.author.AuthorsTopNavPerRelayFilterSet
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupAdminsEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMembersEvent
import com.vitorpamplona.quartz.nip29RelayGroups.metadata.GroupMetadataEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-type discovery match for relay-signed NIP-29 groups. Verifies the three people
 * paths ([relay-key follow / follow-is-admin / follow-is-member]) and the topic/geo tag
 * matches, plus that [toGroupConstraints] resolves each top-nav filter to the right shape.
 */
class RelayGroupDiscoveryConstraintTest {
    private val relay = RelayUrlNormalizer.normalizeOrNull("wss://groups.example/")!!
    private val relayKey = "aa".repeat(32)
    private val alice = "bb".repeat(32)
    private val bob = "cc".repeat(32)
    private val carol = "dd".repeat(32)
    private val sig = "22".repeat(64)

    private fun channel(
        metaTags: Array<Array<String>>,
        adminPubkeys: List<String> = emptyList(),
        memberPubkeys: List<String> = emptyList(),
    ): RelayGroupChannel {
        val gid = "group1"
        val ch = RelayGroupChannel(GroupId(gid, relay))
        ch.updateGroupInfo(
            GroupMetadataEvent("00".repeat(32), relayKey, 1_000L, arrayOf(arrayOf("d", gid), *metaTags), "", sig),
        )
        if (adminPubkeys.isNotEmpty()) {
            ch.updateAdmins(
                GroupAdminsEvent(
                    "11".repeat(32),
                    relayKey,
                    1_000L,
                    arrayOf(arrayOf("d", gid), *adminPubkeys.map { arrayOf("p", it, "admin") }.toTypedArray()),
                    "",
                    sig,
                ),
            )
        }
        if (memberPubkeys.isNotEmpty()) {
            ch.updateMembers(
                GroupMembersEvent(
                    "22".repeat(32),
                    relayKey,
                    1_000L,
                    arrayOf(arrayOf("d", gid), *memberPubkeys.map { arrayOf("p", it) }.toTypedArray()),
                    "",
                    sig,
                ),
            )
        }
        return ch
    }

    @Test
    fun allGroupsMatchesAnyLoadedGroup() {
        assertTrue(GroupDiscoveryConstraint.AllGroups.matches(channel(arrayOf(arrayOf("name", "Any")))))
    }

    @Test
    fun byPeopleMatchesFollowedRelayKey() {
        val c = GroupDiscoveryConstraint.ByPeople(setOf(relayKey))
        assertTrue(c.matches(channel(arrayOf(arrayOf("name", "G")))))
    }

    @Test
    fun byPeopleMatchesFollowedAdminAndMember() {
        val ch = channel(arrayOf(arrayOf("name", "G")), adminPubkeys = listOf(alice), memberPubkeys = listOf(bob))
        assertTrue(GroupDiscoveryConstraint.ByPeople(setOf(alice)).matches(ch))
        assertTrue(GroupDiscoveryConstraint.ByPeople(setOf(bob)).matches(ch))
    }

    @Test
    fun byPeopleRejectsStrangers() {
        val ch = channel(arrayOf(arrayOf("name", "G")), adminPubkeys = listOf(alice), memberPubkeys = listOf(bob))
        assertFalse(GroupDiscoveryConstraint.ByPeople(setOf(carol)).matches(ch))
        assertFalse(GroupDiscoveryConstraint.ByPeople(emptySet()).matches(ch))
    }

    @Test
    fun byHashtagsMatchesCaseInsensitively() {
        val ch = channel(arrayOf(arrayOf("name", "G"), arrayOf("t", "Bitcoin")))
        assertTrue(GroupDiscoveryConstraint.ByHashtags(setOf("bitcoin")).matches(ch))
        assertFalse(GroupDiscoveryConstraint.ByHashtags(setOf("nostr")).matches(ch))
    }

    @Test
    fun byGeohashesMatchesMipMapPrefix() {
        // GroupMetadataEvent.build mip-maps geohashes; here we store the full one and a prefix.
        val ch = channel(arrayOf(arrayOf("name", "G"), arrayOf("g", "u0nd"), arrayOf("g", "u0")))
        assertTrue(GroupDiscoveryConstraint.ByGeohashes(setOf("u0")).matches(ch))
        assertFalse(GroupDiscoveryConstraint.ByGeohashes(setOf("9q")).matches(ch))
    }

    @Test
    fun anyOfMatchesIfEitherLensMatches() {
        val ch = channel(arrayOf(arrayOf("name", "G"), arrayOf("t", "bitcoin")))
        val c =
            GroupDiscoveryConstraint.AnyOf(
                listOf(
                    GroupDiscoveryConstraint.ByPeople(setOf(carol)),
                    GroupDiscoveryConstraint.ByHashtags(setOf("bitcoin")),
                ),
            )
        assertTrue(c.matches(ch))
    }

    @Test
    fun globalFilterResolvesToAllGroupsPerRelay() {
        val set = GlobalTopNavPerRelayFilterSet(mapOf(relay to GlobalTopNavPerRelayFilter))
        val constraints = set.toGroupConstraints()
        assertEquals(GroupDiscoveryConstraint.AllGroups, constraints[relay])
    }

    @Test
    fun authorsFilterResolvesToByPeople() {
        val set = AuthorsTopNavPerRelayFilterSet(mapOf(relay to AuthorsTopNavPerRelayFilter(setOf(alice))))
        val constraints = set.toGroupConstraints()
        assertEquals(GroupDiscoveryConstraint.ByPeople(setOf(alice)), constraints[relay])
    }

    @Test
    fun hashtagFilterResolvesToByHashtags() {
        val set = HashtagTopNavPerRelayFilterSet(mapOf(relay to HashtagTopNavPerRelayFilter(setOf("bitcoin"))))
        val constraints = set.toGroupConstraints()
        assertTrue(constraints[relay] is GroupDiscoveryConstraint.ByHashtags)
    }
}
