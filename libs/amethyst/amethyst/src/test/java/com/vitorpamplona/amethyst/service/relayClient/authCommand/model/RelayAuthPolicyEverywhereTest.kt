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

import com.vitorpamplona.amethyst.commons.relayClient.auth.InMemoryRelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthSessionGrants
import com.vitorpamplona.amethyst.commons.relayClient.auth.UserAuthChoice
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt's two account-wide links — "Always, all relays"
 * ([UserAuthChoice.ALWAYS_ALLOW_EVERYWHERE]) and "Never, all relays"
 * ([UserAuthChoice.NEVER_ALLOW_EVERYWHERE]) — each do exactly one thing: flip the account's policy.
 * These tests pin what a flip has to buy — the prompt never comes back for *any* relay, in either
 * direction — and what it must not quietly do: write a per-relay exception that would outlive a
 * later switch back to "decide per relay", or override the rules that rank above the policy.
 */
class RelayAuthPolicyEverywhereTest {
    private val relay = "wss://auth.example.com/"
    private val other = "wss://elsewhere.example.com/"
    private val blockedRelay = "wss://blocked.example.com/"

    private var policy = RelayAuthPolicy.CUSTOM
    private val store: RelayAuthPermissionStore = InMemoryRelayAuthPermissionStore()

    private val ledger =
        RelayAuthPermissionLedger(
            store = store,
            globalPolicy = { policy },
            sessionGrants = RelayAuthSessionGrants(),
            isBlocked = { it == blockedRelay },
        )

    /** A challenge we can explain but have no automatic rule for: the ASK case the prompt is shown for. */
    private fun askable(relayUrl: String) = RelayAuthContext(relayUrl, listOf(AuthPurpose(AuthPurposeKind.MY_INBOX)))

    @Test
    fun theFlipAnswersThisRelayAndEveryOtherOne() =
        runTest {
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(relay)))
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(other)))

            policy = RelayAuthPolicy.ALWAYS

            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))
            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(other)))
        }

    /**
     * The relay that happened to be asking gets no exception of its own. An ALLOW written here would
     * survive a later switch back to CUSTOM/NEVER and keep authenticating a relay the user thought
     * they had stopped — the policy is the whole answer, so it is the only thing that changes.
     */
    @Test
    fun theFlipWritesNoPerRelayException() =
        runTest {
            policy = RelayAuthPolicy.ALWAYS
            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))

            assertNull(store.loadDecision(relay))
            assertEquals(emptyMap<String, RelayAuthDecision>(), store.allDecisions())

            policy = RelayAuthPolicy.CUSTOM
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(relay)))
        }

    /** Both rules that outrank the policy keep outranking it, which is what the confirmation promises. */
    @Test
    fun blockedRelaysAndNeverExceptionsStillWin() =
        runTest {
            ledger.setDecision(other, RelayAuthDecision.DENY)
            policy = RelayAuthPolicy.ALWAYS

            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(blockedRelay)))
            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(other)))
            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))
        }

    @Test
    fun theNeverFlipSilencesThisRelayAndEveryOtherOne() =
        runTest {
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(relay)))

            policy = RelayAuthPolicy.NEVER

            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(relay)))
            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(other)))
            assertNull(store.loadDecision(relay))
        }

    /** The "Always, set by you" exceptions the settings screen lists are standing answers, not casual ones. */
    @Test
    fun theNeverFlipLeavesAlwaysExceptionsAlone() =
        runTest {
            ledger.setDecision(other, RelayAuthDecision.ALLOW)
            policy = RelayAuthPolicy.NEVER

            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(other)))
            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(relay)))
        }

    /**
     * The two account-wide answers are the only ones the host may apply to prompts other than the one
     * on screen, and only to the account that was asked. Pinning the mapping keeps that fan-out — and
     * the setting write that survives an expired prompt — from ever reaching a per-relay answer.
     */
    @Test
    fun onlyTheAccountWideAnswersCarryAPolicy() {
        assertEquals(RelayAuthPolicy.ALWAYS, UserAuthChoice.ALWAYS_ALLOW_EVERYWHERE.policyEverywhere)
        assertEquals(RelayAuthPolicy.NEVER, UserAuthChoice.NEVER_ALLOW_EVERYWHERE.policyEverywhere)
        assertTrue(
            listOf(
                UserAuthChoice.ALLOW_ONCE,
                UserAuthChoice.ALWAYS_ALLOW,
                UserAuthChoice.BLOCK,
                UserAuthChoice.DISMISS,
            ).all { it.policyEverywhere == null },
        )
    }

    /**
     * Why the coordinator routes this through [com.vitorpamplona.amethyst.commons.model.Account], which drops
     * the session grants with the flip: a grant left behind outranks the policy, so "never log in"
     * would keep authenticating exactly the relays the user had just answered "log in" for.
     */
    @Test
    fun aSessionGrantWouldOutrankTheNeverFlipIfItSurvived() =
        runTest {
            val grants = RelayAuthSessionGrants()
            val ledger =
                RelayAuthPermissionLedger(
                    store = InMemoryRelayAuthPermissionStore(),
                    globalPolicy = { policy },
                    sessionGrants = grants,
                )
            grants.grant(relay)
            policy = RelayAuthPolicy.NEVER

            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))

            grants.clear()

            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(relay)))
        }
}
