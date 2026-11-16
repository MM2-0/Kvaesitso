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
package com.vitorpamplona.amethyst.commons.connectedApps.nip46

import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppSignerPolicy
import com.vitorpamplona.amethyst.commons.connectedApps.signers.InMemoryNostrSignerPermissionStore
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseError
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponseEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.server.BunkerRequestProcessor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end proof that interactive consent flows through the real dispatch path:
 * [BunkerRequestProcessor] → [Nip46PermissionAuthorizer] → the `opConsent`/`connectConsent`
 * prompts, with a real [NostrSignerInternal] doing the signing. Covers the two outcomes the
 * per-op prompt must produce (a signed event vs. an `unauthorized` error) and a dangerous kind
 * being allowed once the app is FULL_TRUST.
 */
class Nip46ConsentIntegrationTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val client = "c".repeat(64)
    private val coordinate get() = Nip46PermissionAuthorizer.coordinateFor(signer.pubKey, client)

    private fun ledger() = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())

    private fun signRequest(kind: Int) = BunkerRequestSign("1", EventTemplate<Event>(createdAt = 1L, kind = kind, tags = emptyArray(), content = "hi"))

    @Test
    fun askSignPromptedAllowProducesASignedEvent() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer.pubKey,
                    validateSecret = { _, _ -> true },
                    opConsent = { _, _, _, _ -> SignerOpGrant.AllowOnce },
                )
            val processor = BunkerRequestProcessor(signer, { emptySet() }, authorizer)

            val response = processor.process(client, signRequest(1))

            assertTrue(response is BunkerResponseEvent, "an allowed sign returns the signed event")
            assertEquals(signer.pubKey, response.event.pubKey, "the event is signed by the identity key")
        }

    @Test
    fun askSignDeniedReturnsUnauthorized() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer.pubKey,
                    validateSecret = { _, _ -> true },
                    opConsent = { _, _, _, _ -> SignerOpGrant.DenyOnce },
                )
            val processor = BunkerRequestProcessor(signer, { emptySet() }, authorizer)

            val response = processor.process(client, signRequest(1))

            assertTrue(response is BunkerResponseError)
            assertEquals(BunkerRequestProcessor.ERROR_UNAUTHORIZED, response.error)
        }

    @Test
    fun signsARealWorldKind1FromAClientPreservingTheTemplate() =
        runTest {
            // The exact payload a client like Ditto would hand the bunker: a kind-1 note with a
            // `client` tag, a fixed created_at, and content — signed remotely while the key stays on
            // the identity signer (here NostrSignerInternal; in production an external Amber account).
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer.pubKey,
                    validateSecret = { _, _ -> true },
                    // No opConsent wired: proves kind 1 is auto-allowed under REASONABLE (no prompt).
                )
            val processor = BunkerRequestProcessor(signer, { emptySet() }, authorizer)

            val template =
                EventTemplate<Event>(
                    createdAt = 1784214635L,
                    kind = 1,
                    tags = arrayOf(arrayOf("client", "Ditto", "31990:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:ditto")),
                    content = "Posting on Ditto through Amethyst's NIP-46 signer, with the keys fully offline on Amber.",
                )

            val response = processor.process(client, BunkerRequestSign("42", template))

            assertTrue(response is BunkerResponseEvent, "kind 1 signs without a prompt under REASONABLE")
            val event = response.event
            assertEquals(1, event.kind)
            assertEquals(1784214635L, event.createdAt, "the client-supplied created_at is preserved")
            assertEquals(template.content, event.content)
            assertEquals(listOf("client", "Ditto", "31990:781a1527055f74c1f70230f10384609b34548f8ab6a0a6caa74025827f9fdae5:ditto"), event.tags[0].toList())
            assertEquals(signer.pubKey, event.pubKey, "authored by the identity key, never the transport key")
            assertTrue(event.verify(), "the signature and id are valid")
        }

    @Test
    fun fullTrustFromConnectConsentSignsEvenDangerousKinds() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            var opPrompts = 0
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer.pubKey,
                    validateSecret = { _, _ -> true },
                    connectConsent = { _, _, _ -> AppConnectResult.Connected(AppSignerPolicy.FULL_TRUST) },
                    opConsent = { _, _, _, _ ->
                        opPrompts++
                        SignerOpGrant.DenyOnce
                    },
                )
            val processor = BunkerRequestProcessor(signer, { emptySet() }, authorizer)

            authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "x"))
            // kind 0 (profile) is a dangerous kind that REASONABLE would ASK for; FULL_TRUST allows it outright.
            val response = processor.process(client, signRequest(0))

            assertTrue(response is BunkerResponseEvent, "FULL_TRUST signs without prompting")
            assertEquals(0, opPrompts, "a FULL_TRUST app never reaches the per-op prompt")
        }
}
