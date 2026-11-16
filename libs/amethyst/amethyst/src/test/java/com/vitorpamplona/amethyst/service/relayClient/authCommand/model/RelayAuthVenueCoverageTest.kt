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

import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthSessionGrants
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthCustomToggles
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * "…it's my relay, or a room I joined" must actually cover every room the app lets you join. NIP-29
 * relay groups and Concord communities were invisible to it: neither id is on the public-chat or
 * NIP-72 community lists the venue check consulted, so a relay whose only job is hosting a group the
 * user joined fell through to a prompt on every connection (or, with nothing attributable yet, to a
 * silent denial that left the room empty).
 */
class RelayAuthVenueCoverageTest {
    private val groupRelay = "wss://groups.example.com/"
    private val concordRelay = "wss://relay.dreamith.to/"
    private val joinedGroupId = "abcd1234"
    private val joinedCommunityId = "c".repeat(64)

    private class NoStore : RelayAuthPermissionStore {
        override suspend fun loadDecision(relayUrl: String): RelayAuthDecision? = null

        override suspend fun storeDecision(
            relayUrl: String,
            decision: RelayAuthDecision,
        ) = Unit

        override suspend fun clearDecision(relayUrl: String) = Unit

        override suspend fun allDecisions(): Map<String, RelayAuthDecision> = emptyMap()
    }

    private fun ledger(toggles: RelayAuthCustomToggles = RelayAuthCustomToggles()) =
        RelayAuthPermissionLedger(
            store = NoStore(),
            globalPolicy = { RelayAuthPolicy.CUSTOM },
            sessionGrants = RelayAuthSessionGrants(),
            customToggles = { toggles },
            isTrustedVenue = { _, venueId -> venueId == joinedGroupId || venueId == joinedCommunityId },
            isVenueHostRelay = { it == groupRelay || it == concordRelay },
        )

    private fun readVenue(
        relayUrl: String,
        venueId: String,
    ) = RelayAuthContext(relayUrl, listOf(AuthPurpose(AuthPurposeKind.READ_VENUE, venues = setOf(venueId))))

    @Test
    fun readingAJoinedRelayGroupAutoAuthenticates() =
        runTest {
            // The group's chat is `#h`-scoped: the subscription declares RELAY_GROUPS, which carries
            // the group id as its venue. That id is on the kind-10009 list, so the venue toggle covers
            // it exactly like a public chat the user joined.
            assertEquals(RelayAuthVerdict.ALLOW, ledger().decide(readVenue(groupRelay, joinedGroupId)))
        }

    @Test
    fun readingAJoinedConcordCommunityAutoAuthenticates() =
        runTest {
            assertEquals(RelayAuthVerdict.ALLOW, ledger().decide(readVenue(concordRelay, joinedCommunityId)))
        }

    @Test
    fun aJoinedRoomsHostAutoAuthenticatesBeforeAnyOfItsFiltersExist() =
        runTest {
            // The challenge that matters arrives on connect, before the room's subscription is
            // assembled — so there is no venue in the context to match. The host relay itself is the
            // signal: nothing else is on it.
            assertEquals(RelayAuthVerdict.ALLOW, ledger().decide(RelayAuthContext(groupRelay)))
        }

    @Test
    fun aJoinedRoomsHostStillAsksWhenTheVenueToggleIsOff() =
        runTest {
            // Turning the toggle off must mean "ask me", not "deny in silence": hosting a room the user
            // joined is an explanation, so the challenge is attributable even with no purposes.
            val verdict = ledger(RelayAuthCustomToggles(myRelaysAndVenues = false)).decide(RelayAuthContext(groupRelay))
            assertEquals(RelayAuthVerdict.ASK, verdict)
        }

    @Test
    fun aGroupOnARelayIHaveNotJoinedIsNotAutoAuthenticated() =
        runTest {
            // Browsing a group directory names a venue we never joined on a relay that hosts nothing of
            // ours — explainable, so it asks, but it is never silently granted.
            val verdict = ledger().decide(readVenue("wss://stranger.example.com/", "somebodyElsesGroup"))
            assertEquals(RelayAuthVerdict.ASK, verdict)
        }

    @Test
    fun aJoinedRoomsHostIsNotAutoAuthenticatedForABystanderAccount() =
        runTest {
            // The room belongs to another logged-in account; this one has no first-party reason to be
            // on the relay, so it asks instead of revealing its npub on its own.
            val verdict = ledger().decide(readVenue(groupRelay, joinedGroupId), isFirstParty = false)
            assertEquals(RelayAuthVerdict.ASK, verdict)
        }
}
