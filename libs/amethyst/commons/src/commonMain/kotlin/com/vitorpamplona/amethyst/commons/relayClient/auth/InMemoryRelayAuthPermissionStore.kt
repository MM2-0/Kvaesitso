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

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurposeKind
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthDecision
import com.vitorpamplona.amethyst.commons.relayauth.RelayAuthPermissionStore

/**
 * Volatile, non-persistent [RelayAuthPermissionStore]. Used as the default for [com.vitorpamplona.amethyst.commons.model.Account]
 * instances built without a disk-backed store (Compose previews, unit tests, the mock account view
 * models) so nothing on the auth path has to null-check the store.
 */
class InMemoryRelayAuthPermissionStore : RelayAuthPermissionStore {
    private val decisions = mutableMapOf<String, RelayAuthDecision>()
    private val rationale = mutableMapOf<String, MutableMap<AuthPurposeKind, MutableSet<String>>>()

    override suspend fun loadDecision(relayUrl: String): RelayAuthDecision? = decisions[relayUrl]

    override suspend fun storeDecision(
        relayUrl: String,
        decision: RelayAuthDecision,
    ) {
        decisions[relayUrl] = decision
    }

    override suspend fun clearDecision(relayUrl: String) {
        decisions.remove(relayUrl)
    }

    override suspend fun allDecisions(): Map<String, RelayAuthDecision> = decisions.toMap()

    override suspend fun recordUse(
        relayUrl: String,
        additions: Map<AuthPurposeKind, Set<String>>,
    ) {
        val forRelay = rationale.getOrPut(relayUrl) { mutableMapOf() }
        for ((kind, pubkeys) in additions) {
            forRelay.getOrPut(kind) { mutableSetOf() }.addAll(pubkeys)
        }
    }

    override suspend fun loadRationale(relayUrl: String): Map<AuthPurposeKind, Set<String>> = rationale[relayUrl]?.mapValues { it.value.toSet() } ?: emptyMap()

    override suspend fun allRationales(): Map<String, Map<AuthPurposeKind, Set<String>>> = rationale.mapValues { entry -> entry.value.mapValues { it.value.toSet() } }

    override suspend fun clearRationale(relayUrl: String) {
        rationale.remove(relayUrl)
    }
}
