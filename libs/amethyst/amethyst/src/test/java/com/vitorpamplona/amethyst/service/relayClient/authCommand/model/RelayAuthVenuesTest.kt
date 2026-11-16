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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthVenues
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEntry
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip29RelayGroups.GroupId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The joined-room sources behind the "…it's my relay, or a room I joined" auto-login toggle. */
class RelayAuthVenuesTest {
    private val groupHost = RelayUrlNormalizer.normalize("wss://groups.example.com")
    private val communityId = "c".repeat(64)

    private val joinedGroups = setOf(GroupId("abcd1234", groupHost))

    private fun community(relays: List<String>) =
        ConcordCommunityListEntry(
            id = communityId,
            owner = "1".repeat(64),
            ownerSalt = "2".repeat(64),
            root = "3".repeat(64),
            relays = relays,
            name = "Dreamith",
        )

    @Test
    fun bothJoinedRoomKindsContributeTheirHostRelays() {
        val relays = RelayAuthVenues.hostRelays(joinedGroups, listOf(community(listOf("wss://relay.dreamith.to"))))

        assertEquals(
            setOf(groupHost, RelayUrlNormalizer.normalize("wss://relay.dreamith.to")),
            relays,
        )
    }

    @Test
    fun aConcordEntrysRawRelayStringIsNormalizedBeforeItIsCompared() {
        // The community list stores whatever url the community's creator wrote. Comparing that
        // verbatim against a normalized challenge url is how a whole community silently stops
        // matching over a trailing slash or an upper-case host.
        val relays = RelayAuthVenues.hostRelays(emptySet(), listOf(community(listOf("wss://Relay.Dreamith.to/"))))

        assertEquals(setOf(RelayUrlNormalizer.normalize("wss://relay.dreamith.to")), relays)
    }

    @Test
    fun anUnparseableRelayIsDroppedRatherThanFailingTheWholeList() {
        val relays = RelayAuthVenues.hostRelays(joinedGroups, listOf(community(listOf("not a url at all"))))

        assertEquals(setOf(groupHost), relays)
    }

    @Test
    fun aJoinedGroupIdAndCommunityIdAreBothJoinedRooms() {
        val communities = listOf(community(listOf("wss://relay.dreamith.to")))

        assertTrue(RelayAuthVenues.isJoinedRoom("abcd1234", groupHost, joinedGroups, communities))
        assertTrue(RelayAuthVenues.isJoinedRoom(communityId, groupHost, joinedGroups, communities))
    }

    @Test
    fun aRoomIHaveNotJoinedIsNotAJoinedRoom() {
        val communities = listOf(community(listOf("wss://relay.dreamith.to")))

        assertFalse(RelayAuthVenues.isJoinedRoom("someoneElsesGroup", groupHost, joinedGroups, communities))
        assertFalse(RelayAuthVenues.isJoinedRoom("d".repeat(64), groupHost, joinedGroups, communities))
    }

    @Test
    fun aGroupIdOnlyCountsOnTheRelayIJoinedItOn() {
        // NIP-29 ids are relay-scoped and often generic — `_` is the spec's relay-wide group — so an
        // id-only match would let a group merely browsed on some other relay pass for one I joined,
        // and hand that relay an automatic login.
        val elsewhere = RelayUrlNormalizer.normalize("wss://other.example.com")

        assertFalse(RelayAuthVenues.isJoinedRoom("abcd1234", elsewhere, joinedGroups, emptyList()))
        assertFalse(RelayAuthVenues.isJoinedRoom("abcd1234", null, joinedGroups, emptyList()))
    }

    @Test
    fun aCommunityIdCountsWhereverItIsServed() {
        // Unlike a group id, a Concord community id is a 64-hex derived value that names exactly one
        // community anywhere — and a community's own relays are covered by hostRelays regardless.
        val elsewhere = RelayUrlNormalizer.normalize("wss://other.example.com")
        val communities = listOf(community(listOf("wss://relay.dreamith.to")))

        assertTrue(RelayAuthVenues.isJoinedRoom(communityId, elsewhere, emptySet(), communities))
    }
}
