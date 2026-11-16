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
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerConsentPrompt
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import com.vitorpamplona.amethyst.commons.napplet.permissions.InMemoryNappletPermissionStore
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.napplet.permissions.PermissionDecision
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletRequest
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletResponse
import com.vitorpamplona.amethyst.commons.napplet.protocol.toSignerOp
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NappletBrokerTest {
    private val userPriv = "0000000000000000000000000000000000000000000000000000000000000007"
    private val signer = NostrSignerInternal(KeyPair(userPriv.hexToByteArray()))

    private val applet = NappletIdentity(authorPubKey = "aa".repeat(32), identifier = "demo")

    private val allDeclared = NappletCapability.entries.toSet()

    private class ScriptedPrompt(
        private val answer: GrantState,
    ) : NappletConsentPrompt {
        var calls = 0
            private set

        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState {
            calls++
            return answer
        }
    }

    private class RecordingRelay(
        private val queryResult: List<Event> = emptyList(),
    ) : NappletRelayGateway {
        val published = mutableListOf<Event>()

        override suspend fun publish(event: Event): List<String> {
            published.add(event)
            return listOf("wss://relay.example")
        }

        override suspend fun query(filters: List<Filter>): List<Event> = queryResult
    }

    private class RecordingWallet(
        private val preimage: String? = "preimage",
    ) : NappletWalletGateway {
        var calls = 0
            private set

        override suspend fun payInvoice(invoice: String): String? {
            calls++
            return preimage
        }
    }

    /** A non-internal signer that stands in for a remote (NIP-46) / external (NIP-55) signer. */
    private class FakeExternalSigner(
        pubKey: HexKey,
    ) : NostrSigner(pubKey) {
        override fun isWriteable() = true

        override suspend fun <T : Event> sign(
            createdAt: Long,
            kind: Int,
            tags: Array<Array<String>>,
            content: String,
        ): T = throw NotImplementedError()

        override suspend fun nip04Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ): String = throw NotImplementedError()

        override suspend fun nip04Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String = throw NotImplementedError()

        override suspend fun nip44Encrypt(
            plaintext: String,
            toPublicKey: HexKey,
        ): String = throw NotImplementedError()

        override suspend fun nip44Decrypt(
            ciphertext: String,
            fromPublicKey: HexKey,
        ): String = throw NotImplementedError()

        override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent = throw NotImplementedError()

        override suspend fun deriveKey(nonce: HexKey): HexKey = throw NotImplementedError()

        override suspend fun signPsbt(psbtHex: String): String = throw NotImplementedError()

        override fun hasForegroundSupport() = true
    }

    private class MapStorage : NappletStorage {
        val data = mutableMapOf<String, String>()

        private fun k(
            coordinate: String,
            key: String,
        ) = "$coordinate::$key"

        override suspend fun get(
            coordinate: String,
            key: String,
        ): String? = data[k(coordinate, key)]

        override suspend fun set(
            coordinate: String,
            key: String,
            value: String,
        ) {
            data[k(coordinate, key)] = value
        }

        override suspend fun remove(
            coordinate: String,
            key: String,
        ) {
            data.remove(k(coordinate, key))
        }

        override suspend fun keys(coordinate: String): List<String> = data.keys.filter { it.startsWith("$coordinate::") }.map { it.removePrefix("$coordinate::") }
    }

    private fun broker(
        prompt: NappletConsentPrompt,
        relay: NappletRelayGateway? = null,
        storage: NappletStorage? = null,
        wallet: NappletWalletGateway? = null,
        resource: NappletResourceGateway? = null,
        signer: NostrSigner = this.signer,
        ledger: NappletPermissionLedger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
    ) = NappletBroker(
        signer = signer,
        ledger = ledger,
        consentPrompt = prompt,
        relay = relay,
        storage = storage,
        wallet = wallet,
        resource = resource,
    )

    @Test
    fun getPublicKeyReturnsTheUsersKeyWhenAllowed() =
        runTest {
            val response = broker(ScriptedPrompt(GrantState.ALLOW_ONCE)).handle(applet, NappletRequest.GetPublicKey, allDeclared)
            assertEquals(NappletResponse.PublicKey(signer.pubKey), response)
        }

    @Test
    fun undeclaredCapabilityIsDeniedWithoutPrompting() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)

            // The manifest declared only RELAY, but the applet asks to sign (IDENTITY).
            val response = broker(prompt).handle(applet, NappletRequest.GetPublicKey, setOf(NappletCapability.RELAY))

            assertIs<NappletResponse.Denied>(response)
            assertEquals(0, prompt.calls) // never reaches the user — refused at the declaration gate
        }

    @Test
    fun standingDenyBlocksWithoutPrompting() =
        runTest {
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.DENY)
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)

            val response = broker(prompt, ledger = ledger).handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertIs<NappletResponse.Denied>(response)
            assertEquals(0, prompt.calls) // never asked the user — the standing DENY is authoritative
        }

    @Test
    fun userDeclineYieldsDenied() =
        runTest {
            val response = broker(ScriptedPrompt(GrantState.DENY)).handle(applet, NappletRequest.GetPublicKey, allDeclared)
            assertIs<NappletResponse.Denied>(response)
        }

    @Test
    fun allowAlwaysPromptsOnceThenRemembers() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)
            val broker = broker(prompt)

            broker.handle(applet, NappletRequest.GetPublicKey, allDeclared)
            broker.handle(applet, NappletRequest.GetPublicKey, allDeclared)
            broker.handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertEquals(1, prompt.calls) // only the first request prompted
        }

    /** A prompt that blocks inside [request] until [gate] is released, tracking peak concurrency. */
    private class GatedPrompt(
        private val answer: GrantState,
    ) : NappletConsentPrompt {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
            private set
        var maxActive = 0
            private set
        private var active = 0

        override suspend fun request(
            identity: NappletIdentity,
            capability: NappletCapability,
            request: NappletRequest,
        ): GrantState {
            calls++
            active++
            if (active > maxActive) maxActive = active
            try {
                gate.await()
                return answer
            } finally {
                active--
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun concurrentRequestsForOneCapabilitySerializeIntoASinglePrompt() =
        runTest {
            // Five capability calls land at once (a napplet hitting relays/storage/identity on load).
            // Without serialization each would launch its own consent prompt concurrently; the host
            // can only show one, so the rest are dropped and hang. The broker must queue them through
            // a single prompt, and siblings must honor the grant the first one records.
            val prompt = GatedPrompt(GrantState.ALLOW_ALWAYS)
            val broker = broker(prompt)

            val jobs = List(5) { async { broker.handle(applet, NappletRequest.GetPublicKey, allDeclared) } }
            advanceUntilIdle() // everyone reaches the gate/lock; only the lock holder is inside request()

            assertEquals(1, prompt.maxActive) // never two prompts at once (the bug would show 5)

            prompt.gate.complete(Unit)
            val responses = jobs.awaitAll()

            assertEquals(1, prompt.calls) // siblings honored the recorded ALLOW_ALWAYS instead of re-prompting
            responses.forEach { assertEquals(NappletResponse.PublicKey(signer.pubKey), it) }
        }

    /** A first-connect prompt that returns a scripted sequence of answers and counts its calls. */
    private class ScriptedConnectPrompt(
        private vararg val answers: AppConnectResult,
    ) : NostrConnectPrompt {
        var calls = 0
            private set

        /** The capabilities the last call was asked to disclose, so tests can assert the dialog got them. */
        var lastDeclared: Set<NappletCapability> = emptySet()
            private set

        override suspend fun request(
            identity: NappletIdentity,
            declared: Set<NappletCapability>,
        ): AppConnectResult {
            val answer = answers[minOf(calls, answers.size - 1)]
            calls++
            lastDeclared = declared
            return answer
        }
    }

    /** A per-op signer consent prompt that always answers [answer] and counts its calls. */
    private class ScriptedSignerPrompt(
        private val answer: SignerOpGrant,
    ) : NostrSignerConsentPrompt {
        var calls = 0
            private set

        override suspend fun request(
            identity: NappletIdentity,
            op: NostrSignerOp,
            request: NappletRequest,
        ): SignerOpGrant {
            calls++
            return answer
        }
    }

    @Test
    fun aParanoidSignatureAsksOnceNotTwice() =
        runTest {
            // Under PARANOID the capability ledger is empty, so a signature used to ask twice: a
            // generic "this site wants to use Relays" prompt, then the signer prompt with the event.
            // The signer prompt alone decides now.
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.PARANOID)
            val capabilityPrompt = ScriptedPrompt(GrantState.ALLOW_ONCE)
            val opPrompt = ScriptedSignerPrompt(SignerOpGrant.AllowOnce)
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = capabilityPrompt,
                    relay = RecordingRelay(),
                    signerLedger = signerLedger,
                    signerConsentPrompt = opPrompt,
                )

            val sign = NappletRequest.SignEvent(kind = 22242, tags = arrayOf(arrayOf("challenge", "x")), content = "", createdAt = 1)
            assertIs<NappletResponse.Published>(broker.handle(applet, sign, allDeclared))
            assertEquals(0, capabilityPrompt.calls)
            assertEquals(1, opPrompt.calls)

            // Declining in that one prompt still refuses.
            val denying =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = capabilityPrompt,
                    relay = RecordingRelay(),
                    signerLedger = signerLedger,
                    signerConsentPrompt = ScriptedSignerPrompt(SignerOpGrant.DenyOnce),
                )
            assertIs<NappletResponse.Denied>(denying.handle(applet, sign, allDeclared))
            assertEquals(0, capabilityPrompt.calls)
        }

    @Test
    fun revokingAnAppDropsItsLiveSessionSignerGrants() =
        runTest {
            // Regression: "allow for this session" grants live in the broker, keyed by the
            // account-namespaced coordinate (`napplet:<signer>:<coordinate>|<op>`), while the
            // Connected Apps UI only ever holds the BARE coordinate. Revoking used to clear the
            // persisted ledgers and leave the session grants matching nothing — so a revoked app
            // kept signing for as long as any applet surface stayed open.
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            // PARANOID asks for every op, so each publish either prompts or rides a session grant.
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.PARANOID)

            val opPrompt = ScriptedSignerPrompt(SignerOpGrant.AllowForSession(NostrSignerOp.SignKind(1)))
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    relay = RecordingRelay(),
                    signerLedger = signerLedger,
                    signerConsentPrompt = opPrompt,
                )
            val publish = NappletRequest.Publish(kind = 1, tags = arrayOf(arrayOf("t", "napplet")), content = "gm")

            // 1. First publish prompts, and the user allows for the session.
            assertIs<NappletResponse.Published>(broker.handle(applet, publish, allDeclared))
            assertEquals(1, opPrompt.calls)

            // 2. The session grant carries the next publish with no prompt — that's the point of it.
            assertIs<NappletResponse.Published>(broker.handle(applet, publish, allDeclared))
            assertEquals(1, opPrompt.calls)

            // 3. The user revokes the app in Connected Apps, which passes the bare coordinate.
            broker.revokeSessionGrants(applet.coordinate)

            // 4. ...so the app has to ask again rather than riding the dead grant.
            assertIs<NappletResponse.Published>(broker.handle(applet, publish, allDeclared))
            assertEquals(2, opPrompt.calls)
        }

    @Test
    fun revokingOneAppLeavesAnotherAppsSessionGrantsAlone() =
        runTest {
            // The prefix match must not be so loose that revoking one app disarms every other one
            // the user is still using.
            val other = NappletIdentity(authorPubKey = "bb".repeat(32), identifier = "other")
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.PARANOID)
            signerLedger.setPolicy("napplet:${signer.pubKey}:${other.coordinate}", AppSignerPolicy.PARANOID)

            val opPrompt = ScriptedSignerPrompt(SignerOpGrant.AllowForSession(NostrSignerOp.SignKind(1)))
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    relay = RecordingRelay(),
                    signerLedger = signerLedger,
                    signerConsentPrompt = opPrompt,
                )
            val publish = NappletRequest.Publish(kind = 1, tags = arrayOf(arrayOf("t", "napplet")), content = "gm")

            broker.handle(applet, publish, allDeclared) // applet prompts once
            broker.handle(other, publish, allDeclared) // other prompts once
            assertEquals(2, opPrompt.calls)

            broker.revokeSessionGrants(applet.coordinate)

            // The other app is untouched and still rides its own session grant.
            assertIs<NappletResponse.Published>(broker.handle(other, publish, allDeclared))
            assertEquals(2, opPrompt.calls)
        }

    @Test
    fun aGrantFromOneAccountNeverAuthorizesTheSameAppUnderAnother() =
        runTest {
            // The signer permission store is shared across accounts (and with NIP-46), so the napplet
            // coordinate must carry the account. Without that, "always allow" granted by one npub
            // silently authorized signing under every other npub on the device — which would defeat
            // the point of keeping a pseudonymous account separate from a real one.
            val store = InMemoryNostrSignerPermissionStore()
            val otherSigner = NostrSignerInternal(KeyPair())

            fun brokerFor(who: NostrSignerInternal) =
                NappletBroker(
                    signer = who,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    signerLedger = NostrSignerPermissionLedger(store),
                    nostrConnectPrompt = ScriptedConnectPrompt(AppConnectResult.Connected(AppSignerPolicy.FULL_TRUST)),
                )

            // Account A connects the applet and fully trusts it.
            assertEquals(
                NappletResponse.PublicKey(signer.pubKey),
                brokerFor(signer).handle(applet, NappletRequest.GetPublicKey, allDeclared),
            )
            assertEquals(AppSignerPolicy.FULL_TRUST, store.loadPolicy("napplet:${signer.pubKey}:${applet.coordinate}"))

            // Account B has granted the very same applet nothing.
            assertNull(store.loadPolicy("napplet:${otherSigner.pubKey}:${applet.coordinate}"))

            // ...so B's broker must run its own first-connect flow rather than inheriting A's trust.
            val bConnect = ScriptedConnectPrompt(AppConnectResult.Cancelled)
            val bBroker =
                NappletBroker(
                    signer = otherSigner,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    signerLedger = NostrSignerPermissionLedger(store),
                    nostrConnectPrompt = bConnect,
                )
            assertIs<NappletResponse.Denied>(bBroker.handle(applet, NappletRequest.GetPublicKey, allDeclared))
            assertEquals(1, bConnect.calls)
        }

    @Test
    fun cancellingFirstConnectThenRetryingRePromptsOnceCooldownLapses() =
        runTest {
            // Repro for the reported bug: dismissing the "Connect to Nostr" dialog (Back) used to
            // suppress every future prompt for the broker's whole lifetime, with nothing in Connected
            // Apps to clear. The user could never re-trigger login. After the fix the suppression is a
            // short self-clearing cooldown: a deliberate retry past it prompts again.
            var clock = 1_000L
            val connect = ScriptedConnectPrompt(AppConnectResult.Cancelled, AppConnectResult.Connected(AppSignerPolicy.REASONABLE))
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.DENY),
                    signerLedger = signerLedger,
                    nostrConnectPrompt = connect,
                    nowMillis = { clock },
                )

            // 1. User dismisses the dialog → Denied, and no policy is persisted (nothing to clear).
            assertIs<NappletResponse.Denied>(broker.handle(applet, NappletRequest.GetPublicKey, allDeclared))
            assertNull(signerLedger.store.loadPolicy(applet.coordinate))

            // 2. A request inside the cooldown is suppressed silently (no second dialog) — this drains
            //    the load-time burst without re-prompting per request.
            assertIs<NappletResponse.Denied>(broker.handle(applet, NappletRequest.GetPublicKey, allDeclared))
            assertEquals(1, connect.calls)

            // 3. The user retries after the cooldown lapses → the dialog shows again and they connect.
            clock += 10_000L
            assertEquals(NappletResponse.PublicKey(signer.pubKey), broker.handle(applet, NappletRequest.GetPublicKey, allDeclared))
            assertEquals(2, connect.calls)
            // Signer grants are stored under the account-scoped coordinate, not the bare one.
            assertEquals(AppSignerPolicy.REASONABLE, signerLedger.store.loadPolicy("napplet:${signer.pubKey}:${applet.coordinate}"))
        }

    @Test
    fun publishSignsTheTemplateAsTheUserAndSends() =
        runTest {
            val relay = RecordingRelay()
            val request = NappletRequest.Publish(kind = 1, tags = arrayOf(arrayOf("t", "napplet")), content = "gm")

            val response = broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = relay).handle(applet, request, allDeclared)

            // The shell signs the unsigned template and resolves to the signed event + relays.
            assertIs<NappletResponse.Published>(response)
            val event = response.event
            assertEquals(signer.pubKey, event.pubKey) // applet supplied no pubkey; the shell fixed it to the user
            assertEquals(1, event.kind)
            assertEquals("gm", event.content)
            assertTrue(event.verify()) // id + signature are real
            assertEquals(listOf("wss://relay.example"), response.relays)
            assertEquals(1, relay.published.size)
            assertEquals(event.id, relay.published.first().id)
        }

    @Test
    fun publishWithoutAGatewayIsUnsupported() =
        runTest {
            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = null)
                    .handle(applet, NappletRequest.Publish(1, emptyArray(), "hi"), allDeclared)
            assertIs<NappletResponse.Unsupported>(response)
        }

    @Test
    fun relayDenyDoesNotReachTheGateway() =
        runTest {
            val relay = RecordingRelay()

            val response =
                broker(ScriptedPrompt(GrantState.DENY), relay = relay)
                    .handle(applet, NappletRequest.Publish(1, emptyArray(), "blocked"), allDeclared)

            assertIs<NappletResponse.Denied>(response)
            assertTrue(relay.published.isEmpty())
        }

    @Test
    fun queryReturnsTheGatewayResults() =
        runTest {
            val cached: Event = signer.sign(1L, 1, emptyArray(), "cached")
            val relay = RecordingRelay(queryResult = listOf(cached))

            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), relay = relay)
                    .handle(applet, NappletRequest.QueryEvents(listOf(Filter(kinds = listOf(1)))), allDeclared)

            assertEquals(NappletResponse.Events(listOf(cached)), response)
        }

    @Test
    fun storageRoundTripsAndIsScopedToTheApplet() =
        runTest {
            val storage = MapStorage()
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ALWAYS), storage = storage)

            assertEquals(NappletResponse.Done, broker.handle(applet, NappletRequest.StorageSet("k", "v"), allDeclared))
            assertEquals(NappletResponse.StorageValue("v"), broker.handle(applet, NappletRequest.StorageGet("k"), allDeclared))

            // Shared storage is scoped to the verified artifact identity, not just the d-tag.
            assertEquals("v", storage.data["${applet.storageCoordinate}::k"])

            assertEquals(NappletResponse.Done, broker.handle(applet, NappletRequest.StorageRemove("k"), allDeclared))
            assertEquals(NappletResponse.StorageValue(null), broker.handle(applet, NappletRequest.StorageGet("k"), allDeclared))
        }

    @Test
    fun storageKeysListsOnlyThisAppletsKeys() =
        runTest {
            val storage = MapStorage()
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ALWAYS), storage = storage)
            val other = NappletIdentity(authorPubKey = "bb".repeat(32), identifier = "other")

            broker.handle(applet, NappletRequest.StorageSet("a", "1"), allDeclared)
            broker.handle(applet, NappletRequest.StorageSet("b", "2"), allDeclared)
            broker.handle(other, NappletRequest.StorageSet("c", "3"), allDeclared)

            val response = broker.handle(applet, NappletRequest.StorageKeys(), allDeclared)
            assertIs<NappletResponse.Strings>(response)
            assertEquals(setOf("a", "b"), response.values.toSet()) // never sees the other applet's "c"
        }

    @Test
    fun storageWithoutAGatewayIsUnsupported() =
        runTest {
            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), storage = null)
                    .handle(applet, NappletRequest.StorageGet("k"), allDeclared)
            assertIs<NappletResponse.Unsupported>(response)
        }

    @Test
    fun walletWithoutAGatewayIsUnsupported() =
        runTest {
            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE))
                    .handle(applet, NappletRequest.PayInvoice("lnbc1..."), allDeclared)
            assertIs<NappletResponse.Unsupported>(response)
        }

    @Test
    fun walletConfirmsEveryPaymentEvenAfterAllowAlways() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS) // user keeps tapping "always"
            val wallet = RecordingWallet()
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            val broker = broker(prompt, wallet = wallet, ledger = ledger)

            broker.handle(applet, NappletRequest.PayInvoice("lnbc1"), allDeclared)
            broker.handle(applet, NappletRequest.PayInvoice("lnbc1"), allDeclared)

            assertEquals(2, prompt.calls) // every payment prompts
            assertEquals(2, wallet.calls)
            // ALLOW_ALWAYS must not have been persisted for a per-use capability.
            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.VALUE))
        }

    @Test
    fun externalSignerIsPromptedByAmethystForIdentity() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.DENY)
            val external = FakeExternalSigner("dd".repeat(32))

            val response =
                broker(prompt, signer = external).handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertIs<NappletResponse.Denied>(response) // Amethyst asked and user denied
            assertEquals(1, prompt.calls) // Amethyst prompts all signer types
        }

    @Test
    fun externalSignerAllowedByAmethystReturnsPublicKey() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ONCE)
            val external = FakeExternalSigner("dd".repeat(32))

            val response =
                broker(prompt, signer = external).handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertEquals(NappletResponse.PublicKey(external.pubKey), response)
            assertEquals(1, prompt.calls) // Amethyst prompted before the external signer
        }

    @Test
    fun externalSignerStillHonorsAStandingDeny() =
        runTest {
            val external = FakeExternalSigner("dd".repeat(32))
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.DENY)

            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ONCE), signer = external, ledger = ledger)
                    .handle(applet, NappletRequest.GetPublicKey, allDeclared)

            assertIs<NappletResponse.Denied>(response)
        }

    @Test
    fun identityReadReturnsGatewayJsonOrUnsupported() =
        runTest {
            val gateway =
                NappletIdentityGateway { method, _ ->
                    if (method == "getFollows") """["aa","bb"]""" else null
                }
            val broker =
                NappletBroker(
                    signer,
                    NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    ScriptedPrompt(GrantState.ALLOW_ONCE),
                    identityReads = gateway,
                )

            val implemented = broker.handle(applet, NappletRequest.IdentityRead("getFollows"), allDeclared)
            assertIs<NappletResponse.Json>(implemented)
            assertEquals("""["aa","bb"]""", implemented.raw)

            // A method the gateway doesn't implement degrades gracefully to Unsupported.
            assertIs<NappletResponse.Unsupported>(broker.handle(applet, NappletRequest.IdentityRead("getZaps"), allDeclared))
        }

    @Test
    fun identityReadIsUnsupportedWithoutAGateway() =
        runTest {
            val response = broker(ScriptedPrompt(GrantState.ALLOW_ONCE)).handle(applet, NappletRequest.IdentityRead("getProfile"), allDeclared)
            assertIs<NappletResponse.Unsupported>(response)
        }

    @Test
    fun resourceAndUploadAreUnsupportedWithoutGateways() =
        runTest {
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ONCE))
            assertIs<NappletResponse.Unsupported>(broker.handle(applet, NappletRequest.ResourceBytes("https://x"), allDeclared))
            assertIs<NappletResponse.Unsupported>(broker.handle(applet, NappletRequest.UploadBlob(ByteArray(0), "image/png"), allDeclared))
        }

    private class FakeUpload(
        private val server: Boolean = true,
        override val maxBytes: Long = 4,
    ) : NappletUploadGateway {
        var uploads = 0

        override suspend fun hasServer() = server

        override suspend fun upload(
            bytes: ByteArray,
            contentType: String,
            filename: String?,
        ): NappletUploadResult {
            uploads++
            return NappletUploadResult("https://blossom.example/h$uploads", "h$uploads", bytes.size.toLong(), contentType)
        }
    }

    private fun uploadBroker(
        prompt: NappletConsentPrompt,
        upload: NappletUploadGateway,
    ) = NappletBroker(signer = signer, ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()), consentPrompt = prompt, upload = upload)

    @Test
    fun uploadFollowsNapUpload() =
        runTest {
            val gateway = FakeUpload()
            val broker = uploadBroker(ScriptedPrompt(GrantState.ALLOW_ALWAYS), gateway)

            val done = broker.handle(applet, NappletRequest.UploadBlob(byteArrayOf(1, 2), "image/png"), allDeclared)
            assertIs<NappletResponse.Uploaded>(done)
            assertEquals("h1", done.uploadId)
            assertTrue(done.updatedAt > 0)
            // The status of its own upload, and nobody else's.
            assertEquals(done, broker.handle(applet, NappletRequest.UploadStatus("h1"), allDeclared))
            val other = NappletIdentity(authorPubKey = "bb".repeat(32), identifier = "other")
            assertIs<NappletResponse.Failed>(broker.handle(other, NappletRequest.UploadStatus("h1"), allDeclared))

            // Refused before anything is sent: another rail, too many bytes.
            assertEquals("unsupported rail", (broker.handle(applet, NappletRequest.UploadBlob(byteArrayOf(1), "image/png", rail = "nip96"), allDeclared) as NappletResponse.Failed).reason)
            assertEquals("file too large", (broker.handle(applet, NappletRequest.UploadBlob(ByteArray(5), "image/png"), allDeclared) as NappletResponse.Failed).reason)
            assertEquals(1, gateway.uploads)
        }

    @Test
    fun uploadWithoutAServerSaysSoAndInfoNeverPrompts() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ONCE)
            val broker = uploadBroker(prompt, FakeUpload(server = false))
            assertEquals(NappletResponse.UploadInfo(enabled = false, maxBytes = 4), broker.handle(applet, NappletRequest.UploadInfo, allDeclared))
            assertEquals(0, prompt.calls, "info is advisory introspection")
            assertEquals("no server configured", (broker.handle(applet, NappletRequest.UploadBlob(byteArrayOf(1), "image/png"), allDeclared) as NappletResponse.Failed).reason)
        }

    @Test
    fun resourceInfoAndTypedFailuresFollowTheCurrentNapContract() =
        runTest {
            val resource =
                NappletResourceGateway { _, _ ->
                    NappletResourceResult.Failure("blocked-by-policy", "private target")
                }
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ALWAYS), resource = resource)

            val info = broker.handle(applet, NappletRequest.ResourceInfo, allDeclared)
            assertIs<NappletResponse.ResourceInfo>(info)
            assertEquals(listOf("data", "https", "blossom", "nostr"), info.schemes)
            assertEquals(10L * 1024L * 1024L, info.maxBytes)

            assertEquals(
                NappletResponse.ResourceFailure("blocked-by-policy", "private target"),
                broker.handle(applet, NappletRequest.ResourceBytes("https://internal.example"), allDeclared),
            )
        }

    @Test
    fun resourceBytesManyPreservesOrderAndSiblingResults() =
        runTest {
            val resource =
                NappletResourceGateway { url, _ ->
                    if (url.endsWith("ok")) {
                        NappletResourceResult.Success(NappletResource("ok".encodeToByteArray(), "text/plain"))
                    } else {
                        NappletResourceResult.Failure("not-found")
                    }
                }
            val response =
                broker(ScriptedPrompt(GrantState.ALLOW_ALWAYS), resource = resource)
                    .handle(applet, NappletRequest.ResourceBytesMany(listOf("https://x/ok", "https://x/missing")), allDeclared)

            assertIs<NappletResponse.ResourceItems>(response)
            assertEquals(listOf("https://x/ok", "https://x/missing"), response.items.map { it.url })
            assertIs<NappletResponse.Bytes>(response.items[0].resource)
            assertEquals("not-found", response.items[1].error)
        }

    @Test
    fun resourceBytesManyRejectsInvalidBulkSizesWithNapErrorCodes() =
        runTest {
            val resource = NappletResourceGateway { _, _ -> error("invalid bulk must not fetch") }
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ALWAYS), resource = resource)

            val empty = broker.handle(applet, NappletRequest.ResourceBytesMany(emptyList()), allDeclared)
            val tooLarge = broker.handle(applet, NappletRequest.ResourceBytesMany(List(17) { "data:,x" }), allDeclared)

            assertIs<NappletResponse.ResourceFailure>(empty)
            assertEquals("invalid-request", empty.error)
            assertIs<NappletResponse.ResourceFailure>(tooLarge)
            assertEquals("too-large", tooLarge.error)
        }

    // ---- NIP-44 (window.nostr.nip44) ------------------------------------------------------------
    //
    // The gap these close: a page could sign but not encrypt, so it could not build a kind:13 seal
    // and every NIP-59 / NIP-17 flow was unreachable through the in-app browser.

    @Test
    fun nip44RoundTripsThroughTheBrokerWithoutExposingTheKey() =
        runTest {
            val peer = NostrSignerInternal(KeyPair("11".repeat(32).hexToByteArray()))
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ALWAYS))

            val encrypted =
                broker.handle(applet, NappletRequest.Nip44Encrypt(peer.pubKey, "gm from the nsite"), allDeclared)
            assertIs<NappletResponse.Text>(encrypted)

            // The peer decrypts it with its own key: proof this is real NIP-44 to that pubkey and
            // not some shell-local encoding.
            assertEquals("gm from the nsite", peer.nip44Decrypt(encrypted.value, signer.pubKey))

            // ...and back the other way, which is what an inbound seal needs.
            val sealed = peer.nip44Encrypt("hello back", signer.pubKey)
            val decrypted = broker.handle(applet, NappletRequest.Nip44Decrypt(peer.pubKey, sealed), allDeclared)
            assertIs<NappletResponse.Text>(decrypted)
            assertEquals("hello back", decrypted.value)
        }

    @Test
    fun nip44IsRefusedWhenTheHostDidNotDeclareTheSignerCapability() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)
            val broker = broker(prompt)

            // A locked napplet's declared set can never contain SIGNER (no NAP domain maps to it),
            // so the request must die at the capability gate without ever reaching a prompt.
            val response =
                broker.handle(
                    applet,
                    NappletRequest.Nip44Decrypt("bb".repeat(32), "cipher"),
                    setOf(NappletCapability.IDENTITY, NappletCapability.RELAY),
                )

            assertIs<NappletResponse.Denied>(response)
            assertEquals(NappletCapability.SIGNER, response.capability)
            assertEquals(0, prompt.calls)
        }

    @Test
    fun noNapDomainCanEverGrantTheSignerCapability() {
        // The website-only guarantee is structural, not a policy someone can misconfigure: if any
        // domain string ever mapped to SIGNER, a manifest could ask for the user's decryption.
        assertNull(NappletCapability.fromNapDomain("signer"))
        assertTrue(NappletCapability.supportedNapDomains.none { NappletCapability.fromNapDomain(it) == NappletCapability.SIGNER })
    }

    @Test
    fun decryptAsksEveryTimeUnderTheReasonablePolicyWhileEncryptDoesNot() =
        runTest {
            // The asymmetry is the point: encrypting writes content the user is already composing,
            // decrypting reads private content, so only the latter keeps prompting.
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.REASONABLE)

            val opPrompt = ScriptedSignerPrompt(SignerOpGrant.AllowOnce)
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    signerLedger = signerLedger,
                    signerConsentPrompt = opPrompt,
                )

            val peer = NostrSignerInternal(KeyPair("22".repeat(32).hexToByteArray()))
            broker.handle(applet, NappletRequest.Nip44Encrypt(peer.pubKey, "a"), allDeclared)
            broker.handle(applet, NappletRequest.Nip44Encrypt(peer.pubKey, "b"), allDeclared)
            assertEquals(0, opPrompt.calls)

            val sealed = peer.nip44Encrypt("secret", signer.pubKey)
            broker.handle(applet, NappletRequest.Nip44Decrypt(peer.pubKey, sealed), allDeclared)
            broker.handle(applet, NappletRequest.Nip44Decrypt(peer.pubKey, sealed), allDeclared)
            assertEquals(2, opPrompt.calls)
        }

    @Test
    fun anEncryptedPublishIsJudgedByTheKindItSigns() =
        runTest {
            // Allowed to encrypt is not allowed to replace the follow list: the event published is
            // a kind 3 either way, so it asks as a plain kind-3 publish does.
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.REASONABLE)
            val opPrompt = ScriptedSignerPrompt(SignerOpGrant.DenyOnce)
            val relay = RecordingRelay()
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    relay = relay,
                    signerLedger = signerLedger,
                    signerConsentPrompt = opPrompt,
                )

            val peer = NostrSignerInternal(KeyPair("44".repeat(32).hexToByteArray()))
            val response =
                broker.handle(applet, NappletRequest.PublishEncrypted(3, arrayOf(arrayOf("p", peer.pubKey)), "{}", peer.pubKey, "nip44"), allDeclared)
            assertIs<NappletResponse.Denied>(response)
            assertEquals(1, opPrompt.calls)
            assertTrue(relay.published.isEmpty())
        }

    @Test
    fun aDeclinedDecryptReturnsNoPlaintext() =
        runTest {
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.PARANOID)

            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    signerLedger = signerLedger,
                    signerConsentPrompt = ScriptedSignerPrompt(SignerOpGrant.DenyOnce),
                )

            val peer = NostrSignerInternal(KeyPair("33".repeat(32).hexToByteArray()))
            val sealed = peer.nip44Encrypt("secret", signer.pubKey)

            val response = broker.handle(applet, NappletRequest.Nip44Decrypt(peer.pubKey, sealed), allDeclared)
            assertIs<NappletResponse.Denied>(response)
        }

    @Test
    fun theWebsiteCapabilitySetCarriesEverythingNip07Needs() {
        // Two places mint this set (the nSite HostProfile and the in-app browser's per-origin
        // token). They drifted once: the browser kept IDENTITY+RELAY while the shim advertised
        // nip44, so every call was denied by a capability the page was told it had.
        assertTrue(NappletCapability.IDENTITY in NappletCapability.WEBSITE_CAPABILITIES)
        assertTrue(NappletCapability.RELAY in NappletCapability.WEBSITE_CAPABILITIES)
        assertTrue(NappletCapability.SIGNER in NappletCapability.WEBSITE_CAPABILITIES)
    }

    @Test
    fun nip44WorksUnderTheWebsiteCapabilitySetAlone() =
        runTest {
            // What a browsed page actually gets — not `allDeclared`, which would hide a missing grant.
            val peer = NostrSignerInternal(KeyPair("44".repeat(32).hexToByteArray()))
            val broker = broker(ScriptedPrompt(GrantState.ALLOW_ALWAYS))

            val response =
                broker.handle(
                    applet,
                    NappletRequest.Nip44Encrypt(peer.pubKey, "gm"),
                    NappletCapability.WEBSITE_CAPABILITIES,
                )

            assertIs<NappletResponse.Text>(response)
            assertEquals("gm", peer.nip44Decrypt(response.value, signer.pubKey))
        }

    @Test
    fun anAlwaysAllowForOneCounterpartyDoesNotUnlockTheRest() =
        runTest {
            // The dialog can hand back DecryptFrom(alice) instead of the broad Decrypt. Recording the
            // REQUESTED op there would silently upgrade "only Alice" into every conversation forever.
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.REASONABLE)

            val alice = NostrSignerInternal(KeyPair("55".repeat(32).hexToByteArray()))
            val bob = NostrSignerInternal(KeyPair("66".repeat(32).hexToByteArray()))

            val opPrompt = ScriptedSignerPrompt(SignerOpGrant.AllowForOp(NostrSignerOp.DecryptFrom(alice.pubKey)))
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    signerLedger = signerLedger,
                    signerConsentPrompt = opPrompt,
                )

            val fromAlice = alice.nip44Encrypt("hi", signer.pubKey)
            val fromBob = bob.nip44Encrypt("hi", signer.pubKey)

            // 1. First read from Alice prompts; the user allows, but only for Alice.
            assertIs<NappletResponse.Text>(broker.handle(applet, NappletRequest.Nip44Decrypt(alice.pubKey, fromAlice), allDeclared))
            assertEquals(1, opPrompt.calls)

            // 2. Reading Alice again rides the narrow grant — no second prompt.
            assertIs<NappletResponse.Text>(broker.handle(applet, NappletRequest.Nip44Decrypt(alice.pubKey, fromAlice), allDeclared))
            assertEquals(1, opPrompt.calls)

            // 3. Bob is a different conversation and must ask again. If the broad Decrypt had been
            //    recorded in step 1, this would sail through without the user ever agreeing to it.
            broker.handle(applet, NappletRequest.Nip44Decrypt(bob.pubKey, fromBob), allDeclared)
            assertEquals(2, opPrompt.calls)

            // The broad grant was never written.
            assertNull(signerLedger.store.loadOpDecision("napplet:${signer.pubKey}:${applet.coordinate}", NostrSignerOp.Decrypt))
        }

    @Test
    fun aSessionGrantIsStoredNoWiderThanTheUserGaveIt() =
        runTest {
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.PARANOID)

            val alice = NostrSignerInternal(KeyPair("77".repeat(32).hexToByteArray()))
            val bob = NostrSignerInternal(KeyPair("88".repeat(32).hexToByteArray()))

            val opPrompt = ScriptedSignerPrompt(SignerOpGrant.AllowForSession(NostrSignerOp.DecryptFrom(alice.pubKey)))
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS),
                    signerLedger = signerLedger,
                    signerConsentPrompt = opPrompt,
                )

            broker.handle(applet, NappletRequest.Nip44Decrypt(alice.pubKey, alice.nip44Encrypt("a", signer.pubKey)), allDeclared)
            assertEquals(1, opPrompt.calls)

            // Same counterparty rides the session grant; a different one must not.
            broker.handle(applet, NappletRequest.Nip44Decrypt(alice.pubKey, alice.nip44Encrypt("a", signer.pubKey)), allDeclared)
            assertEquals(1, opPrompt.calls)
            broker.handle(applet, NappletRequest.Nip44Decrypt(bob.pubKey, bob.nip44Encrypt("b", signer.pubKey)), allDeclared)
            assertEquals(2, opPrompt.calls)
        }

    private val inlineApplet = applet.copy(inline = true, instanceId = "card-1")

    @Test
    fun anAppInsideANoteNeverAsksTheUser() =
        runTest {
            val asks = NappletInlineAsks()
            val prompt = ScriptedPrompt(GrantState.ALLOW_ALWAYS)
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            val broker = NappletBroker(signer = signer, ledger = ledger, consentPrompt = prompt, inlineAsks = asks)

            assertIs<NappletResponse.Denied>(broker.handle(inlineApplet, NappletRequest.GetPublicKey, allDeclared))
            assertEquals(0, prompt.calls)
            // Nothing is held against the app: opening it asks as usual.
            assertEquals(PermissionDecision.ASK, ledger.decide(applet, NappletCapability.IDENTITY))
            assertTrue(asks.asked("card-1").first())
            assertEquals(false, asks.asked("card-2").first())

            assertEquals(NappletResponse.PublicKey(signer.pubKey), broker.handle(applet, NappletRequest.GetPublicKey, allDeclared))
            assertEquals(1, prompt.calls)
        }

    @Test
    fun anAppInsideANoteKeepsWhatTheUserGranted() =
        runTest {
            val asks = NappletInlineAsks()
            val prompt = ScriptedPrompt(GrantState.DENY)
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            ledger.record(applet, NappletCapability.IDENTITY, GrantState.ALLOW_ALWAYS)
            val broker = NappletBroker(signer = signer, ledger = ledger, consentPrompt = prompt, inlineAsks = asks)

            assertEquals(NappletResponse.PublicKey(signer.pubKey), broker.handle(inlineApplet, NappletRequest.GetPublicKey, allDeclared))
            assertEquals(0, prompt.calls)
            assertEquals(false, asks.asked("card-1").first())
        }

    @Test
    fun anAppInsideANoteNeitherConnectsNorSignsWithoutTheUser() =
        runTest {
            val asks = NappletInlineAsks()
            val connect = ScriptedConnectPrompt(AppConnectResult.Connected(AppSignerPolicy.REASONABLE))
            val opPrompt = ScriptedSignerPrompt(SignerOpGrant.AllowOnce)
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ONCE),
                    relay = RecordingRelay(),
                    signerLedger = signerLedger,
                    nostrConnectPrompt = connect,
                    signerConsentPrompt = opPrompt,
                    inlineAsks = asks,
                )
            val sign = NappletRequest.SignEvent(kind = 1, tags = emptyArray(), content = "hi", createdAt = 1)

            // Not connected yet: the first-connect dialog would show.
            assertIs<NappletResponse.Denied>(broker.handle(inlineApplet, sign, allDeclared))
            assertEquals(0, connect.calls)

            // Connected, but every signature asks the user: still nothing shows.
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.PARANOID)
            assertIs<NappletResponse.Denied>(broker.handle(inlineApplet, sign, allDeclared))
            assertEquals(0, opPrompt.calls)
            assertTrue(asks.asked("card-1").first())
        }

    private class FakeOutbox(
        private val read: OutboxRead = OutboxRead(emptyList()),
        private val delivery: (Event) -> OutboxDelivery = { OutboxDelivery(mapOf(RelayUrlNormalizer.normalize("wss://mine.example.com") to true)) },
    ) : NappletOutboxGateway {
        val published = mutableListOf<Event>()
        var publishedTo: Triple<Boolean, List<String>, List<String>>? = null

        override suspend fun getEvent(
            eventId: HexKey,
            author: HexKey?,
            relayHints: List<String>,
            timeoutMs: Long?,
        ) = read

        override suspend fun query(
            filters: List<Filter>,
            authors: List<HexKey>,
            relayHints: List<String>,
            timeoutMs: Long?,
        ) = read

        override suspend fun subscriptionRelays(
            filters: List<Filter>,
            authors: List<HexKey>,
            relayHints: List<String>,
        ) = setOf(RelayUrlNormalizer.normalize("wss://author.example.com"))

        override suspend fun publish(
            event: Event,
            toOutbox: Boolean,
            toInboxes: List<HexKey>,
            relays: List<String>,
        ): OutboxDelivery {
            published += event
            publishedTo = Triple(toOutbox, toInboxes, relays)
            return delivery(event)
        }

        override suspend fun plan(
            authors: List<HexKey>,
            write: Boolean,
        ) = OutboxRelayPlan(listOf("wss://author.example.com/"), OutboxRelayPlan.Source.NIP65)
    }

    private fun template(kind: Int = 30078) = NappletRequest.Publish(kind, arrayOf(arrayOf("d", "save")), "state")

    @Test
    fun anOutboxPublishIsSignedOnceAndDecidedAsTheEventItSigns() =
        runTest {
            val outbox = FakeOutbox()
            val opPrompt = ScriptedSignerPrompt(SignerOpGrant.AllowOnce)
            val shown = mutableListOf<NappletRequest>()
            val signerLedger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            signerLedger.setPolicy("napplet:${signer.pubKey}:${applet.coordinate}", AppSignerPolicy.PARANOID)
            val broker =
                NappletBroker(
                    signer = signer,
                    ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()),
                    consentPrompt = ScriptedPrompt(GrantState.ALLOW_ONCE),
                    signerLedger = signerLedger,
                    signerConsentPrompt =
                        NostrSignerConsentPrompt { identity, op, request ->
                            shown += request
                            opPrompt.request(identity, op, request)
                        },
                    outbox = outbox,
                )

            val request = NappletRequest.OutboxPublish(template(), toOutbox = false, toInboxes = listOf("bb".repeat(32)), relays = listOf("wss://x.example.com"))
            val response = broker.handle(applet, request, allDeclared)

            assertIs<NappletResponse.OutboxPublished>(response)
            assertTrue(response.ok)
            assertEquals(1, outbox.published.size)
            assertEquals(signer.pubKey, outbox.published.single().pubKey)
            assertEquals(30078, outbox.published.single().kind)
            assertEquals(Triple(false, listOf("bb".repeat(32)), listOf("wss://x.example.com")), outbox.publishedTo)
            // The signer prompt saw the event itself, as for relay.publish.
            assertEquals(listOf<NappletRequest>(template()), shown)
            assertEquals(NostrSignerOp.SignKind(30078), request.toSignerOp())
        }

    @Test
    fun anOutboxPublishThatReachedNoneOrMissedAnInboxIsNotOk() =
        runTest {
            val ledger = NappletPermissionLedger(InMemoryNappletPermissionStore())
            val nowhere = FakeOutbox(delivery = { OutboxDelivery(mapOf(RelayUrlNormalizer.normalize("wss://mine.example.com") to false)) })
            val refused = NappletBroker(signer = signer, ledger = ledger, consentPrompt = ScriptedPrompt(GrantState.ALLOW_ONCE), outbox = nowhere).handle(applet, NappletRequest.OutboxPublish(template()), allDeclared)
            assertIs<NappletResponse.OutboxPublished>(refused)
            assertEquals(false, refused.ok)
            assertEquals("publish failed", refused.error)

            val missing = FakeOutbox(delivery = { OutboxDelivery(mapOf(RelayUrlNormalizer.normalize("wss://mine.example.com") to true), listOf("cc".repeat(32))) })
            val partial = NappletBroker(signer = signer, ledger = ledger, consentPrompt = ScriptedPrompt(GrantState.ALLOW_ONCE), outbox = missing).handle(applet, NappletRequest.OutboxPublish(template(1), toInboxes = listOf("cc".repeat(32))), allDeclared)
            assertIs<NappletResponse.OutboxPublished>(partial)
            assertEquals(false, partial.ok)
            assertEquals("relay list unavailable", partial.error)
        }

    @Test
    fun outboxReadsAskOnceAndCheckWhatTheyReturn() =
        runTest {
            val bob = NostrSignerInternal(KeyPair())
            val note = bob.sign<Event>(1, 1, emptyArray(), "hi")
            val other = bob.sign<Event>(2, 1, emptyArray(), "other")
            val prompt = ScriptedPrompt(GrantState.ALLOW_SESSION)
            val outbox = FakeOutbox(read = OutboxRead(listOf(OutboxEvent(other, listOf("wss://bob.example.com/")), OutboxEvent(note)), incomplete = true))
            val broker = NappletBroker(signer = signer, ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()), consentPrompt = prompt, outbox = outbox)

            // getEvent never hands back an event that isn't the one asked for.
            val found = broker.handle(applet, NappletRequest.OutboxGetEvent(note.id), allDeclared)
            assertIs<NappletResponse.OutboxFound>(found)
            assertEquals(note.id, found.found?.event?.id)
            val absent = broker.handle(applet, NappletRequest.OutboxGetEvent("dd".repeat(32)), allDeclared)
            assertIs<NappletResponse.OutboxFound>(absent)
            assertNull(absent.found)
            assertEquals("not found", absent.error)

            val queried = broker.handle(applet, NappletRequest.OutboxQuery(listOf(Filter(authors = listOf(bob.pubKey))), limit = 1), allDeclared)
            assertIs<NappletResponse.OutboxEvents>(queried)
            assertEquals(listOf(other.id), queried.events.map { it.event.id })
            assertTrue(queried.incomplete)

            assertEquals(1, prompt.calls)
        }

    @Test
    fun outboxIsDeniedWhenNotDeclaredAndUnsupportedWithoutAGateway() =
        runTest {
            val prompt = ScriptedPrompt(GrantState.ALLOW_ONCE)
            val withGateway = NappletBroker(signer = signer, ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()), consentPrompt = prompt, outbox = FakeOutbox())
            assertIs<NappletResponse.Denied>(withGateway.handle(applet, NappletRequest.OutboxQuery(listOf(Filter())), setOf(NappletCapability.RELAY)))
            assertEquals(0, prompt.calls)

            val without = NappletBroker(signer = signer, ledger = NappletPermissionLedger(InMemoryNappletPermissionStore()), consentPrompt = prompt)
            assertIs<NappletResponse.Unsupported>(without.handle(applet, NappletRequest.OutboxQuery(listOf(Filter())), allDeclared))
        }
}
