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

import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthPermissionLedger
import com.vitorpamplona.amethyst.commons.relayClient.auth.RelayAuthSessionGrants
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthContext
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** In-memory [RelayAuthPermissionStore] that unions rationale like the DataStore impl does. */
private class InMemoryStore : RelayAuthPermissionStore {
    private val overrides = mutableMapOf<String, RelayAuthDecision>()
    private val rationale = mutableMapOf<String, MutableMap<AuthPurposeKind, MutableSet<String>>>()

    override suspend fun loadDecision(relayUrl: String) = overrides[relayUrl]

    override suspend fun storeDecision(
        relayUrl: String,
        decision: RelayAuthDecision,
    ) {
        overrides[relayUrl] = decision
    }

    override suspend fun clearDecision(relayUrl: String) {
        overrides.remove(relayUrl)
    }

    override suspend fun allDecisions() = overrides.toMap()

    override suspend fun recordUse(
        relayUrl: String,
        additions: Map<AuthPurposeKind, Set<String>>,
    ) {
        val forRelay = rationale.getOrPut(relayUrl) { mutableMapOf() }
        for ((kind, pubkeys) in additions) {
            forRelay.getOrPut(kind) { mutableSetOf() }.addAll(pubkeys)
        }
    }

    override suspend fun loadRationale(relayUrl: String) = rationale[relayUrl]?.mapValues { it.value.toSet() } ?: emptyMap()
}

class RelayAuthGrantRationaleTest {
    private val relay = "wss://inbox.relay.test"
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun ledger(store: RelayAuthPermissionStore) = RelayAuthPermissionLedger(store, { RelayAuthPolicy.CUSTOM }, RelayAuthSessionGrants())

    @Test
    fun recordsCounterpartiesGroupedByPurpose() =
        runTest {
            val store = InMemoryStore()
            ledger(store).recordGrant(
                RelayAuthContext(
                    relay,
                    listOf(
                        AuthPurpose(AuthPurposeKind.SEND_DM, setOf(alice)),
                        AuthPurpose(AuthPurposeKind.READ_OUTBOX, setOf(bob)),
                    ),
                ),
            )

            assertEquals(
                mapOf(
                    AuthPurposeKind.SEND_DM to setOf(alice),
                    AuthPurposeKind.READ_OUTBOX to setOf(bob),
                ),
                store.loadRationale(relay),
            )
        }

    @Test
    fun mergesNewCounterpartiesAcrossGrants() =
        runTest {
            val store = InMemoryStore()
            val ledger = ledger(store)

            ledger.recordGrant(RelayAuthContext(relay, listOf(AuthPurpose(AuthPurposeKind.SEND_DM, setOf(alice)))))
            ledger.recordGrant(RelayAuthContext(relay, listOf(AuthPurpose(AuthPurposeKind.SEND_DM, setOf(carol)))))

            assertEquals(mapOf(AuthPurposeKind.SEND_DM to setOf(alice, carol)), store.loadRationale(relay))
        }

    @Test
    fun ignoresPurposesWithoutCounterparties() =
        runTest {
            val store = InMemoryStore()
            ledger(store).recordGrant(RelayAuthContext(relay, listOf(AuthPurpose(AuthPurposeKind.MY_OWN_RELAY))))

            assertEquals(emptyMap<AuthPurposeKind, Set<String>>(), store.loadRationale(relay))
        }
}
