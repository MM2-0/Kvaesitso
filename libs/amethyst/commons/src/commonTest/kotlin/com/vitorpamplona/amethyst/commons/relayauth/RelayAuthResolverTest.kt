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
package com.vitorpamplona.amethyst.commons.relayauth

import kotlin.test.Test
import kotlin.test.assertEquals

class RelayAuthResolverTest {
    private fun inputs(
        storedOverride: RelayAuthDecision? = null,
        isBlocked: Boolean = false,
        policy: RelayAuthPolicy = RelayAuthPolicy.CUSTOM,
        toggles: RelayAuthCustomToggles = RelayAuthCustomToggles(),
        isInMyRelayList: Boolean = false,
        servesTrustedVenue: Boolean = false,
        servesFollowedReadCounterparty: Boolean = false,
        servesFollowedWriteCounterparty: Boolean = false,
        servesStrangerWriteCounterparty: Boolean = false,
        hasAttributablePurpose: Boolean = true,
        isFirstParty: Boolean = true,
        hasSessionGrant: Boolean = false,
    ) = RelayAuthInputs(
        storedOverride = storedOverride,
        isBlocked = isBlocked,
        hasSessionGrant = hasSessionGrant,
        policy = policy,
        toggles = toggles,
        isInMyRelayList = isInMyRelayList,
        servesTrustedVenue = servesTrustedVenue,
        servesFollowedReadCounterparty = servesFollowedReadCounterparty,
        servesFollowedWriteCounterparty = servesFollowedWriteCounterparty,
        servesStrangerWriteCounterparty = servesStrangerWriteCounterparty,
        hasAttributablePurpose = hasAttributablePurpose,
        isFirstParty = isFirstParty,
    )

    private fun resolve(inputs: RelayAuthInputs) = RelayAuthResolver.resolve(inputs)

    @Test
    fun blockedRelayAlwaysDeniesEvenWithAllowOverrideAndAlwaysPolicy() {
        assertEquals(
            RelayAuthVerdict.DENY,
            resolve(
                inputs(
                    isBlocked = true,
                    storedOverride = RelayAuthDecision.ALLOW,
                    policy = RelayAuthPolicy.ALWAYS,
                ),
            ),
        )
    }

    @Test
    fun sessionGrantAllowsWhatWouldOtherwiseAsk() {
        // Without the grant this is the plain "we can explain it, so ask" case.
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs()))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(hasSessionGrant = true)))
    }

    @Test
    fun sessionGrantSurvivesTheCasesThatWouldNormallySuppressTheAnswer() {
        // The two inputs that turn an automatic grant off: no first-party reason to be here, and a
        // NEVER policy. An answer the user typed for this exact relay outranks both.
        assertEquals(
            RelayAuthVerdict.ALLOW,
            resolve(inputs(hasSessionGrant = true, isFirstParty = false)),
        )
        assertEquals(
            RelayAuthVerdict.ALLOW,
            resolve(inputs(hasSessionGrant = true, policy = RelayAuthPolicy.NEVER)),
        )
    }

    @Test
    fun blockedRelayAndStoredDenyBothBeatASessionGrant() {
        assertEquals(
            RelayAuthVerdict.DENY,
            resolve(inputs(hasSessionGrant = true, isBlocked = true)),
        )
        // "Never allow" answered later in the same session must take effect immediately.
        assertEquals(
            RelayAuthVerdict.DENY,
            resolve(inputs(hasSessionGrant = true, storedOverride = RelayAuthDecision.DENY)),
        )
    }

    @Test
    fun explicitOverrideBeatsPolicy() {
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(storedOverride = RelayAuthDecision.DENY, policy = RelayAuthPolicy.ALWAYS)))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(storedOverride = RelayAuthDecision.ALLOW, policy = RelayAuthPolicy.NEVER)))
    }

    @Test
    fun neverAndAlwaysAreUnconditional() {
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(policy = RelayAuthPolicy.NEVER, isInMyRelayList = true)))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(policy = RelayAuthPolicy.ALWAYS, hasAttributablePurpose = false)))
    }

    @Test
    fun customMyRelaysAndVenuesToggleGatesOwnRelaysAndVenues() {
        // On (default): my own relay and any joined venue auto-auth.
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(isInMyRelayList = true)))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(servesTrustedVenue = true)))
        // Off: even my own relay prompts.
        val off = RelayAuthCustomToggles(myRelaysAndVenues = false)
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(isInMyRelayList = true, toggles = off)))
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(servesTrustedVenue = true, toggles = off)))
    }

    @Test
    fun customReadFollowsToggleGatesReadingFollows() {
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(servesFollowedReadCounterparty = true)))
        assertEquals(
            RelayAuthVerdict.ASK,
            resolve(inputs(servesFollowedReadCounterparty = true, toggles = RelayAuthCustomToggles(readFollows = false))),
        )
    }

    @Test
    fun readFollowsGrantsOnTheFollowsOwnOutboxRelay() {
        // The situation the toggle is *named for*: someone we follow publishes to a relay of theirs
        // that we do not use. `isFirstParty` is false by construction there — the relay is theirs, we
        // have no traffic of our own on it — so gating this category on it made "…I'm reading someone
        // I follow" unreachable: every follow's outbox relay prompted, on an account with the toggle
        // explicitly on. The only time it ever granted was when the relay was also on our own list,
        // where `myRelaysAndVenues` already covered it.
        assertEquals(
            RelayAuthVerdict.ALLOW,
            resolve(inputs(servesFollowedReadCounterparty = true, isFirstParty = false)),
        )
        // Still off when the toggle is off.
        assertEquals(
            RelayAuthVerdict.ASK,
            resolve(
                inputs(
                    servesFollowedReadCounterparty = true,
                    isFirstParty = false,
                    toggles = RelayAuthCustomToggles(readFollows = false),
                ),
            ),
        )
    }

    @Test
    fun customMessageFollowsToggleGatesMessagingFollows() {
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(servesFollowedWriteCounterparty = true)))
        assertEquals(
            RelayAuthVerdict.ASK,
            resolve(inputs(servesFollowedWriteCounterparty = true, toggles = RelayAuthCustomToggles(messageFollows = false))),
        )
    }

    @Test
    fun customMessageStrangersIsOffByDefault() {
        // Default off: messaging a stranger prompts...
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(servesStrangerWriteCounterparty = true)))
        // ...on: auto-auth.
        assertEquals(
            RelayAuthVerdict.ALLOW,
            resolve(inputs(servesStrangerWriteCounterparty = true, toggles = RelayAuthCustomToggles(messageStrangers = true))),
        )
    }

    @Test
    fun customHasNoToggleForReadingStrangers() {
        // Reading a non-followed author (no matching category) always prompts, even with every
        // toggle on — there is deliberately no "read strangers" trust category.
        val allOn = RelayAuthCustomToggles(myRelaysAndVenues = true, readFollows = true, messageFollows = true, messageStrangers = true)
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, hasAttributablePurpose = true)))
    }

    @Test
    fun customFallsThroughForUncoveredSituation() {
        // Nothing matches -> prompt when we know why, else silent deny.
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(hasAttributablePurpose = true)))
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(hasAttributablePurpose = false)))
    }

    @Test
    fun nonFirstPartyAsksInsteadOfAutoAllowing() {
        // Every category that would auto-auth on our own relay becomes a question on a relay we have
        // no first-party reason to be on. Nothing here is *denied* — the user still gets to decide.
        val allOn = RelayAuthCustomToggles(myRelaysAndVenues = true, readFollows = true, messageFollows = true, messageStrangers = true)
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, isInMyRelayList = true, isFirstParty = false)))
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, servesTrustedVenue = true, isFirstParty = false)))
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, servesFollowedWriteCounterparty = true, isFirstParty = false)))
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, servesStrangerWriteCounterparty = true, isFirstParty = false)))
        // readFollows is deliberately absent: see readFollowsGrantsOnTheFollowsOwnOutboxRelay. Its
        // relay is the *follow's*, never ours, so the gate could only ever empty the category.
    }

    @Test
    fun readFollowsExemptionDoesNotLeakIntoTheOtherCategories() {
        // Only the read category is exempt. With readFollows on but nothing being read from a follow,
        // a non-first-party relay still asks for every other reason it might want us.
        val allOn = RelayAuthCustomToggles(myRelaysAndVenues = true, readFollows = true, messageFollows = true, messageStrangers = true)
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, isInMyRelayList = true, isFirstParty = false)))
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, servesFollowedWriteCounterparty = true, isFirstParty = false)))
        // The bystander case the gate exists for: another account's outgoing DM names someone we
        // follow. Ours is not the traffic, so we do not sign for it without being asked.
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, servesStrangerWriteCounterparty = true, isFirstParty = false)))
    }

    @Test
    fun alwaysPolicyAuthsEvenWhenNotFirstParty() {
        // "Always log in" means every relay that asks, first-party or not — narrowing it to the relays
        // this account uses is what CUSTOM is for. (This previously returned ASK for non-first-party,
        // which left no way to express "authenticate everywhere" and prompted once per third-party
        // outbox relay on a fresh install.)
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(policy = RelayAuthPolicy.ALWAYS, isFirstParty = true)))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(policy = RelayAuthPolicy.ALWAYS, isFirstParty = false)))
    }

    @Test
    fun customPolicyStillRequiresFirstParty() {
        // The first-party gate belongs to CUSTOM: a toggle that matches is not enough if the only reason
        // we are on this relay belongs to somebody else. (Except readFollows, whose relay always
        // belongs to the follow — see readFollowsGrantsOnTheFollowsOwnOutboxRelay.)
        val allOn = RelayAuthCustomToggles(myRelaysAndVenues = true, readFollows = true, messageFollows = true, messageStrangers = true)
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(toggles = allOn, isInMyRelayList = true, isFirstParty = true)))
        assertEquals(RelayAuthVerdict.ASK, resolve(inputs(toggles = allOn, isInMyRelayList = true, isFirstParty = false)))
    }

    @Test
    fun nonFirstPartyStillHonoursBlocksOverridesAndNever() {
        // Restoring the question must not reopen anything the user already closed.
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(isBlocked = true, isFirstParty = false)))
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(storedOverride = RelayAuthDecision.DENY, isFirstParty = false)))
        assertEquals(RelayAuthVerdict.ALLOW, resolve(inputs(storedOverride = RelayAuthDecision.ALLOW, isFirstParty = false)))
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(policy = RelayAuthPolicy.NEVER, isFirstParty = false)))
        // Still no prompt when we cannot explain the challenge.
        assertEquals(RelayAuthVerdict.DENY, resolve(inputs(hasAttributablePurpose = false, isFirstParty = false)))
    }
}
