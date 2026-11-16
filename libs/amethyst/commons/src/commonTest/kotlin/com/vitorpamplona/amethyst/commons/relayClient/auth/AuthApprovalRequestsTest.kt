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
package com.vitorpamplona.amethyst.commons.relayClient.auth

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthApprovalRequestsTest {
    private val ownInbox = NormalizedRelayUrl("wss://my.inbox.relay/")
    private val someOtherRelay = NormalizedRelayUrl("wss://random.relay/")

    private fun pendingFor(relay: NormalizedRelayUrl): CompletableDeferred<AuthApprovalScope> {
        val deferred = CompletableDeferred<AuthApprovalScope>()
        return deferred
    }

    /**
     * Reproduces the cold-boot tier-1 race.
     *
     * A relay challenges for AUTH before the account's kind:10050 has loaded,
     * so it is surfaced as a tier-2 pending prompt. When kind:10050 finally
     * loads and the relay turns out to be the user's own DM inbox (tier-1), the
     * spurious prompt must clear and the suspended signer must be released to
     * sign — without persisting anything (ONCE), because it is trusted by
     * identity, not by an explicit user grant.
     *
     * RED until [AuthApprovalRequests.autoApproveNowTrusted] is implemented.
     */
    @Test
    fun pendingChallengeForNowTrustedRelayIsAutoApproved() =
        runTest {
            val requests = AuthApprovalRequests()
            val deferred = pendingFor(ownInbox)
            requests.add(PendingAuthApproval(ownInbox, deferred))
            assertTrue(requests.pending.value.containsKey(ownInbox))

            // kind:10050 arrives: the challenging relay is now tier-1.
            requests.autoApproveNowTrusted(setOf(ownInbox))

            assertFalse(
                requests.pending.value.containsKey(ownInbox),
                "spurious banner must clear once the relay is known to be trusted",
            )
            assertTrue(deferred.isCompleted, "the suspended signer must be released")
            assertEquals(
                AuthApprovalScope.ONCE,
                deferred.await(),
                "tier-1 auto-approval must sign but NOT persist (ONCE)",
            )
        }

    @Test
    fun pendingChallengeForUntrustedRelayIsLeftForTheUser() =
        runTest {
            val requests = AuthApprovalRequests()
            val deferred = pendingFor(someOtherRelay)
            requests.add(PendingAuthApproval(someOtherRelay, deferred))

            // A different relay became trusted; this one is still unknown.
            requests.autoApproveNowTrusted(setOf(ownInbox))

            assertTrue(
                requests.pending.value.containsKey(someOtherRelay),
                "an untrusted relay's prompt must remain for the user to decide",
            )
            assertFalse(deferred.isCompleted, "the untrusted signer must stay suspended")
        }

    @Test
    fun resolveRemovesEntryAndCompletesDeferred() =
        runTest {
            val requests = AuthApprovalRequests()
            val deferred = pendingFor(someOtherRelay)
            requests.add(PendingAuthApproval(someOtherRelay, deferred))

            assertTrue(requests.resolve(someOtherRelay, AuthApprovalScope.BLOCKED))

            assertFalse(requests.pending.value.containsKey(someOtherRelay))
            assertEquals(AuthApprovalScope.BLOCKED, deferred.await())
            assertFalse(requests.resolve(someOtherRelay, AuthApprovalScope.ONCE), "second resolve is a no-op")
        }

    @Test
    fun cancelAllReleasesEverySignerWithBlocked() =
        runTest {
            val requests = AuthApprovalRequests()
            val a = pendingFor(ownInbox)
            val b = pendingFor(someOtherRelay)
            requests.add(PendingAuthApproval(ownInbox, a))
            requests.add(PendingAuthApproval(someOtherRelay, b))

            requests.cancelAll()

            assertTrue(requests.pending.value.isEmpty())
            assertEquals(AuthApprovalScope.BLOCKED, a.await())
            assertEquals(AuthApprovalScope.BLOCKED, b.await())
        }
}
