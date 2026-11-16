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
package com.vitorpamplona.amethyst.commons.napplet

import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrConnectPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerConsentPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.napplet.gateways.AccountNappletRelayGateway
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.permissions.PermissionDecision
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Regression tests for the napplet broker findings of the 2026-10-31 security review. */
@OptIn(ExperimentalCoroutinesApi::class)
class NappletBrokerSecurityTest {
    private val user = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000007".hexToByteArray()))
    private val stranger = NostrSignerInternal(KeyPair("0000000000000000000000000000000000000000000000000000000000000009".hexToByteArray()))
    private val friend = NostrSignerInternal(KeyPair("000000000000000000000000000000000000000000000000000000000000000b".hexToByteArray()))

    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "demo")
    private val signerCoordinate = "napplet:${user.pubKey}:${applet.coordinate}"

    private class FixedRelay(
        private val events: List<Event>,
    ) : NappletRelayGateway {
        override suspend fun publish(event: Event): List<String> = listOf("wss://relay.example")

        override suspend fun query(filters: List<Filter>): List<Event> = events
    }

    private object NeverAsk : NappletConsentPrompt {
        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState = error("no consent prompt expected")
    }

    private suspend fun broker(
        relayEvents: List<Event>,
        policy: AppSignerPolicy,
        signerLedger: NostrSignerPermissionLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore()),
    ): NappletBroker {
        signerLedger.setPolicy(signerCoordinate, policy)
        val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
        // The state after one "Connect": every declared capability granted for good.
        ledger.record(applet, NappletCapability.RELAY, GrantState.ALLOW_ALWAYS)
        ledger.record(applet, NappletCapability.SIGNER, GrantState.ALLOW_ALWAYS)
        return NappletBroker(
            signer = user,
            ledger = ledger,
            consentPrompt = NeverAsk,
            relay = FixedRelay(relayEvents),
            signerLedger = signerLedger,
        )
    }

    private suspend fun dmTo(
        from: NostrSignerInternal,
        to: String,
        text: String,
    ): Event = from.sign(TimeUtils.now(), 4, arrayOf(arrayOf("p", to)), from.nip04Encrypt(text, to))

    private suspend fun query(broker: NappletBroker): List<Event> {
        val response = broker.handle(applet, NappletRequest.QueryEvents(listOf(Filter(kinds = listOf(4, 7)))), setOf(NappletCapability.RELAY))
        return assertIs<NappletResponse.Events>(response).events
    }

    @Test
    fun reasonablePolicyNeverHandsOverDecryptedDms() =
        runTest {
            val dm = dmTo(stranger, user.pubKey, "the secret")

            val events = query(broker(listOf(dm), AppSignerPolicy.REASONABLE))

            assertTrue(events.isEmpty(), "a DM must be withheld without a decrypt grant, got $events")
        }

    @Test
    fun anExplicitDecryptGrantReleasesTheDm() =
        runTest {
            val dm = dmTo(stranger, user.pubKey, "the secret")
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setOpDecision(signerCoordinate, NostrSignerOp.Decrypt, NostrOpDecision.ALLOW)

            val events = query(broker(listOf(dm), AppSignerPolicy.REASONABLE, signerLedger))

            assertEquals(listOf("the secret"), events.map { it.content })
        }

    @Test
    fun fullTrustReadsDms() =
        runTest {
            val dm = dmTo(stranger, user.pubKey, "the secret")

            val events = query(broker(listOf(dm), AppSignerPolicy.FULL_TRUST))

            assertEquals(listOf("the secret"), events.map { it.content })
        }

    @Test
    fun aNarrowGrantReadsOnlyThatCounterparty() =
        runTest {
            val fromFriend = dmTo(friend, user.pubKey, "hi from a friend")
            val fromStranger = dmTo(stranger, user.pubKey, "the secret")
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setOpDecision(signerCoordinate, NostrSignerOp.DecryptFrom(friend.pubKey), NostrOpDecision.ALLOW)

            val events = query(broker(listOf(fromFriend, fromStranger), AppSignerPolicy.REASONABLE, signerLedger))

            assertEquals(listOf("hi from a friend"), events.map { it.content })
        }

    // The decryption oracle: the app gets the user to sign a reaction (auto-allowed under REASONABLE)
    // whose content is the ciphertext of the stranger's DM and whose `p` tag names the stranger, then
    // reads the reaction back. Even with a grant for that stranger, the reaction's peer is the user.
    @Test
    fun aReactionCarryingSomeoneElsesCiphertextIsNotDecryptedWithItsPTag() =
        runTest {
            val dm = dmTo(stranger, user.pubKey, "the secret")
            val broker = broker(emptyList(), AppSignerPolicy.REASONABLE)
            val published = broker.handle(applet, NappletRequest.Publish(7, arrayOf(arrayOf("p", stranger.pubKey)), dm.content), setOf(NappletCapability.RELAY))
            val reaction = assertIs<NappletResponse.Published>(published).event

            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setOpDecision(signerCoordinate, NostrSignerOp.DecryptFrom(stranger.pubKey), NostrOpDecision.ALLOW)
            val events = query(broker(listOf(reaction), AppSignerPolicy.REASONABLE, signerLedger))

            assertFalse(events.any { it.content == "the secret" }, "the reaction must never come back decrypted: $events")
        }

    @Test
    fun aStandingRelayDenialAlsoDeniesDecryption() =
        runTest {
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy(signerCoordinate, AppSignerPolicy.FULL_TRUST)
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            ledger.record(applet, NappletCapability.RELAY, GrantState.DENY)
            val broker = NappletBroker(signer = user, ledger = ledger, consentPrompt = NeverAsk, signerLedger = signerLedger)

            assertFalse(broker.mayDecrypt(applet, stranger.pubKey))
        }

    @Test
    fun tooManyFiltersAreRefused() =
        runTest {
            val filters = List(NappletBroker.MAX_RELAY_FILTERS + 1) { Filter(kinds = listOf(1)) }
            val broker = broker(emptyList(), AppSignerPolicy.REASONABLE)

            assertIs<NappletResponse.Failed>(broker.handle(applet, NappletRequest.QueryEvents(filters), setOf(NappletCapability.RELAY)))
            assertIs<NappletResponse.Failed>(broker.handle(applet, NappletRequest.Subscribe(filters), setOf(NappletCapability.RELAY)))
        }

    @Test
    fun queryResultsAreCapped() =
        runTest {
            val notes = List(NappletBroker.MAX_QUERY_RESULTS + 20) { i -> user.sign<Event>(1000L + i, 1, emptyArray(), "note $i") }

            val events = query(broker(notes, AppSignerPolicy.REASONABLE))

            assertEquals(NappletBroker.MAX_QUERY_RESULTS, events.size)
        }

    // The cache holds the unsigned rumors unwrapped from the user's NIP-17 gift wraps: private
    // messages that were never on a relay. Only signed events may reach an app.
    @Test
    fun cachedRumorsNeverLeaveTheGateway() =
        runTest {
            val signed = user.sign<Event>(2000L, 1, emptyArray(), "public")
            val rumor = Event("ab".repeat(32), stranger.pubKey, 3000L, 14, arrayOf(arrayOf("p", user.pubKey)), "private chat message", "")

            val merged = AccountNappletRelayGateway.merged(listOf(rumor, signed), listOf(Filter(kinds = listOf(1, 14))))

            assertEquals(listOf(signed), merged)
        }

    private class CountingSignerPrompt(
        private val answer: SignerOpGrant,
    ) : NostrSignerConsentPrompt {
        var calls = 0

        override suspend fun request(
            identity: NappletIdentity,
            op: NostrSignerOp,
            request: NappletRequest,
        ): SignerOpGrant {
            calls++
            return answer
        }
    }

    // nostr.signEvent hands back a raw signature dated whenever the app likes. Only the website
    // posture (SIGNER) may have it; a NIP-5D manifest declaring `relay` must not.
    @Test
    fun aNip5dNappletCannotGetARawSignature() =
        runTest {
            val declared = resolveRequiredCapabilities(listOf("relay", "identity", "storage", "outbox")).capabilities
            val broker = broker(emptyList(), AppSignerPolicy.REASONABLE)

            val response = broker.handle(applet, NappletRequest.SignEvent(kind = 30023, tags = arrayOf(arrayOf("d", "article")), content = "x", createdAt = 4102444800), declared)

            assertIs<NappletResponse.Denied>(response)
        }

    @Test
    fun aWebsiteStillGetsNip07SignEvent() =
        runTest {
            val broker = broker(emptyList(), AppSignerPolicy.REASONABLE)

            val response = broker.handle(applet, NappletRequest.SignEvent(kind = 1, tags = emptyArray(), content = "hi", createdAt = 1234), NappletCapability.WEBSITE_CAPABILITIES)

            assertEquals(1234L, assertIs<NappletResponse.Published>(response).event.createdAt)
        }

    // Consent fatigue: a napplet looping a follow-list publish must not bring the dialog straight back.
    @Test
    fun aDeclinedPromptStaysQuietForAWhile() =
        runTest {
            var now = 1_000_000L
            val prompt = CountingSignerPrompt(SignerOpGrant.DenyOnce)
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy(signerCoordinate, AppSignerPolicy.REASONABLE)
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            ledger.record(applet, NappletCapability.RELAY, GrantState.ALLOW_ALWAYS)
            val broker =
                NappletBroker(
                    signer = user,
                    ledger = ledger,
                    consentPrompt = NeverAsk,
                    relay = FixedRelay(emptyList()),
                    signerLedger = signerLedger,
                    signerConsentPrompt = prompt,
                    nowMillis = { now },
                )
            val followList = NappletRequest.Publish(3, arrayOf(arrayOf("p", stranger.pubKey)), "")

            suspend fun ask() = assertIs<NappletResponse.Denied>(broker.handle(applet, followList, setOf(NappletCapability.RELAY)))

            // A single dismissal may be a slip: the user's retry right after still gets the dialog.
            ask()
            ask()
            assertEquals(2, prompt.calls)

            // Declined twice in a row: the app's next asks are dropped for a while.
            repeat(10) { ask() }
            assertEquals(2, prompt.calls)

            now += NappletBroker.DECLINE_BACKOFF_MS + 1
            ask()
            assertEquals(3, prompt.calls)

            // A third decline in a row waits twice as long.
            now += NappletBroker.DECLINE_BACKOFF_MS + 1
            ask()
            assertEquals(3, prompt.calls)
            now += NappletBroker.DECLINE_BACKOFF_MS
            ask()
            assertEquals(4, prompt.calls)
        }

    @Test
    fun blockingAnAppDeniesCapabilitiesItHasNotDeclaredYet() =
        runTest {
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            val broker =
                NappletBroker(
                    signer = user,
                    ledger = ledger,
                    consentPrompt = NeverAsk,
                    signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore()),
                    nostrConnectPrompt =
                        object : NostrConnectPrompt {
                            override suspend fun request(
                                identity: NappletIdentity,
                                declared: Set<NappletCapability>,
                            ) = AppConnectResult.Blocked
                        },
                )

            broker.handle(applet, NappletRequest.GetPublicKey, setOf(NappletCapability.IDENTITY))

            NappletCapability.entries.forEach { assertEquals(PermissionDecision.DENY, ledger.decide(applet, it), "$it") }
        }

    @Test
    fun anAppCannotPileUpRequestsBehindAnOpenDialog() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            val prompt =
                object : NappletConsentPrompt {
                    override suspend fun request(
                        identity: NappletIdentity,
                        capability: NappletCapability,
                        request: NappletRequest,
                    ): GrantState {
                        gate.await()
                        return GrantState.ALLOW_ONCE
                    }
                }
            val broker = NappletBroker(signer = user, ledger = ledger, consentPrompt = prompt, storage = null)
            val asks = List(NappletBroker.MAX_WAITING_PROMPTS + 10) { async { broker.handle(applet, NappletRequest.GetPublicKey, setOf(NappletCapability.IDENTITY)) } }
            advanceUntilIdle()

            val refusedAtOnce = asks.count { it.isCompleted }
            gate.complete(Unit)
            asks.awaitAll()

            assertEquals(10, refusedAtOnce)
        }

    // Reads ask whether they may decrypt for every encrypted event; they must not wait behind a consent
    // dialog another app (or this one) has open.
    @Test
    fun anOpenSigningDialogDoesNotStallReadsThatDecrypt() =
        runTest {
            val dm = dmTo(stranger, user.pubKey, "the secret")
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy(signerCoordinate, AppSignerPolicy.REASONABLE)
            signerLedger.setOpDecision(signerCoordinate, NostrSignerOp.Decrypt, NostrOpDecision.ALLOW)
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            ledger.record(applet, NappletCapability.RELAY, GrantState.ALLOW_ALWAYS)
            val dialogOpen = CompletableDeferred<Unit>()
            val answer = CompletableDeferred<SignerOpGrant>()
            val broker =
                NappletBroker(
                    signer = user,
                    ledger = ledger,
                    consentPrompt = NeverAsk,
                    relay = FixedRelay(listOf(dm)),
                    signerLedger = signerLedger,
                    signerConsentPrompt =
                        NostrSignerConsentPrompt { _, _, _ ->
                            dialogOpen.complete(Unit)
                            answer.await()
                        },
                )
            val followList = async { broker.handle(applet, NappletRequest.Publish(3, emptyArray(), ""), setOf(NappletCapability.RELAY)) }
            dialogOpen.await()

            val events = query(broker)

            assertEquals(listOf("the secret"), events.map { it.content })
            answer.complete(SignerOpGrant.DenyOnce)
            followList.await()
        }
}
