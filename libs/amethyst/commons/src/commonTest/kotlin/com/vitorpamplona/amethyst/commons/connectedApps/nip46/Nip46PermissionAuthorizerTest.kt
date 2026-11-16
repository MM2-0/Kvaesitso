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
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrOpDecision
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import com.vitorpamplona.quartz.nip46RemoteSigner.server.Nip46ConnectDecision
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Nip46PermissionAuthorizerTest {
    private val signer = "a".repeat(64)
    private val client = "c".repeat(64)
    private val coordinate = Nip46PermissionAuthorizer.coordinateFor(signer, client)

    private fun ledger() = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())

    private fun signRequest(kind: Int) = BunkerRequestSign("1", EventTemplate<Event>(createdAt = 1L, kind = kind, tags = emptyArray(), content = "x"))

    @Test
    fun connectWithValidSecretRegistersReasonablePolicyAndEchoesSecret() =
        runTest {
            val ledger = ledger()
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, s -> s == "good" })

            val decision = authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "good"))

            assertTrue(decision is Nip46ConnectDecision.Accept)
            assertEquals("good", decision.ackSecret)
            assertEquals(AppSignerPolicy.REASONABLE, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun connectWithBadSecretRejectsAndDoesNotRegister() =
        runTest {
            val ledger = ledger()
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, s -> s == "good" })

            val decision = authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "bad"))

            assertTrue(decision is Nip46ConnectDecision.Reject)
            assertEquals(null, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun connectDoesNotDowngradeAnExistingPolicy() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "x"))

            assertEquals(AppSignerPolicy.FULL_TRUST, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun reasonableAppAllowsTextNoteButRefusesDecrypt() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
            assertFalse(authorizer.authorize(client, BunkerRequestNip44Decrypt("2", client, "ct")))
        }

    // ---------------------------------------------------------------------
    // Pairing gate (identity reads)
    // ---------------------------------------------------------------------

    @Test
    fun isPairedIsFalseBeforeConnectAndTrueAfter() =
        runTest {
            val ledger = ledger()
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, s -> s == "good" })

            assertFalse(authorizer.isPaired(client), "a client that never connected must not read the identity")

            authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "good"))

            assertTrue(authorizer.isPaired(client), "a successful connect pairs the client")
        }

    @Test
    fun isPairedStaysFalseAfterARejectedConnect() =
        runTest {
            val ledger = ledger()
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, s -> s == "good" })

            authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "wrong"))

            assertFalse(authorizer.isPaired(client))
        }

    @Test
    fun forgetUnpairsTheClient() =
        runTest {
            val ledger = ledger()
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })
            authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "x"))

            authorizer.forget(client)

            assertFalse(authorizer.isPaired(client))
        }

    // ---------------------------------------------------------------------
    // Per-counterparty decrypt grants
    // ---------------------------------------------------------------------

    private val alice = "1".repeat(64)
    private val bob = "2".repeat(64)

    @Test
    fun aPerCounterpartyDecryptGrantAllowsThatConversationWithoutPrompting() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            var prompts = 0
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    opConsent = { _, _, _, _ ->
                        prompts++
                        SignerOpGrant.DenyOnce
                    },
                )

            ledger.setOpDecision(coordinate, NostrSignerOp.DecryptFrom(alice), NostrOpDecision.ALLOW)

            assertTrue(authorizer.authorize(client, BunkerRequestNip44Decrypt("1", alice, "ct")))
            assertEquals(0, prompts, "a standing narrow grant must not re-prompt")
        }

    @Test
    fun aPerCounterpartyDecryptGrantDoesNotLeakToOtherCounterparties() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            ledger.setOpDecision(coordinate, NostrSignerOp.DecryptFrom(alice), NostrOpDecision.ALLOW)

            assertFalse(authorizer.authorize(client, BunkerRequestNip44Decrypt("1", bob, "ct")), "Bob's messages were never granted")
        }

    @Test
    fun aBroadDecryptDenyIsNotOverriddenByANarrowGrant() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            ledger.setOpDecision(coordinate, NostrSignerOp.Decrypt, NostrOpDecision.DENY)
            ledger.setOpDecision(coordinate, NostrSignerOp.DecryptFrom(alice), NostrOpDecision.ALLOW)

            assertFalse(authorizer.authorize(client, BunkerRequestNip44Decrypt("1", alice, "ct")), "an explicit broad DENY is final")
        }

    @Test
    fun aNarrowRememberFromTheDialogIsPersistedAndReusedForThatCounterpartyOnly() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            var prompts = 0
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    // The dialog's "Always allow for Alice" button returns the NARROW op.
                    opConsent = { _, _, _, _ ->
                        prompts++
                        SignerOpGrant.AllowForOp(NostrSignerOp.DecryptFrom(alice))
                    },
                )

            assertTrue(authorizer.authorize(client, BunkerRequestNip44Decrypt("1", alice, "ct")))
            assertEquals(1, prompts)

            // Second request from Alice reuses the stored narrow grant.
            assertTrue(authorizer.authorize(client, BunkerRequestNip44Decrypt("2", alice, "ct2")))
            assertEquals(1, prompts, "the narrow grant must be remembered")

            // Bob still prompts (and this prompt would grant Alice again, so it is denied).
            authorizer.authorize(client, BunkerRequestNip44Decrypt("3", bob, "ct3"))
            assertEquals(2, prompts, "a different counterparty is a different decision")
        }

    @Test
    fun aNarrowSessionGrantIsScopedToItsOwnCounterparty() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            var prompts = 0
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    opConsent = { _, _, _, _ ->
                        prompts++
                        SignerOpGrant.AllowForSession(NostrSignerOp.DecryptFrom(alice))
                    },
                )

            assertTrue(authorizer.authorize(client, BunkerRequestNip44Decrypt("1", alice, "ct")))
            assertTrue(authorizer.authorize(client, BunkerRequestNip44Decrypt("2", alice, "ct2")))
            assertEquals(1, prompts, "the session grant covers Alice for the rest of the session")

            authorizer.authorize(client, BunkerRequestNip44Decrypt("3", bob, "ct3"))
            assertEquals(2, prompts, "and must NOT cover Bob")
        }

    @Test
    fun paranoidAppRefusesEverythingUntilPerOpGrant() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            assertFalse(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))

            ledger.setOpDecision(coordinate, NostrSignerOp.SignKind(TextNoteEvent.KIND), NostrOpDecision.ALLOW)
            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
        }

    @Test
    fun askWithoutAPromptFailsClosed() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            // No opConsent wired: an ASK op (decrypt under REASONABLE) is denied, never silently allowed.
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })
            assertFalse(authorizer.authorize(client, BunkerRequestNip44Decrypt("2", client, "ct")))
        }

    @Test
    fun askPromptsAndAllowOnceIsNotPersisted() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)
            var prompts = 0
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    opConsent = { _, _, _, _ ->
                        prompts++
                        SignerOpGrant.AllowOnce
                    },
                )

            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
            assertEquals(2, prompts, "allow-once must prompt every time")
        }

    @Test
    fun askPromptAllowForOpIsRememberedAndStopsPrompting() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)
            var prompts = 0
            val op = NostrSignerOp.SignKind(TextNoteEvent.KIND)
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    opConsent = { _, _, _, _ ->
                        prompts++
                        SignerOpGrant.AllowForOp(op)
                    },
                )

            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
            assertEquals(1, prompts, "allow-for-op persists, so the second request does not prompt")
            assertEquals(NostrOpDecision.ALLOW, ledger.store.loadOpDecision(coordinate, op))
        }

    @Test
    fun askPromptDenyOnceRefusesTheRequest() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    opConsent = { _, _, _, _ -> SignerOpGrant.DenyOnce },
                )
            assertFalse(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
        }

    @Test
    fun allowForSessionSkipsFuturePromptsUntilForgotten() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)
            var prompts = 0
            val op = NostrSignerOp.SignKind(TextNoteEvent.KIND)
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    opConsent = { _, _, _, _ ->
                        prompts++
                        SignerOpGrant.AllowForSession(op)
                    },
                )

            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
            assertEquals(1, prompts, "session grant is remembered in memory, no re-prompt")
            assertEquals(null, ledger.store.loadOpDecision(coordinate, op), "session grant is not persisted")

            authorizer.forget(client)
            assertTrue(authorizer.authorize(client, signRequest(TextNoteEvent.KIND)))
            assertEquals(2, prompts, "forgetting clears the session grant, so it prompts again")
        }

    @Test
    fun firstConnectConsentChoosesTheTrustLevel() =
        runTest {
            val ledger = ledger()
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    connectConsent = { _, _, _ -> AppConnectResult.Connected(AppSignerPolicy.FULL_TRUST) },
                )

            val decision = authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "x"))

            assertTrue(decision is Nip46ConnectDecision.Accept)
            assertEquals(AppSignerPolicy.FULL_TRUST, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun cancelledConnectConsentRejectsAndStoresNoPolicy() =
        runTest {
            val ledger = ledger()
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    connectConsent = { _, _, _ -> AppConnectResult.Cancelled },
                )

            val decision = authorizer.onConnect(client, BunkerRequestConnect(id = "1", remoteKey = client, secret = "x"))

            assertTrue(decision is Nip46ConnectDecision.Reject)
            assertEquals(null, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun parsePermsMapsTokensToOpsAndIgnoresTheRest() {
        val ops =
            Nip46PermissionAuthorizer.parsePerms(
                "sign_event:1, sign_event:5, nip44_encrypt, nip04_decrypt, get_public_key, connect, sign_event",
            )
        assertEquals(
            listOf(
                NostrSignerOp.SignKind(1),
                NostrSignerOp.SignKind(5),
                NostrSignerOp.Encrypt,
                NostrSignerOp.Decrypt,
            ),
            ops,
            "known tokens map to ops (encrypt/decrypt collapse NIP-04+44); get_public_key/connect/kindless sign_event are dropped",
        )
    }

    @Test
    fun parsePermsDeduplicatesAndHandlesBlank() {
        assertTrue(Nip46PermissionAuthorizer.parsePerms(null).isEmpty())
        assertTrue(Nip46PermissionAuthorizer.parsePerms("").isEmpty())
        assertEquals(
            listOf(NostrSignerOp.Encrypt),
            Nip46PermissionAuthorizer.parsePerms("nip04_encrypt,nip44_encrypt"),
            "NIP-04 and NIP-44 encrypt both map to Encrypt and are de-duplicated",
        )
    }

    @Test
    fun coordinateRoundTrips() {
        assertEquals(client, Nip46PermissionAuthorizer.clientPubKeyOf(coordinate))
        assertEquals(null, Nip46PermissionAuthorizer.clientPubKeyOf("browser:https://x.com"))
    }

    @Test
    fun sameClientOnTwoAccountsGetsIndependentCoordinates() {
        val otherSigner = "d".repeat(64)
        val a = Nip46PermissionAuthorizer.coordinateFor(signer, client)
        val b = Nip46PermissionAuthorizer.coordinateFor(otherSigner, client)
        assertTrue(a != b)
        assertTrue(Nip46PermissionAuthorizer.belongsTo(a, signer))
        assertFalse(Nip46PermissionAuthorizer.belongsTo(a, otherSigner))
    }

    @Test
    fun logoutRevokesTheClientsGrant() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            authorizer.onLogout(client)

            assertEquals(null, ledger.store.loadPolicy(coordinate))
        }

    @Test
    fun forgetClearsGrantAndStoreAndSignalsDisconnect() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)
            val store = InMemoryNip46ClientStore()
            store.store(coordinate, Nip46ClientInfo(name = "X", relays = setOf("wss://relay.example.com")))
            var disconnected: String? = null
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    clientStore = store,
                    onDisconnected = { disconnected = it },
                )

            authorizer.forget(client)

            assertEquals(null, ledger.store.loadPolicy(coordinate), "grant cleared")
            assertEquals(null, store.load(coordinate), "stored metadata + relays cleared")
            assertEquals(client, disconnected, "host notified so it can drop the relays this session")
        }

    @Test
    fun pruneIdleForgetsAppsPastTheCutoffAndKeepsActiveOnes() =
        runTest {
            val ledger = ledger()
            val store = InMemoryNip46ClientStore()
            val idle = "b".repeat(64)
            val active = "e".repeat(64)
            val idleCoord = Nip46PermissionAuthorizer.coordinateFor(signer, idle)
            val activeCoord = Nip46PermissionAuthorizer.coordinateFor(signer, active)
            ledger.setPolicy(idleCoord, AppSignerPolicy.REASONABLE)
            ledger.setPolicy(activeCoord, AppSignerPolicy.REASONABLE)
            store.store(idleCoord, Nip46ClientInfo(name = "Idle", relays = setOf("wss://idle.example.com")))
            store.store(activeCoord, Nip46ClientInfo(name = "Active", relays = setOf("wss://active.example.com")))

            val now = 1_000_000L
            val idleSeconds = 100L
            ledger.updateLastUsed(idleCoord, now - idleSeconds - 1)
            ledger.updateLastUsed(activeCoord, now - idleSeconds + 1)

            val disconnected = mutableListOf<String>()
            val authorizer =
                Nip46PermissionAuthorizer(
                    ledger,
                    signerPubKey = signer,
                    validateSecret = { _, _ -> true },
                    clientStore = store,
                    onDisconnected = { disconnected.add(it) },
                )

            val pruned = authorizer.pruneIdle(idleSeconds, now = now)

            assertEquals(listOf(idle), pruned, "only the idle app is forgotten")
            assertEquals(listOf(idle), disconnected, "host notified for the idle app so its relay is dropped")
            assertEquals(null, ledger.store.loadPolicy(idleCoord), "idle grant cleared")
            assertEquals(AppSignerPolicy.REASONABLE, ledger.store.loadPolicy(activeCoord), "active grant kept")
            assertEquals(null, store.load(idleCoord), "idle metadata cleared")
        }

    @Test
    fun pruneIdleIgnoresOtherAccountsApps() =
        runTest {
            val ledger = ledger()
            val otherSigner = "f".repeat(64)
            val otherCoord = Nip46PermissionAuthorizer.coordinateFor(otherSigner, client)
            ledger.setPolicy(otherCoord, AppSignerPolicy.REASONABLE)
            ledger.updateLastUsed(otherCoord, 0L)
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true })

            val pruned = authorizer.pruneIdle(100L, now = 1_000_000L)

            assertTrue(pruned.isEmpty(), "an app belonging to another account is never pruned by this signer")
            assertEquals(AppSignerPolicy.REASONABLE, ledger.store.loadPolicy(otherCoord))
        }

    @Test
    fun logoutIsEquivalentToForget() =
        runTest {
            val ledger = ledger()
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)
            val store = InMemoryNip46ClientStore()
            store.store(coordinate, Nip46ClientInfo(name = "X"))
            val authorizer = Nip46PermissionAuthorizer(ledger, signerPubKey = signer, validateSecret = { _, _ -> true }, clientStore = store)

            authorizer.onLogout(client)

            assertEquals(null, ledger.store.loadPolicy(coordinate))
            assertEquals(null, store.load(coordinate))
        }
}
