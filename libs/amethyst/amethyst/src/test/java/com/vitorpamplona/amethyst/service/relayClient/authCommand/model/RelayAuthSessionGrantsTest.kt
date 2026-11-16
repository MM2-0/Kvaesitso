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
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPermissionCache
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthSessionGrants
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthVerdict
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Log in" without the remember switch has to survive the relay's next reconnect, or the same dialog
 * comes back every time the socket drops — which is what pushed users into "always allow".
 *
 * These tests drive [RelayAuthPermissionLedger] rather than the pure resolver, because the thing worth
 * pinning is that the grant is consulted on the real decision path and that the persisted rules still
 * outrank it.
 */
class RelayAuthSessionGrantsTest {
    private val relay = "wss://auth.example.com/"
    private val other = "wss://elsewhere.example.com/"

    private fun ledger(
        grants: RelayAuthSessionGrants = RelayAuthSessionGrants(),
        store: RelayAuthPermissionStore = InMemoryRelayAuthPermissionStore(),
        blocked: Set<String> = emptySet(),
        policy: () -> RelayAuthPolicy = { RelayAuthPolicy.CUSTOM },
    ) = RelayAuthPermissionLedger(
        store = store,
        globalPolicy = policy,
        sessionGrants = grants,
        isBlocked = { it in blocked },
    )

    /** A challenge we can explain but have no automatic rule for: the ASK case. */
    private fun askable(relayUrl: String) =
        RelayAuthContext(
            relayUrl,
            listOf(AuthPurpose(AuthPurposeKind.MY_INBOX)),
        )

    @Test
    fun withoutAGrantTheSameRelayKeepsAsking() =
        runTest {
            val ledger = ledger()
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(relay)))
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(relay)))
        }

    @Test
    fun aSessionGrantAnswersEveryLaterReconnect() =
        runTest {
            val ledger = ledger()
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(relay)))

            ledger.grantForSession(relay)

            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))
            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))
        }

    @Test
    fun aGrantCoversOnlyTheRelayItWasGivenFor() =
        runTest {
            val ledger = ledger()
            ledger.grantForSession(relay)

            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(other)))
        }

    @Test
    fun grantsAreNeverWrittenToTheStore() =
        runTest {
            val store = InMemoryRelayAuthPermissionStore()
            val ledger = ledger(store = store)
            ledger.grantForSession(relay)

            // Nothing persisted: a fresh process (a ledger over the same disk, with empty session
            // memory) is back to asking.
            assertEquals(emptyMap<String, RelayAuthDecision>(), store.allDecisions())
            assertEquals(RelayAuthVerdict.ASK, ledger(store = store).decide(askable(relay)))
        }

    @Test
    fun blockListOutranksAGrant() =
        runTest {
            val ledger = ledger(blocked = setOf(relay))
            ledger.grantForSession(relay)

            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(relay)))
        }

    @Test
    fun neverAllowTakesEffectImmediatelyOverAGrant() =
        runTest {
            val grants = RelayAuthSessionGrants()
            val ledger = ledger(grants)
            ledger.grantForSession(relay)
            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))

            // The user answers "Never allow" on a later prompt for the same relay.
            ledger.setDecision(relay, RelayAuthDecision.DENY)

            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(relay)))
            assertFalse(grants.isGranted(relay))
        }

    @Test
    fun clearingAnExceptionDropsTheGrantSoTheRelayReallyFollowsTheRulesAgain() =
        runTest {
            val grants = RelayAuthSessionGrants()
            val ledger = ledger(grants)
            ledger.grantForSession(relay)
            ledger.setDecision(relay, RelayAuthDecision.ALLOW)

            ledger.clearDecision(relay)

            assertFalse(grants.isGranted(relay))
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(relay)))
        }

    @Test
    fun revokingRestoresTheQuestion() =
        runTest {
            val grants = RelayAuthSessionGrants()
            val ledger = ledger(grants)
            ledger.grantForSession(relay)
            assertTrue(grants.isGranted(relay))

            ledger.revokeSessionGrant(relay)

            assertFalse(grants.isGranted(relay))
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(relay)))
        }

    /**
     * A store whose write suspends until [gate] opens, modelling the real one: the disk write is
     * awaited *before* [RelayAuthPermissionCache] publishes the new override to memory, so there is a
     * window where the override is not yet readable.
     */
    private class GatedStore(
        private val gate: CompletableDeferred<Unit>,
        private val inner: RelayAuthPermissionStore = InMemoryRelayAuthPermissionStore(),
    ) : RelayAuthPermissionStore by inner {
        override suspend fun storeDecision(
            relayUrl: String,
            decision: RelayAuthDecision,
        ) {
            gate.await()
            inner.storeDecision(relayUrl, decision)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun promotingAGrantToAlwaysNeverOpensAGapThatRePrompts() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val ledger = ledger(store = GatedStore(gate))
            ledger.grantForSession(relay)

            val write = launch { ledger.setDecision(relay, RelayAuthDecision.ALLOW) }
            runCurrent()

            // Mid-write the override is not readable yet. If the grant has already been dropped the
            // relay is momentarily undecided and a reconnect lands a fresh dialog on a user who just
            // pressed "Always" — the exact prompt this feature exists to stop.
            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))

            gate.complete(Unit)
            write.join()
            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun neverAllowStopsAuthenticatingBeforeItsWriteLands() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val ledger = ledger(store = GatedStore(gate))
            ledger.grantForSession(relay)

            val write = launch { ledger.setDecision(relay, RelayAuthDecision.DENY) }
            runCurrent()

            // The opposite bias to the ALLOW case: a user who just said "never" must not have one more
            // AUTH signed on the strength of the grant they are replacing.
            assertNotEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))

            gate.complete(Unit)
            write.join()
            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(relay)))
        }

    @Test
    fun grantsAreObservableForTheSettingsScreen() {
        val grants = RelayAuthSessionGrants()
        assertEquals(emptySet<String>(), grants.grants.value)

        grants.grant(relay)
        grants.grant(other)
        assertEquals(setOf(relay, other), grants.grants.value)

        grants.revoke(relay)
        assertEquals(setOf(other), grants.grants.value)

        grants.clear()
        assertEquals(emptySet<String>(), grants.grants.value)
    }

    @Test
    fun aGrantIsRefusedWhileThePolicyIsNever() =
        runTest {
            val grants = RelayAuthSessionGrants()
            val ledger = ledger(grants = grants, policy = { RelayAuthPolicy.NEVER })

            assertFalse(ledger.grantForSession(relay))
            assertFalse(grants.isGranted(relay))
            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(relay)))
        }

    @Test
    fun undoingAForgetAfterSwitchingToNeverDoesNotResurrectTheGrant() =
        runTest {
            // The settings screen's undo snackbar carries an action label, so Material3 leaves it up
            // indefinitely — the user can switch the whole policy off and only then tap undo.
            val grants = RelayAuthSessionGrants()
            var policy = RelayAuthPolicy.CUSTOM
            val ledger = ledger(grants = grants, policy = { policy })

            assertTrue(ledger.grantForSession(relay))
            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(relay)))

            // "Forget this login", then "Never log in" — which also clears what is already granted.
            ledger.revokeSessionGrant(relay)
            policy = RelayAuthPolicy.NEVER
            grants.clear()

            // ...and only now, undo.
            assertFalse(ledger.grantForSession(relay))
            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(relay)))
        }

    @Test
    fun theGuardOnlyAppliesToNever() =
        runTest {
            assertTrue(ledger(policy = { RelayAuthPolicy.CUSTOM }).grantForSession(relay))
            assertTrue(ledger(policy = { RelayAuthPolicy.ALWAYS }).grantForSession(relay))
        }

    @Test
    fun aStoredDecisionTakenDuringTheUndoWindowNeedsNoGuardBecauseItOutranksTheGrant() =
        runTest {
            // Why the guard is narrowed to the policy: an override written while the snackbar was up
            // is ranked above the grant, so restoring the grant cannot undo the user's newer answer.
            val ledger = ledger()

            ledger.revokeSessionGrant(relay)
            ledger.setDecision(relay, RelayAuthDecision.DENY)

            assertTrue(ledger.grantForSession(relay))
            assertEquals(RelayAuthVerdict.DENY, ledger.decide(askable(relay)))
        }

    @Test
    fun blockingARelayForgetsItsSessionGrantSoUnblockingDoesNotResumeIt() =
        runTest {
            // While the block is in force the relay is denied whatever the grant says, so what this
            // pins is the state left behind for when the block is lifted.
            val grants = RelayAuthSessionGrants()
            val ledger = ledger(grants = grants)

            ledger.grantForSession(relay)
            ledger.grantForSession(other)

            ledger.revokeSessionGrantsFor(listOf(relay))

            assertEquals(setOf(other), grants.grants.value)
            assertEquals(RelayAuthVerdict.ASK, ledger.decide(askable(relay)))
            assertEquals(RelayAuthVerdict.ALLOW, ledger.decide(askable(other)))
        }
}
