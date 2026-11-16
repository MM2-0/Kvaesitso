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
package com.vitorpamplona.amethyst.commons.connectedApps.signers

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NostrSignerPermissionLedgerTest {
    private val coordinate = "aa".repeat(32) + ":demo"

    @Test
    fun reasonableAutoApprovesAdditivePublicContentKinds() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)

            // Every kind in the curated set auto-allows; encryption too.
            for (kind in NostrSignerPermissionLedger.REASONABLE_SIGN_KINDS) {
                assertEquals(
                    NostrOpDecision.ALLOW,
                    ledger.decide(coordinate, NostrSignerOp.SignKind(kind)),
                    "kind $kind should be auto-approved under REASONABLE",
                )
            }
            assertEquals(NostrOpDecision.ALLOW, ledger.decide(coordinate, NostrSignerOp.Encrypt))
        }

    @Test
    fun reasonableStillAsksForRiskyKindsAndDecryption() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)

            // Profile (0), contacts (3), deletion (5), relay list (10002), nutzap (9321), NIP-98 HTTP
            // auth (27235), relay auth (22242), and gift-wrapped DM (1059) must never auto-sign — they
            // change config, delete, spend ecash, authorize arbitrary HTTP calls, authenticate as the
            // user, or leak. These are replaceable *configuration* (0/3/10002), unlike addressable
            // *content* such as long-form (30023) which is allowed. A nutzap in particular *is* the
            // payment (it carries the ecash proofs), unlike a zap request (9734) which only fetches an
            // invoice. Decryption reveals private content, so it also asks. A report (1984) accuses
            // someone else in public, and web-of-trust apps score people by them: it asks too.
            for (kind in listOf(0, 3, 5, 10002, 9321, 27235, 22242, 1059, 1984)) {
                assertEquals(
                    NostrOpDecision.ASK,
                    ledger.decide(coordinate, NostrSignerOp.SignKind(kind)),
                    "kind $kind must still prompt under REASONABLE",
                )
            }
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.Decrypt))

            // Contrast: a Lightning zap request (9734) auto-signs (the payment prompts separately),
            // but an ecash nutzap (9321) does not — publishing it spends the tokens.
            assertEquals(NostrOpDecision.ALLOW, ledger.decide(coordinate, NostrSignerOp.SignKind(9734)))
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.SignKind(9321)))

            // NIP-42 relay auth (22242) must ASK. Being replay-bound is not the protection it looks
            // like: the requesting app supplies the relay and challenge tags, so it can obtain a FRESH
            // signature naming any relay and then AUTH there as the user — reading whatever that relay
            // gates behind AUTH (the kind-1059 giftwrap inbox and its DM metadata) and spending the
            // quota on paid relays, which bill whoever authenticates.
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.SignKind(22242)))
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.SignKind(27235)))
        }

    @Test
    fun fullTrustAllowsEveryKindAndDecryption() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            ledger.setPolicy(coordinate, AppSignerPolicy.FULL_TRUST)

            assertEquals(NostrOpDecision.ALLOW, ledger.decide(coordinate, NostrSignerOp.SignKind(9999)))
            assertEquals(NostrOpDecision.ALLOW, ledger.decide(coordinate, NostrSignerOp.Decrypt))
        }

    @Test
    fun paranoidAsksForEverything() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            ledger.setPolicy(coordinate, AppSignerPolicy.PARANOID)

            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.SignKind(1)))
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.Encrypt))
        }

    @Test
    fun noPolicyAsks() =
        runTest {
            val ledger = NostrSignerPermissionLedger(InMemoryNostrSignerPermissionStore())
            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.SignKind(1)))
        }

    // ---------------------------------------------------------------------
    // Per-counterparty decrypt op
    // ---------------------------------------------------------------------

    @Test
    fun decryptFromRoundTripsThroughItsStorageKeyWithoutColliding() {
        val alice = "1".repeat(64)
        val op = NostrSignerOp.DecryptFrom(alice)

        assertEquals("decrypt:$alice", op.key)
        // The broad grant keeps its historical bare key, so no stored decision migrates.
        assertEquals("decrypt", NostrSignerOp.Decrypt.key)
        assertEquals(op, NostrSignerOp.fromKey(op.key))
        assertEquals(NostrSignerOp.Decrypt, NostrSignerOp.fromKey("decrypt"))
        assertEquals(null, NostrSignerOp.fromKey("decrypt:"))
    }

    @Test
    fun decryptFromAlwaysAsksUnderReasonableSoItIsOnlyEverGrantedExplicitly() =
        runTest {
            val store = InMemoryNostrSignerPermissionStore()
            val ledger = NostrSignerPermissionLedger(store)
            val coordinate = "nip46:a:b"
            ledger.setPolicy(coordinate, AppSignerPolicy.REASONABLE)

            assertEquals(NostrOpDecision.ASK, ledger.decide(coordinate, NostrSignerOp.DecryptFrom("1".repeat(64))))
        }
}
