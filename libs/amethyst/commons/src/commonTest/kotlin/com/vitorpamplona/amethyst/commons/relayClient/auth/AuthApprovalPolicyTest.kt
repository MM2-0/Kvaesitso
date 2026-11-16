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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AuthApprovalPolicyTest {
    private val ownOutbox = NormalizedRelayUrl("wss://own.outbox/")
    private val unknown = NormalizedRelayUrl("wss://unknown.relay/")
    private val blockedRelay = NormalizedRelayUrl("wss://blocked.relay/")

    private fun newPolicy(
        ownSet: Set<NormalizedRelayUrl> = setOf(ownOutbox),
        onPrompt: (PendingAuthApproval) -> Unit = {},
    ): Pair<AuthApprovalPolicy, AuthApprovalStore> {
        val store = InMemoryAuthApprovalStore()
        val policy =
            AuthApprovalPolicy(
                selfApprovedRelays = { ownSet },
                store = store,
                onPromptRequired = onPrompt,
            )
        return policy to store
    }

    @Test
    fun tier1OwnOutboxRelayIsAutoAllowed() =
        runTest {
            val (policy, _) = newPolicy()
            val decision = policy.classify(ownOutbox)
            assertSame(AuthApprovalDecision.Allow, decision)
        }

    @Test
    fun unknownRelayPromptsAndReturnsPending() =
        runTest {
            val prompts = mutableListOf<PendingAuthApproval>()
            val (policy, _) = newPolicy(onPrompt = { prompts += it })

            val decision = policy.classify(unknown)

            assertIs<AuthApprovalDecision.Pending>(decision)
            assertEquals(1, prompts.size)
            assertEquals(unknown, prompts.first().relayUrl)
            assertSame(decision.pending, prompts.first().decision)
        }

    @Test
    fun persistedAlwaysIsAutoAllowed() =
        runTest {
            val (policy, store) = newPolicy()
            store.setScope(unknown, AuthApprovalScope.ALWAYS)
            val decision = policy.classify(unknown)
            assertSame(AuthApprovalDecision.Allow, decision)
        }

    @Test
    fun persistedBlockedIsAutoBlockedEvenForOwnOutbox() =
        runTest {
            // Explicit user `[Never]` overrides tier-1 — if the user blocked a relay
            // that happens to be in their outbox, respect that.
            val (policy, store) = newPolicy()
            store.setScope(ownOutbox, AuthApprovalScope.BLOCKED)
            val decision = policy.classify(ownOutbox)
            assertSame(AuthApprovalDecision.Block, decision)
        }

    @Test
    fun recordDecisionPersistsAndChangesSubsequentClassification() =
        runTest {
            var promptCount = 0
            val (policy, _) = newPolicy(onPrompt = { promptCount++ })

            // First call prompts.
            val first = policy.classify(unknown)
            assertIs<AuthApprovalDecision.Pending>(first)
            assertEquals(1, promptCount)

            // User picks `[Always]`.
            policy.recordDecision(unknown, AuthApprovalScope.ALWAYS)

            // Subsequent calls return Allow without prompting.
            val second = policy.classify(unknown)
            assertSame(AuthApprovalDecision.Allow, second)
            assertEquals(1, promptCount, "should not prompt again after Always grant")
        }

    @Test
    fun blockedDecisionPersistsAndStaysBlocked() =
        runTest {
            var promptCount = 0
            val (policy, _) = newPolicy(onPrompt = { promptCount++ })

            // First call prompts.
            policy.classify(blockedRelay)
            assertEquals(1, promptCount)

            // User picks `[Never]`.
            policy.recordDecision(blockedRelay, AuthApprovalScope.BLOCKED)

            // Subsequent classify returns Block without prompting.
            val decision = policy.classify(blockedRelay)
            assertSame(AuthApprovalDecision.Block, decision)
            assertEquals(1, promptCount, "should not prompt again after Never")
        }

    @Test
    fun selfApprovedRelaysIsReevaluatedPerCall() =
        runTest {
            // Account state changes (user adds a relay to their outbox) must take
            // effect immediately — the policy reads the supplier per classify.
            var ownSet = setOf<NormalizedRelayUrl>()
            val policy =
                AuthApprovalPolicy(
                    selfApprovedRelays = { ownSet },
                    store = InMemoryAuthApprovalStore(),
                    onPromptRequired = {},
                )

            assertIs<AuthApprovalDecision.Pending>(policy.classify(ownOutbox))

            ownSet = setOf(ownOutbox)
            assertSame(AuthApprovalDecision.Allow, policy.classify(ownOutbox))
        }

    @Test
    fun storeClearWipesAllApprovals() =
        runTest {
            val store = InMemoryAuthApprovalStore()
            store.setScope(unknown, AuthApprovalScope.ALWAYS)
            store.setScope(blockedRelay, AuthApprovalScope.BLOCKED)

            store.clear()

            // Both relays now unknown → fresh classification prompts.
            assertTrue(store.getScope(unknown) == null)
            assertTrue(store.getScope(blockedRelay) == null)
        }
}
