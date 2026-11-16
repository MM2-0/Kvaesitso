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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip42RelayAuth.RelayAuthEvent
import com.vitorpamplona.quartz.nip42RelayAuth.tags.RelayTag
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * End-to-end exercise of the AUTH stack: policy classification +
 * RelayAuthEvent.build template + real NostrSignerInternal signing.
 *
 * This is the lambda-level shape that [DesktopAuthCoordinator]'s
 * `signWithAllLoggedInUsers` calls into. It isolates the policy/signer
 * round-trip from the live websocket layer (which has its own coverage
 * in `geode/.../KtorRelayTest.kt` against a Ktor mock relay).
 *
 * Together with the existing AuthApprovalPolicyTest (classifier),
 * PoolEventOutboxStateTest (auth-required carve-out), and
 * GiftWrapRelayHintTest (NIP-17 hint placement), this covers the AUTH
 * pipeline at unit granularity — the geode Ktor tests handle the
 * websocket-level round-trip.
 */
class AuthApprovalEndToEndTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val ownInbox = NormalizedRelayUrl("wss://own.inbox/")
    private val unknown = NormalizedRelayUrl("wss://unknown.relay/")
    private val challenge = "test-challenge-abc123"

    private fun newPolicy(
        ownSet: Set<NormalizedRelayUrl> = setOf(ownInbox),
        onPrompt: (PendingAuthApproval) -> Unit = {},
    ): Pair<AuthApprovalPolicy, AuthApprovalStore> {
        val store = InMemoryAuthApprovalStore()
        return AuthApprovalPolicy(
            selfApprovedRelays = { ownSet },
            store = store,
            onPromptRequired = onPrompt,
        ) to store
    }

    /**
     * Coordinator's lambda shape, distilled. Returns the signed AUTH event
     * (or null on Block / not-signed-by-policy).
     */
    private suspend fun signWithPolicy(
        relay: NormalizedRelayUrl,
        policy: AuthApprovalPolicy,
    ): RelayAuthEvent? {
        val template = RelayAuthEvent.build(relay, challenge)
        val relayFromTemplate = template.tags.firstNotNullOfOrNull(RelayTag::parse)
        assertEquals(relay, relayFromTemplate, "RelayAuthEvent.build must round-trip via RelayTag.parse")
        return when (val decision = policy.classify(relay)) {
            AuthApprovalDecision.Allow -> signer.sign(template)
            AuthApprovalDecision.Block -> null
            is AuthApprovalDecision.Pending -> {
                val resolved = decision.pending.await()
                if (resolved != AuthApprovalScope.ONCE) policy.recordDecision(relay, resolved)
                if (resolved == AuthApprovalScope.BLOCKED) null else signer.sign(template)
            }
        }
    }

    @Test
    fun tier1OwnInboxAutoSignsValidAuthEvent() =
        runTest {
            val (policy, _) = newPolicy()
            val signed = signWithPolicy(ownInbox, policy)
            assertNotNull(signed)
            assertEquals(RelayAuthEvent.KIND, signed.kind)
            assertEquals(signer.pubKey, signed.pubKey)
            assertEquals(challenge, signed.challenge())
            assertEquals(ownInbox, signed.relay())
        }

    @Test
    fun tier2UnknownPromptsAndOnceResolutionSigns() =
        runTest {
            var prompted: PendingAuthApproval? = null
            val (policy, _) = newPolicy(onPrompt = { prompted = it })

            coroutineScope {
                // Concurrent: lambda suspends inside policy.classify; we
                // resolve the deferred from outside as the banner UI would.
                val deferred = async { signWithPolicy(unknown, policy) }
                yieldUntilNotNull { prompted }
                prompted!!.decision.complete(AuthApprovalScope.ONCE)

                val signed = deferred.await()
                assertNotNull(signed)
                assertEquals(unknown, signed.relay())
            }
        }

    @Test
    fun tier2BlockedResolutionReturnsNullAndPersists() =
        runTest {
            var prompted: PendingAuthApproval? = null
            val (policy, store) = newPolicy(onPrompt = { prompted = it })

            coroutineScope {
                val deferred = async { signWithPolicy(unknown, policy) }
                yieldUntilNotNull { prompted }
                prompted!!.decision.complete(AuthApprovalScope.BLOCKED)

                val signed = deferred.await()
                assertNull(signed)
                assertEquals(AuthApprovalScope.BLOCKED, store.getScope(unknown))
            }
        }

    @Test
    fun tier2AlwaysPersistsAndSkipsPromptNextTime() =
        runTest {
            var promptCount = 0
            val (policy, store) =
                newPolicy(onPrompt = {
                    it.decision.complete(AuthApprovalScope.ALWAYS)
                    promptCount++
                })

            // First call: prompts and resolves to ALWAYS.
            val first = signWithPolicy(unknown, policy)
            assertNotNull(first)
            assertEquals(1, promptCount)
            assertEquals(AuthApprovalScope.ALWAYS, store.getScope(unknown))

            // Second call: should NOT prompt again.
            val second = signWithPolicy(unknown, policy)
            assertNotNull(second)
            assertEquals(1, promptCount, "ALWAYS persisted — no second prompt")
        }
}

private suspend inline fun <T> yieldUntilNotNull(crossinline supplier: () -> T?): T {
    repeat(100) {
        supplier()?.let { return it }
        yield()
    }
    error("supplier never produced a value within 100 yields")
}
